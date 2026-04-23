(ns vibe-flow.platform.target.repo
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.string :as str]))

(defn git-repo? [target-root]
  (.exists (io/file target-root ".git")))

(defn require-git-repo! [target-root]
  (when-not (git-repo? target-root)
    (throw (ex-info "Target is not a git repository."
                    {:target-root (str (.getCanonicalFile (io/file target-root)))})))
  target-root)

(defn git! [target-root & args]
  (let [{:keys [exit out err]}
        (apply shell/sh (concat ["git"] args [:dir (str target-root)]))]
    (when-not (zero? exit)
      (throw (ex-info "Git command failed."
                      {:target-root (str target-root)
                       :args args
                       :exit exit
                       :out out
                       :err err})))
    (str/trim out)))

(defn current-head [target-root]
  (git! target-root "rev-parse" "HEAD"))

(defn current-branch [target-root]
  (git! target-root "rev-parse" "--abbrev-ref" "HEAD"))

(defn working-tree-dirty? [dir]
  (not (str/blank? (git! dir "status" "--porcelain"))))

(defn commit-worktree! [dir message]
  (git! dir "add" "-A")
  (let [{:keys [exit out err]}
        (shell/sh "git"
                  "-c" "user.name=vibe-flow"
                  "-c" "user.email=vibe-flow@example.com"
                  "commit" "-m" message
                  :dir (str dir))]
    (when-not (zero? exit)
      (throw (ex-info "Git commit failed."
                      {:dir (str dir)
                       :message message
                       :exit exit
                       :out out
                       :err err}))))
  (current-head dir))
