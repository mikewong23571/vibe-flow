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
  (.getCanonicalPath ^java.io.File (io/file ".")))

(defn getenv [name]
  (System/getenv name))

(defn cli-caller-root []
  (.getCanonicalPath ^java.io.File (io/file (or (getenv "VIBE_FLOW_CLI_CWD") "."))))

(defn resolve-cli-target [target]
  (let [^java.io.File target-file (io/file (or target "."))
        ^java.io.File resolved (if (.isAbsolute target-file)
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

(defn list-task-types
  ([]
   (list-task-types "."))
  ([target-root]
   (task-type-manager/list-task-types target-root)))

(defn inspect-task-type
  ([target-root task-type]
   (task-type-manager/inspect-task-type target-root task-type)))

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
     :task-type (get options "--task-type")
     :task-id (get options "--task-id")
     :mgr-run-id (get options "--mgr-run-id")
     :worker-launcher (get options "--worker-launcher")
     :decision (get options "--decision")
     :reason (get options "--reason")}))

(defn required-cli-option [command option-name value]
  (when-not value
    (throw (ex-info "Missing required CLI option."
                    {:command command
                     :option option-name})))
  value)

(defn mgr-advance! [target-root task-id mgr-run-id decision reason]
  (workflow-control/advance-task! target-root
                                  task-id
                                  mgr-run-id
                                  (parse-decision decision)
                                  reason))

(defn planned-product-cli-command! [command metadata]
  (throw (ex-info "Governed product CLI command is not implemented yet."
                  (assoc metadata
                         :command command
                         :status :planned
                         :surface :product-cli))))

(defn governed-product-cli-design-doc-path []
  "docs/plan/product-cli-facade-governance.md")

(defn governed-cli-provider-whitelist []
  [{:id :tasks
    :kind :resource-family
    :status :planned
    :provider-ns 'vibe-flow.product.cli.tasks
    :provider-fn 'command-spec
    :subcommands [:list :show]
    :design-doc (governed-product-cli-design-doc-path)}
   {:id :collections
    :kind :resource-family
    :status :planned
    :provider-ns 'vibe-flow.product.cli.collections
    :provider-fn 'command-spec
    :subcommands [:list :show]
    :design-doc (governed-product-cli-design-doc-path)}
   {:id :task-types
    :kind :resource-family
    :status :planned
    :provider-ns 'vibe-flow.product.cli.task-types
    :provider-fn 'command-spec
    :subcommands [:list :show]
    :design-doc (governed-product-cli-design-doc-path)}
   {:id :agent-homes
    :kind :resource-family
    :status :planned
    :provider-ns 'vibe-flow.product.cli.agent-homes
    :provider-fn 'command-spec
    :subcommands [:list :show]
    :design-doc (governed-product-cli-design-doc-path)}
   {:id :doctor
    :kind :singleton-command
    :status :planned
    :provider-ns 'vibe-flow.product.cli.doctor
    :provider-fn 'command-spec
    :design-doc (governed-product-cli-design-doc-path)}])

(defn governed-cli-registry-contract []
  {:status :planned
   :design-doc (governed-product-cli-design-doc-path)
   :registry-ns 'vibe-flow.product.cli.registry
   :provider-whitelist-fn 'governed-cli-provider-whitelist
   :allowed-kinds #{:resource-family :singleton-command}
   :required-provider-fields [:id :kind :status :provider-ns :provider-fn :design-doc]
   :resource-family-required-fields [:subcommands]
   :singleton-command-required-fields []})

(defn load-governed-cli-registry! []
  (planned-product-cli-command! :load-governed-cli-registry
                                {:kind :registry-contract
                                 :contract (governed-cli-registry-contract)}))

(defn dispatch-governed-cli-command!
  ([command]
   (dispatch-governed-cli-command! command nil {}))
  ([command target-root options]
   (planned-product-cli-command! :dispatch-governed-cli-command
                                 {:kind :registry-contract
                                  :requested-command command
                                  :target-root target-root
                                  :options options
                                  :contract (governed-cli-registry-contract)})))

(defn governed-cli-command-whitelist []
  {:implemented {"help" {:status :implemented
                         :kind :singleton-command}
                 "install" {:status :implemented
                            :kind :singleton-command}
                 "install-target" {:status :implemented
                                   :kind :singleton-command}
                 "bootstrap" {:status :implemented
                              :kind :singleton-command}
                 "list-task-types" {:status :implemented
                                    :kind :singleton-command}
                 "inspect-task-type" {:status :implemented
                                      :kind :singleton-command}
                 "mgr-advance" {:status :implemented
                                :kind :singleton-command}}
   :design-doc (governed-product-cli-design-doc-path)
   :planned {:providers (governed-cli-provider-whitelist)
             :registry-contract (governed-cli-registry-contract)}})

(defn usage-lines []
  ["Usage:"
   "  vibe-flow install"
   "  vibe-flow install-target [--target <repo>]"
   "  vibe-flow bootstrap [--target <repo>]"
   "  vibe-flow list-task-types [--target <repo>]"
   "  vibe-flow inspect-task-type [--target <repo>] --task-type <id>"
   "  vibe-flow mgr-advance --target <repo> --task-id <id> --mgr-run-id <id> --decision <impl|review|refine|done|error> --reason <text>"
   ""
   "Examples:"
   "  vibe-flow install"
   "  vibe-flow install-target --target /path/to/repo"
   "  vibe-flow bootstrap --target /path/to/repo"
   "  vibe-flow list-task-types --target /path/to/repo"
   "  vibe-flow inspect-task-type --target /path/to/repo --task-type impl"
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
                          :list list-task-types
                          :inspect inspect-task-type
                          :register! task-type-manager/register-installed-task-type!}
   :product-cli-governance {:provider-whitelist governed-cli-provider-whitelist
                            :registry-contract governed-cli-registry-contract
                            :load-registry! load-governed-cli-registry!
                            :dispatch! dispatch-governed-cli-command!
                            :whitelist governed-cli-command-whitelist}
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
  (let [{:keys [command target task-type task-id mgr-run-id decision reason]} (parse-cli-args args)]
    (case command
      "help" (print-usage!)
      "install" (prn (install-toolchain!))
      "install-target" (prn (install-target! target))
      "bootstrap" (prn (bootstrap-self-host! target))
      "list-task-types" (prn (list-task-types target))
      "inspect-task-type" (prn (inspect-task-type target
                                                  (required-cli-option command "--task-type" task-type)))
      "mgr-advance" (prn (mgr-advance! target task-id mgr-run-id decision reason))
      (throw (ex-info "Unknown vibe-flow command."
                      {:command command
                       :target target})))))
