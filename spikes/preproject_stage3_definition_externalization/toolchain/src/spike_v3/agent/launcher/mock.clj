(ns spike-v3.agent.launcher.mock
  (:require
   [babashka.fs :as fs]
   [clojure.string :as str]
   [spike-v3.support.util :as util]))

(defn append-worklog! [run line]
  (spit (str (fs/path (:worktree-dir run) "mock-worklog.txt"))
        (str line "\n")
        :append true))

(defn launch! [worker task run]
  (case worker
    :impl
    (do
      (spit (str (fs/path (:worktree-dir run) "feature.txt"))
            (str "implemented by mock at " (util/now) "\n")
            :append true)
      (append-worklog! run (str "impl:" (:id task)))
      {:ok? true
       :message "mock impl completed"})

    :review
    (let [feature (slurp (str (fs/path (:worktree-dir run) "feature.txt")))]
      (append-worklog! run (str "review:" (:id task)))
      (if (str/includes? feature "refined by mock")
        {:ok? true
         :control :pass
         :message "RESULT: pass\nmock review passed"}
        {:ok? true
         :control :needs-refine
         :message "RESULT: needs_refine\nmock review requested refine"}))

    :refine
    (do
      (spit (str (fs/path (:worktree-dir run) "feature.txt"))
            (str "refined by mock at " (util/now) "\n")
            :append true)
      (append-worklog! run (str "refine:" (:id task)))
      {:ok? true
       :message "mock refine completed"})))
