(ns vibe-flow.definition.rendering
  (:require
   [clojure.string :as str]))

(defn text-block [value]
  (cond
    (nil? value) "none"
    (string? value) (if (str/blank? value) "none" value)
    (sequential? value) (if (seq value) (str/join "\n" (map str value)) "none")
    :else (str value)))

(defn render-template [template replacements]
  (reduce-kv
   (fn [content key value]
     (str/replace content
                  (str "{{" (name key) "}}")
                  (str value)))
   template
   replacements))
