(ns vibe-flow.management.task-type
  (:require
   [clojure.java.io :as io]
   [vibe-flow.definition.task-type :as definition]
   [vibe-flow.platform.state.system-store :as system-store]
   [vibe-flow.platform.support.edn :as edn]
   [vibe-flow.platform.support.time :as time]
   [vibe-flow.platform.target.paths :as paths]
   [vibe-flow.management.task-type-prompts :as prompts]))

(def prompt-files prompts/prompt-files)
(def prompt-skeletons prompts/prompt-skeletons)
(def legacy-prompt-skeletons prompts/legacy-prompt-skeletons)

(defn ensure-installed-target! [target-root]
  (when-not (system-store/installed? target-root)
    (throw (ex-info "Target is not installed yet."
                    {:target-root (str (paths/resolve-target-root target-root))
                     :path (str (paths/install-path target-root))})))
  target-root)

(defn write-file! [path content]
  (let [file (io/file path)]
    (io/make-parents file)
    (spit file content)
    file))

(defn render-template [template replacements]
  (prompts/render-template template replacements))

(defn rendered-prompt [task-type prompt-name prompt-set]
  (prompts/rendered-prompt task-type prompt-name prompt-set))

(defn registry-path [target-root]
  (paths/task-types-registry-path target-root))

(defn load-registry [target-root]
  (ensure-installed-target! target-root)
  (edn/read-edn (registry-path target-root) []))

(defn save-registry! [target-root entries]
  (edn/write-edn! (registry-path target-root)
                  (vec (sort-by :id entries))))

(defn task-type-meta-record [target-root task-type source existing]
  {:id task-type
   :version (or (:version existing) 1)
   :status (or (:status existing) :active)
   :managed-by :vibe-flow.toolchain
   :layout-version paths/layout-version
   :source source
   :installed-at (or (:installed-at existing) (time/now))
   :updated-at (time/now)
   :task-type-path (str (paths/task-type-path target-root task-type))})

(defn registry-entry [target-root task-type meta]
  {:id task-type
   :kind :task-type
   :path (str (paths/task-type-dir target-root task-type))
   :version (:version meta)
   :status (:status meta)
   :layout-version (:layout-version meta)
   :source (:source meta)
   :installed-at (:installed-at meta)
   :updated-at (:updated-at meta)})

(defn write-task-type-meta! [target-root task-type meta]
  (edn/write-edn! (paths/task-type-meta-path target-root task-type) meta))

