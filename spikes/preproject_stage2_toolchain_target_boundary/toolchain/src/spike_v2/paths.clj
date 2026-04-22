(ns spike-v2.paths
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

(defn default-target-root []
  (fs/path (spike-root) "sample_target"))

(defn resolve-target-root [target]
  (-> (or target (default-target-root))
      fs/absolutize
      fs/canonicalize))

(defn state-root [target-root]
  (fs/path target-root ".workflow"))

(defn install-path [target-root]
  (fs/path (state-root target-root) "install.edn"))

(defn target-path [target-root]
  (fs/path (state-root target-root) "target.edn"))

(defn task-types-root [target-root]
  (fs/path (state-root target-root) "task_types"))

(defn task-type-dir [target-root task-type]
  (fs/path (task-types-root target-root) (name task-type)))

(defn task-type-path [target-root task-type]
  (fs/path (task-type-dir target-root task-type) "task_type.edn"))

(defn task-type-prompts-root [target-root task-type]
  (fs/path (task-type-dir target-root task-type) "prompts"))

(defn task-type-prompt-path [target-root task-type prompt-name]
  (fs/path (task-type-prompts-root target-root task-type) (str (name prompt-name) ".txt")))

(defn task-type-prepare-run-script [target-root task-type]
  (fs/path (task-type-dir target-root task-type) "prepare_run"))

(defn collections-root [target-root]
  (fs/path (state-root target-root) "collections"))

(defn collection-path [target-root collection-id]
  (fs/path (collections-root target-root) (str collection-id ".edn")))

(defn tasks-root [target-root]
  (fs/path (state-root target-root) "tasks"))

(defn task-path [target-root task-id]
  (fs/path (tasks-root target-root) (str task-id ".edn")))

(defn runs-root [target-root]
  (fs/path (state-root target-root) "runs"))

(defn run-dir [target-root run-id]
  (fs/path (runs-root target-root) run-id))

(defn run-path [target-root run-id]
  (fs/path (run-dir target-root run-id) "run.edn"))

(defn prompt-file [target-root run-id]
  (fs/path (run-dir target-root run-id) "prompt.txt"))

(defn output-file [target-root run-id]
  (fs/path (run-dir target-root run-id) "output.txt"))

(defn worktree-dir [target-root run-id]
  (fs/path (run-dir target-root run-id) "worktree"))

(defn mgr-runs-root [target-root]
  (fs/path (state-root target-root) "mgr_runs"))

(defn mgr-run-dir [target-root mgr-run-id]
  (fs/path (mgr-runs-root target-root) mgr-run-id))

(defn mgr-run-path [target-root mgr-run-id]
  (fs/path (mgr-run-dir target-root mgr-run-id) "mgr_run.edn"))

(defn mgr-prompt-file [target-root mgr-run-id]
  (fs/path (mgr-run-dir target-root mgr-run-id) "prompt.txt"))

(defn mgr-output-file [target-root mgr-run-id]
  (fs/path (mgr-run-dir target-root mgr-run-id) "output.txt"))

(defn mgr-cli-script [target-root mgr-run-id]
  (fs/path (mgr-run-dir target-root mgr-run-id) "workflow-advance"))

(defn mgr-workdir [target-root mgr-run-id]
  (fs/path (mgr-run-dir target-root mgr-run-id) "workdir"))

(defn agent-homes-root [target-root]
  (fs/path (state-root target-root) "agent_homes"))

(defn home-path [target-root home-name]
  (fs/path (agent-homes-root target-root) (name home-name)))
