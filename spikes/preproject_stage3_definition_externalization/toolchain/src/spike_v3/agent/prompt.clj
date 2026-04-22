(ns spike-v3.agent.prompt
  (:require
   [spike-v3.definition.task-type :as task-type]
   [spike-v3.support.util :as util]))

(defn prompt-template [target-root task-type-name prompt-name]
  (slurp (str (task-type/prompt-path target-root task-type-name prompt-name))))

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
                 :goal (util/text-block (:goal task))
                 :scope (util/text-block (:scope task))
                 :constraints (util/text-block (:constraints task))
                 :success_criteria (util/text-block (:success-criteria task))
                 :input_head (:input-head run)}
                (:prompt-inputs run))]
    (util/render-template template values)))

(defn mgr-prompt [target-root task mgr-run context]
  (util/render-template
   (prompt-template target-root (:task-type task) :mgr)
   {:task_id (:id task)
    :goal (util/text-block (:goal task))
    :scope (util/text-block (:scope task))
    :constraints (util/text-block (:constraints task))
    :success_criteria (util/text-block (:success-criteria task))
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
