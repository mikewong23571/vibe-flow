(ns spike-v1.task-store
  (:require
   [spike-v1.model :as model]
   [spike-v1.paths :as paths]
   [spike-v1.runtime-repo :as runtime-repo]
   [spike-v1.util :as util]))

(defn load-tasks []
  (vec (util/read-edn (paths/tasks-path) [])))

(defn save-tasks! [tasks]
  (util/write-edn! (paths/tasks-path) tasks))

(defn sample-task []
  {:id "sample-impl-task"
   :task-type :impl
   :goal "Create and refine a tiny change in feature.txt using per-run worktrees."
   :stage :todo
   :repo-head (runtime-repo/current-head (paths/runtime-repo-root))
   :latest-run nil
   :run-count 0
   :review-count 0
   :created-at (util/now)
   :updated-at (util/now)})

(defn seed-sample! []
  (save-tasks! [(sample-task)])
  (println "Seeded sample task into" (paths/tasks-path)))

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
