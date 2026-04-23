(ns vibe-flow.platform.runtime.agent-home-adapter
  (:require
   [clojure.java.io :as io]))

(def required-config-files ["auth.json" "config.toml"])

(defn file-exists? [path]
  (let [^java.io.File file (io/file path)]
    (and (.exists file) (.isFile file))))

(defn home-configured? [home-dir]
  (boolean
   (some #(file-exists? (io/file home-dir %))
         required-config-files)))

(defn agent-home-check [{:keys [role stage home path]}]
  (let [^java.io.File home-dir (io/file path)
        exists? (.exists home-dir)
        directory? (.isDirectory home-dir)
        configured? (and directory? (home-configured? home-dir))]
    (cond-> {:role role
             :stage stage
             :home home
             :path (str home-dir)
             :exists? exists?
             :directory? directory?
             :configured? configured?
             :required-any required-config-files}
      (not exists?)
      (assoc :reason :missing-home)

      (and exists? (not directory?))
      (assoc :reason :not-directory)

      (and directory? (not configured?))
      (assoc :reason :missing-config-file))))

(defn assert-agent-home-ready! [home-context]
  (let [check (agent-home-check home-context)]
    (when-not (:configured? check)
      (throw (ex-info "Agent home is not ready."
                      check)))
    check))

(defn assert-agent-homes-ready! [home-contexts]
  (let [checks (mapv agent-home-check home-contexts)
        failures (vec (remove :configured? checks))]
    (when (seq failures)
      (throw (ex-info "Agent homes are not ready."
                      {:required-any required-config-files
                       :failures failures})))
    checks))
