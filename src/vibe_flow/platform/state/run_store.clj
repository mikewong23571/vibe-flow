(ns vibe-flow.platform.state.run-store
  (:require
   [vibe-flow.platform.support.edn :as edn]
   [vibe-flow.platform.target.paths :as paths]))

(defn run-files [target-root]
  (let [dir (paths/runs-root target-root)
        entries (.listFiles dir)]
    (if entries
      (->> entries
           (filter #(.isDirectory %))
           (map #(paths/run-path target-root (.getName %)))
           (filter #(.exists %))
           (sort-by #(.getPath %))
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
