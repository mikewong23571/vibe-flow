(ns vibe-flow.platform.runtime.run
  (:require
   [clojure.java.io :as io]
   [vibe-flow.definition.task-type :as task-type]
   [vibe-flow.platform.support.time :as time]
   [vibe-flow.platform.target.paths :as paths]
   [vibe-flow.platform.target.repo :as repo]))

(defn uuid []
  (str (java.util.UUID/randomUUID)))

(defn write-file! [path content]
  (let [file (io/file path)]
    (io/make-parents file)
    (spit file content)
    file))

(defn prepare-run! [target-root task worker launcher]
  (let [run-id (uuid)
        prepare-spec (task-type/prepare-run-spec target-root task worker launcher)
        input-head (:input-head prepare-spec)
        worktree-strategy (:worktree-strategy prepare-spec)
        worktree-dir (paths/run-worktree-dir target-root run-id)
        prompt-path (paths/run-prompt-path target-root run-id)
        output-path (paths/run-output-path target-root run-id)
        run {:id run-id
             :task-id (:id task)
             :task-type (:task-type task)
             :worker worker
             :launcher launcher
             :worker-home (:worker-home prepare-spec)
             :prepare-run {:input-head input-head
                           :worktree-strategy worktree-strategy
                           :prompt-inputs (:prompt-inputs prepare-spec)}
             :worktree {:dir (str worktree-dir)
                        :strategy worktree-strategy}
             :heads {:input input-head}
             :prompt {:path (str prompt-path)}
             :output {:path (str output-path)}
             :started-at (time/now)}
        run (cond-> run
              (:before-prepare-run prepare-spec)
              (assoc-in [:prepare-run :before-prepare-run] (:before-prepare-run prepare-spec)))
        prompt-text (task-type/worker-prompt target-root task run)]
    (case worktree-strategy
      :git-worktree
      (repo/git! target-root "worktree" "add" "--detach" (str worktree-dir) input-head)
      (throw (ex-info "Unsupported worktree strategy."
                      {:task-id (:id task)
                       :strategy worktree-strategy})))
    (write-file! prompt-path prompt-text)
    (assoc run :prompt {:path (str prompt-path)
                        :text prompt-text})))

(defn task-error? [result]
  (or (not (:ok? result))
      (= (:control result) :error)
      (= (:control result) :unknown)))

(defn finalize-run! [task run result]
  (let [worker (:worker run)
        worktree-dir (get-in run [:worktree :dir])
        commit? (and (:ok? result)
                     (contains? #{:impl :refine} worker)
                     (repo/working-tree-dirty? worktree-dir))
        output-head (if commit?
                      (repo/commit-worktree! worktree-dir
                                             (str "vibe-flow " (:id task) " " (name worker)))
                      (get-in run [:heads :input]))
        output-path (get-in run [:output :path])]
    (write-file! output-path (:message result))
    (assoc run
           :ended-at (time/now)
           :error? (task-error? result)
           :heads (assoc (:heads run) :output output-head)
           :output (assoc (:output run) :text (:message result))
           :result result)))
