(ns spike-v3.target.install
  (:require
   [babashka.fs :as fs]
   [clojure.string :as str]
   [spike-v3.management.task-type-manager :as task-type-manager]
   [spike-v3.support.paths :as paths]
   [spike-v3.support.util :as util]
   [spike-v3.target.repo :as target-repo]))

(def gitignore-begin "# >>> preproject_stage3_definition_externalization workflow >>>")
(def gitignore-end "# <<< preproject_stage3_definition_externalization workflow <<<")

(def gitignore-block
  (str gitignore-begin "\n"
       "/.workflow/local/\n"
       gitignore-end "\n"))

(defn target-layout [target-root]
  {:target-root (str target-root)
   :state-root (str (paths/state-root target-root))
   :durable-root (str (paths/durable-root target-root))
   :system-root (str (paths/system-root target-root))
   :definitions-root (str (paths/definitions-root target-root))
   :domain-root (str (paths/domain-root target-root))
   :registries-root (str (paths/registries-root target-root))
   :task-types-root (str (paths/task-types-root target-root))
   :collections-root (str (paths/collections-root target-root))
   :tasks-root (str (paths/tasks-root target-root))
   :local-root (str (paths/local-root target-root))
   :runs-root (str (paths/runs-root target-root))
   :mgr-runs-root (str (paths/mgr-runs-root target-root))
   :agent-homes-root (str (paths/agent-homes-root target-root))})

(defn layout-record [target-root]
  {:layout-version 1
   :updated-at (util/now)
   :state-layout
   {:system-root (str (paths/system-root target-root))
    :definitions-root (str (paths/definitions-root target-root))
    :domain-root (str (paths/domain-root target-root))
    :local-root (str (paths/local-root target-root))}
   :registries
   {:task-types (str (paths/task-types-registry-path target-root))}})

(defn install-record [target-root]
  {:spike-version 3
   :installed-at (util/now)
   :toolchain-root (str (paths/toolchain-root))
   :target-root (str target-root)
   :workflow-state-root (str (paths/state-root target-root))
   :target-layout (target-layout target-root)
   :principles
   ["target is the git repository being worked on"
    "definition layer is installed as artifacts inside target/.workflow/state/definitions/task_types"
    "workflow state is split into system models, definition models, domain models, and local runtime state"
    "task_type is managed as a package with task_type.edn, meta.edn, prompts, hooks, and registry entry"
    "install writes install metadata, target metadata, layout metadata, task_type artifacts, agent_homes, and .gitignore updates"
    "mgr remains a separate agent and calls workflow CLI to return control to toolchain"
    "run is both an execution record and a runtime container"
    "each run owns its own worktree under .workflow/local/runs/<run-id>/worktree"
    "prepare_run remains built in while task_type can declaratively influence it"
    "before_prepare_run is the only extension point and may return limited fields only"
    "agent_home remains opaque at toolchain level"]
   :governance
   {:system-models [(str (paths/install-path target-root))
                    (str (paths/target-path target-root))
                    (str (paths/layout-path target-root))
                    (str (paths/task-types-registry-path target-root))]
    :definition-models [(str (paths/task-types-root target-root))]
    :domain-models [(str (paths/collections-root target-root))
                    (str (paths/tasks-root target-root))]
    :local-runtime-state [(str (paths/runs-root target-root))
                          (str (paths/mgr-runs-root target-root))
                          (str (paths/agent-homes-root target-root))]}
   :launcher
   {:default :mock
    :supported [:mock :codex]
    :codex-command "codex"}})

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
      "This directory is an installed agent_home for preproject_stage3_definition_externalization.\n\n"
      "* toolchain treats this as opaque\n"
      "* codex runtime consumes it through CODEX_HOME\n"
      "* ~/.codex/config.toml is used only as an install-time source template\n"
      "* runtime must use the copied config.toml in this directory, not ~/.codex/config.toml\n"
      "* auth/secrets are intentionally not copied by toolchain\n"))))

(defn copy-dir-contents! [source-dir target-dir]
  (doseq [path (file-seq (java.io.File. (str source-dir)))
          :when (not= (str (fs/path path)) (str source-dir))]
    (let [source-path (fs/path path)
          relative-path (fs/relativize source-dir source-path)
          dest-path (fs/path target-dir relative-path)]
      (if (.isDirectory path)
        (fs/create-dirs dest-path)
        (do
          (fs/create-dirs (fs/parent dest-path))
          (fs/copy source-path dest-path {:replace-existing true})))))) 

(defn safe-delete-tree! [path]
  (when (fs/exists? path)
    (try
      (fs/delete-tree path)
      (catch java.nio.file.NoSuchFileException _
        nil))))

(defn safe-delete-path! [path]
  (when (fs/exists? path)
    (try
      (if (fs/directory? path)
        (fs/delete-tree path)
        (fs/delete path))
      (catch java.nio.file.NoSuchFileException _
        nil))))

(defn install-task-type! [target-root task-type]
  (let [source-dir (paths/task-type-bundle-dir task-type)
        target-dir (paths/task-type-dir target-root task-type)]
    (when-not (fs/exists? source-dir)
      (throw (ex-info "task_type bundle source is missing"
                      {:task-type task-type
                       :source-dir (str source-dir)})))
    (safe-delete-tree! target-dir)
    (fs/create-dirs (paths/task-types-root target-root))
    (fs/create-dirs target-dir)
    (copy-dir-contents! source-dir target-dir)
    (doseq [hook-path (fs/glob target-dir "hooks/*")]
      (.setExecutable (java.io.File. (str hook-path)) true))
    (task-type-manager/register-installed-task-type!
     target-root
     task-type
     {:kind :toolchain-bundle
      :bundle-root (str source-dir)})))

(defn reset-state-dirs! [target-root]
  (doseq [dir [(paths/runs-root target-root)
               (paths/mgr-runs-root target-root)
               (paths/agent-homes-root target-root)]]
    (safe-delete-tree! dir)
    (fs/create-dirs dir)))

(defn cleanup-legacy-durable-layout! [target-root]
  (doseq [path [(fs/path (paths/durable-root target-root) "install.edn")
                (fs/path (paths/durable-root target-root) "target.edn")
                (fs/path (paths/durable-root target-root) "task_types")
                (fs/path (paths/durable-root target-root) "collections")
                (fs/path (paths/durable-root target-root) "tasks")]]
    (safe-delete-path! path)))

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
               (paths/durable-root target-root)
               (paths/system-root target-root)
               (paths/definitions-root target-root)
               (paths/domain-root target-root)
               (paths/registries-root target-root)
               (paths/task-types-root target-root)
               (paths/collections-root target-root)
               (paths/tasks-root target-root)
               (paths/local-root target-root)
               (paths/runs-root target-root)
               (paths/mgr-runs-root target-root)
               (paths/agent-homes-root target-root)]]
    (fs/create-dirs dir))
  (cleanup-legacy-durable-layout! target-root)
  (target-repo/reset-runtime-state! target-root)
  (reset-state-dirs! target-root)
  (doseq [home [:mgr_codex :impl_codex :review_codex :refine_codex]]
    (install-agent-home! target-root home))
  (install-task-type! target-root :impl)
  (install-gitignore! target-root)
  (util/write-edn! (paths/install-path target-root) (install-record target-root))
  (util/write-edn! (paths/target-path target-root) (target-record target-root))
  (util/write-edn! (paths/layout-path target-root) (layout-record target-root))
  (println "Installed preproject_stage3_definition_externalization workflow into" (str target-root)))

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
