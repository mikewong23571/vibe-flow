(ns spike-v2.task-store
  (:require
   [babashka.fs :as fs]
   [spike-v2.collection-store :as collection-store]
   [spike-v2.model :as model]
   [spike-v2.paths :as paths]
   [spike-v2.target-repo :as target-repo]
   [spike-v2.util :as util]))

(defn task-files [target-root]
  (if (fs/exists? (paths/tasks-root target-root))
    (sort (map str (fs/glob (paths/tasks-root target-root) "*.edn")))
    []))

(defn load-tasks [target-root]
  (->> (task-files target-root)
       (map #(util/read-edn % nil))
       (sort-by :created-at)
       vec))

(defn load-task [target-root task-id]
  (util/read-edn (paths/task-path target-root task-id) nil))

(defn save-task! [target-root task]
  (util/write-edn! (paths/task-path target-root (:id task)) task))

(defn sample-task [target-root]
  {:id "sample-impl-task"
   :collection-id collection-store/sample-collection-id
   :task-type :impl
   :goal "Create and refine a tiny change in feature.txt using per-run worktrees inside the target repo."
   :stage :todo
   :repo-head (target-repo/current-head target-root)
   :latest-run nil
   :run-count 0
   :review-count 0
   :created-at (util/now)
   :updated-at (util/now)})

(defn seed-sample! [target-root]
  (collection-store/save-collection! target-root (collection-store/sample-collection))
  (save-task! target-root (sample-task target-root))
  (println "Seeded sample collection and task into" (str (paths/state-root target-root))))

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
