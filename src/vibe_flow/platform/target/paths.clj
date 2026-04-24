(ns vibe-flow.platform.target.paths
  (:require
   [clojure.java.io :as io]))

(def layout-version 1)

(defn resolve-target-root [target-root]
  (.getCanonicalFile (io/file target-root)))

(defn workflow-root [target-root]
  (io/file (resolve-target-root target-root) ".workflow"))

(defn state-root [target-root]
  (io/file (workflow-root target-root) "state"))

(defn system-root [target-root]
  (io/file (state-root target-root) "system"))

(defn definitions-root [target-root]
  (io/file (state-root target-root) "definitions"))

(defn task-types-root [target-root]
  (io/file (definitions-root target-root) "task_types"))

(defn task-type-dir [target-root task-type]
  (io/file (task-types-root target-root) (name task-type)))

(defn task-type-path [target-root task-type]
  (io/file (task-type-dir target-root task-type) "task_type.edn"))

(defn task-type-meta-path [target-root task-type]
  (io/file (task-type-dir target-root task-type) "meta.edn"))

(defn task-type-prompts-dir [target-root task-type]
  (io/file (task-type-dir target-root task-type) "prompts"))

(defn task-type-hooks-dir [target-root task-type]
  (io/file (task-type-dir target-root task-type) "hooks"))

(defn domain-root [target-root]
  (io/file (state-root target-root) "domain"))

(defn collections-root [target-root]
  (io/file (domain-root target-root) "collections"))

(defn collection-path [target-root collection-id]
  (io/file (collections-root target-root) (str collection-id ".edn")))

(defn tasks-root [target-root]
  (io/file (domain-root target-root) "tasks"))

(defn task-path [target-root task-id]
  (io/file (tasks-root target-root) (str task-id ".edn")))

(defn local-root [target-root]
  (io/file (workflow-root target-root) "local"))

(defn task-runtime-root [target-root]
  (io/file (local-root target-root) "task_runtime"))

(defn task-runtime-path [target-root task-id]
  (io/file (task-runtime-root target-root) (str task-id ".edn")))

(defn runs-root [target-root]
  (io/file (local-root target-root) "runs"))

(defn run-dir [target-root run-id]
  (io/file (runs-root target-root) run-id))

(defn run-path [target-root run-id]
  (io/file (run-dir target-root run-id) "run.edn"))

(defn run-prompt-path [target-root run-id]
  (io/file (run-dir target-root run-id) "prompt.txt"))

(defn run-output-path [target-root run-id]
  (io/file (run-dir target-root run-id) "output.txt"))

(defn run-worktree-dir [target-root run-id]
  (io/file (run-dir target-root run-id) "worktree"))

(defn mgr-runs-root [target-root]
  (io/file (local-root target-root) "mgr_runs"))

(defn mgr-run-dir [target-root mgr-run-id]
  (io/file (mgr-runs-root target-root) mgr-run-id))

(defn mgr-run-path [target-root mgr-run-id]
  (io/file (mgr-run-dir target-root mgr-run-id) "mgr_run.edn"))

(defn mgr-run-prompt-path [target-root mgr-run-id]
  (io/file (mgr-run-dir target-root mgr-run-id) "prompt.txt"))

(defn mgr-run-output-path [target-root mgr-run-id]
  (io/file (mgr-run-dir target-root mgr-run-id) "output.txt"))

(defn mgr-run-workdir [target-root mgr-run-id]
  (io/file (mgr-run-dir target-root mgr-run-id) "workdir"))

(defn mgr-run-cli-path [target-root mgr-run-id]
  (io/file (mgr-run-dir target-root mgr-run-id) "workflow-advance"))

(defn agent-homes-root [target-root]
  (io/file (local-root target-root) "agent_homes"))

(defn agent-home-path [target-root home-name]
  (io/file (agent-homes-root target-root) (name home-name)))

(defn registries-root [target-root]
  (io/file (system-root target-root) "registries"))

(defn task-types-registry-path [target-root]
  (io/file (registries-root target-root) "task_types.edn"))

(defn install-path [target-root]
  (io/file (system-root target-root) "install.edn"))

(defn target-path [target-root]
  (io/file (system-root target-root) "target.edn"))

(defn layout-path [target-root]
  (io/file (system-root target-root) "layout.edn"))

(defn toolchain-path [target-root]
  (io/file (system-root target-root) "toolchain.edn"))

(defn materialized-directories [target-root]
  [(workflow-root target-root)
   (state-root target-root)
   (system-root target-root)
   (registries-root target-root)
   (definitions-root target-root)
   (task-types-root target-root)
   (domain-root target-root)
   (collections-root target-root)
   (tasks-root target-root)
   (local-root target-root)
   (task-runtime-root target-root)
   (runs-root target-root)
   (mgr-runs-root target-root)
   (agent-homes-root target-root)])

(defn layout-map [target-root]
  {:target-root (str (resolve-target-root target-root))
   :workflow-root (str (workflow-root target-root))
   :state-root (str (state-root target-root))
   :system-root (str (system-root target-root))
   :registries-root (str (registries-root target-root))
   :definitions-root (str (definitions-root target-root))
   :task-types-root (str (task-types-root target-root))
   :domain-root (str (domain-root target-root))
   :collections-root (str (collections-root target-root))
   :tasks-root (str (tasks-root target-root))
   :local-root (str (local-root target-root))
   :task-runtime-root (str (task-runtime-root target-root))
   :runs-root (str (runs-root target-root))
   :mgr-runs-root (str (mgr-runs-root target-root))
   :agent-homes-root (str (agent-homes-root target-root))})
