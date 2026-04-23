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
        run {:worker-home :impl_codex
             :worktree {:dir (str temp-dir)}
             :prompt {:path (str (io/file temp-dir "prompt.txt"))
                      :text "Implement this task"}
             :output {:path output-path}}]
    (try
      (testing "worker home is created and exported even on first launch"
        (let [calls (atom nil)]
          (with-redefs [shell/sh
                        (fn [& args]
                          (reset! calls args)
                          (spit output-path "done")
                          {:exit 0
                           :out ""
                           :err ""})]
            (let [result (launcher/launch! temp-dir :codex :impl {:id "task-1"} run)
                  code-home (io/file temp-dir ".workflow/local/agent_homes/impl_codex")]
              (is (:ok? result))
              (is (.isDirectory code-home))
              (is (= (str code-home) (get-in result [:launch :code-home])))
              (let [env (last @calls)]
                (is (= :env (nth @calls (- (count @calls) 2))))
                (is (= (str code-home) (get env "CODEX_HOME")))
                (is (= (System/getenv "PATH") (get env "PATH"))))))))
      (finally
        (doseq [child (reverse (file-seq temp-dir))]
          (.delete child))))))
