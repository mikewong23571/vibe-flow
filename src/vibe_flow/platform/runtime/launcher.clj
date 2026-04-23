(ns vibe-flow.platform.runtime.launcher
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.string :as str]
   [vibe-flow.definition.task-type :as task-type]
   [vibe-flow.platform.runtime.agent-home-adapter :as agent-home-adapter]
   [vibe-flow.platform.support.time :as time]
   [vibe-flow.platform.target.paths :as paths]))

(defn append-worklog! [run line]
  (spit (io/file (get-in run [:worktree :dir]) "mock-worklog.txt")
        (str line "\n")
        :append true))

(defn launch-mock! [worker task run]
  (case worker
    :impl
    (do
      (spit (io/file (get-in run [:worktree :dir]) "feature.txt")
            (str "implemented by mock at " (time/now) "\n")
            :append true)
      (append-worklog! run (str "impl:" (:id task)))
      {:ok? true
       :message "mock impl completed"})

    :review
    (let [^java.io.File feature-file (io/file (get-in run [:worktree :dir]) "feature.txt")
          feature (if (.exists feature-file) (slurp feature-file) "")]
      (append-worklog! run (str "review:" (:id task)))
      (if (str/includes? feature "refined by mock")
        {:ok? true
         :control :pass
         :message "RESULT: pass\nmock review passed"}
        {:ok? true
         :control :needs-refine
         :message "RESULT: needs_refine\nmock review requested refine"}))

    :refine
    (do
      (spit (io/file (get-in run [:worktree :dir]) "feature.txt")
            (str "refined by mock at " (time/now) "\n")
            :append true)
      (append-worklog! run (str "refine:" (:id task)))
      {:ok? true
       :message "mock refine completed"})

    {:ok? false
     :control :error
     :message (str "mock launcher does not support worker " worker)}))

(defn codex-home-check [target-root worker run]
  (when-let [worker-home (:worker-home run)]
    ;; TODO: Add a first-class target setup/doctor flow that tells users how to
    ;; provision required Codex home auth/config before their first codex run.
    (agent-home-adapter/assert-agent-home-ready!
     {:role worker
      :stage worker
      :home worker-home
      :path (str (paths/agent-home-path target-root worker-home))})))

(declare codex-command)

(defn codex-home-failure-result [run ex]
  {:ok? false
   :control :error
   :message (.getMessage ex)
   :launch {:launcher :codex
            :cmd (codex-command run)
            :code-home nil
            :agent-home (ex-data ex)
            :stdout ""
            :stderr (.getMessage ex)
            :exit nil
            :prompt-path (get-in run [:prompt :path])
            :output-path (get-in run [:output :path])}})

(defn codex-env [home-check]
  (cond-> (into {} (System/getenv))
    home-check
    (assoc "CODEX_HOME" (:path home-check))))

(defn codex-command [run]
  ["codex"
   "exec"
   "-C" (get-in run [:worktree :dir])
   "--dangerously-bypass-approvals-and-sandbox"
   "--output-last-message" (get-in run [:output :path])
   (get-in run [:prompt :text])])

(defn codex-launch-result [worker run cmd home-check {:keys [exit out err]} output]
  (let [message (if (str/blank? output)
                  (str/trim (str out "\n" err))
                  output)]
    {:ok? (zero? exit)
     :control (cond
                (not (zero? exit)) :error
                (= worker :review) (task-type/parse-review-control message)
                :else nil)
     :message message
     :launch {:launcher :codex
              :cmd cmd
              :code-home (:path home-check)
              :agent-home home-check
              :stdout out
              :stderr err
              :exit exit
              :prompt-path (get-in run [:prompt :path])
              :output-path (get-in run [:output :path])}}))

(defn launch-codex! [target-root worker _task run]
  (try
    (let [cmd (codex-command run)
          home-check (codex-home-check target-root worker run)
          env (codex-env home-check)
          result (apply shell/sh
                        (concat cmd
                                [:dir (get-in run [:worktree :dir])]
                                [:env env]))
          ^java.io.File output-file (io/file (get-in run [:output :path]))
          output (if (.exists output-file)
                   (slurp output-file)
                   "")]
      (codex-launch-result worker run cmd home-check result output))
    (catch clojure.lang.ExceptionInfo ex
      (codex-home-failure-result run ex))))

(defn launch! [target-root launcher worker task run]
  (case launcher
    :mock (launch-mock! worker task run)
    :codex (launch-codex! target-root worker task run)
    (throw (ex-info "Unknown launcher."
                    {:launcher launcher
                     :worker worker
                     :task-id (:id task)}))))
