(ns spike-v1.paths
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]))

(defn source-file []
  (or *file*
      (System/getProperty "babashka.file")))

(defn toolchain-root []
  (let [cwd (fs/cwd)
        bb-edn (fs/path cwd "bb.edn")]
    (if (fs/exists? bb-edn)
      cwd
      (-> (source-file)
          io/file
          .getCanonicalFile
          .getParentFile
          .getParentFile
          .getParentFile
          fs/path))))

(defn spike-root []
  (fs/parent (toolchain-root)))

(defn target-root []
  (fs/path (spike-root) "spike_target"))

(defn state-root []
  (fs/path (target-root) ".spike-v1"))

(defn install-path []
  (fs/path (state-root) "install.edn"))

(defn tasks-path []
  (fs/path (state-root) "tasks.edn"))

(defn runs-root []
  (fs/path (state-root) "runs"))

(defn prompts-root []
  (fs/path (state-root) "prompts"))

(defn mgr-runs-root []
  (fs/path (state-root) "mgr_runs"))

(defn agent-homes-root []
  (fs/path (state-root) "agent_homes"))

(defn templates-root []
  (fs/path (state-root) "task_types"))

(defn runtime-repo-root []
  (fs/path (target-root) "runtime_repo"))

(defn runtime-readme-path []
  (fs/path (runtime-repo-root) "README.md"))

(defn runtime-feature-path []
  (fs/path (runtime-repo-root) "feature.txt"))

(defn run-dir [run-id]
  (fs/path (runs-root) run-id))

(defn mgr-run-dir [mgr-run-id]
  (fs/path (mgr-runs-root) mgr-run-id))

(defn run-path [run-id]
  (fs/path (run-dir run-id) "run.edn"))

(defn mgr-run-path [mgr-run-id]
  (fs/path (mgr-run-dir mgr-run-id) "mgr_run.edn"))

(defn prompt-file [run-id]
  (fs/path (run-dir run-id) "prompt.txt"))

(defn mgr-prompt-file [mgr-run-id]
  (fs/path (mgr-run-dir mgr-run-id) "prompt.txt"))

(defn output-file [run-id]
  (fs/path (run-dir run-id) "output.txt"))

(defn mgr-output-file [mgr-run-id]
  (fs/path (mgr-run-dir mgr-run-id) "output.txt"))

(defn mgr-cli-script [mgr-run-id]
  (fs/path (mgr-run-dir mgr-run-id) "workflow-advance"))

(defn worktree-dir [run-id]
  (fs/path (run-dir run-id) "worktree"))

(defn mgr-workdir [mgr-run-id]
  (fs/path (mgr-run-dir mgr-run-id) "workdir"))

(defn home-path [home-name]
  (fs/path (agent-homes-root) (name home-name)))
