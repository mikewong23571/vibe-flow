(ns vibe-flow.platform.state.collection-store
  (:require
   [vibe-flow.platform.support.edn :as edn]
   [vibe-flow.platform.target.paths :as paths]))

(defn collection-files [target-root]
  (let [^java.io.File dir (paths/collections-root target-root)
        files (.listFiles dir)]
    (if files
      (->> files
           (filter (fn [^java.io.File file] (.isFile file)))
           (filter (fn [^java.io.File file] (.endsWith (.getName file) ".edn")))
           (sort-by (fn [^java.io.File file] (.getName file)))
           vec)
      [])))

(defn load-collections [target-root]
  (mapv #(edn/read-edn % nil) (collection-files target-root)))

(defn load-collection [target-root collection-id]
  (edn/read-edn (paths/collection-path target-root collection-id) nil))

(defn save-collection! [target-root collection]
  (edn/write-edn! (paths/collection-path target-root (:id collection)) collection))
