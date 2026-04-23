(ns vibe-flow.platform.state.collection-store
  (:require
   [vibe-flow.platform.support.edn :as edn]
   [vibe-flow.platform.target.paths :as paths]))

(defn collection-files [target-root]
  (let [dir (paths/collections-root target-root)
        files (.listFiles dir)]
    (if files
      (->> files
           (filter #(.isFile %))
           (filter #(.endsWith (.getName %) ".edn"))
           (sort-by #(.getName %))
           vec)
      [])))

(defn load-collections [target-root]
  (mapv #(edn/read-edn % nil) (collection-files target-root)))

(defn load-collection [target-root collection-id]
  (edn/read-edn (paths/collection-path target-root collection-id) nil))

(defn save-collection! [target-root collection]
  (edn/write-edn! (paths/collection-path target-root (:id collection)) collection))
