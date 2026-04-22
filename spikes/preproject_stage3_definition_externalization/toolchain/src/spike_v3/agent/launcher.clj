(ns spike-v3.agent.launcher
  (:require
   [spike-v3.agent.launcher.codex :as codex]
   [spike-v3.agent.launcher.mock :as mock]))

(defn launch! [target-root launcher worker task run]
  (case launcher
    :mock (mock/launch! worker task run)
    :codex (codex/launch! target-root worker task run)
    (throw (ex-info "unknown launcher" {:launcher launcher}))))
