(ns spike-v3.core.task-lifecycle
  (:require
   [spike-v3.core.model :as model]
   [spike-v3.support.util :as util]))

(defn select-task [tasks]
  (first (filter #(not (model/terminal-stage? (:stage %))) tasks)))

(defn merge-task-update [task run]
  (let [result (:result run)
        worker (:worker run)]
    (cond-> (assoc task
                   :stage (model/next-stage worker result)
                   :repo-head (:output-head run)
                   :latest-run (:id run)
                   :latest-worker worker
                   :latest-worker-output (:message result)
                   :latest-worker-control (:control result)
                   :latest-worktree (:worktree-dir run)
                   :run-count (inc (:run-count task))
                   :updated-at (util/now))
      (= worker :review)
      (assoc :review-count (inc (:review-count task))
             :latest-review-output (:message result))

      (:error? run)
      (assoc :error-output (:message result)))))

(defn merge-mgr-update [task mgr-run]
  (let [result (:result mgr-run)]
    (assoc task
           :latest-mgr-run (:id mgr-run)
           :latest-mgr-decision (:decision result)
           :latest-mgr-output (:message result)
           :mgr-count (inc (:mgr-count task 0))
           :updated-at (util/now))))
