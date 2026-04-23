(ns vibe-flow.management.task-type
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [vibe-flow.definition.task-type :as definition]
   [vibe-flow.platform.state.system-store :as system-store]
   [vibe-flow.platform.support.edn :as edn]
   [vibe-flow.platform.support.time :as time]
   [vibe-flow.platform.target.paths :as paths]))

(def prompt-files
  {:mgr "mgr.txt"
   :impl "impl.txt"
   :review "review.txt"
   :refine "refine.txt"})

(def prompt-skeletons
  {:mgr
   (str "You are the mgr agent for {{task_type}}.\n"
        "Decide the next workflow step for exactly one task, then hand control back through the workflow surface.\n\n"
        "Task\n"
        "- id: {{task_id}}\n"
        "- stage: {{task_stage}}\n"
        "- mgr run id: {{mgr_run_id}}\n"
        "- worker launcher: {{worker_launcher}}\n"
        "- goal: {{goal}}\n\n"
        "Scope\n"
        "{{scope}}\n\n"
        "Constraints\n"
        "{{constraints}}\n\n"
        "Success Criteria\n"
        "{{success_criteria}}\n\n"
        "Latest State\n"
        "- latest worker: {{latest_worker}}\n"
        "- latest run id: {{latest_run_id}}\n"
        "- latest review: {{latest_review}}\n\n"
        "Workflow CLI\n"
        "{{workflow_cli_path}}\n\n"
        "Choose the smallest safe next step consistent with the current stage.\n"
        "Do not launch workers directly.\n"
        "Instead, call the workflow CLI exactly once using this pattern:\n"
        "{{workflow_cli_path}} --decision <impl|review|refine|done|error> --reason \"<one concise reason>\"\n"
        "After the command succeeds, reply with the exact stdout from that command only.\n")
   :impl
   (str "You are the impl worker for {{task_type}}.\n"
        "Implement the task in the checked-out worktree.\n\n"
        "Context\n"
        "- task id: {{task_id}}\n"
        "- task stage: {{task_stage}}\n"
        "- worktree root: {{worktree_root}}\n"
        "- input head: {{input_head}}\n\n"
        "Goal\n"
        "{{goal}}\n\n"
        "Scope\n"
        "{{scope}}\n\n"
        "Constraints\n"
        "{{constraints}}\n\n"
        "Success Criteria\n"
        "{{success_criteria}}\n\n"
        "Execution Rules\n"
        "- Make the smallest viable change that satisfies the task.\n"
        "- Keep edits inside the stated scope.\n"
        "- Do not edit workflow state under .workflow/ unless the task explicitly requires it.\n"
        "- Preserve the existing code style and architecture.\n"
        "- Run the narrowest relevant validation for touched code before finishing when practical.\n")
   :review
   (str "You are the review worker for {{task_type}}.\n"
        "Review the candidate change in the current worktree. Do not modify files.\n\n"
        "Context\n"
        "- task id: {{task_id}}\n"
        "- task stage: {{task_stage}}\n"
        "- worktree root: {{worktree_root}}\n"
        "- input head: {{input_head}}\n"
        "- latest worker: {{latest_worker}}\n"
        "- latest run id: {{latest_run_id}}\n\n"
        "Goal\n"
        "{{goal}}\n\n"
        "Scope\n"
        "{{scope}}\n\n"
        "Constraints\n"
        "{{constraints}}\n\n"
        "Success Criteria\n"
        "{{success_criteria}}\n\n"
        "Review Focus\n"
        "- correctness and behavioral regressions\n"
        "- missing edge cases or validation\n"
        "- mismatch between code changes and success criteria\n"
        "- unnecessary scope expansion\n\n"
        "Output Rules\n"
        "First line must be exactly `RESULT: pass` or `RESULT: needs_refine`.\n"
        "Second line should start with `REASON:` and give the decisive reason.\n"
        "Then include only the most important findings or gaps.\n")
   :refine
   (str "You are the refine worker for {{task_type}}.\n"
        "Address review feedback with the narrowest fix that gets the task back to review.\n\n"
        "Context\n"
        "- task id: {{task_id}}\n"
        "- task stage: {{task_stage}}\n"
        "- worktree root: {{worktree_root}}\n"
        "- input head: {{input_head}}\n"
        "- latest worker: {{latest_worker}}\n"
        "- latest run id: {{latest_run_id}}\n\n"
        "Goal\n"
        "{{goal}}\n\n"
        "Scope\n"
        "{{scope}}\n\n"
        "Constraints\n"
        "{{constraints}}\n\n"
        "Success Criteria\n"
        "{{success_criteria}}\n\n"
        "Latest Review Feedback\n"
        "{{latest_review}}\n\n"
        "Execution Rules\n"
        "- Fix only what is needed to address the review.\n"
        "- Preserve correct existing changes.\n"
        "- Do not broaden scope unless the review proves it is necessary.\n"
        "- Run the narrowest relevant validation for touched code before finishing when practical.\n")})