(defn register-task-type! [target-root task-type meta]
  (let [entry (registry-entry target-root task-type meta)
        updated (->> (load-registry target-root)
                     (remove #(= (:id %) task-type))
                     (cons entry))]
    (save-registry! target-root updated)
    entry))

(defn register-installed-task-type! [target-root task-type source]
  (ensure-installed-target! target-root)
  (let [task-type* (definition/task-type-id task-type)
        _ (definition/load-task-type target-root task-type*)
        existing (edn/read-edn (paths/task-type-meta-path target-root task-type*) nil)
        meta (task-type-meta-record target-root task-type* source existing)]
    (write-task-type-meta! target-root task-type* meta)
    (register-task-type! target-root task-type* meta)
    meta))

(defn skeleton-task-type-record [task-type]
  {:task-type task-type
   :mgr-home :mgr_codex
   :workers {:todo :impl
             :awaiting-review :review
             :awaiting-refine :refine}
   :worker-homes {:mgr :mgr_codex
                  :impl :impl_codex
                  :review :review_codex
                  :refine :refine_codex}
   :prompts {:mgr "prompts/mgr.txt"
             :impl "prompts/impl.txt"
             :review "prompts/review.txt"
             :refine "prompts/refine.txt"}
   :task-schema {:required [:goal :scope :constraints :success-criteria]}
   :prepare-run {:input-head {:task-field :repo-head
                              :fallback :current-head}
                 :worktree-strategy :git-worktree
                 :prompt-inputs {}
                 :before {:kind :command
                          :hook "hooks/before_prepare_run"
                          :allowed-fields [:prompt-inputs]}}})

(defn prompt-path [target-root task-type prompt-name]
  (io/file (paths/task-type-prompts-dir target-root task-type)
           (get prompts/prompt-files prompt-name)))

(def hook-script
  "#!/usr/bin/env bash\nprintf '{}\\n'\n")

(defn create-task-type! [target-root task-type]
  (ensure-installed-target! target-root)
  (let [task-type* (definition/task-type-id task-type)
        task-type-dir (paths/task-type-dir target-root task-type*)
        hooks-dir (paths/task-type-hooks-dir target-root task-type*)
        hook-path (io/file hooks-dir "before_prepare_run")]
    (when (.exists task-type-dir)
      (throw (ex-info "task_type already exists."
                      {:task-type task-type*
                       :path (str task-type-dir)})))
    (.mkdirs (paths/task-type-prompts-dir target-root task-type*))
    (.mkdirs hooks-dir)
    (edn/write-edn! (paths/task-type-path target-root task-type*)
                    (skeleton-task-type-record task-type*))
    (doseq [[prompt-name template] prompts/prompt-skeletons]
      (write-file! (prompt-path target-root task-type* prompt-name)
                   (prompts/render-template template {:task_type (name task-type*)})))
    (write-file! hook-path hook-script)
    (.setExecutable hook-path true)
    (register-installed-task-type! target-root
                                   task-type*
                                   {:kind :target-created
                                    :created-in-target (str (paths/resolve-target-root target-root))})
    {:ok? true
     :task-type task-type*
     :path (str task-type-dir)}))

(defn managed-task-type? [target-root task-type]
  (= :vibe-flow.toolchain
     (:managed-by (edn/read-edn (paths/task-type-meta-path target-root task-type) nil))))

(defn refresh-generated-task-type! [target-root task-type source]
  (ensure-installed-target! target-root)
  (let [task-type* (definition/task-type-id task-type)]
    (when (managed-task-type? target-root task-type*)
      (let [refreshed? (reduce
                        (fn [changed? prompt-name]
                          (let [path (prompt-path target-root task-type* prompt-name)
                                expected (prompts/rendered-prompt task-type* prompt-name prompts/prompt-skeletons)
                                legacy (prompts/rendered-prompt task-type* prompt-name prompts/legacy-prompt-skeletons)
                                existing (when (.exists path) (slurp path))]
                            (cond
                              (= existing expected)
                              changed?

                              (or (nil? existing)
                                  (= existing legacy))
                              (do
                                (write-file! path expected)
                                true)

                              :else
                              changed?)))
                        false
                        (keys prompts/prompt-files))]
        (when refreshed?
          (register-installed-task-type! target-root task-type* source))
        refreshed?))))

(defn list-task-types [target-root]
  (load-registry target-root))

(defn inspect-task-type [target-root task-type]
  (ensure-installed-target! target-root)
  (let [task-type* (definition/task-type-id task-type)
        task-type-dir (paths/task-type-dir target-root task-type*)
        prompt-files (or (.listFiles (paths/task-type-prompts-dir target-root task-type*)) [])
        hook-files (or (.listFiles (paths/task-type-hooks-dir target-root task-type*)) [])
        registry-entry (some #(when (= (:id %) task-type*) %)
                             (load-registry target-root))]
    {:task-type task-type*
     :definition (definition/load-task-type target-root task-type*)
     :meta (definition/load-task-type-meta target-root task-type*)
     :registry registry-entry
     :layout {:task-type-dir (str task-type-dir)
              :task-type-path (str (paths/task-type-path target-root task-type*))
              :meta-path (str (paths/task-type-meta-path target-root task-type*))
              :prompts (->> prompt-files
                            (map #(.getPath %))
                            sort
                            vec)
              :hooks (->> hook-files
                          (map #(.getPath %))
                          sort
                          vec)}}))
