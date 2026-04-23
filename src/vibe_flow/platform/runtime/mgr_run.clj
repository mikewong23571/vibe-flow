(ns vibe-flow.platform.runtime.mgr-run
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [vibe-flow.definition.task-type :as task-type]
   [vibe-flow.platform.state.system-store :as system-store]
   [vibe-flow.platform.support.shell :as shell]
   [vibe-flow.platform.support.time :as time]
   [vibe-flow.platform.target.paths :as paths]))

(defn uuid []
  (str (java.util.UUID/randomUUID)))

(defn write-file! [path content]
  (let [^java.io.File file (io/file path)]
    (io/make-parents file)
    (spit file content)
    file))

(defn executable-file? [path]
  (let [^java.io.File file (some-> path io/file)]
    (boolean
     (and file
          (.exists file)
          (.isFile file)
          (.canExecute file)))))

(defn command-has-path? [command]
  (str/includes? command (str java.io.File/separatorChar)))

(defn path-command [command]
  (when (executable-file? command)
    (str (.getCanonicalFile ^java.io.File (io/file command)))))

(defn path-entries []
  (str/split (or (System/getenv "PATH") "")
             (re-pattern java.io.File/pathSeparator)))

(defn path-search-command [command]
  (when-not (command-has-path? command)
    (some (fn [dir]
            (let [^java.io.File candidate (io/file dir command)]
              (when (executable-file? candidate)
                (str (.getCanonicalFile candidate)))))
          (path-entries))))

(defn resolve-command [command]
  (when (and (string? command)
             (not (str/blank? command)))
    (or (path-command command)
        (path-search-command command))))

(defn workflow-command [target-root]
  (let [stored-command (or (:command (system-store/load-toolchain target-root))
                           "vibe-flow")]
    (or (resolve-command stored-command)
        (throw (ex-info "Workflow command is unavailable."
                        {:target-root (str (paths/resolve-target-root target-root))
                         :command stored-command
                         :toolchain-path (str (paths/toolchain-path target-root))})))))

(defn cli-wrapper-text [target-root workflow-command task mgr-run-id]
  (str "#!/usr/bin/env bash\n"
       "set -euo pipefail\n"
       "exec "
       (shell/join-command [workflow-command
                            "mgr-advance"
                            "--target" (str (paths/resolve-target-root target-root))
                            "--task-id" (:id task)
                            "--mgr-run-id" mgr-run-id])
       " \"$@\"\n"))

(defn write-cli-wrapper! [target-root workflow-command task mgr-run-id]
  (let [path (paths/mgr-run-cli-path target-root mgr-run-id)]
    (write-file! path (cli-wrapper-text target-root workflow-command task mgr-run-id))
    (.setExecutable ^java.io.File path true)
    path))

(defn prepare-mgr-run!
  ([target-root task launcher]
   (prepare-mgr-run! target-root task launcher launcher))
  ([target-root task launcher worker-launcher]
   (let [mgr-run-id (uuid)
         workflow-command* (workflow-command target-root)
         prompt-path (paths/mgr-run-prompt-path target-root mgr-run-id)
         output-path (paths/mgr-run-output-path target-root mgr-run-id)
         ^java.io.File workdir (paths/mgr-run-workdir target-root mgr-run-id)]
     (.mkdirs ^java.io.File (paths/mgr-run-dir target-root mgr-run-id))
     (.mkdirs workdir)
     (let [cli-script (write-cli-wrapper! target-root workflow-command* task mgr-run-id)
           mgr-run {:id mgr-run-id
                    :task-id (:id task)
                    :task-type (:task-type task)
                    :launcher launcher
                    :worker-launcher worker-launcher
                    :cli-script (str cli-script)
                    :workdir (str workdir)
                    :prompt {:path (str prompt-path)}
                    :output {:path (str output-path)}
                    :started-at (time/now)}
           prompt-text (task-type/mgr-prompt target-root task mgr-run)]
       (write-file! prompt-path prompt-text)
       (assoc mgr-run
              :prompt {:path (str prompt-path)
                       :text prompt-text})))))

(defn finalize-mgr-run! [mgr-run result]
  (let [output-path (get-in mgr-run [:output :path])]
    (write-file! output-path (:message result))
    (assoc mgr-run
           :ended-at (time/now)
           :error? (not (:ok? result))
           :output (assoc (:output mgr-run)
                          :text (:message result))
           :result result)))
