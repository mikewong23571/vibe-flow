(ns spike-v3.management.task-type-manager
  (:require
   [babashka.fs :as fs]
   [clojure.string :as str]
   [spike-v3.definition.task-type :as task-type]
   [spike-v3.support.paths :as paths]
   [spike-v3.support.util :as util]))

(def layout-version 1)

(defn task-type-keyword [task-type]
  (if (keyword? task-type)
    task-type
    (keyword task-type)))

(defn require-installed-target! [target-root]
  (when-not (fs/exists? (paths/install-path target-root))
    (throw (ex-info "target is not installed yet"
                    {:target-root (str target-root)
                     :path (str (paths/install-path target-root))})))
  target-root)

(defn load-task-types-registry [target-root]
  (util/read-edn (paths/task-types-registry-path target-root) []))

(defn save-task-types-registry! [target-root entries]
  (util/write-edn! (paths/task-types-registry-path target-root)
                   (vec (sort-by :id entries))))

(defn package-files [task-type-dir]
  (->> (file-seq (java.io.File. (str task-type-dir)))
       (filter #(.isFile %))
       (map fs/path)
       (remove #(= "meta.edn" (str (fs/file-name %))))
       (sort-by #(str (fs/relativize task-type-dir %)))))

(defn package-checksum [task-type-dir]
  (->> (package-files task-type-dir)
       (map (fn [path]
              (str (fs/relativize task-type-dir path)
                   ":"
                   (util/sha256 (slurp (str path))))))
       (str/join "\n")
       util/sha256))

(defn task-type-meta-record [target-root task-type source]
  (let [task-type-id (task-type-keyword task-type)
        task-type-dir (paths/task-type-dir target-root task-type-id)
        existing (util/read-edn (paths/task-type-meta-path target-root task-type-id) nil)
        now (util/now)]
    {:id task-type-id
     :version (or (:version existing) 1)
     :status (or (:status existing) :active)
     :managed-by :spike-v3.toolchain
     :layout-version layout-version
     :source source
     :installed-at (or (:installed-at existing) now)
     :updated-at now
     :checksum (package-checksum task-type-dir)}))

(defn registry-entry [target-root task-type meta]
  {:id (:id meta)
   :kind :task-type
   :path (str (paths/task-type-dir target-root task-type))
   :version (:version meta)
   :status (:status meta)
   :layout-version (:layout-version meta)
   :source (:source meta)
   :installed-at (:installed-at meta)
   :updated-at (:updated-at meta)})

(defn register-task-type! [target-root task-type meta]
  (let [task-type-id (task-type-keyword task-type)
        entry (registry-entry target-root task-type-id meta)
        registry (->> (load-task-types-registry target-root)
                      (remove #(= (:id %) task-type-id))
                      (cons entry))]
    (save-task-types-registry! target-root registry)
    entry))

(defn write-task-type-meta! [target-root task-type meta]
  (util/write-edn! (paths/task-type-meta-path target-root (task-type-keyword task-type)) meta))

(defn register-installed-task-type! [target-root task-type source]
  (let [task-type-id (task-type-keyword task-type)
        meta (task-type-meta-record target-root task-type-id source)]
    (write-task-type-meta! target-root task-type-id meta)
    (register-task-type! target-root task-type-id meta)
    meta))

(defn list-task-types [target-root]
  (require-installed-target! target-root)
  (load-task-types-registry target-root))

(defn inspect-task-type [target-root task-type]
  (require-installed-target! target-root)
  (let [task-type-id (task-type-keyword task-type)
        definition (task-type/load-task-type target-root task-type-id)
        meta (task-type/load-task-type-meta target-root task-type-id)
        registry-entry (some #(when (= (:id %) task-type-id) %)
                             (load-task-types-registry target-root))
        task-type-dir (paths/task-type-dir target-root task-type-id)]
    {:task-type task-type-id
     :definition definition
     :meta meta
     :registry registry-entry
     :layout {:task-type-dir (str task-type-dir)
              :task-type-path (str (paths/task-type-path target-root task-type-id))
              :meta-path (str (paths/task-type-meta-path target-root task-type-id))
              :prompts (->> (fs/glob task-type-dir "prompts/*")
                            (map str)
                            sort
                            vec)
              :hooks (->> (fs/glob task-type-dir "hooks/*")
                          (map str)
                          sort
                          vec)}}))

(defn skeleton-task-type-record [task-type]
  (let [task-type-id (task-type-keyword task-type)]
    {:task-type task-type-id
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
                   :prompt-inputs {:latest_review {:task-field :latest-review-output
                                                  :default "none"}}}}))

(def prompt-skeletons
  {:mgr
   (str
    "You are the mgr agent for {{task_type}}.\n"
    "Choose the next workflow decision and call the workflow CLI exactly once.\n")
   :impl
   (str
    "You are the impl worker for {{task_type}}.\n"
    "Task goal: {{goal}}\n"
    "Work inside {{worktree_root}} only.\n")
   :review
   (str
    "You are the review worker for {{task_type}}.\n"
    "Reply with RESULT: pass or RESULT: needs_refine.\n")
   :refine
   (str
    "You are the refine worker for {{task_type}}.\n"
    "Use the latest review feedback to make the smallest fix.\n")})

(defn render-prompt-skeleton [task-type prompt-name]
  (util/render-template (get prompt-skeletons prompt-name)
                        {:task_type (name (task-type-keyword task-type))}))

(defn create-task-type! [target-root task-type]
  (require-installed-target! target-root)
  (let [task-type-id (task-type-keyword task-type)
        task-type-dir (paths/task-type-dir target-root task-type-id)]
    (when (fs/exists? task-type-dir)
      (throw (ex-info "task_type already exists"
                      {:task-type task-type-id
                       :path (str task-type-dir)})))
    (fs/create-dirs (fs/path task-type-dir "prompts"))
    (fs/create-dirs (fs/path task-type-dir "hooks"))
    (util/write-edn! (paths/task-type-path target-root task-type-id)
                     (skeleton-task-type-record task-type-id))
    (doseq [prompt-name [:mgr :impl :review :refine]]
      (util/write-file! (fs/path task-type-dir "prompts" (str (name prompt-name) ".txt"))
                        (render-prompt-skeleton task-type-id prompt-name)))
    (util/write-file! (fs/path task-type-dir "README.md")
                      (str "# " (name task-type-id) "\n\n"
                           "Target-managed task_type created by preproject_stage3_definition_externalization.\n"))
    (register-installed-task-type! target-root
                                   task-type-id
                                   {:kind :target-created
                                    :created-in-target (str target-root)})
    {:ok? true
     :task-type task-type-id
     :path (str task-type-dir)}))
