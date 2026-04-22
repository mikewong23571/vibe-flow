(ns spike-v2.install
  (:require
   [babashka.fs :as fs]
   [clojure.string :as str]
   [spike-v2.model :as model]
   [spike-v2.paths :as paths]
   [spike-v2.target-repo :as target-repo]
   [spike-v2.util :as util]))

(def gitignore-begin "# >>> preproject_stage2_toolchain_target_boundary workflow >>>")
(def gitignore-end "# <<< preproject_stage2_toolchain_target_boundary workflow <<<")

(def gitignore-block
  (str gitignore-begin "\n"
       "/.workflow/agent_homes/\n"
       "/.workflow/runs/\n"
       "/.workflow/mgr_runs/\n"
       gitignore-end "\n"))

(defn target-layout [target-root]
  {:target-root (str target-root)
   :state-root (str (paths/state-root target-root))
   :task-types-root (str (paths/task-types-root target-root))
   :collections-root (str (paths/collections-root target-root))
   :tasks-root (str (paths/tasks-root target-root))
   :runs-root (str (paths/runs-root target-root))
   :mgr-runs-root (str (paths/mgr-runs-root target-root))
   :agent-homes-root (str (paths/agent-homes-root target-root))})

(defn install-record [target-root]
  {:spike-version 2
   :installed-at (util/now)
   :toolchain-root (str (paths/toolchain-root))
   :target-root (str target-root)
   :workflow-state-root (str (paths/state-root target-root))
   :target-layout (target-layout target-root)
   :principles
   ["target is the git repository being worked on"
    "workflow state is installed inside target/.workflow"
    "install writes install metadata, target metadata, task_types, agent_homes, and .gitignore updates"
    "mgr remains a separate agent and calls workflow CLI to return control to toolchain"
    "run is both an execution record and a runtime container"
    "each run owns its own worktree under .workflow/runs/<run-id>/worktree"
    "task_type.prepare_run is a formal command hook"
    "agent_home remains opaque at toolchain level"]
   :launcher
   {:default :mock
    :supported [:mock :codex]
    :codex-command "codex"}
   :task-types
   {:impl
    {:mgr-home :mgr_codex
     :workers model/worker-order
     :worker-homes model/worker-home-map
     :prepare-run
     {:kind :command
      :hook (str (paths/task-type-prepare-run-script target-root :impl))
      :result-format :edn}}}})

(defn target-record [target-root]
  {:target-root (str target-root)
   :repo-kind :git
   :installed-at (util/now)
   :current-branch (target-repo/current-branch target-root)
   :current-head (target-repo/current-head target-root)
   :workflow-state-root (str (paths/state-root target-root))})

(defn install-agent-home! [target-root home-name]
  (let [dest (paths/home-path target-root home-name)
        user-config (fs/path (System/getProperty "user.home") ".codex" "config.toml")
        readme (fs/path dest "README.md")
        source-meta (fs/path dest "install-source.edn")]
    (fs/create-dirs dest)
    (if (fs/exists? user-config)
      (fs/copy user-config (fs/path dest "config.toml") {:replace-existing true})
      (util/write-file! (fs/path dest "config.toml")
                        "model = \"gpt-5.4\"\n"))
    (util/write-edn!
     source-meta
     {:home-name home-name
      :installed-at (util/now)
      :config-source (if (fs/exists? user-config)
                       (str user-config)
                       :generated-fallback)
      :runtime-rule "After install, launcher must use only this copied agent_home via CODEX_HOME."})
    (util/write-file!
     readme
     (str
      "# " (name home-name) "\n\n"
      "This directory is an installed agent_home for preproject_stage2_toolchain_target_boundary.\n\n"
      "* toolchain treats this as opaque\n"
      "* codex runtime consumes it through CODEX_HOME\n"
      "* ~/.codex/config.toml is used only as an install-time source template\n"
      "* runtime must use the copied config.toml in this directory, not ~/.codex/config.toml\n"
      "* auth/secrets are intentionally not copied by toolchain\n"))))

