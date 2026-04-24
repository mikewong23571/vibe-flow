(ns vibe-flow.core-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [vibe-flow.core :as core]
   [vibe-flow.definition.task-type :as definition]
   [vibe-flow.management.domain :as domain]
   [vibe-flow.management.task-type :as task-type-manager]
   [vibe-flow.platform.runtime.run :as run]
   [vibe-flow.platform.state.mgr-run-store :as mgr-run-store]
   [vibe-flow.platform.state.run-store :as run-store]
   [vibe-flow.platform.state.system-store :as system-store]
   [vibe-flow.platform.support.edn :as edn]
   [vibe-flow.platform.target.install :as install]
   [vibe-flow.platform.target.paths :as paths]
   [vibe-flow.platform.toolchain.paths :as toolchain-paths]
   [vibe-flow.system :as system]
   [vibe-flow.target.install-test :as install-fixture]
   [vibe-flow.workflow.control :as control]))

(use-fixtures :each install-fixture/with-fake-toolchain-command)

(deftest product-surface-inspects-state-and-recovery
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
          (is (= install-fixture/*fake-toolchain-command*
                 (get-in state [:system :toolchain :command])))
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

(deftest system-bootstrap-self-host-installs-and-seeds-current-target
  (let [target-root (install-fixture/init-git-target! (install-fixture/make-temp-dir))]
    (try
      (testing "bootstrap installs the workflow target and seeds the self-improvement backlog"
        (let [result (system/bootstrap-self-host! target-root)
              task-types (task-type-manager/list-task-types target-root)
              collections (domain/list-collections target-root)
              tasks (domain/list-tasks target-root)]
          (is (system-store/installed? target-root))
          (is (= :created (get-in result [:task-type :status])))
          (is (= :created (get-in result [:collection :status])))
          (is (= :created (get-in result [:task :status])))
          (is (= 1 (count task-types)))
          (is (= :impl (get-in task-types [0 :id])))
          (is (= 1 (count collections)))
          (is (= "self-improvement" (:id (first collections))))
          (is (= :impl (:task-type (first collections))))
          (is (= 1 (count tasks)))
          (is (= "bootstrap-self-optimization" (:id (first tasks))))
          (is (= "self-improvement" (:collection-id (first tasks))))
          (is (= :todo (:stage (first tasks))))))

      (testing "bootstrap is idempotent and reuses the seeded state on later runs"
        (system/bootstrap-self-host! target-root)
        (let [result (system/bootstrap-self-host! target-root)]
          (is (= :existing (get-in result [:task-type :status])))
          (is (= :existing (get-in result [:collection :status])))
          (is (= :existing (get-in result [:task :status])))
          (is (= install-fixture/*fake-toolchain-command*
                 (:command (system-store/load-toolchain target-root))))
          (is (= 1 (count (task-type-manager/list-task-types target-root))))
          (is (= 1 (count (domain/list-collections target-root))))
          (is (= 1 (count (domain/list-tasks target-root))))))
      (finally
        (install-fixture/delete-tree! target-root)))))

(deftest system-bootstrap-refreshes-legacy-managed-prompts
  (let [target-root (install-fixture/init-git-target! (install-fixture/make-temp-dir))]
    (try
      (install/install! target-root)
      (task-type-manager/create-task-type! target-root :impl)
      (install-fixture/create-agent-home-configs! target-root)
      (spit (definition/prompt-path target-root :impl :mgr)
            (task-type-manager/render-template
             (:mgr task-type-manager/legacy-prompt-skeletons)
             {:task_type "impl"}))
      (let [result (system/bootstrap-self-host! target-root)
            mgr-prompt (slurp (definition/prompt-path target-root :impl :mgr))]
        (is (= :existing (get-in result [:task-type :status])))
        (is (true? (get-in result [:task-type :refreshed?])))
        (is (re-find #"workflow_cli_path" mgr-prompt))
        (is (re-find #"--decision <impl\|review\|refine\|done\|error>" mgr-prompt)))
      (finally
        (install-fixture/delete-tree! target-root)))))

(deftest mgr-advance-cli-advances-task-through-persisted-mgr-run
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
                           {:id "impl-task-cli"
                            :collection-id "impl-backlog"
                            :task-type :impl
                            :goal "Implement through mgr CLI."
                            :scope ["Edit src/ only."]
                            :constraints ["Do not change workflow metadata."]
                            :success-criteria ["Task advances through the CLI callback."]})
      (let [mgr-run (control/create-mgr-run! target-root
                                             (domain/inspect-task target-root "impl-task-cli")
                                             :codex
                                             :mock)
            output (with-out-str
                     (system/-main "mgr-advance"
                                   "--target" (str target-root)
                                   "--task-id" "impl-task-cli"
                                   "--mgr-run-id" (:id mgr-run)
                                   "--worker-launcher" "codex"
                                   "--decision" "impl"
                                   "--reason" "launch implementation"))
            updated-task (domain/inspect-task target-root "impl-task-cli")
            updated-mgr-run (mgr-run-store/load-mgr-run target-root (:id mgr-run))
            latest-run (run-store/load-run target-root (:latest-run updated-task))]
        (testing "mgr run prompt and wrapper expose the workflow callback path"
          (is (.exists (java.io.File. (:cli-script mgr-run))))
          (is (.canExecute (java.io.File. (:cli-script mgr-run))))
          (is (re-find #"workflow-advance" (:cli-script mgr-run)))
          (is (re-find #"workflow-advance --decision <impl\|review\|refine\|done\|error>"
                       (get-in mgr-run [:prompt :text]))))
        (testing "mgr-advance CLI finalizes the mgr_run and advances the task"
          (is (re-find #"impl-task-cli" output))
          (is (= :awaiting-review (:stage updated-task)))
          (is (= :impl (:latest-mgr-decision updated-task)))
          (is (= :impl (:latest-worker updated-task)))
          (is (string? (:latest-run updated-task)))
          (is (some? (:ended-at updated-mgr-run)))
          (is (= :impl (get-in updated-mgr-run [:result :decision])))
          (is (= :mock (:launcher latest-run)))
          (is (= :impl (:worker latest-run)))))
      (finally
        (install-fixture/delete-tree! target-root)))))

(deftest mgr-start-cli-polls-a-task-type-until-the-workflow-closes
  (let [target-root (install-fixture/init-git-target! (install-fixture/make-temp-dir))]
    (try
      (install/install! target-root)
      (task-type-manager/create-task-type! target-root :impl)
      (domain/create-collection! target-root
                                 {:id "impl-backlog"
                                  :task-type :impl
                                  :name "Implementation backlog"})
      (domain/create-task! target-root
                           {:id "impl-task-mgr-start"
                            :collection-id "impl-backlog"
                            :task-type :impl
                            :goal "Close the loop through mgr-start."
                            :scope ["Edit src/ only."]
                            :constraints ["Do not change workflow metadata."]
                            :success-criteria ["The task is driven to done through mgr-start."]})
      (let [output (with-redefs [vibe-flow.system/getenv
                                 (fn [name]
                                   (when (= name "VIBE_FLOW_CLI_CWD")
                                     (str target-root)))]
                     (with-out-str
                       (system/-main "mgr-start"
                                     "--task-type" "impl"
                                     "--mgr-launcher" "mock"
                                     "--worker-launcher" "mock"
                                     "--poll-interval-ms" "0"
                                     "--max-steps" "4")))
            task (domain/inspect-task target-root "impl-task-mgr-start")]
        (is (re-find #":status :max-steps-reached" output))
        (is (re-find #":steps 4" output))
        (is (= :done (:stage task)))
        (is (= 4 (:mgr-count task)))
        (is (= 4 (:run-count task))))
      (finally
        (install-fixture/delete-tree! target-root)))))

(deftest mgr-start-cli-requires-installed-target-when-using-current-directory-default
  (let [target-root (install-fixture/init-git-target! (install-fixture/make-temp-dir))]
    (try
      (with-redefs [vibe-flow.system/getenv
                    (fn [name]
                      (when (= name "VIBE_FLOW_CLI_CWD")
                        (str target-root)))]
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"installed workflow target"
             (system/-main "mgr-start"
                           "--task-type" "impl"
                           "--mgr-launcher" "mock"
                           "--worker-launcher" "mock"
                           "--max-idle-polls" "0"))))
      (finally
        (install-fixture/delete-tree! target-root)))))

(deftest mgr-start-cli-rejects-negative-numeric-options
  (is (thrown-with-msg?
       clojure.lang.ExceptionInfo
       #"non-negative integer"
       (system/-main "mgr-start"
                     "--task-type" "impl"
                     "--poll-interval-ms" "-1")))
  (is (thrown-with-msg?
       clojure.lang.ExceptionInfo
       #"non-negative integer"
       (system/-main "mgr-start"
                     "--task-type" "impl"
                     "--max-steps" "-1")))
  (is (thrown-with-msg?
       clojure.lang.ExceptionInfo
       #"non-negative integer"
       (system/-main "mgr-start"
                     "--task-type" "impl"
                     "--max-idle-polls" "-1"))))

(deftest system-install-toolchain-materializes-user-command
  (let [temp-home (install-fixture/make-temp-dir)
        temp-data-home (install-fixture/make-temp-dir)
        source-root (.getCanonicalPath (java.io.File. "."))]
    (try
      (with-redefs [vibe-flow.platform.toolchain.paths/home-dir
                    (fn [] (.getCanonicalPath temp-home))
                    vibe-flow.platform.toolchain.paths/xdg-data-home
                    (fn [] (.getCanonicalPath temp-data-home))
                    vibe-flow.platform.toolchain.paths/shim-path
                    (fn []
                      (io/file (toolchain-paths/bin-root)
                               toolchain-paths/command-name))]
        (let [install-record (system/install-toolchain! source-root)
              install-root (toolchain-paths/toolchain-root)
              shim-path (toolchain-paths/shim-path)
              shim-text (slurp shim-path)]
          (is (= :user-install (:kind install-record)))
          (is (= "vibe-flow" (:command install-record)))
          (is (= (str install-root) (:toolchain-root install-record)))
          (is (.isDirectory install-root))
          (is (.exists (java.io.File. install-root "deps.edn")))
          (is (.isDirectory (java.io.File. install-root "src")))
          (is (.isDirectory (java.io.File. install-root "resources")))
          (is (.exists shim-path))
          (is (.canExecute shim-path))
          (is (.exists (toolchain-paths/install-record-path)))
          (is (= install-record
                 (edn/read-edn
                  (toolchain-paths/install-record-path)
                  nil)))
          (is (re-find #"export VIBE_FLOW_CLI_CWD=\"\$\{PWD\}\""
                       shim-text))
          (is (re-find #"'clojure' '-M:cli'"
                       shim-text))))
      (finally
        (install-fixture/delete-tree! temp-home)
        (install-fixture/delete-tree! temp-data-home)))))

(deftest system-parse-cli-args-resolves-target-against-shim-caller-root
  (let [caller-root (install-fixture/make-temp-dir)]
    (try
      (with-redefs [vibe-flow.system/getenv
                    (fn [name]
                      (when (= name "VIBE_FLOW_CLI_CWD")
                        (str caller-root)))]
        (is (= (.getCanonicalPath caller-root)
               (:target (system/parse-cli-args ["install-target"]))))
        (is (= (.getCanonicalPath (io/file caller-root "relative" "repo"))
               (:target (system/parse-cli-args ["install-target"
                                                "--target" "relative/repo"])))))
      (finally
        (install-fixture/delete-tree! caller-root)))))

(deftest system-governed-cli-roadmap-is-structured-and-explicit
  (testing "planned provider whitelist and registry contract are exposed as governed surface metadata"
    (let [providers (system/governed-cli-provider-whitelist)
          contract (system/governed-cli-registry-contract)
          whitelist (system/governed-cli-command-whitelist)
          design-doc (system/governed-product-cli-design-doc-path)]
      (is (= #{:tasks :collections :task-types :agent-homes :doctor}
             (set (map :id providers))))
      (is (= "docs/plan/product-cli-facade-governance.md" design-doc))
      (is (= design-doc (:design-doc (first providers))))
      (is (= [:list :show]
             (:subcommands (first (filter #(= :tasks (:id %)) providers)))))
      (is (= :planned (:status (first (filter #(= :agent-homes (:id %)) providers)))))
      (is (= 'vibe-flow.product.cli.registry (:registry-ns contract)))
      (is (= #{:resource-family :singleton-command} (:allowed-kinds contract)))
      (is (= design-doc (:design-doc contract)))
      (is (= design-doc (:design-doc whitelist)))
      (is (= :implemented (get-in whitelist [:implemented "install" :status])))
      (is (= :planned (get-in whitelist [:planned :registry-contract :status])))))

  (testing "planned product CLI registry functions fail with structured not-implemented metadata"
    (try
      (system/load-governed-cli-registry!)
      (is false "Expected load-governed-cli-registry! to throw")
      (catch clojure.lang.ExceptionInfo ex
        (is (= "Governed product CLI command is not implemented yet."
               (.getMessage ex)))
        (is (= :load-governed-cli-registry (:command (ex-data ex))))
        (is (= :registry-contract (:kind (ex-data ex))))
        (is (= :planned (:status (ex-data ex))))))
    (try
      (system/dispatch-governed-cli-command! :tasks "/tmp/target" {:subcommand :list})
      (is false "Expected dispatch-governed-cli-command! to throw")
      (catch clojure.lang.ExceptionInfo ex
        (is (= :dispatch-governed-cli-command (:command (ex-data ex))))
        (is (= :registry-contract (:kind (ex-data ex))))
        (is (= :tasks (:requested-command (ex-data ex))))
        (is (= :planned (:status (ex-data ex))))))))

(deftest system-install-toolchain-supports-in-place-reinstall
  (let [temp-home (install-fixture/make-temp-dir)
        temp-data-home (install-fixture/make-temp-dir)
        source-root (.getCanonicalPath (java.io.File. "."))]
    (try
      (with-redefs [vibe-flow.platform.toolchain.paths/home-dir
                    (fn [] (.getCanonicalPath temp-home))
                    vibe-flow.platform.toolchain.paths/xdg-data-home
                    (fn [] (.getCanonicalPath temp-data-home))
                    vibe-flow.platform.toolchain.paths/shim-path
                    (fn []
                      (io/file (toolchain-paths/bin-root)
                               toolchain-paths/command-name))]
        (let [initial (system/install-toolchain! source-root)
              reinstall (system/install-toolchain! (str (toolchain-paths/toolchain-root)))
              install-root (toolchain-paths/toolchain-root)]
          (is (= :user-install (:kind initial)))
          (is (= :user-install (:kind reinstall)))
          (is (= (str install-root) (:toolchain-root reinstall)))
          (is (.exists (io/file install-root "deps.edn")))
          (is (.exists (io/file install-root "src" "vibe_flow" "system.clj")))
          (is (.canExecute (toolchain-paths/shim-path)))))
      (finally
        (install-fixture/delete-tree! temp-home)
        (install-fixture/delete-tree! temp-data-home)))))
