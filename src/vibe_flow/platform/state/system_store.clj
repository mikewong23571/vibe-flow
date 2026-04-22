(ns vibe-flow.platform.state.system-store
  (:require
   [vibe-flow.platform.support.edn :as edn]
   [vibe-flow.platform.target.paths :as paths]))

(defn installed? [target-root]
  (.exists (paths/install-path target-root)))

(defn save-install! [target-root install-record]
  (edn/write-edn! (paths/install-path target-root) install-record))

(defn load-install [target-root]
  (edn/read-edn (paths/install-path target-root) nil))

(defn save-target! [target-root target-record]
  (edn/write-edn! (paths/target-path target-root) target-record))

(defn load-target [target-root]
  (edn/read-edn (paths/target-path target-root) nil))

(defn save-layout! [target-root layout-record]
  (edn/write-edn! (paths/layout-path target-root) layout-record))

(defn load-layout [target-root]
  (edn/read-edn (paths/layout-path target-root) nil))
