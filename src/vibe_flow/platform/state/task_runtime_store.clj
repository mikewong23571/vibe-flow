(ns vibe-flow.platform.state.task-runtime-store
  (:require
   [vibe-flow.platform.support.edn :as edn]
   [vibe-flow.platform.support.task-fields :as task-fields]
   [vibe-flow.platform.target.paths :as paths]))

(def runtime-fields
  task-fields/runtime-fields)

(def default-runtime
  task-fields/default-runtime)

(defn domain-task [task]
  (task-fields/domain-task task))

(defn runtime-task [task]
  (task-fields/runtime-task task))

(defn load-task-runtime [target-root task-id]
  (edn/read-edn (paths/task-runtime-path target-root task-id) nil))

(defn hydrate-task [target-root task]
  (when task
    (merge default-runtime
           task
           (runtime-task (load-task-runtime target-root (:id task))))))

(defn save-task-runtime! [target-root task-id runtime]
  (edn/write-edn! (paths/task-runtime-path target-root task-id)
                  (select-keys runtime runtime-fields)))

(defn delete-task-runtime! [target-root task-id]
  (let [^java.io.File file (paths/task-runtime-path target-root task-id)]
    (when (.exists file)
      (.delete file))))

(defn merge-task-runtime [target-root task-id runtime]
  (save-task-runtime! target-root
                      task-id
                      (merge (or (load-task-runtime target-root task-id)
                                 default-runtime)
                             runtime)))
