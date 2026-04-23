(ns vibe-flow.system
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [vibe-flow.definition.task-type :as definition]
   [vibe-flow.management.domain :as domain]
   [vibe-flow.management.task-type :as task-type-manager]
   [vibe-flow.platform.runtime.launcher :as launcher]
   [vibe-flow.platform.state.system-store :as system-store]
   [vibe-flow.platform.toolchain.install :as toolchain-install]
   [vibe-flow.platform.target.install :as install]
   [vibe-flow.workflow.control :as workflow-control]))

(def default-bootstrap-config
  {:task-type :impl
   :collection-id "self-improvement"
   :collection-name "Self Improvement Backlog"
   :task-id "bootstrap-self-optimization"
   :goal "Use vibe-flow to improve the vibe-flow repository through its own workflow."
   :scope ["Work only inside this repository."
           "Prefer small, reviewable changes that strengthen the formal product surface."
           "Keep changes aligned with design.md, architecture.md, and governance.md."]
   :constraints ["Do not break the formal workflow layout under .workflow/."
                 "Do not overwrite existing user changes without an explicit task decision."
                 "Preserve governance and test coverage while evolving the system."]
   :success-criteria ["The repository can self-host the workflow state in .workflow/."
                      "There is a runnable backlog collection bound to the installed impl task_type."
                      "There is at least one seed task for self-optimization."]})

(defn installed-task-type? [target-root task-type]
  (let [task-type* (definition/task-type-id task-type)]
    (boolean
     (some #(= (:id %) task-type*)
           (task-type-manager/list-task-types target-root)))))

(defn ensure-task-type! [target-root task-type]
  (let [task-type* (definition/task-type-id task-type)]
    (if (installed-task-type? target-root task-type*)
      {:status :existing
       :task-type task-type*
       :refreshed?
       (boolean
        (task-type-manager/refresh-generated-task-type!
         target-root
         task-type*
         {:kind :repo-updated
          :reason :prompt-refresh}))}
      (assoc (task-type-manager/create-task-type! target-root task-type*)
             :status :created))))

(defn ensure-collection! [target-root {:keys [collection-id collection-name task-type]}]
  (if-let [existing (domain/inspect-collection target-root collection-id)]
    (do
      (when (not= (:task-type existing) (definition/task-type-id task-type))
        (throw (ex-info "Existing bootstrap collection uses a different task_type."
                        {:collection-id collection-id
                         :expected-task-type (definition/task-type-id task-type)
                         :actual-task-type (:task-type existing)})))
      {:status :existing
       :collection existing})
    {:status :created
     :collection (domain/create-collection! target-root
                                            {:id collection-id
                                             :task-type task-type
                                             :name collection-name})}))

(defn ensure-task! [target-root {:keys [task-id collection-id task-type]
                                 :as bootstrap-config}]
  (if-let [existing (domain/inspect-task target-root task-id)]
    (do
      (when (not= (:collection-id existing) collection-id)
        (throw (ex-info "Existing bootstrap task belongs to a different collection."
                        {:task-id task-id
                         :expected-collection-id collection-id
                         :actual-collection-id (:collection-id existing)})))
      (when (not= (:task-type existing) (definition/task-type-id task-type))
        (throw (ex-info "Existing bootstrap task uses a different task_type."
                        {:task-id task-id
                         :expected-task-type (definition/task-type-id task-type)
                         :actual-task-type (:task-type existing)})))
      {:status :existing
       :task existing})
    {:status :created
     :task (domain/create-task! target-root
                                {:id task-id
                                 :collection-id collection-id
                                 :task-type task-type
                                 :goal (:goal bootstrap-config)
                                 :scope (:scope bootstrap-config)
                                 :constraints (:constraints bootstrap-config)
                                 :success-criteria (:success-criteria bootstrap-config)})}))

(defn bootstrap-self-host!
  ([]
   (bootstrap-self-host! "."))
  ([target-root]
   (bootstrap-self-host! target-root default-bootstrap-config))
  ([target-root bootstrap-config]
   (let [config (merge default-bootstrap-config bootstrap-config)
         install-result (install/install! target-root)
         task-type-result (ensure-task-type! target-root (:task-type config))
         collection-result (ensure-collection! target-root config)
         task-result (ensure-task! target-root config)]
     {:target-root target-root
      :install install-result
      :task-type task-type-result
      :collection collection-result
      :task task-result})))

(defn current-command-root []
  (.getCanonicalPath (io/file ".")))

(defn getenv [name]
  (System/getenv name))

(defn cli-caller-root []
  (.getCanonicalPath (io/file (or (getenv "VIBE_FLOW_CLI_CWD") "."))))

