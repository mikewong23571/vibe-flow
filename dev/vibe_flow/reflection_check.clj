(ns vibe-flow.reflection-check
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def checked-namespaces
  '[vibe-flow.system])

(defn make-temp-dir []
  (.toFile (java.nio.file.Files/createTempDirectory
            "vibe-flow-reflection-check"
            (make-array java.nio.file.attribute.FileAttribute 0))))

(defn delete-tree! [file]
  (let [^java.io.File file* (io/file file)]
    (when (.exists file*)
      (doseq [^java.io.File child (reverse (file-seq file*))]
        (.delete child)))))

(defn compile-output [classes-dir]
  (let [writer (java.io.StringWriter.)]
    (binding [*out* writer
              *err* writer
              *warn-on-reflection* true
              *compile-path* (.getPath ^java.io.File classes-dir)]
      (doseq [namespace checked-namespaces]
        (compile namespace)))
    (str writer)))

(defn reflection-warning? [output]
  (str/includes? output "Reflection warning"))

(defn -main [& _args]
  (let [classes-dir (make-temp-dir)]
    (try
      (let [output (compile-output classes-dir)]
        (if (reflection-warning? output)
          (do
            (binding [*out* *err*]
              (println (str/trim output)))
            (System/exit 1))
          (println "No reflection warnings found.")))
      (finally
        (delete-tree! classes-dir)
        (shutdown-agents)))))
