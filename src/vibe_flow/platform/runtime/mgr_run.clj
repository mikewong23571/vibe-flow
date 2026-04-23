(ns vibe-flow.platform.runtime.mgr-run
  (:require
   [clojure.java.io :as io]
   [vibe-flow.definition.task-type :as task-type]
   [vibe-flow.platform.support.time :as time]
   [vibe-flow.platform.target.paths :as paths]))

(defn uuid []
  (str (java.util.UUID/randomUUID)))

(defn write-file! [path content]
  (let [file (io/file path)]
    (io/make-parents file)
    (spit file content)
    file))

(defn prepare-mgr-run! [target-root task launcher]
  (let [mgr-run-id (uuid)
        prompt-text (task-type/mgr-prompt target-root task {:id mgr-run-id})
        prompt-path (paths/mgr-run-prompt-path target-root mgr-run-id)
        output-path (paths/mgr-run-output-path target-root mgr-run-id)
        workdir (paths/mgr-run-workdir target-root mgr-run-id)]
    (.mkdirs (paths/mgr-run-dir target-root mgr-run-id))
    (.mkdirs workdir)
    (write-file! prompt-path prompt-text)
    {:id mgr-run-id
     :task-id (:id task)
     :task-type (:task-type task)
     :launcher launcher
     :workdir (str workdir)
     :prompt {:path (str prompt-path)
              :text prompt-text}
     :output {:path (str output-path)}
     :started-at (time/now)}))

(defn finalize-mgr-run! [mgr-run result]
  (let [output-path (get-in mgr-run [:output :path])]
    (write-file! output-path (:message result))
    (assoc mgr-run
           :ended-at (time/now)
           :error? (not (:ok? result))
           :output (assoc (:output mgr-run)
                          :text (:message result))
           :result result)))
