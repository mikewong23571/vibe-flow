(ns vibe-flow.governance.cli
  (:require
   [clojure.string :as str]
   [vibe-flow.governance.checks :as checks]))

(defn format-issue [{:keys [severity path message intent guidance]}]
  (str (str/upper-case (name severity)) "\n"
       "path: " path "\n"
       "message: " message "\n"
       "intent: " intent "\n"
       "guidance: " guidance "\n"))

(defn -main [& _]
  (let [issues (checks/all-issues)
        errors (filter #(= :error (:severity %)) issues)
        warnings (filter #(= :warning (:severity %)) issues)]
    (doseq [entry issues]
      (println (format-issue entry)))
    (println "summary:")
    (println "  errors:" (count errors))
    (println "  warnings:" (count warnings))
    (if (seq errors)
      (System/exit 1)
      (println "Governance check passed."))))
