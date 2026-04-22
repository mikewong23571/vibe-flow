(ns spike-v1.mgr
  (:require
   [babashka.process :refer [shell]]
   [clojure.string :as str]
   [spike-v1.install :as install]
   [spike-v1.model :as model]
   [spike-v1.mgr-store :as mgr-store]
   [spike-v1.paths :as paths]
   [spike-v1.prompt :as prompt]
   [spike-v1.run-store :as run-store]
   [spike-v1.util :as util]))

(defn recent-run-summary [task]
  (let [runs (take-last 3 (run-store/task-runs (:id task)))]
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

(defn mgr-context [task mgr-run worker-launcher]
  {:recent-runs (recent-run-summary task)
   :max-run-count model/max-run-count
   :max-review-count model/max-review-count
   :worker-launcher (name worker-launcher)
   :workflow-cli-path (or (:cli-script mgr-run)
                          (str (paths/mgr-cli-script (:id mgr-run))))})

(defn parse-decision [s]
  (let [decision-match (re-find #"(?m)^DECISION:\s*(impl|review|refine|done|error)\s*$" s)
        reason-match (re-find #"(?m)^REASON:\s*(.+)\s*$" s)]
    (when decision-match
      {:decision (keyword (second decision-match))
       :reason (when reason-match
                 (second reason-match))})))

(defn mock-cli-command [task mgr-run worker-launcher]
  (let [decision (cond
                   (contains? #{:done :error} (:stage task)) (:stage task)
                   (model/task-overrun? task) :error
                   :else (model/stage->worker task))]
    [(str (paths/mgr-cli-script (:id mgr-run)))
     "--task-id" (:id task)
     "--mgr-run-id" (:id mgr-run)
     "--worker-launcher" (name worker-launcher)
     "--decision" (name decision)
     "--reason" (if (= decision :error)
                  "mock mgr stopped after hitting spike guardrails."
                  "mock mgr followed task stage.")]))

(defn mock-drive [task mgr-run worker-launcher]
  (let [cmd (mock-cli-command task mgr-run worker-launcher)
        {:keys [exit out err]}
        (apply shell
               {:dir (str (paths/toolchain-root))
                :out :string
                :err :string}
               cmd)]
    {:ok? (zero? exit)
     :message (str/trim (if (str/blank? out) err out))
     :launch
     {:cmd cmd
      :stdout out
      :stderr err}}))

(defn codex-drive [task mgr-run worker-launcher]
  (let [install-record (install/load-install!)
        home-name (get-in install-record [:task-types :impl :mgr-home])
        home-dir (paths/home-path home-name)
        rendered (prompt/mgr-prompt task mgr-run (mgr-context task mgr-run worker-launcher))
        cmd ["codex" "exec"
             "--skip-git-repo-check"
             "-C" (:workdir mgr-run)
             "--dangerously-bypass-approvals-and-sandbox"
             "--output-last-message" (:output-path mgr-run)
             rendered]]
    (spit (:prompt-path mgr-run) rendered)
    (let [{:keys [exit out err]}
          (apply shell
                 {:dir (str (paths/target-root))
                  :out :string
                  :err :string
                  :extra-env {"CODEX_HOME" (str home-dir)}}
                 cmd)
          output (if (.exists (java.io.File. (:output-path mgr-run)))
                   (slurp (:output-path mgr-run))
                   (str/trim out))]
      (if (zero? exit)
        {:ok? true
         :message (str/trim output)
         :launch
         {:cmd cmd
          :home-dir (str home-dir)
          :config-path (str (paths/home-path home-name) "/config.toml")
          :workdir (:workdir mgr-run)
          :stdout out
          :stderr err
          :prompt-path (:prompt-path mgr-run)
          :output-path (:output-path mgr-run)}}
        {:ok? false
         :message (if (str/blank? output) err output)
         :launch
         {:cmd cmd
          :home-dir (str home-dir)
          :config-path (str (paths/home-path home-name) "/config.toml")
          :workdir (:workdir mgr-run)
          :stdout out
          :stderr err
          :prompt-path (:prompt-path mgr-run)
          :output-path (:output-path mgr-run)}}))))

(defn drive-task! [launcher task mgr-run worker-launcher]
  (case launcher
    :mock (mock-drive task mgr-run worker-launcher)
    :codex (codex-drive task mgr-run worker-launcher)
    (throw (ex-info "unknown mgr launcher" {:launcher launcher}))))
