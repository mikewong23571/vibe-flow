(ns spike-v1.install
  (:require
   [babashka.fs :as fs]
   [spike-v1.model :as model]
   [spike-v1.paths :as paths]
   [spike-v1.runtime-repo :as runtime-repo]
   [spike-v1.util :as util]))

(defn target-layout []
  {:target-root (str (paths/target-root))
   :state-root (str (paths/state-root))
   :tasks-path (str (paths/tasks-path))
   :runs-root (str (paths/runs-root))
   :mgr-runs-root (str (paths/mgr-runs-root))
   :prompts-root (str (paths/prompts-root))
   :templates-root (str (paths/templates-root))
   :agent-homes-root (str (paths/agent-homes-root))
   :runtime-repo-root (str (paths/runtime-repo-root))})

(defn install-record []
  {:spike-version 1
   :installed-at (util/now)
   :toolchain-root (str (paths/toolchain-root))
   :target-layout (target-layout)
   :principles
   ["toolchain and runtime target are split into sibling directories"
    "toolchain installs its own state into spike_target"
    "install resets prior runs, mgr_runs, and detached worktrees for a clean spike target"
    "mgr decides which worker to launch next using task state and latest run outputs"
    "run is both an execution record and a worker runtime envelope"
    "each run owns its own worktree under runs/<run-id>/worktree"
    "task expands into a sequence of worker launch -> run"
    "agent should mainly see the run worktree plus explicit task input"
    "agent_home is an opaque directory at toolchain level"
    "~/.codex/config.toml is copied only at install time; runtime uses the copied agent_home only"]
   :launcher
   {:default :mock
    :supported [:mock :codex]
    :codex-command "codex"}
   :task-types
   {:impl
    {:mgr-home :mgr_codex
     :workers {:todo :impl
               :awaiting-review :review
               :awaiting-refine :refine}
     :worker-homes model/worker-home-map}}
   :runtime
   {:repo-kind :git
    :repo-root (str (paths/runtime-repo-root))
    :run-worktree-layout "runs/<run-id>/worktree"}})

(defn install-agent-home! [home-name]
  (let [dest (paths/home-path home-name)
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
      "This directory is an installed agent_home for preproject_stage1_minimal_workflow.\n\n"
      "* toolchain treats this as opaque\n"
      "* codex runtime consumes it through CODEX_HOME\n"
      "* ~/.codex/config.toml is used only as an install-time source template\n"
      "* runtime must use the copied config.toml in this directory, not ~/.codex/config.toml\n"
      "* auth/secrets are intentionally not copied by toolchain\n"))))

(defn install-prompts! []
  (doseq [[worker template] model/prompt-templates]
    (util/write-file! (fs/path (paths/prompts-root) (str (name worker) ".txt")) template)))

(defn install-task-types! []
  (util/write-edn!
   (fs/path (paths/templates-root) "impl.edn")
   {:task-type :impl
    :workers model/worker-order
    :worker-homes model/worker-home-map
    :note "Minimal spike task type. Each run owns a worktree under runs/<run-id>/worktree."}))

(defn reset-state-dirs! []
  (doseq [dir [(paths/runs-root) (paths/mgr-runs-root)]]
    (when (fs/exists? dir)
      (fs/delete-tree dir))
    (fs/create-dirs dir)))

(defn install! []
  (doseq [dir [(paths/state-root) (paths/runs-root) (paths/mgr-runs-root) (paths/prompts-root) (paths/agent-homes-root) (paths/templates-root)]]
    (fs/create-dirs dir))
  (when (fs/exists? (fs/path (paths/target-root) "workspace"))
    (fs/delete-tree (fs/path (paths/target-root) "workspace")))
  (runtime-repo/ensure-runtime-repo!)
  (runtime-repo/reset-runtime-state!)
  (reset-state-dirs!)
  (doseq [home (vals model/worker-home-map)]
    (install-agent-home! home))
  (install-prompts!)
  (install-task-types!)
  (util/write-file!
   (fs/path (paths/target-root) "README.md")
   (str
    "# spike_target\n\n"
    "This directory is both the installation target and the runtime target for preproject_stage1_minimal_workflow.\n\n"
    "* toolchain-installed state lives under `.spike-v1/`\n"
    "* the seed git repository lives under `runtime_repo/`\n"
    "* each worker run gets its own worktree under `.spike-v1/runs/<run-id>/worktree`\n"))
  (util/write-edn! (paths/install-path) (install-record))
  (util/write-edn! (paths/tasks-path) [])
  (println "Installed spike state into" (paths/target-root)))

(defn load-install! []
  (let [path (paths/install-path)]
    (when-not (fs/exists? path)
      (throw (ex-info "spike_target is not installed yet" {:path (str path)})))
    (util/read-edn path nil)))
