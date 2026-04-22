(ns vibe-flow.governance.manifest
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]))

(def manifest-resource "vibe_flow/governance/module_manifest.edn")

(defn manifest-data []
  (or (some-> manifest-resource io/resource slurp edn/read-string)
      (throw (ex-info "Missing governance module manifest resource."
                      {:resource manifest-resource}))))

(defn namespaces-map []
  (:namespaces (manifest-data)))

(defn module-entry [ns-name]
  (get (namespaces-map) ns-name))
