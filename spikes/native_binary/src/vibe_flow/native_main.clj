(ns vibe-flow.native-main
  (:require
   [vibe-flow.system :as system])
  (:gen-class))

(defn -main [& args]
  (try
    (apply system/-main args)
    (finally
      (shutdown-agents))))
