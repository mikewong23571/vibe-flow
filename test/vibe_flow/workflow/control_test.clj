(ns vibe-flow.workflow.control-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [vibe-flow.definition.task-type :as definition]
   [vibe-flow.management.domain :as domain]
   [vibe-flow.management.task-type :as task-type-manager]
   [vibe-flow.platform.runtime.run :as run]
   [vibe-flow.platform.state.mgr-run-store :as mgr-run-store]
   [vibe-flow.platform.state.run-store :as run-store]
   [vibe-flow.platform.state.task-store :as task-store]
   [vibe-flow.platform.support.edn :as edn]
   [vibe-flow.platform.target.install :as install]
   [vibe-flow.target.install-test :as install-fixture]
   [vibe-flow.platform.target.paths :as paths]
   [vibe-flow.platform.target.repo :as repo]
   [vibe-flow.workflow.control :as control]))

(deftest mock-workflow-run-loop-persists-task-mgr-run-and-run-state
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
      (testing "mock mgr and mock worker complete the minimal workflow loop"
        (let [results (control/run-loop! target-root :mock :mock 4)
              task (task-store/load-task target-root "impl-task-1")
              runs (run-store/task-runs target-root "impl-task-1")
              mgr-runs (mgr-run-store/load-mgr-runs target-root)
              latest-run (last runs)]
          (is (= 4 (count results)))
          (is (= :done (:stage task)))
          (is (= 4 (:mgr-count task)))
          (is (= 4 (:run-count task)))
          (is (= 2 (:review-count task)))
          (is (= 4 (count mgr-runs)))
          (is (= 4 (count runs)))
          (is (contains? #{:impl :review :refine} (:latest-worker task)))
          (is (= :pass (:latest-worker-control task)))
          (is (string? (:latest-review-output task)))
          (is (string? (:repo-head task)))
          (is (= :mock (:launcher latest-run)))
          (is (= :review (:worker latest-run)))
          (is (string? (get-in latest-run [:prompt :text])))
          (is (string? (get-in latest-run [:output :text])))
          (is (string? (get-in latest-run [:worktree :dir])))
          (is (string? (get-in latest-run [:heads :input])))
          (is (string? (get-in latest-run [:heads :output])))))

      (testing "advance-task! rejects mgr_run ids that belong to another task"
        (domain/create-task! target-root
                             {:id "impl-task-2"
                              :collection-id "impl-backlog"
                              :task-type :impl
                              :goal "Implement another feature."
                              :scope ["Edit src/ only."]
                              :constraints ["Do not change workflow metadata."]
                              :success-criteria ["Feature behavior is implemented."]})
        (let [mgr-run (control/create-mgr-run! target-root
                                               (domain/inspect-task target-root "impl-task-1")
                                               :mock)]
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"mgr_run does not belong to the task"
               (control/advance-task! target-root
                                      "impl-task-2"
                                      (:id mgr-run)
                                      :mock
                                      :done
                                      "forced")))
          (is (= :todo (:stage (task-store/load-task target-root "impl-task-2"))))
          (is (nil? (:ended-at (mgr-run-store/load-mgr-run target-root (:id mgr-run)))))))

      (testing "advance-task! rejects mgr_run ids that have already been finalized"
        (let [mgr-run (control/create-mgr-run! target-root
                                               (domain/inspect-task target-root "impl-task-1")
                                               :mock)
              mgr-count-before (:mgr-count (task-store/load-task target-root "impl-task-1"))]
          (control/advance-task! target-root
                                 "impl-task-1"
                                 (:id mgr-run)
                                 :mock
                                 :done
                                 "complete")
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"mgr_run is already finalized"
               (control/advance-task! target-root
                                      "impl-task-1"
                                      (:id mgr-run)
                                      :mock
                                      :done
                                      "complete")))
          (is (= (inc mgr-count-before)
                 (:mgr-count (task-store/load-task target-root "impl-task-1"))))))

      (testing "advance-task! rejects unsupported mgr decisions before mutating task state"
        (domain/create-task! target-root
                             {:id "impl-task-invalid-decision"
                              :collection-id "impl-backlog" :task-type :impl
                              :goal "Reject invalid mgr decisions."
                              :scope ["Edit src/ only."]
                              :constraints ["Do not change workflow metadata."] :success-criteria ["Invalid decisions are rejected."]})
        (let [mgr-run (control/create-mgr-run! target-root
                                               (domain/inspect-task target-root "impl-task-invalid-decision")
                                               :mock)
              task-before (task-store/load-task target-root "impl-task-invalid-decision")]
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"Unsupported mgr decision"
               (control/advance-task! target-root
                                      "impl-task-invalid-decision"
                                      (:id mgr-run)
                                      :mock
                                      :bogus
                                      "bad input")))
          (let [task-after (task-store/load-task target-root "impl-task-invalid-decision")
                mgr-run-after (mgr-run-store/load-mgr-run target-root (:id mgr-run))]
            (is (= (:stage task-before) (:stage task-after)))
            (is (= (:mgr-count task-before) (:mgr-count task-after)))
            (is (nil? (:ended-at mgr-run-after))))))

      (testing "advance-task! rejects stale mgr_run ids after another mgr_run already advanced the task"
        (domain/create-task! target-root
                             {:id "impl-task-stale-mgr"
                              :collection-id "impl-backlog" :task-type :impl
                              :goal "Reject stale mgr_run ids."
                              :scope ["Edit src/ only."]
                              :constraints ["Do not change workflow metadata."] :success-criteria ["Only the current mgr_run can advance the task."]})
        (let [task (domain/inspect-task target-root "impl-task-stale-mgr")
              stale-mgr-run (control/create-mgr-run! target-root task :mock)
              fresh-mgr-run (control/create-mgr-run! target-root task :mock)]
          (control/advance-task! target-root
                                 "impl-task-stale-mgr"
                                 (:id fresh-mgr-run)
                                 :mock
                                 :impl
                                 "launch impl")
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"mgr_run is stale"
               (control/advance-task! target-root
                                      "impl-task-stale-mgr"
                                      (:id stale-mgr-run)
                                      :mock
                                      :done
                                      "late duplicate")))
          (let [task-after (task-store/load-task target-root "impl-task-stale-mgr")
                runs (run-store/task-runs target-root "impl-task-stale-mgr")]
            (is (= 1 (:run-count task-after)))
            (is (= 1 (count runs)))
            (is (= (:id fresh-mgr-run) (:latest-mgr-run task-after))))))
      (finally
        (install-fixture/delete-tree! target-root)))))

