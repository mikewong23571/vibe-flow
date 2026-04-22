(ns spike-v1.runtime-repo
  (:require
   [clojure.string :as str]
   [babashka.fs :as fs]
   [spike-v1.paths :as paths]
   [spike-v1.util :as util]))

(defn git! [dir & args]
  (apply util/sh! {:dir (str dir)} "git" args))

(defn current-head [dir]
  (git! dir "rev-parse" "HEAD"))

(defn working-tree-dirty? [dir]
  (not (str/blank? (git! dir "status" "--porcelain"))))

(defn commit-worktree! [dir message]
  (git! dir "add" "-A")
  (apply util/sh!
         {:dir (str dir)}
         ["git"
          "-c" "user.name=spike-v1"
          "-c" "user.email=spike-v1@example.com"
          "commit"
          "-m" message])
  (current-head dir))

(defn ensure-runtime-repo! []
  (let [git-dir (fs/path (paths/runtime-repo-root) ".git")]
    (when-not (fs/exists? git-dir)
      (fs/create-dirs (paths/runtime-repo-root))
      (util/write-file!
       (paths/runtime-readme-path)
       (str
        "# runtime_repo\n\n"
        "This is the seed git repository for preproject_stage1_minimal_workflow.\n"
        "Each worker run receives its own git worktree cloned from a commit in this repo.\n"))
      (util/write-file!
       (paths/runtime-feature-path)
       "base feature file in runtime repo\n")
      (git! (paths/runtime-repo-root) "init" "-b" "main")
      (git! (paths/runtime-repo-root) "add" "-A")
      (apply util/sh!
             {:dir (str (paths/runtime-repo-root))}
             ["git"
              "-c" "user.name=spike-v1"
              "-c" "user.email=spike-v1@example.com"
              "commit"
              "-m" "Initial runtime repo"]))))

(defn registered-worktrees [dir]
  (->> (str/split-lines (git! dir "worktree" "list" "--porcelain"))
       (keep #(when (str/starts-with? % "worktree ")
                (subs % (count "worktree "))))))

(defn reset-runtime-state! []
  (let [repo-root (str (paths/runtime-repo-root))]
    (doseq [worktree (registered-worktrees repo-root)
            :when (not= worktree repo-root)]
      (git! repo-root "worktree" "remove" "--force" worktree))
    (git! repo-root "worktree" "prune")))
