(ns spike-v3.agent.mgr
  (:require
   [babashka.fs :as fs]
   [babashka.process :refer [shell]]
   [clojure.string :as str]
   [spike-v3.agent.prompt :as prompt]
   [spike-v3.core.model :as model]
   [spike-v3.definition.task-type :as task-type]
   [spike-v3.state.run-store :as run-store]))

(defn recent-run-summary [target-root task]
  (let [runs (take-last 3 (run-store/task-runs target-root (:id task)))]
    (if (seq runs)
      (str/join
       "\n\n"
       (for [run runs]
         (str "run-id: " (:id run) "\n"
              "worker: " (:worker run) "\n"
              "error?: " (:error? run) "\n"
              "output-head: " (:output-head run) "\n"
              "result-control: " (get-in run [:result :control] :none) "\n"
              "result-message:\n"
              (or (get-in run [:result :message]) "none"))))
      "none")))

(defn mgr-context [target-root task mgr-run worker-launcher]
  {:recent-runs (recent-run-summary target-root task)
   :max-run-count model/max-run-count
   :max-review-count model/max-review-count
   :worker-launcher (name worker-launcher)
   :workflow-cli-path (:cli-script mgr-run)})

(defn mock-cli-command [target-root task mgr-run worker-launcher]
  (let [decision (cond
                   (contains? #{:done :error} (:stage task)) (:stage task)
                   (model/task-overrun? task) :error
                   :else (task-type/worker-for-stage target-root task))]
    [(:cli-script mgr-run)
     "--task-id" (:id task)
     "--mgr-run-id" (:id mgr-run)
     "--worker-launcher" (name worker-launcher)
     "--decision" (name decision)
     "--reason" (if (= decision :error)
                  "mock mgr stopped after hitting spike guardrails."
                  "mock mgr followed task stage.")]))

(defn mock-drive [target-root task mgr-run worker-launcher]
  (let [cmd (mock-cli-command target-root task mgr-run worker-launcher)
        {:keys [exit out err]}
        (apply shell
               {:dir (str target-root)
                :out :string
                :err :string}
               cmd)]
    {:ok? (zero? exit)
     :message (str/trim (if (str/blank? out) err out))
     :launch {:cmd cmd
              :stdout out
              :stderr err}}))

(defn codex-drive [target-root task mgr-run worker-launcher]
  (let [home-name (task-type/mgr-home target-root (:task-type task))
        home-dir (fs/path target-root ".workflow" "local" "agent_homes" (name home-name))
        rendered (prompt/mgr-prompt target-root task mgr-run (mgr-context target-root task mgr-run worker-launcher))
        cmd ["codex" "exec"
             "--skip-git-repo-check"
             "-C" (:workdir mgr-run)
             "--dangerously-bypass-approvals-and-sandbox"
             "--output-last-message" (:output-path mgr-run)
             rendered]]
    (spit (:prompt-path mgr-run) rendered)
    (let [{:keys [exit out err]}
          (apply shell
                 {:dir (str target-root)
                  :out :string
                  :err :string
                  :extra-env {"CODEX_HOME" (str home-dir)}}
                 cmd)
          output (if (.exists (java.io.File. (:output-path mgr-run)))
                   (slurp (:output-path mgr-run))
                   (str/trim out))]
      {:ok? (zero? exit)
       :message (str/trim (if (str/blank? output) err output))
       :launch {:cmd cmd
                :home-dir (str home-dir)
                :config-path (str (fs/path home-dir "config.toml"))
                :workdir (:workdir mgr-run)
                :stdout out
                :stderr err
                :prompt-path (:prompt-path mgr-run)
                :output-path (:output-path mgr-run)}})))

(defn drive-task! [target-root launcher task mgr-run worker-launcher]
  (case launcher
    :mock (mock-drive target-root task mgr-run worker-launcher)
    :codex (codex-drive target-root task mgr-run worker-launcher)
    (throw (ex-info "unknown mgr launcher" {:launcher launcher}))))
