(ns spike-v3.state.task-store
  (:require
   [babashka.fs :as fs]
   [spike-v3.definition.task-type :as task-type]
   [spike-v3.support.paths :as paths]
   [spike-v3.support.util :as util]))

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
  (task-type/validate-task-definition! target-root task)
  (util/write-edn! (paths/task-path target-root (:id task)) task))
