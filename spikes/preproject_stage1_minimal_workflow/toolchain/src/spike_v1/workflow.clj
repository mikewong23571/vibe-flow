(ns spike-v1.workflow
  (:require
   [clojure.pprint :refer [pprint]]
   [spike-v1.install :as install]
   [spike-v1.launcher.codex :as codex]
   [spike-v1.launcher.mock :as mock]
   [spike-v1.mgr :as mgr]
   [spike-v1.mgr-store :as mgr-store]
   [spike-v1.model :as model]
   [spike-v1.run-store :as run-store]
   [spike-v1.task-store :as task-store]
   [spike-v1.util :as util]))

(defn launch-worker [launcher install-record worker task run]
  (case launcher
    :mock (mock/launch! worker task run)
    :codex (codex/launch! install-record worker task run)
    (throw (ex-info "unknown launcher" {:launcher launcher}))))

(defn task-by-id [tasks task-id]
  (first (filter #(= task-id (:id %)) tasks)))

(defn replace-task [tasks updated-task]
  (mapv #(if (= (:id %) (:id updated-task)) updated-task %) tasks))

(defn apply-mgr-decision! [task mgr-run decision reason worker-launcher]
  (let [install-record (install/load-install!)
        tasks (task-store/load-tasks)
        mgr-result {:ok? true
                    :decision decision
                    :reason reason
                    :message (str "DECISION: " (name decision) "\nREASON: " reason)}
        finalized-mgr-run (mgr-store/finalize-mgr-run! mgr-run mgr-result)
        task-after-mgr (task-store/merge-mgr-update task finalized-mgr-run)]
    (mgr-store/write-mgr-run! finalized-mgr-run)
    (cond
      (= decision :done)
      (let [updated-task (assoc task-after-mgr :stage :done :updated-at (util/now))]
        (task-store/save-tasks! (replace-task tasks updated-task))
        {:task-id (:id task)
         :decision decision
         :next-stage :done
         :mgr-run-id (:id mgr-run)
         :message "Task marked done by mgr."})

      (= decision :error)
      (let [updated-task (assoc task-after-mgr
                                :stage :error
                                :error-output reason
                                :updated-at (util/now))]
        (task-store/save-tasks! (replace-task tasks updated-task))
        {:task-id (:id task)
         :decision decision
         :next-stage :error
         :mgr-run-id (:id mgr-run)
         :message "Task marked error by mgr."})

      :else
      (let [prepared (run-store/prepare-run! task-after-mgr decision worker-launcher)
            result (launch-worker worker-launcher install-record decision task-after-mgr prepared)
            run (run-store/finalize-run! task-after-mgr prepared result)
            updated-task (task-store/merge-task-update task-after-mgr run)]
        (run-store/write-run! run)
        (task-store/save-tasks! (replace-task tasks updated-task))
        {:task-id (:id task)
         :decision decision
         :worker decision
         :run-id (:id run)
         :mgr-run-id (:id mgr-run)
         :next-stage (:stage updated-task)
         :worktree (:worktree-dir run)
         :message "Task advanced via workflow CLI."}))))

(defn mgr-advance! [task-id mgr-run-id worker-launcher decision reason]
  (let [tasks (task-store/load-tasks)
        task (task-by-id tasks task-id)
        mgr-run (or (mgr-store/load-mgr-run mgr-run-id)
                    {:id mgr-run-id
                     :task-id task-id
                     :launcher :external
                     :workdir nil
                     :prompt-path nil
                     :output-path nil
                     :started-at (util/now)})]
    (when-not task
      (throw (ex-info "task not found" {:task-id task-id})))
    (apply-mgr-decision! task mgr-run decision reason worker-launcher)))

(defn run-once!
  ([worker-launcher]
   (run-once! worker-launcher worker-launcher))
  ([worker-launcher mgr-launcher]
   (let [tasks (task-store/load-tasks)
         task (task-store/select-task tasks)]
     (if-not task
       (println "No runnable task found.")
       (let [mgr-run (mgr-store/prepare-mgr-run! task mgr-launcher)
             _ (mgr-store/write-mgr-run! mgr-run)
             mgr-result (mgr/drive-task! mgr-launcher task mgr-run worker-launcher)
             updated-task (task-by-id (task-store/load-tasks) (:id task))]
         (if-not (:ok? mgr-result)
           (do
             (println "Mgr failed for task" (:id task))
             (println "Task state unchanged or workflow CLI failed."))
           (do
             (println "Mgr launcher:" mgr-launcher)
             (println "Worker launcher:" worker-launcher)
             (println "Mgr advanced task through workflow CLI for" (:id task))
             (println "Mgr stdout:" (:message mgr-result))
             (when updated-task
               (println "Current stage:" (:stage updated-task))
               (println "Latest mgr decision:" (:latest-mgr-decision updated-task))
               (println "Latest run:" (:latest-run updated-task))))))))))

(defn run-loop!
  ([worker-launcher max-steps]
   (run-loop! worker-launcher worker-launcher max-steps))
  ([worker-launcher mgr-launcher max-steps]
   (dotimes [_ max-steps]
     (let [task (task-store/select-task (task-store/load-tasks))]
       (when task
         (run-once! worker-launcher mgr-launcher))))))

(defn show-state! []
  (println "\nInstall:")
  (pprint (install/load-install!))
  (println "\nTasks:")
  (pprint (task-store/load-tasks))
  (println "\nMgr Runs:")
  (doseq [path (mgr-store/mgr-run-files)]
    (println path)
    (pprint (util/read-edn path nil)))
  (println "\nRuns:")
  (doseq [path (run-store/run-files)]
    (println path)
    (pprint (util/read-edn path nil))))

(defn smoke! []
  (install/install!)
  (task-store/seed-sample!)
  (run-loop! :mock :mock 4)
  (show-state!))
