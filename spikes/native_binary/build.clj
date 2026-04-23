(ns build
  (:require
   [clojure.tools.build.api :as b]))

(def basis (b/create-basis {:project "deps.edn"}))
(def class-dir "target/classes")
(def jar-file "target/vibe-flow-native-spike.jar")
(def main-ns 'vibe-flow.native-main)

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (b/compile-clj {:basis basis
                  :class-dir class-dir
                  :ns-compile [main-ns]})
  (b/uber {:basis basis
           :class-dir class-dir
           :uber-file jar-file
           :main main-ns}))
