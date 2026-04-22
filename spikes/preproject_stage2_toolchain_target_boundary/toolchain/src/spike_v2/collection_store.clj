(ns spike-v2.collection-store
  (:require
   [babashka.fs :as fs]
   [spike-v2.paths :as paths]
   [spike-v2.util :as util]))

(def sample-collection-id "sample-impl-collection")

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

(defn sample-collection []
  {:id sample-collection-id
   :task-type :impl
   :name "sample target repo tasks"
   :created-at (util/now)
   :updated-at (util/now)
   :task-ids ["sample-impl-task"]})
