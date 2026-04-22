(ns spike-v3.definition.task-type
  (:require
   [babashka.fs :as fs]
   [babashka.process :refer [shell]]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [spike-v3.support.paths :as paths]
   [spike-v3.support.util :as util]
   [spike-v3.target.repo :as target-repo]))

(defn load-task-type [target-root task-type]
  (let [path (paths/task-type-path target-root task-type)]
    (when-not (fs/exists? path)
      (throw (ex-info "task_type is not installed"
                      {:task-type task-type
                       :path (str path)})))
    (util/read-edn path nil)))

(defn load-task-type-meta [target-root task-type]
  (let [path (paths/task-type-meta-path target-root task-type)]
    (when-not (fs/exists? path)
      (throw (ex-info "task_type meta is missing"
                      {:task-type task-type
                       :path (str path)})))
    (util/read-edn path nil)))

(defn resolve-installed-path [target-root task-type relative-path]
  (fs/path (paths/task-type-dir target-root task-type) relative-path))

(defn prompt-path [target-root task-type prompt-name]
  (let [task-type-def (load-task-type target-root task-type)
        relative-path (get-in task-type-def [:prompts prompt-name])]
    (when-not relative-path
      (throw (ex-info "prompt is not defined in task_type artifact"
                      {:task-type task-type
                       :prompt-name prompt-name})))
    (resolve-installed-path target-root task-type relative-path)))

(defn worker-for-stage [target-root task]
  (get-in (load-task-type target-root (:task-type task)) [:workers (:stage task)]))

(defn mgr-home [target-root task-type]
  (:mgr-home (load-task-type target-root task-type)))

(defn worker-home [target-root task-type worker]
  (get-in (load-task-type target-root task-type) [:worker-homes worker]))

(defn required-task-fields [target-root task-type]
  (or (get-in (load-task-type target-root task-type) [:task-schema :required])
      []))

(defn validate-task-definition! [target-root task]
  (let [missing (->> (required-task-fields target-root (:task-type task))
                     (filter #(util/blankish? (get task %)))
                     vec)]
    (when (seq missing)
      (throw (ex-info "task definition is missing required fields"
                      {:task-id (:id task)
                       :task-type (:task-type task)
                       :missing missing})))
    task))

(defn resolve-input-head [target-root task input-head-spec]
  (let [task-field (:task-field input-head-spec)
        field-value (get task task-field)]
    (or field-value
        (when (= :current-head (:fallback input-head-spec))
          (target-repo/current-head target-root))
        (throw (ex-info "prepare_run could not resolve input head"
                        {:task-id (:id task)
                         :task-type (:task-type task)
                         :input-head-spec input-head-spec})))))

(defn resolve-value [task spec]
  (cond
    (contains? spec :literal)
    (:literal spec)

    (contains? spec :task-field)
    (let [value (get task (:task-field spec))]
      (if (util/blankish? value)
        (:default spec)
        value))

    :else
    (:default spec)))

(defn resolve-prompt-inputs [task prompt-input-specs]
  (reduce-kv
   (fn [m k spec]
     (assoc m k (util/text-block (resolve-value task spec))))
   {}
   prompt-input-specs))

(defn validate-before-fields! [before-spec result]
  (let [allowed (set (:allowed-fields before-spec))
        invalid (->> (keys result)
                     (remove allowed)
                     vec)]
    (when (seq invalid)
      (throw (ex-info "before_prepare_run returned disallowed fields"
                      {:allowed-fields allowed
                       :invalid-fields invalid
                       :result result})))
    result))

(defn run-before-prepare-run! [target-root task worker launcher]
  (let [task-type-def (load-task-type target-root (:task-type task))
        before-spec (get-in task-type-def [:prepare-run :before])]
    (when before-spec
      (let [hook-path (resolve-installed-path target-root (:task-type task) (:hook before-spec))
            {:keys [exit out err]}
            (apply shell
                   {:dir (str target-root)
                    :out :string
                    :err :string}
                   (str hook-path)
                   ["--task-id" (:id task)
                    "--task-type" (name (:task-type task))
                    "--worker" (name worker)
                    "--launcher" (name launcher)])
            output (str/trim (if (str/blank? out) err out))]
        (when-not (zero? exit)
          (throw (ex-info "before_prepare_run hook failed"
                          {:task-id (:id task)
                           :task-type (:task-type task)
                           :worker worker
                           :hook-path (str hook-path)
                           :exit exit
                           :stdout out
                           :stderr err})))
        (validate-before-fields! before-spec (edn/read-string output))))))

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
  (let [task-type-def (load-task-type target-root (:task-type task))
        prepare-spec (:prepare-run task-type-def)
        base {:task-type (:task-type task)
              :worker worker
              :launcher launcher
              :input-head (resolve-input-head target-root task (:input-head prepare-spec))
              :worktree-strategy (:worktree-strategy prepare-spec)
              :worker-home (worker-home target-root (:task-type task) worker)
              :prompt-inputs (resolve-prompt-inputs task (:prompt-inputs prepare-spec))
              :prepared-at (util/now)}
        before-result (run-before-prepare-run! target-root task worker launcher)]
    (assoc (merge-before-result base before-result)
           :definition-source :installed-task-type-artifact
           :before-prepare-run before-result)))
