(ns user
  (:require
   [clojure.java.io :as io]
   [vibe-flow.core :as core]
   [vibe-flow.system :as system]))

(defn repl-overview
  ([] (repl-overview "."))
  ([target-root]
   {:cwd (.getCanonicalPath (io/file "."))
    :target-root (.getCanonicalPath (io/file target-root))
    :app (core/app-overview target-root)
    :surface (keys (system/system-blueprint))}))
