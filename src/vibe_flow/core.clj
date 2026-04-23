(ns vibe-flow.core
  (:require
   [vibe-flow.management.domain :as domain]
   [vibe-flow.management.task-type :as task-type-manager]
   [vibe-flow.platform.state.mgr-run-store :as mgr-run-store]
   [vibe-flow.platform.state.run-store :as run-store]
   [vibe-flow.platform.state.system-store :as system-store]
   [vibe-flow.platform.target.paths :as paths]
   [vibe-flow.workflow.control :as workflow-control]))

(defn inspect-task-type [target-root task-type]
  (task-type-manager/inspect-task-type target-root task-type))

(defn latest-task-run [target-root task]
  (or (when-let [run-id (:latest-run task)]
        (run-store/load-run target-root run-id))
      (last (run-store/task-runs target-root (:id task)))))

(defn latest-task-mgr-run [target-root task]
  (or (when-let [mgr-run-id (:latest-mgr-run task)]
        (mgr-run-store/load-mgr-run target-root mgr-run-id))
      (->> (mgr-run-store/load-mgr-runs target-root)
           (filter #(= (:id task) (:task-id %)))
           last)))

(defn inspect-task [target-root task-id]
  (let [task (domain/inspect-task target-root task-id)]
    (when task
      {:task task
       :collection (domain/inspect-collection target-root (:collection-id task))
       :task-type (inspect-task-type target-root (:task-type task))
       :latest-run (latest-task-run target-root task)
       :latest-mgr-run (latest-task-mgr-run target-root task)})))

(defn inspect-run [target-root run-id]
  (let [run (run-store/load-run target-root run-id)]
    (when run
      {:run run
       :task (domain/inspect-task target-root (:task-id run))
       :task-type (inspect-task-type target-root (:task-type run))})))

(defn inspect-mgr-run [target-root mgr-run-id]
  (let [mgr-run (mgr-run-store/load-mgr-run target-root mgr-run-id)]
    (when mgr-run
      {:mgr-run mgr-run
       :task (domain/inspect-task target-root (:task-id mgr-run))
       :task-type (inspect-task-type target-root (:task-type mgr-run))})))

(defn recovery-overview [target-root]
  (let [runs (run-store/load-runs target-root)
        mgr-runs (mgr-run-store/load-mgr-runs target-root)
        tasks (domain/list-tasks target-root)]
    {:unfinished-runs (->> runs
                           (filter #(nil? (:ended-at %)))
                           (map :id)
                           vec)
     :unfinished-mgr-runs (->> mgr-runs
                               (filter #(nil? (:ended-at %)))
                               (map :id)
                               vec)
     :runnable-task-id (:id (workflow-control/select-runnable-task target-root))
     :task-count (count tasks)}))

(defn show-state [target-root]
  {:system {:install (system-store/load-install target-root)
            :target (system-store/load-target target-root)
            :layout (system-store/load-layout target-root)
            :toolchain (system-store/load-toolchain target-root)
            :task-types-registry (task-type-manager/list-task-types target-root)}
   :domain {:collections (domain/list-collections target-root)
            :tasks (domain/list-tasks target-root)}
   :runtime {:mgr-runs (mgr-run-store/load-mgr-runs target-root)
             :runs (run-store/load-runs target-root)}
   :recovery (recovery-overview target-root)})

(defn app-overview
  ([] (app-overview "."))
  ([target-root]
   {:surface :formal-product
    :available {:show-state show-state
                :inspect-task inspect-task
                :inspect-run inspect-run
                :inspect-mgr-run inspect-mgr-run
                :inspect-task-type inspect-task-type
                :recovery-overview recovery-overview}
    :layout-root (str (paths/workflow-root target-root))}))
