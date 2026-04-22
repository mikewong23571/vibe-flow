(ns spike-v2.target-repo
  (:require
   [babashka.fs :as fs]
   [clojure.string :as str]
   [spike-v2.util :as util]))

(defn git! [dir & args]
  (apply util/sh! {:dir (str dir)} "git" args))

(defn git-repo? [target-root]
  (fs/exists? (fs/path target-root ".git")))

(defn require-git-repo! [target-root]
  (when-not (git-repo? target-root)
    (throw (ex-info "target is not a git repository"
                    {:target-root (str target-root)})))
  target-root)

(defn current-head [target-root]
  (git! target-root "rev-parse" "HEAD"))

(defn current-branch [target-root]
  (git! target-root "rev-parse" "--abbrev-ref" "HEAD"))

(defn working-tree-dirty? [dir]
  (not (str/blank? (git! dir "status" "--porcelain"))))

(defn commit-worktree! [dir message]
  (git! dir "add" "-A")
  (apply util/sh!
         {:dir (str dir)}
         ["git"
          "-c" "user.name=spike-v2"
          "-c" "user.email=spike-v2@example.com"
          "commit"
          "-m" message])
  (current-head dir))

(defn ensure-sample-target! [target-root]
  (fs/create-dirs target-root)
  (when-not (git-repo? target-root)
    (git! target-root "init" "-b" "main")
    (git! target-root "add" "-A")
    (apply util/sh!
           {:dir (str target-root)}
           ["git"
            "-c" "user.name=spike-v2"
            "-c" "user.email=spike-v2@example.com"
            "commit"
            "-m" "Initial sample target"])))

(defn registered-worktrees [target-root]
  (->> (str/split-lines (git! target-root "worktree" "list" "--porcelain"))
       (keep #(when (str/starts-with? % "worktree ")
                (subs % (count "worktree "))))))

(defn reset-runtime-state! [target-root]
  (doseq [worktree (registered-worktrees target-root)
          :when (not= worktree (str target-root))]
    (git! target-root "worktree" "remove" "--force" worktree))
  (git! target-root "worktree" "prune"))
