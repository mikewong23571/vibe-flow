(ns vibe-flow.platform.support.shell
  (:require
   [clojure.string :as str]))

(defn quote-arg [value]
  (str "'"
       (str/replace (str value) "'" "'\"'\"'")
       "'"))

(defn join-command [parts]
  (str/join " " (map quote-arg parts)))
