(ns spike-v3.sample.demo
  (:require
   [spike-v3.state.collection-store :as collection-store]
   [spike-v3.state.task-store :as task-store]
   [spike-v3.support.paths :as paths]
   [spike-v3.support.util :as util]
   [spike-v3.target.repo :as target-repo]))

(def sample-collection-id "sample-impl-collection")

(defn sample-collection []
  {:id sample-collection-id
   :task-type :impl
   :name "sample target repo tasks"
   :created-at (util/now)
   :updated-at (util/now)
   :task-ids ["sample-impl-task"]})

(defn sample-task [target-root]
  {:id "sample-impl-task"
   :collection-id sample-collection-id
   :task-type :impl
   :goal "Implement and refine a minimal change in feature.txt using externalized task_type definitions."
   :scope ["Edit only feature.txt in the target repository."
           "Do not modify workflow metadata or installed task_type artifacts."]
   :constraints ["Use the smallest concrete change that can pass review."
                 "Keep the workflow control path unchanged: mgr -> workflow CLI -> toolchain."
                 "Assume task_type definitions and prompts are already installed artifacts."]
   :success-criteria ["feature.txt contains an implementation change and a follow-up refinement."
                      "review can conclude with RESULT: pass."
                      "runtime files remain under .workflow/local while definitions remain under .workflow/state."]
   :stage :todo
   :repo-head (target-repo/current-head target-root)
   :latest-run nil
   :run-count 0
   :review-count 0
   :created-at (util/now)
   :updated-at (util/now)})

(defn seed! [target-root]
  (collection-store/save-collection! target-root (sample-collection))
  (task-store/save-task! target-root (sample-task target-root))
  (println "Seeded sample collection and task into" (str (paths/durable-root target-root))))
