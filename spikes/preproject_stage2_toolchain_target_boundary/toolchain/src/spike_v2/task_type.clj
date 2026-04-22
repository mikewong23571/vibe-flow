(ns spike-v2.task-type
  (:require
   [babashka.process :refer [shell]]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [spike-v2.paths :as paths]
   [spike-v2.target-repo :as target-repo]
   [spike-v2.util :as util]))

(defn load-task-type [target-root task-type]
  (let [path (paths/task-type-path target-root task-type)]
    (when-not (.exists (java.io.File. (str path)))
      (throw (ex-info "task_type is not installed"
                      {:task-type task-type
                       :path (str path)})))
    (util/read-edn path nil)))

(defn prepare-run-spec [target-root task worker launcher]
  (let [task-type (load-task-type target-root (:task-type task))]
    {:task-type (:task-type task)
     :worker worker
     :launcher launcher
     :input-head (or (:repo-head task)
                     (target-repo/current-head target-root))
     :worktree-strategy :git-worktree
     :worker-home (get-in task-type [:worker-homes worker])
     :prompt-inputs {:latest_review (or (:latest-review-output task) "none")}
     :prepared-at (util/now)}))

(defn run-prepare-run-hook! [target-root task worker launcher]
  (let [task-type (load-task-type target-root (:task-type task))
        hook-path (get-in task-type [:prepare-run :hook])
        {:keys [exit out err]}
        (apply shell
               {:dir (str target-root)
                :out :string
                :err :string}
               hook-path
               ["--task-id" (:id task)
                "--task-type" (name (:task-type task))
                "--worker" (name worker)
                "--launcher" (name launcher)])
        output (str/trim (if (str/blank? out) err out))]
    (when-not (zero? exit)
      (throw (ex-info "prepare_run hook failed"
                      {:task-id (:id task)
                       :task-type (:task-type task)
                       :worker worker
                       :hook-path hook-path
                       :exit exit
                       :stdout out
                       :stderr err})))
    (edn/read-string output)))
