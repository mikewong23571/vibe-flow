(ns spike-v3.core.workflow
  (:require
   [clojure.pprint :refer [pprint]]
   [spike-v3.agent.launcher :as launcher]
   [spike-v3.agent.mgr :as mgr]
   [spike-v3.core.task-lifecycle :as lifecycle]
   [spike-v3.management.task-type-manager :as task-type-manager]
   [spike-v3.sample.demo :as sample-demo]
   [spike-v3.state.collection-store :as collection-store]
   [spike-v3.state.mgr-store :as mgr-store]
   [spike-v3.state.run-store :as run-store]
   [spike-v3.state.task-store :as task-store]
   [spike-v3.support.paths :as paths]
   [spike-v3.support.util :as util]
   [spike-v3.target.install :as install]
   [spike-v3.target.repo :as target-repo]))

(defn apply-mgr-decision! [target-root task mgr-run decision reason worker-launcher]
  (let [mgr-result {:ok? true
                    :decision decision
                    :reason reason
                    :message (str "DECISION: " (name decision) "\nREASON: " reason)}
        finalized-mgr-run (mgr-store/finalize-mgr-run! mgr-run mgr-result)
        task-after-mgr (lifecycle/merge-mgr-update task finalized-mgr-run)]
    (mgr-store/write-mgr-run! target-root finalized-mgr-run)
    (task-store/save-task! target-root task-after-mgr)
    (cond
      (= decision :done)
      (let [updated-task (assoc task-after-mgr :stage :done :updated-at (util/now))]
        (task-store/save-task! target-root updated-task)
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
        (task-store/save-task! target-root updated-task)
        {:task-id (:id task)
         :decision decision
         :next-stage :error
         :mgr-run-id (:id mgr-run)
         :message "Task marked error by mgr."})

      :else
      (let [prepared (run-store/prepare-run! target-root task-after-mgr decision worker-launcher)
            result (launcher/launch! target-root worker-launcher decision task-after-mgr prepared)
            run (run-store/finalize-run! task-after-mgr prepared result)
            updated-task (lifecycle/merge-task-update task-after-mgr run)]
        (run-store/write-run! target-root run)
        (task-store/save-task! target-root updated-task)
        {:task-id (:id task)
         :decision decision
         :worker decision
         :run-id (:id run)
         :mgr-run-id (:id mgr-run)
         :next-stage (:stage updated-task)
         :worktree (:worktree-dir run)
         :message "Task advanced via workflow CLI."}))))

(defn mgr-advance! [target-root task-id mgr-run-id worker-launcher decision reason]
  (let [task (task-store/load-task target-root task-id)
        mgr-run (or (mgr-store/load-mgr-run target-root mgr-run-id)
                    {:id mgr-run-id
                     :task-id task-id
                     :launcher :external
                     :workdir nil
                     :prompt-path nil
                     :output-path nil
                     :started-at (util/now)})]
    (when-not task
      (throw (ex-info "task not found" {:task-id task-id})))
    (apply-mgr-decision! target-root task mgr-run decision reason worker-launcher)))

(defn run-once!
  ([target-root worker-launcher]
   (run-once! target-root worker-launcher worker-launcher))
  ([target-root worker-launcher mgr-launcher]
   (let [tasks (task-store/load-tasks target-root)
         task (lifecycle/select-task tasks)]
     (if-not task
       (println "No runnable task found.")
       (let [mgr-run (mgr-store/prepare-mgr-run! target-root task mgr-launcher)
             _ (mgr-store/write-mgr-run! target-root mgr-run)
             mgr-result (mgr/drive-task! target-root mgr-launcher task mgr-run worker-launcher)
             updated-task (task-store/load-task target-root (:id task))]
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
  ([target-root worker-launcher max-steps]
   (run-loop! target-root worker-launcher worker-launcher max-steps))
  ([target-root worker-launcher mgr-launcher max-steps]
   (dotimes [_ max-steps]
     (let [task (lifecycle/select-task (task-store/load-tasks target-root))]
       (when task
         (run-once! target-root worker-launcher mgr-launcher))))))

(defn show-state! [target-root]
  (println "\nInstall:")
  (pprint (install/load-install! target-root))
  (println "\nTarget:")
  (pprint (install/load-target! target-root))
  (println "\nLayout:")
  (pprint (util/read-edn (paths/layout-path target-root) nil))
  (println "\nTask Types Registry:")
  (pprint (task-type-manager/list-task-types target-root))
  (println "\nCollections:")
  (pprint (collection-store/load-collections target-root))
  (println "\nTasks:")
  (pprint (task-store/load-tasks target-root))
  (println "\nMgr Runs:")
  (doseq [path (mgr-store/mgr-run-files target-root)]
    (println path)
    (pprint (util/read-edn path nil)))
  (println "\nRuns:")
  (doseq [path (run-store/run-files target-root)]
    (println path)
    (pprint (util/read-edn path nil))))

(defn smoke! [target-root]
  (target-repo/ensure-sample-target! target-root)
  (install/install! target-root)
  (sample-demo/seed! target-root)
  (run-loop! target-root :mock :mock 4)
  (show-state! target-root))
