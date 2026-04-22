(ns spike-v2.mgr-store
  (:require
   [babashka.fs :as fs]
   [spike-v2.paths :as paths]
   [spike-v2.util :as util]))

(defn write-cli-wrapper! [target-root mgr-run-id]
  (let [script (paths/mgr-cli-script target-root mgr-run-id)
        content (str "#!/usr/bin/env bash\n"
                     "set -euo pipefail\n"
                     "cd " (pr-str (str (paths/toolchain-root))) "\n"
                     "bb -m spike-v2.toolchain mgr-advance --target "
                     (pr-str (str target-root))
                     " \"$@\"\n")]
    (util/write-file! script content)
    (.setExecutable (java.io.File. (str script)) true)))

(defn prepare-mgr-run! [target-root task launcher]
  (let [mgr-run-id (util/uuid)
        workdir (paths/mgr-workdir target-root mgr-run-id)]
    (fs/create-dirs (paths/mgr-run-dir target-root mgr-run-id))
    (fs/create-dirs workdir)
    (write-cli-wrapper! target-root mgr-run-id)
    {:id mgr-run-id
     :task-id (:id task)
     :task-type (:task-type task)
     :launcher launcher
     :workdir (str workdir)
     :prompt-path (str (paths/mgr-prompt-file target-root mgr-run-id))
     :output-path (str (paths/mgr-output-file target-root mgr-run-id))
     :cli-script (str (paths/mgr-cli-script target-root mgr-run-id))
     :started-at (util/now)}))

(defn finalize-mgr-run! [mgr-run result]
  (assoc mgr-run
         :ended-at (util/now)
         :error? (not (:ok? result))
         :result result))

(defn load-mgr-run [target-root mgr-run-id]
  (util/read-edn (paths/mgr-run-path target-root mgr-run-id) nil))

(defn write-mgr-run! [target-root mgr-run]
  (util/write-edn! (paths/mgr-run-path target-root (:id mgr-run)) mgr-run))

(defn mgr-run-files [target-root]
  (if (fs/exists? (paths/mgr-runs-root target-root))
    (sort (map str (fs/glob (paths/mgr-runs-root target-root) "*/mgr_run.edn")))
    []))
