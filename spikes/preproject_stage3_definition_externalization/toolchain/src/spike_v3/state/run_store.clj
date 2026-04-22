(ns spike-v3.state.run-store
  (:require
   [babashka.fs :as fs]
   [spike-v3.definition.task-type :as task-type]
   [spike-v3.support.paths :as paths]
   [spike-v3.support.util :as util]
   [spike-v3.target.repo :as target-repo]))

(defn prepare-run! [target-root task worker launcher]
  (let [prepare-spec (task-type/prepare-run-spec target-root task worker launcher)
        run-id (util/uuid)
        worktree (paths/worktree-dir target-root run-id)
        input-head (:input-head prepare-spec)]
    (fs/create-dirs (paths/run-dir target-root run-id))
    (case (:worktree-strategy prepare-spec)
      :git-worktree
      (target-repo/git! target-root "worktree" "add" "--detach" (str worktree) input-head)
      (throw (ex-info "unsupported worktree strategy"
                      {:strategy (:worktree-strategy prepare-spec)
                       :task-id (:id task)})))
    {:id run-id
     :task-id (:id task)
     :task-type (:task-type task)
     :worker worker
     :launcher launcher
     :run-dir (str (paths/run-dir target-root run-id))
     :worktree-dir (str worktree)
     :prompt-path (str (paths/prompt-file target-root run-id))
     :output-path (str (paths/output-file target-root run-id))
     :input-head input-head
     :worker-home (:worker-home prepare-spec)
     :worktree-strategy (:worktree-strategy prepare-spec)
     :prompt-inputs (:prompt-inputs prepare-spec)
     :prepare-run prepare-spec
     :started-at (util/now)}))

(defn task-error? [result]
  (or (not (:ok? result))
      (= (:control result) :error)
      (= (:control result) :unknown)))

(defn finalize-run! [task run result]
  (let [worker (:worker run)
        worktree (:worktree-dir run)
        commit? (and (:ok? result)
                     (contains? #{:impl :refine} worker)
                     (target-repo/working-tree-dirty? worktree))
        output-head (if commit?
                      (target-repo/commit-worktree! worktree
                                                    (str "preproject_stage3_definition_externalization " (:id task) " " (name worker)))
                      (:input-head run))]
    (assoc run
           :ended-at (util/now)
           :error? (task-error? result)
           :output-head output-head
           :result result)))

(defn write-run! [target-root run]
  (util/write-edn! (paths/run-path target-root (:id run)) run))

(defn load-run [target-root run-id]
  (when run-id
    (util/read-edn (paths/run-path target-root run-id) nil)))

(defn run-files [target-root]
  (if (fs/exists? (paths/runs-root target-root))
    (sort (map str (fs/glob (paths/runs-root target-root) "*/run.edn")))
    []))

(defn task-runs [target-root task-id]
  (->> (run-files target-root)
       (map #(util/read-edn % nil))
       (filter #(= task-id (:task-id %)))
       (sort-by :started-at)))
