(ns spike-v3.state.collection-store
  (:require
   [babashka.fs :as fs]
   [spike-v3.support.paths :as paths]
   [spike-v3.support.util :as util]))

(defn collection-files [target-root]
  (if (fs/exists? (paths/collections-root target-root))
    (sort (map str (fs/glob (paths/collections-root target-root) "*.edn")))
    []))

(defn load-collections [target-root]
  (mapv #(util/read-edn % nil) (collection-files target-root)))

(defn load-collection [target-root collection-id]
  (util/read-edn (paths/collection-path target-root collection-id) nil))

(defn save-collection! [target-root collection]
  (util/write-edn! (paths/collection-path target-root (:id collection)) collection))
