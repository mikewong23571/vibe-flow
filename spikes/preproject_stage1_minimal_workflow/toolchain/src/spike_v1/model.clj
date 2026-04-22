(ns spike-v1.model)

(def max-run-count 6)

(def max-review-count 3)

(def worker-order
  {:todo :impl
   :awaiting-review :review
   :awaiting-refine :refine})

(def worker-home-map
  {:mgr :mgr_codex
   :impl :impl_codex
   :review :review_codex
   :refine :refine_codex})

(def prompt-templates
  {:mgr
   (str
    "You are the mgr agent for a workflow spike.\n"
    "You do lightweight routing only. Do not implement or review code yourself.\n"
    "Task id: {{task_id}}\n"
    "Task goal: {{goal}}\n"
    "Task stage: {{task_stage}}\n"
    "Task run count: {{run_count}}\n"
    "Task review count: {{review_count}}\n"
    "Mgr run id: {{mgr_run_id}}\n"
    "Worker launcher for this advance: {{worker_launcher}}\n"
    "Workflow CLI path: {{workflow_cli_path}}\n"
    "Latest worker: {{latest_worker}}\n"
    "Latest run id: {{latest_run_id}}\n"
    "Latest worker control: {{latest_worker_control}}\n"
    "Latest error output:\n"
    "{{latest_error}}\n\n"
    "Latest worker output:\n"
    "{{latest_worker_output}}\n\n"
    "Latest review output:\n"
    "{{latest_review}}\n\n"
    "Recent worker runs:\n"
    "{{recent_runs}}\n\n"
    "Allowed decisions: impl, review, refine, done, error\n\n"
    "Routing rules for this spike:\n"
    "* todo -> impl\n"
    "* awaiting-review -> review\n"
    "* awaiting-refine -> refine\n"
    "* done/error should stay terminal\n"
    "* if run count is >= {{max_run_count}}, choose error\n"
    "* if task is awaiting-refine and review count is >= {{max_review_count}}, choose error\n"
    "* if the latest worker output is clearly malformed for the current stage, choose error\n\n"
    "You must not launch workers directly.\n"
    "Instead, call the workflow CLI exactly once using this pattern:\n"
    "{{workflow_cli_path}} --task-id {{task_id}} --mgr-run-id {{mgr_run_id}} --worker-launcher {{worker_launcher}} --decision <allowed value> --reason \"<short sentence>\"\n"
    "After the command succeeds, reply with the exact stdout from that command only.\n")

   :impl
   (str
    "You are the impl worker for a workflow spike.\n"
    "Your working directory is exactly {{worktree_root}}.\n"
    "Only inspect and edit files inside that worktree.\n"
    "Task id: {{task_id}}\n"
    "Task goal: {{goal}}\n\n"
    "Make the smallest concrete implementation you can.\n"
    "After editing files, reply with a brief summary.\n")

   :review
   (str
    "You are the review worker for a workflow spike.\n"
    "Your working directory is exactly {{worktree_root}}.\n"
    "Only inspect files inside that worktree.\n"
    "Task id: {{task_id}}\n"
    "Task goal: {{goal}}\n\n"
    "Reply in exactly this format:\n"
    "RESULT: pass\n"
    "or\n"
    "RESULT: needs_refine\n"
    "Then add a short explanation.\n")

   :refine
   (str
    "You are the refine worker for a workflow spike.\n"
    "Your working directory is exactly {{worktree_root}}.\n"
    "Only inspect and edit files inside that worktree.\n"
    "Task id: {{task_id}}\n"
    "Task goal: {{goal}}\n"
    "Latest review output:\n"
    "{{latest_review}}\n\n"
    "Make the smallest fix you can, then reply with a brief summary.\n")})

(defn terminal-stage? [stage]
  (contains? #{:done :error} stage))

(defn stage->worker [task]
  (get worker-order (:stage task)))

(defn allowed-mgr-decisions []
  [:impl :review :refine :done :error])

(defn task-overrun? [task]
  (or (>= (:run-count task 0) max-run-count)
      (and (= :awaiting-refine (:stage task))
           (>= (:review-count task 0) max-review-count))))

(defn next-stage [worker result]
  (cond
    (not (:ok? result)) :error
    (= worker :impl) :awaiting-review
    (= worker :refine) :awaiting-review
    (= worker :review)
    (case (:control result)
      :pass :done
      :needs-refine :awaiting-refine
      :error :error
      :unknown :error)
    :else :error))