(defn resolve-cli-target [target]
  (let [target-file (io/file (or target "."))
        resolved (if (.isAbsolute target-file)
                   target-file
                   (io/file (cli-caller-root) (or target ".")))]
    (.getCanonicalPath resolved)))

(defn install-toolchain!
  ([]
   (install-toolchain! (current-command-root)))
  ([source-root]
   (toolchain-install/install! source-root)))

(defn install-target!
  ([]
   (install-target! "."))
  ([target-root]
   (install/install! target-root)))

(defn parse-launcher [value]
  (case value
    "mock" :mock
    "codex" :codex
    (throw (ex-info "Unsupported launcher."
                    {:launcher value
                     :supported ["mock" "codex"]}))))

(defn parse-decision [value]
  (let [decision (keyword value)
        allowed #{:impl :review :refine :done :error}]
    (when-not (contains? allowed decision)
      (throw (ex-info "Unsupported mgr decision."
                      {:decision value
                       :allowed-decisions (vec (sort-by name allowed))})))
    decision))

(defn parse-kv-args [args]
  (loop [remaining args
         parsed {}]
    (if (empty? remaining)
      parsed
      (let [[k v & more] remaining]
        (when-not (str/starts-with? (or k "") "--")
          (throw (ex-info "Expected option key starting with --."
                          {:argument k
                           :args args})))
        (when-not v
          (throw (ex-info "Missing value for CLI option."
                          {:argument k
                           :args args})))
        (recur more (assoc parsed k v))))))

(defn parse-cli-args [args]
  (let [[command & more] args
        options (parse-kv-args more)]
    {:command (or command "help")
     :target (resolve-cli-target (get options "--target"))
     :task-id (get options "--task-id")
     :mgr-run-id (get options "--mgr-run-id")
     :worker-launcher (get options "--worker-launcher")
     :decision (get options "--decision")
     :reason (get options "--reason")}))

(defn mgr-advance! [target-root task-id mgr-run-id decision reason]
  (workflow-control/advance-task! target-root
                                  task-id
                                  mgr-run-id
                                  (parse-decision decision)
                                  reason))

(defn usage-lines []
  ["Usage:"
   "  vibe-flow install"
   "  vibe-flow install-target [--target <repo>]"
   "  vibe-flow bootstrap [--target <repo>]"
   "  vibe-flow mgr-advance --target <repo> --task-id <id> --mgr-run-id <id> --decision <impl|review|refine|done|error> --reason <text>"
   ""
   "Examples:"
   "  vibe-flow install"
   "  vibe-flow install-target --target /path/to/repo"
   "  vibe-flow bootstrap --target /path/to/repo"
   "  vibe-flow mgr-advance --target /path/to/repo --task-id task-1 --mgr-run-id mgr-1 --decision impl --reason \"start implementation\""])

(defn print-usage! []
  (doseq [line (usage-lines)]
    (println line)))

(defn system-blueprint []
  {:toolchain {:install! install-toolchain!}
   :bootstrap {:self-host! bootstrap-self-host!}
   :install {:install! install/install!
             :reconcile! install/reconcile!}
   :domain-management {:create-collection! domain/create-collection!
                       :create-task! domain/create-task!
                       :list-collections domain/list-collections
                       :list-tasks domain/list-tasks
                       :inspect-collection domain/inspect-collection
                       :inspect-task domain/inspect-task
                       :validate-task! domain/validate-task!}
   :task-type-management {:create! task-type-manager/create-task-type!
                          :list task-type-manager/list-task-types
                          :inspect task-type-manager/inspect-task-type
                          :register! task-type-manager/register-installed-task-type!}
   :runtime {:launch! launcher/launch!}
   :workflow-control {:select-runnable-task workflow-control/select-runnable-task
                      :create-mgr-run! workflow-control/create-mgr-run!
                      :advance-task! workflow-control/advance-task!
                      :run-once! workflow-control/run-once!
                      :run-loop! workflow-control/run-loop!}
   :system-state {:installed? system-store/installed?
                  :load-install system-store/load-install
                  :load-target system-store/load-target
                  :load-layout system-store/load-layout
                  :load-toolchain system-store/load-toolchain}})

(defn -main [& args]
  (let [{:keys [command target task-id mgr-run-id worker-launcher decision reason]} (parse-cli-args args)]
    (case command
      "help" (print-usage!)
      "install" (prn (install-toolchain!))
      "install-target" (prn (install-target! target))
      "bootstrap" (prn (bootstrap-self-host! target))
      "mgr-advance" (prn (mgr-advance! target task-id mgr-run-id decision reason))
      (throw (ex-info "Unknown vibe-flow command."
                      {:command command
                       :target target})))))
