(ns spike-v2.util
  (:require
   [babashka.fs :as fs]
   [babashka.process :refer [shell]]
   [clojure.edn :as edn]
   [clojure.pprint :refer [pprint]]
   [clojure.string :as str]))

(defn now []
  (str (java.time.Instant/now)))

(defn uuid []
  (str (java.util.UUID/randomUUID)))

(defn pp-str [x]
  (with-out-str
    (binding [*print-namespace-maps* false]
      (pprint x))))

(defn write-file! [path content]
  (fs/create-dirs (fs/parent path))
  (spit (str path) content))

(defn write-edn! [path data]
  (write-file! path (pp-str data)))

(defn read-edn [path default]
  (if (fs/exists? path)
    (edn/read-string (slurp (str path)))
    default))

(defn render-template [template kvs]
  (reduce
   (fn [s [k v]]
     (str/replace s (str "{{" (name k) "}}") (str v)))
   template
   kvs))

(defn sh! [opts & cmd]
  (let [{:keys [exit out err]}
        (apply shell (merge {:out :string :err :string} opts) cmd)]
    (when-not (zero? exit)
      (throw (ex-info "shell command failed"
                      {:cmd cmd
                       :exit exit
                       :out out
                       :err err})))
    (str/trim out)))