(defn install-task-type! [target-root task-type]
  (let [task-type-dir (paths/task-type-dir target-root task-type)
        hook-path (paths/task-type-prepare-run-script target-root task-type)]
    (fs/create-dirs (paths/task-type-prompts-root target-root task-type))
    (util/write-edn!
     (paths/task-type-path target-root task-type)
     {:task-type task-type
      :mgr-home :mgr_codex
      :workers model/worker-order
      :worker-homes model/worker-home-map
      :prepare-run {:kind :command
                    :hook (str hook-path)
                    :result-format :edn}
      :note "Minimal preproject_stage2_toolchain_target_boundary task type installed into target/.workflow/task_types/impl."})
    (doseq [[prompt-name template] model/prompt-templates]
      (util/write-file!
       (paths/task-type-prompt-path target-root task-type prompt-name)
       template))
    (util/write-file!
     hook-path
     (str "#!/usr/bin/env bash\n"
          "set -euo pipefail\n"
          "cd " (pr-str (str (paths/toolchain-root))) "\n"
          "bb -m spike-v2.toolchain task-type-prepare-run --target "
          (pr-str (str target-root))
          " \"$@\"\n"))
    (.setExecutable (java.io.File. (str hook-path)) true)
    (util/write-file!
     (fs/path task-type-dir "README.md")
     (str
      "# " (name task-type) "\n\n"
      "Installed task_type artifact for preproject_stage2_toolchain_target_boundary.\n\n"
      "* prompts live under `prompts/`\n"
      "* `prepare_run` is a formal command hook\n"
      "* toolchain invokes the hook before each worker launch\n"))))

(defn reset-state-dirs! [target-root]
  (doseq [dir [(paths/runs-root target-root)
               (paths/mgr-runs-root target-root)]]
    (when (fs/exists? dir)
      (fs/delete-tree dir))
    (fs/create-dirs dir)))

(defn install-gitignore! [target-root]
  (let [path (fs/path target-root ".gitignore")
        existing (if (fs/exists? path)
                   (slurp (str path))
                   "")
        pattern (re-pattern (str "(?s)" (java.util.regex.Pattern/quote gitignore-begin)
                                 ".*?"
                                 (java.util.regex.Pattern/quote gitignore-end)
                                 "\\n?"))
        cleaned (str/replace existing pattern "")
        base (if (str/blank? cleaned)
               ""
               (str (str/trimr cleaned) "\n\n"))
        updated (str base gitignore-block)]
    (util/write-file! path updated)))

(defn install! [target-root]
  (target-repo/require-git-repo! target-root)
  (doseq [dir [(paths/state-root target-root)
               (paths/task-types-root target-root)
               (paths/collections-root target-root)
               (paths/tasks-root target-root)
               (paths/runs-root target-root)
               (paths/mgr-runs-root target-root)
               (paths/agent-homes-root target-root)]]
    (fs/create-dirs dir))
  (target-repo/reset-runtime-state! target-root)
  (reset-state-dirs! target-root)
  (doseq [home (vals model/worker-home-map)]
    (install-agent-home! target-root home))
  (install-task-type! target-root :impl)
  (install-gitignore! target-root)
  (util/write-edn! (paths/install-path target-root) (install-record target-root))
  (util/write-edn! (paths/target-path target-root) (target-record target-root))
  (println "Installed preproject_stage2_toolchain_target_boundary workflow into" (str target-root)))

(defn load-install! [target-root]
  (let [path (paths/install-path target-root)]
    (when-not (fs/exists? path)
      (throw (ex-info "target is not installed yet" {:path (str path)})))
    (util/read-edn path nil)))

(defn load-target! [target-root]
  (let [path (paths/target-path target-root)]
    (when-not (fs/exists? path)
      (throw (ex-info "target metadata is missing" {:path (str path)})))
    (util/read-edn path nil)))
