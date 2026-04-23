(ns vibe-flow.definition.task-type
  (:require
   [clojure.edn :as clojure-edn]
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.string :as str]
   [vibe-flow.definition.rendering :as rendering]
   [vibe-flow.platform.support.edn :as edn]
   [vibe-flow.platform.target.paths :as paths]
   [vibe-flow.platform.target.repo :as repo]))

(def task-type-segment-pattern #"^[A-Za-z0-9][A-Za-z0-9._-]*$")

(defn validate-task-type-id! [task-type]
  (when (and (string? task-type) (str/blank? task-type))
    (throw (ex-info "task_type must be non-blank."
                    {:task-type task-type})))
  (let [task-type* (if (keyword? task-type)
                     task-type
                     (keyword task-type))]
    (when (namespace task-type*)
      (throw (ex-info "task_type must be unqualified."
                      {:task-type task-type*})))
    (when-not (re-matches task-type-segment-pattern (name task-type*))
      (throw (ex-info "task_type must use only letters, numbers, '.', '_' or '-'."
                      {:task-type task-type*})))
    task-type*))

(defn task-type-id [task-type]
  (validate-task-type-id! task-type))

(defn load-task-type [target-root task-type]
  (let [task-type* (task-type-id task-type)
        ^java.io.File path (paths/task-type-path target-root task-type*)]
    (when-not (.exists path)
      (throw (ex-info "task_type is not installed."
                      {:task-type task-type*
                       :path (str path)})))
    (edn/read-edn path nil)))

(defn load-task-type-meta [target-root task-type]
  (let [task-type* (task-type-id task-type)
        ^java.io.File path (paths/task-type-meta-path target-root task-type*)]
    (when-not (.exists path)
      (throw (ex-info "task_type meta is missing."
                      {:task-type task-type*
                       :path (str path)})))
    (edn/read-edn path nil)))

(defn resolve-installed-path [target-root task-type relative-path]
  (let [^java.io.File task-type-dir (.getCanonicalFile
                                     ^java.io.File
                                     (paths/task-type-dir target-root (task-type-id task-type)))
        ^java.io.File resolved-path (.getCanonicalFile
                                     ^java.io.File
                                     (io/file task-type-dir relative-path))
        task-type-dir-path (.getPath task-type-dir)
        resolved-path-str (.getPath resolved-path)
        within-package? (or (= resolved-path-str task-type-dir-path)
                            (str/starts-with? resolved-path-str
                                              (str task-type-dir-path java.io.File/separator)))]
    (when-not within-package?
      (throw (ex-info "Installed path must stay within the task_type package."
                      {:task-type (task-type-id task-type)
                       :path resolved-path-str
                       :task-type-dir task-type-dir-path
                       :relative-path relative-path})))
    resolved-path))

(defn prompt-path [target-root task-type prompt-name]
  (let [task-type* (task-type-id task-type)
        relative-path (get-in (load-task-type target-root task-type*)
                              [:prompts prompt-name])]
    (when-not relative-path
      (throw (ex-info "Prompt is not defined in task_type."
                      {:task-type task-type*
                       :prompt-name prompt-name})))
    (resolve-installed-path target-root task-type* relative-path)))

(defn hook-path [target-root task-type hook-name]
  (resolve-installed-path target-root
                          task-type
                          (str "hooks/" (name hook-name))))

(defn mgr-home [target-root task-type]
  (:mgr-home (load-task-type target-root task-type)))

(defn worker-home [target-root task-type worker]
  (get-in (load-task-type target-root task-type) [:worker-homes worker]))

(defn worker-for-stage [target-root task]
  (get-in (load-task-type target-root (:task-type task))
          [:workers (or (:stage task) :todo)]))

(declare blankish? validate-task-definition!)

(defn prepare-run-config [target-root task-type]
  (:prepare-run (load-task-type target-root task-type)))

(defn resolve-input-head [target-root task input-head-spec]
  (let [task-field (or (:task-field input-head-spec) :repo-head)
        field-value (get task task-field)]
    (or field-value
        (when (= :current-head (:fallback input-head-spec))
          (repo/current-head target-root))
        (throw (ex-info "Unable to resolve input head for run."
                        {:task-id (:id task)
                         :task-type (:task-type task)
                         :input-head-spec input-head-spec})))))

(defn resolve-value [task spec]
  (cond
    (contains? spec :literal)
    (:literal spec)

    (contains? spec :task-field)
    (let [value (get task (:task-field spec))]
      (if (blankish? value)
        (:default spec)
        value))

    :else
    (:default spec)))

(defn resolve-prompt-inputs [task prompt-input-specs]
  (reduce-kv
   (fn [m k spec]
     (assoc m k (rendering/text-block (resolve-value task spec))))
   {}
   prompt-input-specs))

(def default-before-prepare-run
  {:kind :command
   :hook "hooks/before_prepare_run"
   :allowed-fields [:prompt-inputs]})

(defn validate-before-fields! [before-spec result]
  (let [allowed (set (:allowed-fields before-spec))
        invalid (->> (keys result)
                     (remove allowed)
                     vec)]
    (when (seq invalid)
      (throw (ex-info "before_prepare_run returned disallowed fields."
                      {:allowed-fields allowed
                       :invalid-fields invalid
                       :result result})))
    result))

(defn run-before-prepare-run! [target-root task worker launcher]
  (let [task-type-def (load-task-type target-root (:task-type task))
        before-spec (or (get-in task-type-def [:prepare-run :before])
                        (let [^java.io.File default-hook (hook-path target-root (:task-type task) :before_prepare_run)]
                          (when (.exists default-hook)
                            default-before-prepare-run)))]
    (when before-spec
      (let [hook-path* (resolve-installed-path target-root
                                               (:task-type task)
                                               (:hook before-spec))
            {:keys [exit out err]}
            (apply shell/sh
                   (concat [(str hook-path*)
                            "--task-id" (:id task)
                            "--task-type" (name (task-type-id (:task-type task)))
                            "--worker" (name worker)
                            "--launcher" (name launcher)]
                           [:dir (str (paths/resolve-target-root target-root))]))
            output (str/trim (if (str/blank? out) err out))]
        (when-not (zero? exit)
          (throw (ex-info "before_prepare_run hook failed."
                          {:task-id (:id task)
                           :task-type (:task-type task)
                           :worker worker
                           :hook-path (str hook-path*)
                           :exit exit
                           :stdout out
                           :stderr err})))
        (validate-before-fields! before-spec
                                 (clojure-edn/read-string output))))))

(defn merge-before-result [base before-result]
  (cond-> base
    (:input-head before-result)
    (assoc :input-head (:input-head before-result))

    (:worker-home before-result)
    (assoc :worker-home (:worker-home before-result))

    (:prompt-inputs before-result)
    (update :prompt-inputs merge (:prompt-inputs before-result))))

(defn prepare-run-spec [target-root task worker launcher]
  (validate-task-definition! target-root task)
  (let [prepare-spec (prepare-run-config target-root (:task-type task))
        base {:task-type (:task-type task)
              :worker worker
              :launcher launcher
              :input-head (resolve-input-head target-root task (:input-head prepare-spec))
              :worktree-strategy (or (:worktree-strategy prepare-spec) :git-worktree)
              :worker-home (worker-home target-root (:task-type task) worker)
              :prompt-inputs (resolve-prompt-inputs task (get prepare-spec :prompt-inputs {}))}
        before-result (run-before-prepare-run! target-root task worker launcher)]
    (cond-> (merge-before-result base before-result)
      before-result
      (assoc :before-prepare-run before-result))))

(defn required-task-fields [target-root task-type]
  (or (get-in (load-task-type target-root task-type) [:task-schema :required])
      []))

(defn blankish? [value]
  (or (nil? value)
      (and (string? value) (str/blank? value))
      (and (sequential? value) (empty? value))))

(defn validate-task-definition! [target-root task]
  (let [task-type (:task-type task)
        missing (->> (required-task-fields target-root task-type)
                     (filter #(blankish? (get task %)))
                     vec)]
    (when (seq missing)
      (throw (ex-info "Task definition is missing required fields."
                      {:task-id (:id task)
                       :task-type task-type
                       :missing missing})))
    task))

(defn prompt-template [target-root task-type-id prompt-name]
  (slurp (prompt-path target-root task-type-id prompt-name)))

(defn worker-prompt [target-root task run]
  (let [template (prompt-template target-root (:task-type task) (:worker run))
        values (merge
                {:worktree_root (get-in run [:worktree :dir])
                 :task_id (:id task)
                 :task_stage (or (:stage task) :todo)
                 :goal (rendering/text-block (:goal task))
                 :scope (rendering/text-block (:scope task))
                 :constraints (rendering/text-block (:constraints task))
                 :success_criteria (rendering/text-block (:success-criteria task))
                 :input_head (get-in run [:heads :input])
                 :latest_worker (or (:latest-worker task) "none")
                 :latest_worker_output (rendering/text-block (:latest-worker-output task))
                 :latest_review (rendering/text-block (:latest-review-output task))
                 :latest_run_id (or (:latest-run task) "none")}
                (get-in run [:prepare-run :prompt-inputs] {}))]
    (rendering/render-template template values)))

(defn mgr-prompt [target-root task mgr-run]
  (rendering/render-template
   (prompt-template target-root (:task-type task) :mgr)
   {:task_id (:id task)
    :goal (rendering/text-block (:goal task))
    :scope (rendering/text-block (:scope task))
    :constraints (rendering/text-block (:constraints task))
    :success_criteria (rendering/text-block (:success-criteria task))
    :task_stage (or (:stage task) :todo)
    :mgr_run_id (:id mgr-run)
    :worker_launcher (or (:worker-launcher mgr-run) "none")
    :workflow_cli_path (or (:cli-script mgr-run) "none")
    :latest_worker (or (:latest-worker task) "none")
    :latest_run_id (or (:latest-run task) "none")
    :latest_review (or (:latest-review-output task) "none")}))

(defn parse-review-control [text]
  (cond
    (re-find #"(?m)^RESULT:\s*pass\s*$" text) :pass
    (re-find #"(?m)^RESULT:\s*needs_refine\s*$" text) :needs-refine
    :else :unknown))

(defn definition-blueprint []
  {:task-type-id task-type-id
   :load-task-type load-task-type
   :load-task-type-meta load-task-type-meta
   :mgr-home mgr-home
   :worker-home worker-home
   :worker-for-stage worker-for-stage
   :prepare-run-config prepare-run-config
   :prepare-run-spec prepare-run-spec
   :required-task-fields required-task-fields
   :validate-task-definition! validate-task-definition!
   :render-template rendering/render-template
   :worker-prompt worker-prompt
   :mgr-prompt mgr-prompt
   :parse-review-control parse-review-control
   :resolve-installed-path resolve-installed-path
   :prompt-path prompt-path
   :hook-path hook-path})
