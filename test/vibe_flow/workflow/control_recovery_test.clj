(ns vibe-flow.workflow.control-recovery-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [vibe-flow.management.domain :as domain]
   [vibe-flow.management.task-type :as task-type-manager]
   [vibe-flow.platform.runtime.launcher :as launcher]
   [vibe-flow.platform.runtime.run :as run]
   [vibe-flow.platform.state.mgr-run-store :as mgr-run-store]
   [vibe-flow.platform.state.run-store :as run-store]
   [vibe-flow.platform.state.task-store :as task-store]
   [vibe-flow.platform.support.edn :as edn]
   [vibe-flow.platform.target.install :as install]
   [vibe-flow.platform.target.paths :as paths]
   [vibe-flow.target.install-test :as install-fixture]
   [vibe-flow.workflow.control :as control]))

(use-fixtures :each install-fixture/with-fake-toolchain-command)

(deftest failed-launch-still-persists-prepared-run-for-recovery
  (let [target-root (install-fixture/init-git-target! (install-fixture/make-temp-dir))]
    (try
      (install/install! target-root)
      (task-type-manager/create-task-type! target-root :impl)
      (install-fixture/create-agent-home-configs! target-root)
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
      (testing "prepared runs remain visible to recovery when launch fails"
        (with-redefs [launcher/launch!
                      (fn [& _]
                        (throw (ex-info "launch failed" {})))]
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"launch failed"
               (control/run-once! target-root :mock :mock)))
          (let [runs (run-store/task-runs target-root "impl-task-1")]
            (is (= 1 (count runs)))
            (is (nil? (:ended-at (first runs))))
            (is (= :impl (:worker (first runs))))
            (is (nil? (control/select-runnable-task target-root))))))
      (testing "unfinished mgr_run blocks duplicate scheduling for the same task"
        (let [mgr-run (control/create-mgr-run! target-root
                                               (domain/inspect-task target-root "impl-task-1")
                                               :mock)]
          (is (nil? (control/select-runnable-task target-root)))
          (is (nil? (:ended-at (mgr-run-store/load-mgr-run target-root (:id mgr-run)))))))
      (finally
        (install-fixture/delete-tree! target-root)))))

