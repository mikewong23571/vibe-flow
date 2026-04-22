(ns vibe-flow.governance.ns-inspect
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defn normalize-path [path]
  (str/replace (.getPath (io/file path))
               (re-pattern (java.util.regex.Pattern/quote (str java.io.File/separatorChar)))
               "/"))

(defn relativize-from-root [root file]
  (let [root-path (.toPath (.getCanonicalFile (io/file root)))
        file-path (.toPath (.getCanonicalFile (io/file file)))]
    (str (.toString (.relativize root-path file-path)))))

(defn expected-namespace [root file]
  (->> (str/split (str/replace (relativize-from-root root file) #"\.clj$" "") #"/")
       (map #(str/replace % "_" "-"))
       (str/join ".")))

(defn declared-namespace [file]
  (when-let [[_ ns-name] (re-find #"(?m)^\(ns\s+([^\s\)]+)" (slurp file))]
    ns-name))

(defn read-ns-form [file]
  (with-open [reader (java.io.PushbackReader. (io/reader file))]
    (binding [*read-eval* false]
      (read reader))))

(defn required-libs [file]
  (let [ns-form (read-ns-form file)
        clauses (drop 2 ns-form)]
    (->> clauses
         (filter seq?)
         (filter #(= :require (first %)))
         (mapcat rest)
         (map (fn [libspec]
                (cond
                  (symbol? libspec) libspec
                  (vector? libspec) (first libspec)
                  (seq? libspec) (first libspec)
                  :else nil)))
         (filter symbol?)
         (map str)
         (filter #(str/starts-with? % "vibe-flow."))
         set)))