(def legacy-prompt-skeletons
  {:mgr
   "You are the mgr agent for {{task_type}}.\nChoose the next workflow decision and hand control back through the workflow surface.\n"
   :impl
   "You are the impl worker for {{task_type}}.\nImplement the task with the smallest viable change.\n"
   :review
   "You are the review worker for {{task_type}}.\nReview the candidate change in the current worktree.\nFirst line must be exactly `RESULT: pass` or `RESULT: needs_refine`.\nAfter that, give a concise reason.\n"
   :refine
   "You are the refine worker for {{task_type}}.\nAddress review feedback with the narrowest fix.\n"})

(defn ensure-installed-target! [target-root]
  (when-not (system-store/installed? target-root)
    (throw (ex-info "Target is not installed yet."
                    {:target-root (str (paths/resolve-target-root target-root))
                     :path (str (paths/install-path target-root))})))
  target-root)

(defn write-file! [path content]
  (let [file (io/file path)]
    (io/make-parents file)
    (spit file content)
    file))

(defn render-template [template replacements]
  (reduce-kv
   (fn [content key value]
     (str/replace content
                  (str "{{" (name key) "}}")
                  (str value)))
   template
   replacements))

(defn registry-path [target-root]
  (paths/task-types-registry-path target-root))

(defn load-registry [target-root]
  (ensure-installed-target! target-root)
  (edn/read-edn (registry-path target-root) []))

(defn save-registry! [target-root entries]
  (edn/write-edn! (registry-path target-root)
                  (vec (sort-by :id entries))))

(defn task-type-meta-record [target-root task-type source existing]
  {:id task-type
   :version (or (:version existing) 1)
   :status (or (:status existing) :active)
   :managed-by :vibe-flow.toolchain
   :layout-version paths/layout-version
   :source source
   :installed-at (or (:installed-at existing) (time/now))
   :updated-at (time/now)
   :task-type-path (str (paths/task-type-path target-root task-type))})

(defn registry-entry [target-root task-type meta]
  {:id task-type
   :kind :task-type
   :path (str (paths/task-type-dir target-root task-type))
   :version (:version meta)
   :status (:status meta)
   :layout-version (:layout-version meta)
   :source (:source meta)
   :installed-at (:installed-at meta)
   :updated-at (:updated-at meta)})

(defn write-task-type-meta! [target-root task-type meta]
  (edn/write-edn! (paths/task-type-meta-path target-root task-type) meta))

