(ns vibe-flow.runtime.mgr-run-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [vibe-flow.management.task-type :as task-type-manager]
   [vibe-flow.platform.runtime.mgr-run :as mgr-run]
   [vibe-flow.platform.state.system-store :as system-store]
   [vibe-flow.platform.support.shell :as shell]
   [vibe-flow.platform.target.install :as install]
   [vibe-flow.platform.target.paths :as paths]
   [vibe-flow.target.install-test :as install-fixture]))

(defn delete-tree! [file]
  (when (.exists file)
    (doseq [child (reverse (file-seq file))]
      (.delete child))))

(defn empty-dir? [dir]
  (empty? (seq (or (.listFiles ^java.io.File dir) []))))

(deftest shell-quote-wraps-generated-script-arguments
  (testing "quote-arg uses single-quote escaping that blocks shell interpolation"
    (is (= "'$(echo hi)'" (shell/quote-arg "$(echo hi)")))
    (is (= "'task'\"'\"'id'" (shell/quote-arg "task'id"))))

  (testing "mgr callback wrappers quote persisted arguments before appending user input"
    (let [temp-dir (.toFile (java.nio.file.Files/createTempDirectory
                             "vibe-flow-mgr-wrapper-test"
                             (make-array java.nio.file.attribute.FileAttribute 0)))
          command-file (io/file temp-dir "vibe-flow")]
      (try
        (spit command-file "#!/usr/bin/env bash\nexit 0\n")
        (.setExecutable command-file true)
        (let [command (str (.getCanonicalFile command-file))
              wrapper (mgr-run/cli-wrapper-text "/tmp/target $(echo hi)"
                                                command
                                                {:id "task'id"}
                                                "mgr$(id)")]
          (is (re-find (re-pattern (str "exec " (java.util.regex.Pattern/quote (shell/quote-arg command)) " 'mgr-advance'"))
                       wrapper))
          (is (re-find #"'--target' '/tmp/target \$\(echo hi\)'" wrapper))
          (is (re-find #"'--task-id' 'task'\"'\"'id'" wrapper))
          (is (re-find #"'--mgr-run-id' 'mgr\$\(id\)'" wrapper))
          (is (re-find #" \"\$@\"" wrapper)))
        (finally
          (delete-tree! temp-dir)))))

  (testing "mgr callback wrappers fail before writing scripts for stale commands"
    (let [target-root (install-fixture/init-git-target! (install-fixture/make-temp-dir))
          missing (io/file target-root "missing-vibe-flow-command")
          mgr-runs-root (io/file target-root ".workflow" "local" "mgr_runs")]
      (try
        (install/install! target-root)
        (task-type-manager/create-task-type! target-root :impl)
        (install-fixture/create-agent-home-configs! target-root)
        (with-redefs [system-store/load-toolchain
                      (fn [_] {:command (str missing)})]
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"Workflow command is unavailable"
               (mgr-run/prepare-mgr-run! target-root
                                         {:id "task-id"
                                          :task-type :impl}
                                         :codex))))
        (is (empty-dir? mgr-runs-root))
        (finally
          (delete-tree! target-root))))))

(deftest mgr-run-requires-configured-codex-mgr-home
  (let [target-root (install-fixture/init-git-target! (install-fixture/make-temp-dir))
        mgr-runs-root (paths/mgr-runs-root target-root)]
    (try
      (install/install! target-root)
      (task-type-manager/create-task-type! target-root :impl)
      (testing "mock mgr_run skips codex home preflight"
        (let [record (mgr-run/prepare-mgr-run! target-root
                                               {:id "task-id"
                                                :task-type :impl}
                                               :mock)]
          (is (= "task-id" (:task-id record)))
          (is (string? (get-in record [:prompt :text])))))

      (delete-tree! mgr-runs-root)

      (testing "codex mgr_run is rejected before local runtime files are written"
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"Task type mgr home is not ready"
             (mgr-run/prepare-mgr-run! target-root
                                       {:id "task-id"
                                        :task-type :impl}
                                       :codex)))
        (is (empty-dir? mgr-runs-root)))

      (testing "configured mgr home allows mgr_run preparation without later worker homes"
        (let [mgr-home (paths/agent-home-path target-root :mgr_codex)]
          (.mkdirs mgr-home)
          (spit (io/file mgr-home "config.toml")
                "model_provider = \"test\"\n"))
        (let [record (mgr-run/prepare-mgr-run! target-root
                                               {:id "task-id"
                                                :task-type :impl}
                                               :codex)]
          (is (= "task-id" (:task-id record)))
          (is (string? (get-in record [:prompt :text])))))
      (finally
        (delete-tree! target-root)))))
