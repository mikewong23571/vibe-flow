(ns spike-v2.prompt
  (:require
   [spike-v2.paths :as paths]
   [spike-v2.util :as util]))

(defn prompt-template [target-root task-type prompt-name]
  (slurp (str (paths/task-type-prompt-path target-root task-type prompt-name))))

(defn parse-review-control [s]
  (cond
    (re-find #"(?m)^RESULT:\s*pass\s*$" s) :pass
    (re-find #"(?m)^RESULT:\s*needs_refine\s*$" s) :needs-refine
    :else :unknown))

(defn worker-prompt [target-root task worker run]
  (let [template (prompt-template target-root (:task-type task) worker)
        values (merge
                {:worktree_root (:worktree-dir run)
                 :task_id (:id task)
                 :goal (:goal task)
                 :input_head (:input-head run)}
                (:prompt-inputs run))]
    (util/render-template template values)))

(defn mgr-prompt [target-root task mgr-run context]
  (util/render-template
   (prompt-template target-root (:task-type task) :mgr)
   {:task_id (:id task)
    :goal (:goal task)
    :task_stage (:stage task)
    :run_count (:run-count task)
    :review_count (:review-count task 0)
    :mgr_run_id (:id mgr-run)
    :worker_launcher (:worker-launcher context)
    :workflow_cli_path (:workflow-cli-path context)
    :latest_worker (or (:latest-worker task) "none")
    :latest_run_id (or (:latest-run task) "none")
    :latest_worker_control (or (:latest-worker-control task) "none")
    :latest_error (or (:error-output task) "none")
    :latest_worker_output (or (:latest-worker-output task) "none")
    :latest_review (or (:latest-review-output task) "none")
    :recent_runs (:recent-runs context)
    :max_run_count (:max-run-count context)
    :max_review_count (:max-review-count context)}))