(defn register-task-type! [target-root task-type meta]
  (let [entry (registry-entry target-root task-type meta)
        updated (->> (load-registry target-root)
                     (remove #(= (:id %) task-type))
                     (cons entry))]
    (save-registry! target-root updated)
    entry))

(defn register-installed-task-type! [target-root task-type source]
  (ensure-installed-target! target-root)
  (let [task-type* (definition/task-type-id task-type)
        _ (definition/load-task-type target-root task-type*)
        existing (edn/read-edn (paths/task-type-meta-path target-root task-type*) nil)
        meta (task-type-meta-record target-root task-type* source existing)]
    (write-task-type-meta! target-root task-type* meta)
    (register-task-type! target-root task-type* meta)
    meta))

(defn skeleton-task-type-record [task-type]
  {:task-type task-type
   :mgr-home :mgr_codex
   :workers {:todo :impl
             :awaiting-review :review
             :awaiting-refine :refine}
   :worker-homes {:mgr :mgr_codex
                  :impl :impl_codex
                  :review :review_codex
                  :refine :refine_codex}
   :prompts {:mgr "prompts/mgr.txt"
             :impl "prompts/impl.txt"
             :review "prompts/review.txt"
             :refine "prompts/refine.txt"}
   :task-schema {:required [:goal :scope :constraints :success-criteria]}
   :prepare-run {:input-head {:task-field :repo-head
                              :fallback :current-head}
                 :worktree-strategy :git-worktree
                 :prompt-inputs {}
                 :before {:kind :command
                          :hook "hooks/before_prepare_run"
                          :allowed-fields [:prompt-inputs]}}})

(defn prompt-path [target-root task-type prompt-name]
  (io/file (paths/task-type-prompts-dir target-root task-type)
           (get prompt-files prompt-name)))

(def hook-script
  "#!/usr/bin/env bash\nprintf '{}\\n'\n")

(defn create-task-type! [target-root task-type]
  (ensure-installed-target! target-root)
  (let [task-type* (definition/task-type-id task-type)
        task-type-dir (paths/task-type-dir target-root task-type*)
        hooks-dir (paths/task-type-hooks-dir target-root task-type*)
        hook-path (io/file hooks-dir "before_prepare_run")]
    (when (.exists task-type-dir)
      (throw (ex-info "task_type already exists."
                      {:task-type task-type*
                       :path (str task-type-dir)})))
    (.mkdirs (paths/task-type-prompts-dir target-root task-type*))
    (.mkdirs hooks-dir)
    (edn/write-edn! (paths/task-type-path target-root task-type*)
                    (skeleton-task-type-record task-type*))
    (doseq [[prompt-name template] prompt-skeletons]
      (write-file! (prompt-path target-root task-type* prompt-name)
                   (render-template template {:task_type (name task-type*)})))
    (write-file! hook-path hook-script)
    (.setExecutable hook-path true)
    (register-installed-task-type! target-root
                                   task-type*
                                   {:kind :target-created
                                    :created-in-target (str (paths/resolve-target-root target-root))})
    {:ok? true
     :task-type task-type*
     :path (str task-type-dir)}))

(defn managed-task-type? [target-root task-type]
  (= :vibe-flow.toolchain
     (:managed-by (edn/read-edn (paths/task-type-meta-path target-root task-type) nil))))

(defn rendered-prompt [task-type prompt-name prompt-set]
  (render-template (get prompt-set prompt-name)
                   {:task_type (name task-type)}))

(defn refresh-generated-task-type! [target-root task-type source]
  (ensure-installed-target! target-root)
  (let [task-type* (definition/task-type-id task-type)]
    (when (managed-task-type? target-root task-type*)
      (let [refreshed? (reduce
                        (fn [changed? prompt-name]
                          (let [path (prompt-path target-root task-type* prompt-name)
                                expected (rendered-prompt task-type* prompt-name prompt-skeletons)
                                legacy (rendered-prompt task-type* prompt-name legacy-prompt-skeletons)
                                existing (when (.exists path) (slurp path))]
                            (cond
                              (= existing expected)
                              changed?

                              (or (nil? existing)
                                  (= existing legacy))
                              (do
                                (write-file! path expected)
                                true)

                              :else
                              changed?)))
                        false
                        (keys prompt-files))]
        (when refreshed?
          (register-installed-task-type! target-root task-type* source))
        refreshed?))))

(defn list-task-types [target-root]
  (load-registry target-root))

(defn inspect-task-type [target-root task-type]
  (ensure-installed-target! target-root)
  (let [task-type* (definition/task-type-id task-type)
        task-type-dir (paths/task-type-dir target-root task-type*)
        prompt-files (or (.listFiles (paths/task-type-prompts-dir target-root task-type*)) [])
        hook-files (or (.listFiles (paths/task-type-hooks-dir target-root task-type*)) [])
        registry-entry (some #(when (= (:id %) task-type*) %)
                             (load-registry target-root))]
    {:task-type task-type*
     :definition (definition/load-task-type target-root task-type*)
     :meta (definition/load-task-type-meta target-root task-type*)
     :registry registry-entry
     :layout {:task-type-dir (str task-type-dir)
              :task-type-path (str (paths/task-type-path target-root task-type*))
              :meta-path (str (paths/task-type-meta-path target-root task-type*))
              :prompts (->> prompt-files
                            (map #(.getPath %))
                            sort
                            vec)
              :hooks (->> hook-files
                          (map #(.getPath %))
                          sort
                          vec)}}))