(deftest invalid-prompt-does-not-create-orphaned-worktree
  (let [target-root (install-fixture/init-git-target! (install-fixture/make-temp-dir))]
    (try
      (install/install! target-root)
      (task-type-manager/create-task-type! target-root :impl)
      (install-fixture/create-agent-home-configs! target-root)
      (let [task-type-path (paths/task-type-path target-root :impl)
            definition (edn/read-edn task-type-path nil)]
        (edn/write-edn! task-type-path
                        (assoc-in definition [:prompts :impl] "prompts/missing.txt")))
      (domain/create-collection! target-root
                                 {:id "impl-backlog"
                                  :task-type :impl
                                  :name "Implementation backlog"})
      (let [task (domain/create-task! target-root
                                      {:id "impl-task-1"
                                       :collection-id "impl-backlog"
                                       :task-type :impl
                                       :goal "Implement the feature."
                                       :scope ["Edit src/ only."]
                                       :constraints ["Do not change workflow metadata."]
                                       :success-criteria ["Feature behavior is implemented."]})]
        (testing "prompt validation fails before git worktree creation"
          (let [before (set (map #(.getName %) (or (.listFiles (paths/runs-root target-root)) [])))]
            (is (thrown-with-msg?
                 java.io.FileNotFoundException
                 #"missing.txt"
                 (run/prepare-run! target-root task :impl :mock)))
            (let [after (set (map #(.getName %) (or (.listFiles (paths/runs-root target-root)) [])))]
              (is (= before after))
              (doseq [run-id after]
                (is (not (.exists (paths/run-worktree-dir target-root run-id)))))))))
      (finally
        (install-fixture/delete-tree! target-root)))))

(deftest invalid-input-head-does-not-create-orphaned-run-directory
  (let [target-root (install-fixture/init-git-target! (install-fixture/make-temp-dir))]
    (try
      (install/install! target-root)
      (task-type-manager/create-task-type! target-root :impl)
      (install-fixture/create-agent-home-configs! target-root)
      (domain/create-collection! target-root
                                 {:id "impl-backlog"
                                  :task-type :impl
                                  :name "Implementation backlog"})
      (let [task (domain/create-task! target-root
                                      {:id "impl-task-1"
                                       :collection-id "impl-backlog"
                                       :task-type :impl
                                       :goal "Implement the feature."
                                       :scope ["Edit src/ only."]
                                       :constraints ["Do not change workflow metadata."]
                                       :success-criteria ["Feature behavior is implemented."]
                                       :repo-head "deadbeef"})
            before (set (map #(.getName %) (or (.listFiles (paths/runs-root target-root)) [])))]
        (testing "git worktree failures do not leave undiscoverable run directories behind"
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"Git command failed"
               (run/prepare-run! target-root task :impl :mock)))
          (let [after (set (map #(.getName %) (or (.listFiles (paths/runs-root target-root)) [])))]
            (is (= before after)))))
      (finally
        (install-fixture/delete-tree! target-root)))))

(deftest prepare-run-failure-marks-task-error-instead-of-rescheduling
  (let [target-root (install-fixture/init-git-target! (install-fixture/make-temp-dir))]
    (try
      (install/install! target-root)
      (task-type-manager/create-task-type! target-root :impl)
      (install-fixture/create-agent-home-configs! target-root)
      (let [task-type-path (paths/task-type-path target-root :impl)
            definition (edn/read-edn task-type-path nil)]
        (edn/write-edn! task-type-path
                        (assoc-in definition [:prompts :impl] "prompts/missing.txt")))
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
      (testing "worker preparation failures stop the task instead of making it runnable again"
        (is (thrown-with-msg?
             java.io.FileNotFoundException
             #"missing.txt"
             (control/run-once! target-root :mock :mock)))
        (let [task (task-store/load-task target-root "impl-task-1")
              mgr-runs (mgr-run-store/load-mgr-runs target-root)
              runs (run-store/task-runs target-root "impl-task-1")]
          (is (= :error (:stage task)))
          (is (re-find #"missing.txt" (:error-output task)))
          (is (= 1 (count mgr-runs)))
          (is (empty? runs))
          (is (nil? (control/select-runnable-task target-root)))))
      (finally
        (install-fixture/delete-tree! target-root)))))

(deftest missing-codex-worker-home-finalizes-run-and-marks-task-error
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
      (testing "missing codex worker home becomes a finalized failed run"
        (let [result (control/run-once! target-root :codex :mock)
              task (task-store/load-task target-root "impl-task-1")
              runs (run-store/task-runs target-root "impl-task-1")
              run (first runs)]
          (is (= :error (:next-stage result)))
          (is (= 1 (count runs)))
          (is (some? (:ended-at run)))
          (is (= true (:error? run)))
          (is (= :error (get-in run [:result :control])))
          (is (= "Agent home is not ready." (get-in run [:result :message])))
          (is (= :missing-home (get-in run [:result :launch :agent-home :reason])))
          (is (= :error (:stage task)))
          (is (= (:id run) (:latest-run task)))
          (is (re-find #"Agent home is not ready" (:error-output task)))))
      (finally
        (install-fixture/delete-tree! target-root)))))

(deftest unsupported-mgr-launcher-does-not-persist-blocking-state
  (let [target-root (install-fixture/init-git-target! (install-fixture/make-temp-dir))]
    (try
      (install/install! target-root)
      (task-type-manager/create-task-type! target-root :impl)
      (install-fixture/create-agent-home-configs! target-root)
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
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Unsupported mgr launcher"
           (control/run-once! target-root :mock :codex)))
      (is (empty? (mgr-run-store/load-mgr-runs target-root)))
      (is (= "impl-task-1" (:id (control/select-runnable-task target-root))))
      (finally
        (install-fixture/delete-tree! target-root)))))
