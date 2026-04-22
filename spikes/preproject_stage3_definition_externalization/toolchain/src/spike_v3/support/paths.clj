(ns spike-v3.support.paths
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

(defn durable-root [target-root]
  (fs/path (state-root target-root) "state"))

(defn system-root [target-root]
  (fs/path (durable-root target-root) "system"))

(defn definitions-root [target-root]
  (fs/path (durable-root target-root) "definitions"))

(defn domain-root [target-root]
  (fs/path (durable-root target-root) "domain"))

(defn local-root [target-root]
  (fs/path (state-root target-root) "local"))

(defn registries-root [target-root]
  (fs/path (system-root target-root) "registries"))

(defn install-path [target-root]
  (fs/path (system-root target-root) "install.edn"))

(defn target-path [target-root]
  (fs/path (system-root target-root) "target.edn"))

(defn layout-path [target-root]
  (fs/path (system-root target-root) "layout.edn"))

(defn task-types-registry-path [target-root]
  (fs/path (registries-root target-root) "task_types.edn"))

(defn task-types-root [target-root]
  (fs/path (definitions-root target-root) "task_types"))

(defn task-type-dir [target-root task-type]
  (fs/path (task-types-root target-root) (name task-type)))

(defn task-type-path [target-root task-type]
  (fs/path (task-type-dir target-root task-type) "task_type.edn"))

(defn task-type-meta-path [target-root task-type]
  (fs/path (task-type-dir target-root task-type) "meta.edn"))

(defn collections-root [target-root]
  (fs/path (domain-root target-root) "collections"))

(defn collection-path [target-root collection-id]
  (fs/path (collections-root target-root) (str collection-id ".edn")))

(defn tasks-root [target-root]
  (fs/path (domain-root target-root) "tasks"))

(defn task-path [target-root task-id]
  (fs/path (tasks-root target-root) (str task-id ".edn")))

(defn runs-root [target-root]
  (fs/path (local-root target-root) "runs"))

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
  (fs/path (local-root target-root) "mgr_runs"))

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
  (fs/path (local-root target-root) "agent_homes"))

(defn home-path [target-root home-name]
  (fs/path (agent-homes-root target-root) (name home-name)))

(defn task-type-bundles-root []
  (fs/path (toolchain-root) "task_type_bundles"))

(defn task-type-bundle-dir [task-type]
  (fs/path (task-type-bundles-root) (name task-type)))
