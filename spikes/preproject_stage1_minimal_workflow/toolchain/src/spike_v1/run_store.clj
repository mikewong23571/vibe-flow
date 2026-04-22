(ns spike-v1.run-store
  (:require
   [babashka.fs :as fs]
   [spike-v1.paths :as paths]
   [spike-v1.runtime-repo :as runtime-repo]
   [spike-v1.util :as util]))

(defn prepare-run! [task worker launcher]
  (let [run-id (util/uuid)
        input-head (or (:repo-head task)
                       (runtime-repo/current-head (paths/runtime-repo-root)))
        worktree (paths/worktree-dir run-id)]
    (fs/create-dirs (paths/run-dir run-id))
    (runtime-repo/git! (paths/runtime-repo-root) "worktree" "add" "--detach" (str worktree) input-head)
    {:id run-id
     :task-id (:id task)
     :worker worker
     :launcher launcher
     :run-dir (str (paths/run-dir run-id))
     :worktree-dir (str worktree)
     :prompt-path (str (paths/prompt-file run-id))
     :output-path (str (paths/output-file run-id))
     :input-head input-head
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
                     (runtime-repo/working-tree-dirty? worktree))
        output-head (if commit?
                      (runtime-repo/commit-worktree! worktree
                                                     (str "spike " (:id task) " " (name worker)))
                      (:input-head run))]
    (assoc run
           :ended-at (util/now)
           :error? (task-error? result)
           :output-head output-head
           :result result)))

(defn write-run! [run]
  (util/write-edn! (paths/run-path (:id run)) run))

(defn load-run [run-id]
  (when run-id
    (util/read-edn (paths/run-path run-id) nil)))

(defn run-files []
  (sort (map str (fs/glob (paths/runs-root) "*/run.edn"))))

(defn task-runs [task-id]
  (->> (run-files)
       (map #(util/read-edn % nil))
       (filter #(= task-id (:task-id %)))
       (sort-by :started-at)))
