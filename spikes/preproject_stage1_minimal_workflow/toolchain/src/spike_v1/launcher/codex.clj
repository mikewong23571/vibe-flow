(ns spike-v1.launcher.codex
  (:require
   [babashka.fs :as fs]
   [babashka.process :refer [shell]]
   [spike-v1.paths :as paths]
   [spike-v1.prompt :as prompt]))

(defn launch! [install worker task run]
  (let [home-name (get-in install [:task-types :impl :worker-homes worker])
        home-dir (paths/home-path home-name)
        rendered (prompt/worker-prompt worker task run)
        cmd ["codex" "exec"
             "--skip-git-repo-check"
             "-C" (:worktree-dir run)
             "--dangerously-bypass-approvals-and-sandbox"
             "--output-last-message" (:output-path run)
             rendered]]
    (spit (:prompt-path run) rendered)
    (let [{:keys [exit out err]}
          (apply shell
                 {:dir (str (paths/target-root))
                  :out :string
                  :err :string
                  :extra-env {"CODEX_HOME" (str home-dir)}}
                 cmd)
          output (if (fs/exists? (:output-path run))
                   (slurp (:output-path run))
                   "")]
      (if (zero? exit)
        {:ok? true
         :message output
         :control (when (= worker :review)
                    (prompt/parse-review-control output))
         :launch
         {:cmd cmd
          :home-dir (str home-dir)
          :config-path (str (fs/path home-dir "config.toml"))
          :worktree-dir (:worktree-dir run)
          :stdout out
          :stderr err
          :prompt-path (:prompt-path run)
          :output-path (:output-path run)}}
        {:ok? false
         :message output
         :control :error
         :launch
         {:cmd cmd
          :home-dir (str home-dir)
          :config-path (str (fs/path home-dir "config.toml"))
          :worktree-dir (:worktree-dir run)
          :stdout out
          :stderr err
          :prompt-path (:prompt-path run)
          :output-path (:output-path run)}}))))
