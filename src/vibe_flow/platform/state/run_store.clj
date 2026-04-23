(ns vibe-flow.platform.state.run-store
  (:require
   [vibe-flow.platform.support.edn :as edn]
   [vibe-flow.platform.target.paths :as paths]))

(defn run-files [target-root]
  (let [^java.io.File dir (paths/runs-root target-root)
        entries (.listFiles dir)]
    (if entries
      (->> entries
           (filter (fn [^java.io.File entry] (.isDirectory entry)))
           (map (fn [^java.io.File entry] (paths/run-path target-root (.getName entry))))
           (filter (fn [^java.io.File path] (.exists path)))
           (sort-by (fn [^java.io.File path] (.getPath path)))
           vec)
      [])))

(defn load-runs [target-root]
  (->> (run-files target-root)
       (map #(edn/read-edn % nil))
       (sort-by :started-at)
       vec))

(defn load-run [target-root run-id]
  (edn/read-edn (paths/run-path target-root run-id) nil))

(defn save-run! [target-root run]
  (edn/write-edn! (paths/run-path target-root (:id run)) run))

(defn task-runs [target-root task-id]
  (->> (load-runs target-root)
       (filter #(= task-id (:task-id %)))
       vec))
