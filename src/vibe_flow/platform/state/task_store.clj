(ns vibe-flow.platform.state.task-store
  (:require
   [vibe-flow.platform.support.edn :as edn]
   [vibe-flow.platform.support.task-fields :as task-fields]
   [vibe-flow.platform.target.paths :as paths]))

(defn task-files [target-root]
  (let [^java.io.File dir (paths/tasks-root target-root)
        files (.listFiles dir)]
    (if files
      (->> files
           (filter (fn [^java.io.File file] (.isFile file)))
           (filter (fn [^java.io.File file] (.endsWith (.getName file) ".edn")))
           (sort-by (fn [^java.io.File file] (.getName file)))
           vec)
      [])))

(defn load-tasks [target-root]
  (->> (task-files target-root)
       (map #(edn/read-edn % nil))
       (map task-fields/domain-task)
       (sort-by :created-at)
       vec))

(defn load-task [target-root task-id]
  (some-> (edn/read-edn (paths/task-path target-root task-id) nil)
          task-fields/domain-task))

(defn save-task! [target-root task]
  (edn/write-edn! (paths/task-path target-root (:id task))
                  (task-fields/domain-task task)))
