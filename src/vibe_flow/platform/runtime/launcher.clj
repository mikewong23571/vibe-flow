(ns vibe-flow.platform.runtime.launcher
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.string :as str]
   [vibe-flow.definition.task-type :as task-type]
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
    (let [feature-file (io/file (get-in run [:worktree :dir]) "feature.txt")
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

(defn codex-home-dir [target-root run]
  (when-let [worker-home (:worker-home run)]
    (let [path (paths/agent-home-path target-root worker-home)]
      (.mkdirs path)
      path)))

(defn codex-env [home-dir]
  (cond-> (into {} (System/getenv))
    home-dir
    (assoc "CODEX_HOME" (str home-dir))))

(defn codex-command [run]
  ["codex"
   "exec"
   "-C" (get-in run [:worktree :dir])
   "--dangerously-bypass-approvals-and-sandbox"
   "--output-last-message" (get-in run [:output :path])
   (get-in run [:prompt :text])])

(defn codex-launch-result [worker run cmd home-dir {:keys [exit out err]} output]
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
              :code-home (some-> home-dir str)
              :stdout out
              :stderr err
              :exit exit
              :prompt-path (get-in run [:prompt :path])
              :output-path (get-in run [:output :path])}}))

(defn launch-codex! [target-root worker _task run]
  (let [cmd (codex-command run)
        home-dir (codex-home-dir target-root run)
        env (codex-env home-dir)
        {:keys [exit out err] :as result}
        (apply shell/sh
               (concat cmd
                       [:dir (get-in run [:worktree :dir])]
                       [:env env]))
        output-file (io/file (get-in run [:output :path]))
        output (if (.exists output-file)
                 (slurp output-file)
                 "")]
    (codex-launch-result worker run cmd home-dir result output)))

(defn launch! [target-root launcher worker task run]
  (case launcher
    :mock (launch-mock! worker task run)
    :codex (launch-codex! target-root worker task run)
    (throw (ex-info "Unknown launcher."
                    {:launcher launcher
                     :worker worker
                     :task-id (:id task)}))))
