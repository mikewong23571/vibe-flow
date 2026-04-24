(ns vibe-flow.platform.support.task-fields)

(def runtime-fields
  #{:latest-run
    :latest-mgr-run
    :latest-worktree
    :latest-worker
    :latest-worker-output
    :latest-worker-control
    :latest-review-output
    :latest-mgr-decision
    :latest-mgr-output
    :error-output
    :mgr-count
    :run-count})

(def default-runtime
  {:mgr-count 0
   :run-count 0})

(defn domain-task [task]
  (apply dissoc task runtime-fields))

(defn runtime-task [task]
  (select-keys task runtime-fields))
