(ns vibe-flow.runtime.launcher-test
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.test :refer [deftest is testing]]
   [vibe-flow.platform.runtime.launcher :as launcher]))

(deftest codex-launcher-builds-command-and-parses-review-result
  (let [temp-dir (.toFile (java.nio.file.Files/createTempDirectory
                           "vibe-flow-codex-launcher-test"
                           (make-array java.nio.file.attribute.FileAttribute 0)))
        run {:worker-home nil
             :worktree {:dir (str temp-dir)}
             :prompt {:path (str (io/file temp-dir "prompt.txt"))
                      :text "Review this change and reply with RESULT: pass"}
             :output {:path (str (io/file temp-dir "output.txt"))}}]
    (try
      (testing "codex launcher uses the real CLI command shape and parses review control"
        (with-redefs [shell/sh
                      (fn [& args]
                        (spit (get-in run [:output :path]) "RESULT: pass\nlooks good")
                        {:exit 0
                         :out "ok"
                         :err ""
                         :args args})]
          (let [result (launcher/launch! temp-dir :codex :review {:id "task-1"} run)]
            (is (:ok? result))
            (is (= :pass (:control result)))
            (is (= "RESULT: pass\nlooks good" (:message result)))
            (is (= "codex" (first (get-in result [:launch :cmd]))))
            (is (= "exec" (second (get-in result [:launch :cmd]))))
            (is (= (get-in run [:worktree :dir]) (nth (get-in result [:launch :cmd]) 3)))
            (is (= nil (get-in result [:launch :code-home]))))))
      (finally
        (doseq [child (reverse (file-seq temp-dir))]
          (.delete child))))))

(deftest codex-launcher-materializes-worker-home-before-launch
  (let [temp-dir (.toFile (java.nio.file.Files/createTempDirectory
                           "vibe-flow-codex-home-test"
                           (make-array java.nio.file.attribute.FileAttribute 0)))
        output-path (str (io/file temp-dir "output.txt"))
        code-home (io/file temp-dir ".workflow/local/agent_homes/impl_codex")
        run {:worker-home :impl_codex
             :worktree {:dir (str temp-dir)}
             :prompt {:path (str (io/file temp-dir "prompt.txt"))
                      :text "Implement this task"}
             :output {:path output-path}}]
    (try
      (testing "worker home must already be configured and is exported for launch"
        (.mkdirs code-home)
        (spit (io/file code-home "config.toml") "model_provider = \"test\"\n")
        (let [calls (atom nil)]
          (with-redefs [shell/sh
                        (fn [& args]
                          (reset! calls args)
                          (spit output-path "done")
                          {:exit 0
                           :out ""
                           :err ""})]
            (let [result (launcher/launch! temp-dir :codex :impl {:id "task-1"} run)
                  auth-path (io/file code-home "auth.json")]
              (is (:ok? result))
              (is (.isDirectory code-home))
              (is (not (.exists auth-path)))
              (is (= (str code-home) (get-in result [:launch :code-home])))
              (is (= :impl (get-in result [:launch :agent-home :role])))
              (is (= true (get-in result [:launch :agent-home :configured?])))
              (let [env (last @calls)]
                (is (= :env (nth @calls (- (count @calls) 2))))
                (is (= (str code-home) (get env "CODEX_HOME")))
                (is (= (System/getenv "PATH") (get env "PATH"))))))))
      (finally
        (doseq [child (reverse (file-seq temp-dir))]
          (.delete child))))))

(deftest codex-launcher-treats-missing-worker-home-as-failed-launch
  (let [temp-dir (.toFile (java.nio.file.Files/createTempDirectory
                           "vibe-flow-codex-missing-home-test"
                           (make-array java.nio.file.attribute.FileAttribute 0)))
        output-path (str (io/file temp-dir "output.txt"))
        run {:worker-home :impl_codex
             :worktree {:dir (str temp-dir)}
             :prompt {:path (str (io/file temp-dir "prompt.txt"))
                      :text "Implement this task"}
             :output {:path output-path}}]
    (try
      (testing "launcher does not create or infer agent-home configuration"
        (let [called? (atom false)]
          (with-redefs [shell/sh
                        (fn [& _args]
                          (reset! called? true)
                          {:exit 0
                           :out ""
                           :err ""})]
            (let [result (launcher/launch! temp-dir :codex :impl {:id "task-1"} run)]
              (is (false? (:ok? result)))
              (is (= :error (:control result)))
              (is (= "Agent home is not ready." (:message result)))
              (is (= :codex (get-in result [:launch :launcher])))
              (is (= :missing-home (get-in result [:launch :agent-home :reason])))
              (is (= :impl_codex (get-in result [:launch :agent-home :home])))
              (is (nil? (get-in result [:launch :exit])))
              (is (false? @called?))))))
      (finally
        (doseq [child (reverse (file-seq temp-dir))]
          (.delete child))))))

(deftest codex-launcher-accepts-auth-json-as-agent-home-configuration
  (let [temp-dir (.toFile (java.nio.file.Files/createTempDirectory
                           "vibe-flow-codex-auth-home-test"
                           (make-array java.nio.file.attribute.FileAttribute 0)))
        output-path (str (io/file temp-dir "output.txt"))
        code-home (io/file temp-dir ".workflow/local/agent_homes/impl_codex")
        run {:worker-home :impl_codex
             :worktree {:dir (str temp-dir)}
             :prompt {:path (str (io/file temp-dir "prompt.txt"))
                      :text "Implement this task"}
             :output {:path output-path}}]
    (try
      (testing "auth.json is accepted when it exists inside the target-local agent home"
        (.mkdirs code-home)
        (spit (io/file code-home "auth.json") "{}\n")
        (with-redefs [shell/sh
                      (fn [& _args]
                        (spit output-path "done")
                        {:exit 0
                         :out ""
                         :err ""})]
          (let [result (launcher/launch! temp-dir :codex :impl {:id "task-1"} run)]
            (is (:ok? result))
            (is (= (str code-home) (get-in result [:launch :code-home])))
            (is (= true (get-in result [:launch :agent-home :configured?]))))))
      (finally
        (doseq [child (reverse (file-seq temp-dir))]
          (.delete child))))))