(deftest prepare-run-respects-configured-input-head-task-field
  (let [target-root (install-fixture/init-git-target! (install-fixture/make-temp-dir))]
    (try
      (install/install! target-root)
      (task-type-manager/create-task-type! target-root :impl)
      (let [base-head (repo/current-head target-root)
            _ (spit (java.io.File. target-root "CHANGELOG.md") "second commit\n")
            _ (install-fixture/shell! target-root "git" "add" "CHANGELOG.md")
            _ (install-fixture/shell! target-root
                                      "git" "-c" "user.name=vibe-flow-test"
                                      "-c" "user.email=vibe-flow-test@example.com"
                                      "commit" "-m" "Second target commit")
            current-head (repo/current-head target-root)
            task-type-path (paths/task-type-path target-root :impl)
            definition (edn/read-edn task-type-path nil)]
        (edn/write-edn! task-type-path
                        (assoc-in definition [:prepare-run :input-head :task-field] :base-head))
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
                                         :base-head base-head})
              prepared-run (run/prepare-run! target-root task :impl :mock)]
          (is (not= base-head current-head))
          (is (= base-head (get-in prepared-run [:prepare-run :input-head])))
          (is (= base-head (get-in prepared-run [:heads :input])))))
      (finally
        (install-fixture/delete-tree! target-root)))))

(deftest before-prepare-run-hook-executes-before-prompt-rendering
  (let [target-root (install-fixture/init-git-target! (install-fixture/make-temp-dir))]
    (try
      (install/install! target-root)
      (task-type-manager/create-task-type! target-root :impl)
      (spit (definition/hook-path target-root :impl :before_prepare_run)
            (str "#!/usr/bin/env bash\n"
                 "set -euo pipefail\n"
                 "touch .workflow/state/hook-ran\n"
                 "printf '{:prompt-inputs {:hook_note \"before hook ran\"}}\\n'\n"))
      (.setExecutable (java.io.File. (str (definition/hook-path target-root :impl :before_prepare_run))) true)
      (spit (definition/prompt-path target-root :impl :impl)
            "Hook note: {{hook_note}}\nGoal: {{goal}}\n")
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
                                       :success-criteria ["Feature behavior is implemented."]})
            prepared-run (run/prepare-run! target-root task :impl :mock)]
        (testing "hook output is merged into prompt inputs before prompt rendering"
          (is (.exists (java.io.File. target-root ".workflow/state/hook-ran")))
          (is (= {:prompt-inputs {:hook_note "before hook ran"}}
                 (get-in prepared-run [:prepare-run :before-prepare-run])))
          (is (re-find #"before hook ran" (get-in prepared-run [:prompt :text])))))
      (finally
        (install-fixture/delete-tree! target-root)))))

(deftest failed-launch-still-persists-prepared-run-for-recovery
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
      (testing "prepared runs remain visible to recovery when launch fails"
        (with-redefs [vibe-flow.platform.runtime.launcher/launch!
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

(deftest unsupported-mgr-launcher-does-not-persist-blocking-state
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
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Unsupported mgr launcher"
           (control/run-once! target-root :mock :codex)))
      (is (empty? (mgr-run-store/load-mgr-runs target-root)))
      (is (= "impl-task-1" (:id (control/select-runnable-task target-root))))
      (finally
        (install-fixture/delete-tree! target-root)))))
