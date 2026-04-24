(ns vibe-flow.workflow.control-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [vibe-flow.definition.task-type :as definition]
   [vibe-flow.management.domain :as domain]
   [vibe-flow.management.task-type :as task-type-manager]
   [vibe-flow.platform.runtime.run :as run]
   [vibe-flow.platform.state.mgr-run-store :as mgr-run-store]
   [vibe-flow.platform.state.run-store :as run-store]
   [vibe-flow.platform.state.task-store :as task-store]
   [vibe-flow.platform.state.task-runtime-store :as task-runtime-store]
   [vibe-flow.platform.support.edn :as edn]
   [vibe-flow.platform.target.install :as install]
   [vibe-flow.target.install-test :as install-fixture]
   [vibe-flow.platform.target.paths :as paths]
   [vibe-flow.platform.target.repo :as repo]
   [vibe-flow.workflow.control :as control]))

(use-fixtures :each install-fixture/with-fake-toolchain-command)

(defn load-task-view [target-root task-id]
  (task-runtime-store/hydrate-task target-root
                                   (task-store/load-task target-root task-id)))

(deftest mock-workflow-run-loop-persists-task-mgr-run-and-run-state
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
      (testing "mock mgr and mock worker complete the minimal workflow loop"
        (let [results (control/run-loop! target-root :mock :mock 4)
              domain-task (task-store/load-task target-root "impl-task-1")
              task (load-task-view target-root "impl-task-1")
              runtime (task-runtime-store/load-task-runtime target-root "impl-task-1")
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
          (is (empty? (select-keys domain-task task-runtime-store/runtime-fields)))
          (is (= 4 (:mgr-count runtime)))
          (is (= 4 (:run-count runtime)))
          (is (= (:latest-run task) (:latest-run runtime)))
          (is (= :mock (:launcher latest-run)))
          (is (= :review (:worker latest-run)))
          (is (string? (get-in latest-run [:prompt :text])))
          (is (string? (get-in latest-run [:output :text])))
          (is (string? (get-in latest-run [:worktree :dir])))
          (is (string? (get-in latest-run [:heads :input])))
          (is (string? (get-in latest-run [:heads :output])))))

      (testing "task read models filter polluted persisted state"
        (let [task-path (paths/task-path target-root "impl-task-1")
              runtime-path (paths/task-runtime-path target-root "impl-task-1")
              poisoned-domain (assoc (edn/read-edn task-path nil)
                                     :latest-run "domain-poison"
                                     :run-count 99)
              poisoned-runtime (assoc (edn/read-edn runtime-path nil)
                                      :stage :todo
                                      :task-type :poisoned
                                      :repo-head "runtime-poison")]
          (edn/write-edn! task-path poisoned-domain)
          (edn/write-edn! runtime-path poisoned-runtime)
          (let [domain-task (task-store/load-task target-root "impl-task-1")
                task-view (load-task-view target-root "impl-task-1")]
            (is (nil? (:latest-run domain-task)))
            (is (nil? (:run-count domain-task)))
            (is (= :done (:stage task-view)))
            (is (= :impl (:task-type task-view)))
            (is (not= "runtime-poison" (:repo-head task-view))))))

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
                                      :done
                                      "forced")))
          (is (= :todo (:stage (task-store/load-task target-root "impl-task-2"))))
          (is (nil? (:ended-at (mgr-run-store/load-mgr-run target-root (:id mgr-run)))))))

      (testing "advance-task! rejects mgr_run ids that have already been finalized"
        (let [mgr-run (control/create-mgr-run! target-root
                                               (domain/inspect-task target-root "impl-task-1")
                                               :mock)
              mgr-count-before (:mgr-count (load-task-view target-root "impl-task-1"))]
          (control/advance-task! target-root
                                 "impl-task-1"
                                 (:id mgr-run)
                                 :done
                                 "complete")
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"mgr_run is already finalized"
               (control/advance-task! target-root
                                      "impl-task-1"
                                      (:id mgr-run)
                                      :done
                                      "complete")))
          (is (= (inc mgr-count-before)
                 (:mgr-count (load-task-view target-root "impl-task-1"))))))

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
              task-before (load-task-view target-root "impl-task-invalid-decision")]
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"Unsupported mgr decision"
               (control/advance-task! target-root
                                      "impl-task-invalid-decision"
                                      (:id mgr-run)
                                      :bogus
                                      "bad input")))
          (let [task-after (load-task-view target-root "impl-task-invalid-decision")
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
                                 :impl
                                 "launch impl")
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"mgr_run is stale"
               (control/advance-task! target-root
                                      "impl-task-stale-mgr"
                                      (:id stale-mgr-run)
                                      :done
                                      "late duplicate")))
          (let [task-after (load-task-view target-root "impl-task-stale-mgr")
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
      (install-fixture/create-agent-home-configs! target-root)
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
      (install-fixture/create-agent-home-configs! target-root)
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

(deftest select-runnable-task-and-run-loop-support-task-type-filtering
  (let [target-root (install-fixture/init-git-target! (install-fixture/make-temp-dir))]
    (try
      (install/install! target-root)
      (task-type-manager/create-task-type! target-root :impl)
      (task-type-manager/create-task-type! target-root :ops)
      (domain/create-collection! target-root
                                 {:id "impl-backlog"
                                  :task-type :impl
                                  :name "Implementation backlog"})
      (domain/create-collection! target-root
                                 {:id "ops-backlog"
                                  :task-type :ops
                                  :name "Operations backlog"})
      (domain/create-task! target-root
                           {:id "impl-task-1"
                            :collection-id "impl-backlog"
                            :task-type :impl
                            :goal "Implement the feature."
                            :scope ["Edit src/ only."]
                            :constraints ["Do not change workflow metadata."]
                            :success-criteria ["Feature behavior is implemented."]})
      (domain/create-task! target-root
                           {:id "ops-task-1"
                            :collection-id "ops-backlog"
                            :task-type :ops
                            :goal "Handle an ops workflow."
                            :scope ["Inspect runtime metadata only."]
                            :constraints ["Do not change workflow metadata."]
                            :success-criteria ["The ops task progresses."]})
      (testing "task_type filter isolates runnable selection and execution"
        (is (= "impl-task-1"
               (:id (control/select-runnable-task target-root {:task-type :impl}))))
        (is (= "ops-task-1"
               (:id (control/select-runnable-task target-root {:task-type :ops}))))
        (let [results (control/run-loop! target-root :mock :mock 4 {:task-type :impl})
              impl-task (task-store/load-task target-root "impl-task-1")
              ops-task (load-task-view target-root "ops-task-1")]
          (is (= 4 (count results)))
          (is (= :done (:stage impl-task)))
          (is (= :todo (:stage ops-task)))
          (is (nil? (:latest-run ops-task)))
          (is (= "ops-task-1"
                 (:id (control/select-runnable-task target-root {:task-type :ops}))))))
      (finally
        (install-fixture/delete-tree! target-root)))))
