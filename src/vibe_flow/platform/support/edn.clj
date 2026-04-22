(ns vibe-flow.platform.support.edn
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]))

(defn pprint-str [data]
  (with-out-str
    (binding [*print-namespace-maps* false]
      (pprint data))))

(defn write-edn! [path data]
  (let [file (io/file path)]
    (io/make-parents file)
    (spit file (pprint-str data))
    data))

(defn read-edn [path default]
  (let [file (io/file path)]
    (if (.exists file)
      (edn/read-string (slurp file))
      default)))
