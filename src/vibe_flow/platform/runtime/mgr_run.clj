(ns vibe-flow.platform.runtime.mgr-run
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.string :as str]
   [vibe-flow.definition.task-type :as task-type]
   [vibe-flow.platform.runtime.agent-home-adapter :as agent-home-adapter]
   [vibe-flow.platform.state.system-store :as system-store]
   [vibe-flow.platform.support.shell :as shell-support]
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
       (shell-support/join-command [workflow-command
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

(defn mgr-home-context [target-root task]
  (when-let [mgr-home (task-type/mgr-home target-root (:task-type task))]
    {:role :mgr
     :stage :mgr
     :home mgr-home
     :path (str (paths/agent-home-path target-root mgr-home))}))

(defn assert-mgr-home-ready! [target-root task]
  (when-let [home-context (mgr-home-context target-root task)]
    (try
      ;; TODO: Add a first-class target setup/doctor flow that tells users how to
      ;; provision the required Codex mgr home before their first codex mgr_run.
      (agent-home-adapter/assert-agent-home-ready! home-context)
      (catch clojure.lang.ExceptionInfo ex
        (throw (ex-info "Task type mgr home is not ready."
                        (assoc (ex-data ex)
                               :task-id (:id task)
                               :task-type (task-type/task-type-id (:task-type task)))
                        ex))))))

(defn prepare-mgr-run!
  ([target-root task launcher]
   (prepare-mgr-run! target-root task launcher launcher))
  ([target-root task launcher worker-launcher]
   (when (= :codex launcher)
     (assert-mgr-home-ready! target-root task))
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

(defn mgr-codex-env [home-check]
  (cond-> (into {} (System/getenv))
    home-check
    (assoc "CODEX_HOME" (:path home-check))))

(defn mgr-codex-command [target-root mgr-run]
  ["codex"
   "exec"
   "-C" (str (paths/resolve-target-root target-root))
   "--dangerously-bypass-approvals-and-sandbox"
   (get-in mgr-run [:prompt :text])])

(defn mgr-launch-failure-result [target-root mgr-run ex]
  {:ok? false
   :control :error
   :message (.getMessage ex)
   :launch {:launcher :codex
            :cmd (mgr-codex-command target-root mgr-run)
            :code-home (str (paths/resolve-target-root target-root))
            :agent-home (ex-data ex)
            :stdout ""
            :stderr (.getMessage ex)
            :exit nil
            :prompt-path (get-in mgr-run [:prompt :path])
            :output-path (get-in mgr-run [:output :path])}})

(defn mgr-codex-launch-result [target-root mgr-run cmd home-check {:keys [exit out err]}]
  {:ok? (zero? exit)
   :control (when-not (zero? exit) :error)
   :message (str/trim (str out "\n" err))
   :launch {:launcher :codex
            :cmd cmd
            :code-home (str (paths/resolve-target-root target-root))
            :agent-home home-check
            :stdout out
            :stderr err
            :exit exit
            :prompt-path (get-in mgr-run [:prompt :path])
            :output-path (get-in mgr-run [:output :path])}})

(defn launch-mgr-codex! [target-root task mgr-run]
  (try
    (let [cmd (mgr-codex-command target-root mgr-run)
          home-check (mgr-home-context target-root task)
          env (mgr-codex-env home-check)
          result (apply shell/sh
                        (concat cmd
                                [:dir (str (paths/resolve-target-root target-root))]
                                [:env env]))]
      (mgr-codex-launch-result target-root mgr-run cmd home-check result))
    (catch Exception ex
      (mgr-launch-failure-result target-root mgr-run ex))))
