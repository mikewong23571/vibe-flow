(ns spike-v1.mgr-store
  (:require
   [babashka.fs :as fs]
   [spike-v1.paths :as paths]
   [spike-v1.util :as util]))

(defn write-cli-wrapper! [mgr-run-id]
  (let [script (paths/mgr-cli-script mgr-run-id)
        content (str "#!/usr/bin/env bash\n"
                     "set -euo pipefail\n"
                     "cd " (pr-str (str (paths/toolchain-root))) "\n"
                     "bb -m spike-v1.toolchain mgr-advance \"$@\"\n")]
    (util/write-file! script content)
    (.setExecutable (java.io.File. (str script)) true)))

(defn prepare-mgr-run! [task launcher]
  (let [mgr-run-id (util/uuid)
        workdir (paths/mgr-workdir mgr-run-id)]
    (fs/create-dirs (paths/mgr-run-dir mgr-run-id))
    (fs/create-dirs workdir)
    (write-cli-wrapper! mgr-run-id)
    {:id mgr-run-id
     :task-id (:id task)
     :launcher launcher
     :workdir (str workdir)
     :prompt-path (str (paths/mgr-prompt-file mgr-run-id))
     :output-path (str (paths/mgr-output-file mgr-run-id))
     :cli-script (str (paths/mgr-cli-script mgr-run-id))
     :started-at (util/now)}))

(defn finalize-mgr-run! [mgr-run result]
  (assoc mgr-run
         :ended-at (util/now)
         :error? (not (:ok? result))
         :result result))

(defn load-mgr-run [mgr-run-id]
  (util/read-edn (paths/mgr-run-path mgr-run-id) nil))

(defn write-mgr-run! [mgr-run]
  (util/write-edn! (paths/mgr-run-path (:id mgr-run)) mgr-run))

(defn mgr-run-files []
  (sort (map str (fs/glob (paths/mgr-runs-root) "*/mgr_run.edn"))))
