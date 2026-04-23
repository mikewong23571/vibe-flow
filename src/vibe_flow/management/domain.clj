(ns vibe-flow.management.domain
  (:require
   [clojure.string :as str]
   [vibe-flow.definition.task-type :as task-type]
   [vibe-flow.platform.state.collection-store :as collection-store]
   [vibe-flow.platform.state.system-store :as system-store]
   [vibe-flow.platform.state.task-store :as task-store]
   [vibe-flow.platform.support.time :as time]
   [vibe-flow.platform.target.paths :as paths]))

(defn blankish? [value]
  (or (nil? value)
      (and (string? value) (str/blank? value))
      (and (sequential? value) (empty? value))))

(def safe-id-pattern #"^[A-Za-z0-9][A-Za-z0-9._-]*$")

(defn validate-domain-id! [field-name value]
  (when (blankish? value)
    (throw (ex-info (str field-name " must be non-blank.")
                    {:field field-name
                     :value value})))
  (when-not (and (string? value)
                 (re-matches safe-id-pattern value))
    (throw (ex-info (str field-name " must use only letters, numbers, '.', '_' or '-'.")
                    {:field field-name
                     :value value})))
  value)

(defn ensure-installed-target! [target-root]
  (when-not (system-store/installed? target-root)
    (throw (ex-info "Target is not installed yet."
                    {:target-root (str (paths/resolve-target-root target-root))
                     :path (str (paths/install-path target-root))})))
  target-root)

(defn normalize-task-type [record]
  (if (blankish? (:task-type record))
    record
    (update record :task-type task-type/task-type-id)))

(defn validate-collection! [target-root collection]
  (let [collection* (normalize-task-type collection)]
    (ensure-installed-target! target-root)
    (validate-domain-id! "Collection :id" (:id collection*))
    (when (blankish? (:task-type collection*))
      (throw (ex-info "Collection must declare :task-type."
                      {:collection-id (:id collection*)})))
    (task-type/load-task-type target-root (:task-type collection*))
    collection*))

(defn validate-task-binding! [target-root task]
  (let [task* (normalize-task-type task)
        collection-id (:collection-id task*)
        collection (some-> (collection-store/load-collection target-root collection-id)
                           normalize-task-type)]
    (validate-domain-id! "Task :collection-id" collection-id)
    (when-not collection
      (throw (ex-info "Task collection does not exist."
                      {:task-id (:id task*)
                       :collection-id collection-id})))
    (when (not= (:task-type collection) (:task-type task*))
      (throw (ex-info "Task task_type must match its collection task_type."
                      {:task-id (:id task*)
                       :collection-id collection-id
                       :collection-task-type (:task-type collection)
                       :task-type (:task-type task*)})))
    task*))

(defn validate-task! [target-root task]
  (let [task* (normalize-task-type task)]
    (ensure-installed-target! target-root)
    (validate-domain-id! "Task :id" (:id task*))
    (when (blankish? (:task-type task*))
      (throw (ex-info "Task must declare :task-type."
                      {:task-id (:id task*)})))
    (task-type/load-task-type target-root (:task-type task*))
    (->> task*
         (validate-task-binding! target-root)
         (task-type/validate-task-definition! target-root))))

(defn create-collection! [target-root attrs]
  (let [collection (validate-collection! target-root
                                         (merge {:created-at (time/now)
                                                 :updated-at (time/now)}
                                                attrs))]
    (when (collection-store/load-collection target-root (:id collection))
      (throw (ex-info "Collection already exists."
                      {:collection-id (:id collection)})))
    (collection-store/save-collection! target-root collection)))

(defn create-task! [target-root attrs]
  (let [task (validate-task! target-root
                             (merge {:stage :todo
                                     :run-count 0
                                     :mgr-count 0
                                     :review-count 0
                                     :created-at (time/now)
                                     :updated-at (time/now)}
                                    attrs))]
    (when (task-store/load-task target-root (:id task))
      (throw (ex-info "Task already exists."
                      {:task-id (:id task)})))
    (task-store/save-task! target-root task)))

(defn list-collections [target-root]
  (ensure-installed-target! target-root)
  (collection-store/load-collections target-root))

(defn list-tasks [target-root]
  (ensure-installed-target! target-root)
  (task-store/load-tasks target-root))

(defn inspect-collection [target-root collection-id]
  (ensure-installed-target! target-root)
  (collection-store/load-collection target-root collection-id))

(defn inspect-task [target-root task-id]
  (ensure-installed-target! target-root)
  (task-store/load-task target-root task-id))
