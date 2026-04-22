(ns vibe-flow.platform.state.mgr-run-store
  (:require
   [vibe-flow.platform.support.edn :as edn]
   [vibe-flow.platform.target.paths :as paths]))

(defn mgr-run-files [target-root]
  (let [dir (paths/mgr-runs-root target-root)
        entries (.listFiles dir)]
    (if entries
      (->> entries
           (filter #(.isDirectory %))
           (map #(paths/mgr-run-path target-root (.getName %)))
           (filter #(.exists %))
           (sort-by #(.getPath %))
           vec)
      [])))

(defn load-mgr-runs [target-root]
  (->> (mgr-run-files target-root)
       (map #(edn/read-edn % nil))
       (sort-by :started-at)
       vec))

(defn load-mgr-run [target-root mgr-run-id]
  (edn/read-edn (paths/mgr-run-path target-root mgr-run-id) nil))

(defn save-mgr-run! [target-root mgr-run]
  (edn/write-edn! (paths/mgr-run-path target-root (:id mgr-run)) mgr-run))
