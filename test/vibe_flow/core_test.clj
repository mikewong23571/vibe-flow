(ns vibe-flow.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [vibe-flow.core :as core]
   [vibe-flow.management.domain :as domain]
   [vibe-flow.management.task-type :as task-type-manager]
   [vibe-flow.platform.runtime.run :as run]
   [vibe-flow.platform.state.mgr-run-store :as mgr-run-store]
   [vibe-flow.platform.state.run-store :as run-store]
   [vibe-flow.platform.target.paths :as paths]
   [vibe-flow.platform.target.install :as install]
   [vibe-flow.target.install-test :as install-fixture]
   [vibe-flow.workflow.control :as control]))

(deftest product-surface-inspects-state-and-recovery
  (let [target-root (install-fixture/init-git-target! (install-fixture/make-temp-dir))]
    (try
      (install/install! target-root)
      (task-type-manager/create-task-type! target-root :impl)
      (domain/create-collection! target-root
                                 {:id "impl-backlog"
                                  :task-type :impl
                                  :name "Implementation backlog"})
      (domain/create-task! target-root
                           {:id "impl-task-1"
                            :collection-id "impl-backlog"
                            :task-type :impl
                            :goal "Implement the feature."
                            :scope ["Edit src/ only."]
                            :constraints ["Do not change workflow metadata."]
                            :success-criteria ["Feature behavior is implemented."]})
      (testing "inspect-task tolerates tasks that have not run yet"
        (let [task-view (core/inspect-task target-root "impl-task-1")]
          (is (= "impl-task-1" (get-in task-view [:task :id])))
          (is (nil? (:latest-run task-view)))
          (is (nil? (:latest-mgr-run task-view)))))

      (testing "show-state exposes durable and runtime state plus recovery hints"
        (let [mgr-run (control/create-mgr-run! target-root
                                               (domain/inspect-task target-root "impl-task-1")
                                               :mock)
              pending-run (run/prepare-run! target-root
                                            (domain/inspect-task target-root "impl-task-1")
                                            :impl
                                            :mock)
              _ (run-store/save-run! target-root pending-run)
              state (core/show-state target-root)]
          (is (= 1 (count (get-in state [:system :task-types-registry]))))
          (is (= 1 (count (get-in state [:domain :collections]))))
          (is (= 1 (count (get-in state [:domain :tasks]))))
          (is (= 1 (count (get-in state [:runtime :mgr-runs]))))
          (is (= 1 (count (get-in state [:runtime :runs]))))
          (is (= [(:id pending-run)] (get-in state [:recovery :unfinished-runs])))
          (is (= [(:id mgr-run)] (get-in state [:recovery :unfinished-mgr-runs])))
          (is (nil? (get-in state [:recovery :runnable-task-id])))
          (is (= (:id pending-run)
                 (get-in (core/inspect-task target-root "impl-task-1") [:latest-run :id])))
          (is (= (:id mgr-run)
                 (get-in (core/inspect-task target-root "impl-task-1") [:latest-mgr-run :id])))
          (run-store/save-run! target-root (assoc pending-run :ended-at "recovered"))
          (mgr-run-store/save-mgr-run! target-root (assoc mgr-run :ended-at "recovered"))))

      (testing "inspect surfaces return joined context for task, run, mgr_run, and task_type"
        (let [result (control/run-once! target-root :mock :mock)
              task-view (core/inspect-task target-root "impl-task-1")
              run-view (core/inspect-run target-root (:run-id result))
              mgr-run-view (core/inspect-mgr-run target-root (:mgr-run-id result))
              task-type-view (core/inspect-task-type target-root :impl)]
          (is (= "impl-task-1" (get-in task-view [:task :id])))
          (is (= "impl-backlog" (get-in task-view [:collection :id])))
          (is (= :impl (get-in task-view [:task-type :task-type])))
          (is (= (:run-id result) (get-in task-view [:latest-run :id])))
          (is (= (:mgr-run-id result) (get-in task-view [:latest-mgr-run :id])))
          (is (= (:run-id result) (get-in run-view [:run :id])))
          (is (= "impl-task-1" (get-in run-view [:task :id])))
          (is (= (:mgr-run-id result) (get-in mgr-run-view [:mgr-run :id])))
          (is (= "impl-task-1" (get-in mgr-run-view [:task :id])))
          (is (= :impl (:task-type task-type-view)))))

      (testing "app-overview reports the workflow root for the requested target"
        (is (= (str (paths/workflow-root target-root))
               (:layout-root (core/app-overview target-root)))))
      (finally
        (install-fixture/delete-tree! target-root)))))
