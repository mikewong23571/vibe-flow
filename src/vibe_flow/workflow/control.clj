(ns vibe-flow.workflow.control
  (:require
   [vibe-flow.definition.task-type :as task-type]
   [vibe-flow.platform.runtime.launcher :as launcher]
   [vibe-flow.platform.runtime.mgr-run :as mgr-run]
   [vibe-flow.platform.runtime.run :as run]
   [vibe-flow.platform.state.mgr-run-store :as mgr-run-store]
   [vibe-flow.platform.state.task-store :as task-store]
   [vibe-flow.platform.state.task-runtime-store :as task-runtime-store]
   [vibe-flow.platform.state.run-store :as run-store]
   [vibe-flow.platform.support.time :as time]))

(defn task-stage [task]
  (or (:stage task) :todo))

(defn hydrate-task [target-root task]
  (task-runtime-store/hydrate-task target-root task))

(defn load-task-view [target-root task-id]
  (hydrate-task target-root (task-store/load-task target-root task-id)))

(defn save-task-state! [target-root task]
  (task-store/save-task! target-root task)
  (task-runtime-store/save-task-runtime! target-root
                                         (:id task)
                                         (task-runtime-store/runtime-task task))
  task)

(defn terminal-stage? [stage]
  (contains? #{:done :error} stage))

(defn parse-instant [text]
  (when text
    (java.time.Instant/parse text)))

(defn expected-mgr-decision [target-root task]
  (task-type/worker-for-stage target-root task))

(defn allowed-mgr-decisions [target-root task]
  (cond-> #{:done :error}
    (expected-mgr-decision target-root task)
    (conj (expected-mgr-decision target-root task))))

(defn validate-mgr-decision! [target-root task decision]
  (let [allowed (allowed-mgr-decisions target-root task)]
    (when-not (contains? allowed decision)
      (throw (ex-info "Unsupported mgr decision for the current task stage."
                      {:task-id (:id task)
                       :task-stage (task-stage task)
                       :decision decision
                       :allowed-decisions (vec (sort-by name allowed))})))
    decision))

(defn stale-mgr-run? [task mgr-run-record]
  (let [mgr-started-at (parse-instant (:started-at mgr-run-record))
        task-updated-at (parse-instant (:updated-at task))]
    (and (:latest-mgr-run task)
         (not= (:latest-mgr-run task) (:id mgr-run-record))
         mgr-started-at
         task-updated-at
         (.isBefore ^java.time.Instant mgr-started-at task-updated-at))))

(defn next-stage [worker result]
  (cond
    (not (:ok? result)) :error
    (= worker :impl) :awaiting-review
    (= worker :refine) :awaiting-review
    (= worker :review)
    (case (:control result)
      :pass :done
      :needs-refine :awaiting-refine
      :error :error
      :unknown :error
      :error)
    :else :error))

(defn merge-mgr-update [task mgr-run-record]
  (let [result (:result mgr-run-record)]
    (assoc task
           :latest-mgr-run (:id mgr-run-record)
           :latest-mgr-decision (:decision result)
           :latest-mgr-output (:message result)
           :mgr-count (inc (or (:mgr-count task) 0))
           :updated-at (time/now))))

(defn merge-run-update [task run-record]
  (let [result (:result run-record)
        worker (:worker run-record)]
    (cond-> (assoc task
                   :stage (next-stage worker result)
                   :repo-head (get-in run-record [:heads :output])
                   :latest-run (:id run-record)
                   :latest-worker worker
                   :latest-worker-output (:message result)
                   :latest-worker-control (:control result)
                   :latest-worktree (get-in run-record [:worktree :dir])
                   :run-count (inc (or (:run-count task) 0))
                   :updated-at (time/now))
      (= worker :review)
      (assoc :review-count (inc (or (:review-count task) 0))
             :latest-review-output (:message result))

      (:error? run-record)
      (assoc :error-output (:message result)))))

(defn task-error-update [task message]
  (assoc task
         :stage :error
         :error-output message
         :updated-at (time/now)))

(defn unfinished-task-ids [target-root]
  (let [unfinished-runs (->> (run-store/load-runs target-root)
                             (filter #(nil? (:ended-at %)))
                             (map :task-id))
        unfinished-mgr-runs (->> (mgr-run-store/load-mgr-runs target-root)
                                 (filter #(nil? (:ended-at %)))
                                 (map :task-id))]
    (set (concat unfinished-runs unfinished-mgr-runs))))

(defn task-matches-filter? [task task-type]
  (or (nil? task-type)
      (= (:task-type task) (task-type/task-type-id task-type))))

(defn select-runnable-task
  ([target-root]
   (select-runnable-task target-root {}))
  ([target-root {:keys [task-type]}]
   (let [blocked-task-ids (unfinished-task-ids target-root)]
     (first (filter #(and (task-matches-filter? % task-type)
                          (not (terminal-stage? (task-stage %)))
                          (not (contains? blocked-task-ids (:id %))))
                    (task-store/load-tasks target-root))))))

(defn create-mgr-run!
  ([target-root task launcher]
   (create-mgr-run! target-root task launcher launcher))
  ([target-root task launcher worker-launcher]
   (let [task (hydrate-task target-root task)
         record (mgr-run/prepare-mgr-run! target-root task launcher worker-launcher)]
     (mgr-run-store/save-mgr-run! target-root record)
     record)))

(defn mock-mgr-result [target-root task]
  (if-let [worker (task-type/worker-for-stage target-root task)]
    {:ok? true
     :decision worker
     :message (str "DECISION: " (name worker) "\nREASON: mock mgr followed current task stage.")}
    {:ok? true
     :decision :error
     :message "DECISION: error\nREASON: no worker is defined for the current task stage."}))

(defn mgr-run-summary [target-root task-id mgr-run-id]
  (let [task (load-task-view target-root task-id)
        mgr-run-record (mgr-run-store/load-mgr-run target-root mgr-run-id)
        latest-run-id (:latest-run task)
        latest-run (when latest-run-id
                     (run-store/load-run target-root latest-run-id))]
    {:task-id task-id
     :mgr-run-id mgr-run-id
     :decision (get-in mgr-run-record [:result :decision])
     :next-stage (:stage task)
     :run-id (:latest-run task)
     :worker (:latest-worker task)
     :worktree (get-in latest-run [:worktree :dir])}))

(defn finalize-mgr-launch-error! [target-root task mgr-run-record launch-result]
  (let [task (hydrate-task target-root task)
        finalized-mgr-run (mgr-run/finalize-mgr-run! mgr-run-record launch-result)
        updated-task (task-error-update task (:message launch-result))]
    (mgr-run-store/save-mgr-run! target-root finalized-mgr-run)
    (save-task-state! target-root updated-task)
    {:task-id (:id updated-task)
     :mgr-run-id (:id finalized-mgr-run)
     :decision :error
     :next-stage :error}))

(defn mgr-launch-missing-callback-result [mgr-launcher]
  {:ok? false
   :control :error
   :message (str "mgr launcher exited without finalizing the mgr_run via callback (" (name mgr-launcher) ").")})

(defn launch-mgr! [target-root task mgr-run-record mgr-launcher]
  (case mgr-launcher
    :mock (mock-mgr-result target-root task)
    :codex (mgr-run/launch-mgr-codex! target-root task mgr-run-record)
    (throw (ex-info "Unsupported mgr launcher."
                    {:mgr-launcher mgr-launcher
                     :task-id (:id task)}))))

(defn assert-supported-mgr-launcher! [mgr-launcher task]
  (when-not (contains? #{:mock :codex} mgr-launcher)
    (throw (ex-info "Unsupported mgr launcher."
                    {:mgr-launcher mgr-launcher
                     :task-id (:id task)}))))

(defn apply-mgr-decision! [target-root task mgr-run-record decision message worker-launcher]
  (let [task (hydrate-task target-root task)
        mgr-result {:ok? true
                    :decision decision
                    :message message}
        finalized-mgr-run (mgr-run/finalize-mgr-run! mgr-run-record mgr-result)
        task-after-mgr (merge-mgr-update task finalized-mgr-run)]
    (mgr-run-store/save-mgr-run! target-root finalized-mgr-run)
    (save-task-state! target-root task-after-mgr)
    (cond
      (= decision :done)
      (let [updated-task (assoc task-after-mgr :stage :done :updated-at (time/now))]
        (save-task-state! target-root updated-task)
        {:task-id (:id updated-task)
         :decision decision
         :next-stage :done
         :mgr-run-id (:id finalized-mgr-run)})

      (= decision :error)
      (let [updated-task (assoc task-after-mgr
                                :stage :error
                                :error-output message
                                :updated-at (time/now))]
        (save-task-state! target-root updated-task)
        {:task-id (:id updated-task)
         :decision decision
         :next-stage :error
         :mgr-run-id (:id finalized-mgr-run)})

      :else
      (let [prepared-run (try
                           (run/prepare-run! target-root task-after-mgr decision worker-launcher)
                           (catch Exception ex
                             (save-task-state! target-root
                                               (task-error-update task-after-mgr (.getMessage ex)))
                             (throw ex)))
            _ (run-store/save-run! target-root prepared-run)
            launch-result (launcher/launch! target-root worker-launcher decision task-after-mgr prepared-run)
            finalized-run (run/finalize-run! task-after-mgr prepared-run launch-result)
            updated-task (merge-run-update task-after-mgr finalized-run)]
        (run-store/save-run! target-root finalized-run)
        (save-task-state! target-root updated-task)
        {:task-id (:id updated-task)
         :decision decision
         :worker decision
         :mgr-run-id (:id finalized-mgr-run)
         :run-id (:id finalized-run)
         :next-stage (:stage updated-task)
         :worktree (get-in finalized-run [:worktree :dir])}))))

(defn advance-task! [target-root task-id mgr-run-id decision message]
  (let [task (load-task-view target-root task-id)
        mgr-run-record (mgr-run-store/load-mgr-run target-root mgr-run-id)]
    (when-not task
      (throw (ex-info "Task not found."
                      {:task-id task-id})))
    (when-not mgr-run-record
      (throw (ex-info "mgr_run not found."
                      {:mgr-run-id mgr-run-id
                       :task-id task-id})))
    (when-not (= task-id (:task-id mgr-run-record))
      (throw (ex-info "mgr_run does not belong to the task."
                      {:mgr-run-id mgr-run-id
                       :mgr-run-task-id (:task-id mgr-run-record)
                       :task-id task-id})))
    (when (:ended-at mgr-run-record)
      (throw (ex-info "mgr_run is already finalized."
                      {:mgr-run-id mgr-run-id
                       :task-id task-id
                       :ended-at (:ended-at mgr-run-record)})))
    (when (stale-mgr-run? task mgr-run-record)
      (throw (ex-info "mgr_run is stale for the current task state."
                      {:mgr-run-id mgr-run-id
                       :task-id task-id
                       :task-stage (task-stage task)
                       :latest-mgr-run (:latest-mgr-run task)
                       :task-updated-at (:updated-at task)
                       :mgr-run-started-at (:started-at mgr-run-record)})))
    (let [worker-launcher (or (:worker-launcher mgr-run-record)
                              (:launcher mgr-run-record))]
      (when-not worker-launcher
        (throw (ex-info "mgr_run is missing a persisted worker launcher."
                        {:mgr-run-id mgr-run-id
                         :task-id task-id})))
      (validate-mgr-decision! target-root task decision)
      (apply-mgr-decision! target-root task mgr-run-record decision message worker-launcher))))

(defn run-once!
  ([target-root]
   (run-once! target-root :mock :mock))
  ([target-root worker-launcher mgr-launcher]
   (run-once! target-root worker-launcher mgr-launcher {}))
  ([target-root worker-launcher mgr-launcher options]
   (when-let [task (select-runnable-task target-root options)]
     (assert-supported-mgr-launcher! mgr-launcher task)
     (let [mgr-run-record (create-mgr-run! target-root task mgr-launcher worker-launcher)
           launch-result (launch-mgr! target-root task mgr-run-record mgr-launcher)]
       (if (= :mock mgr-launcher)
         (apply-mgr-decision! target-root
                              task
                              mgr-run-record
                              (:decision launch-result)
                              (:message launch-result)
                              worker-launcher)
         (let [updated-mgr-run (mgr-run-store/load-mgr-run target-root (:id mgr-run-record))]
           (cond
             (:ended-at updated-mgr-run)
             (mgr-run-summary target-root (:id task) (:id mgr-run-record))

             (:ok? launch-result)
             (finalize-mgr-launch-error! target-root
                                         task
                                         mgr-run-record
                                         (mgr-launch-missing-callback-result mgr-launcher))

             :else
             (finalize-mgr-launch-error! target-root task mgr-run-record launch-result))))))))

(defn run-loop!
  ([target-root max-steps]
   (run-loop! target-root :mock :mock max-steps))
  ([target-root worker-launcher mgr-launcher max-steps]
   (run-loop! target-root worker-launcher mgr-launcher max-steps {}))
  ([target-root worker-launcher mgr-launcher max-steps options]
   (loop [remaining max-steps
          results []]
     (if (or (zero? remaining) (nil? (select-runnable-task target-root options)))
       results
       (recur (dec remaining)
              (conj results (run-once! target-root worker-launcher mgr-launcher options)))))))

(def default-poll-interval-ms 5000)

(defn run-poll-loop!
  [target-root worker-launcher mgr-launcher
   {:keys [task-type poll-interval-ms max-steps max-idle-polls]
    :or {poll-interval-ms default-poll-interval-ms}}]
  (loop [steps 0
         idle-polls 0
         results []]
    (cond
      (and max-steps (>= steps max-steps))
      {:status :max-steps-reached
       :steps steps
       :idle-polls idle-polls
       :task-type task-type
       :results results}

      :else
      (if-let [result (run-once! target-root
                                 worker-launcher
                                 mgr-launcher
                                 {:task-type task-type})]
        (recur (inc steps) 0 (conj results result))
        (if (and max-idle-polls (>= idle-polls max-idle-polls))
          {:status :idle-timeout
           :steps steps
           :idle-polls idle-polls
           :task-type task-type
           :results results}
          (do
            (Thread/sleep poll-interval-ms)
            (recur steps (inc idle-polls) results)))))))

(defn control-blueprint []
  {:select-runnable-task select-runnable-task
   :create-mgr-run! create-mgr-run!
   :advance-task! advance-task!
   :run-once! run-once!
   :run-loop! run-loop!
   :run-poll-loop! run-poll-loop!})
