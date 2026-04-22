(ns vibe-flow.governance.checks
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [vibe-flow.governance.manifest :as manifest]
   [vibe-flow.governance.ns-inspect :as ns-inspect]
   [vibe-flow.governance.rules :as rules]))

(defn file-exists? [path]
  (.exists (io/file path)))

(defn directory? [path]
  (.isDirectory (io/file path)))

(defn list-visible-children [dir]
  (->> (.listFiles (io/file dir))
       (filter some?)
       (remove #(str/starts-with? (.getName %) "."))
       sort))

(defn clojure-source-files []
  (for [root rules/governed-roots
        :when (directory? root)
        file (file-seq (io/file root))
        :when (and (.isFile file)
                   (str/ends-with? (.getName file) ".clj"))]
    {:root root
     :file file}))

(defn governed-directories []
  (for [root rules/governed-roots
        :when (directory? root)
        dir (file-seq (io/file root))
        :when (.isDirectory dir)]
    dir))

(defn whitelisted-empty-directory? [dir]
  (contains? rules/empty-directory-whitelist
             (ns-inspect/normalize-path dir)))

(defn line-count [file]
  (with-open [reader (io/reader file)]
    (count (line-seq reader))))

(defn issue [rule-id severity path message]
  (merge {:severity severity
          :path path
          :message message}
         (rules/rule-metadata rule-id)))

(defn manifest-entry [ns-name]
  (manifest/module-entry ns-name))

(defn project-layout-issues []
  (for [path rules/required-project-paths
        :when (not (file-exists? path))]
    (issue :project-layout
           :error
           path
           (str "Missing required project path `" path "`."))))

(defn pre-commit-issues []
  (let [path ".pre-commit-config.yaml"]
    (cond
      (not (file-exists? path))
      [(issue :pre-commit
              :error
              path
              "Missing .pre-commit-config.yaml.")]

      (not (str/includes? (slurp path) rules/pre-commit-entry))
      [(issue :pre-commit
              :error
              path
              (str "Expected pre-commit hook entry `" rules/pre-commit-entry "`."))]

      :else
      [])))

(defn namespace-issues []
  (mapcat
   (fn [file]
     (let [{:keys [root file]} file
           path (ns-inspect/normalize-path file)
           declared (ns-inspect/declared-namespace file)
           expected (ns-inspect/expected-namespace root file)]
       (cond
         (nil? declared)
         [(issue :namespace-match
                 :error
                 path
                 "Missing namespace declaration.")]

         (not= declared expected)
         [(issue :namespace-match
                 :error
                 path
                 (str "Namespace `" declared "` does not match expected `" expected "`.") )]

         :else
         [])))
   (clojure-source-files)))

(defn module-manifest-issues []
  (let [manifested (set (keys (manifest/namespaces-map)))
        discovered (->> (clojure-source-files)
                        (map (fn [{:keys [file]}] (ns-inspect/declared-namespace file)))
                        set)
        missing (sort (remove manifested discovered))
        stale (sort (remove discovered manifested))]
    (concat
     (for [ns-name missing]
       (issue :module-manifest
              :error
              ns-name
              (str "Namespace `" ns-name "` is not registered in the governance manifest.")))
     (for [ns-name stale]
       (issue :module-manifest
              :warning
              ns-name
              (str "Manifest entry exists for `" ns-name "` but no matching source file was found."))))))

(defn module-contract-issues []
  (mapcat
   (fn [{:keys [file]}]
     (let [path (ns-inspect/normalize-path file)
           ns-name (ns-inspect/declared-namespace file)
           entry (manifest-entry ns-name)
           required-base [:layer :volatility :module-kind :complexity :responsibility]
           missing-base (remove #(contains? entry %) required-base)
           layer (:layer entry)
           volatility (:volatility entry)
           complexity (:complexity entry)]
       (concat
        (for [field missing-base]
          (issue :module-contract
                 :error
                 path
                 (str "Manifest entry for `" ns-name "` is missing required field `" field "`.")))
        (when (and entry (not (contains? rules/machine-governed-layers layer)))
          [(issue :module-contract
                  :error
                  path
                  (str "Manifest entry for `" ns-name "` uses unknown layer `" layer "`."))])
        (when (and entry (= :high volatility) (not (contains? entry :split-axis)))
          [(issue :module-contract
                  :error
                  path
                  (str "High-volatility namespace `" ns-name "` must declare `:split-axis`."))])
        (when (and entry (= :complex complexity) (not (contains? entry :split-axis)))
          [(issue :module-contract
                  :error
                  path
                  (str "Complex namespace `" ns-name "` must declare `:split-axis`."))])
        (when (and entry (= :low volatility) (not (contains? entry :stability-role)))
          [(issue :module-contract
                  :error
                  path
                  (str "Low-volatility namespace `" ns-name "` must declare `:stability-role`."))]))))
   (clojure-source-files)))

(defn sample-boundary-issues []
  (mapcat
   (fn [{:keys [file]}]
     (let [path (ns-inspect/normalize-path file)
           ns-name (ns-inspect/declared-namespace file)
           entry (manifest-entry ns-name)
           layer (:layer entry)]
       (cond
         (and (str/starts-with? path "src/")
              (= layer :sample-debug))
         [(issue :sample-boundary
                 :error
                 path
                 "Formal source tree must not contain sample/debug modules.")]

         (or (str/includes? path "/sample/")
             (str/includes? path "/debug/"))
         [(issue :sample-boundary
                 :error
                 path
                 "Formal source path must not encode sample/debug modules inside src/test.")]

         :else
         [])))
   (clojure-source-files)))

(defn layer-dependency-issues []
  (mapcat
   (fn [{:keys [file]}]
     (let [path (ns-inspect/normalize-path file)
           ns-name (ns-inspect/declared-namespace file)
           entry (manifest-entry ns-name)
           layer (:layer entry)
           allowed (get rules/allowed-layer-dependencies layer #{})]
       (mapcat
        (fn [dep]
          (let [dep-entry (manifest-entry dep)
                dep-layer (:layer dep-entry)]
            (cond
              (nil? dep-entry)
              [(issue :layer-dependency
                      :error
                      path
                      (str "Project dependency `" dep "` is not registered in the governance manifest."))]

              (contains? allowed dep-layer)
              []

              :else
              [(issue :layer-dependency
                      :error
                      path
                      (str "Namespace `" ns-name "` in layer `" layer
                           "` cannot depend on `" dep "` in layer `" dep-layer "`.") )])))
        (ns-inspect/required-libs file))))
   (clojure-source-files)))

(defn file-length-issues []
  (mapcat
   (fn [file]
     (let [{:keys [file]} file
           path (ns-inspect/normalize-path file)
           lines (line-count file)
           warn-threshold (:warn rules/file-line-thresholds)
           error-threshold (:error rules/file-line-thresholds)]
       (cond
         (> lines error-threshold)
         [(issue :file-length
                 :error
                 path
                 (str "File has " lines " lines; limit is " error-threshold "."))]

         (> lines warn-threshold)
         [(issue :file-length
                 :warning
                 path
                 (str "File has " lines " lines; warning threshold is " warn-threshold "."))]

         :else
         [])))
   (clojure-source-files)))

(defn empty-directory-issues []
  (mapcat
   (fn [dir]
     (let [path (ns-inspect/normalize-path dir)
           entries (list-visible-children dir)]
       (cond
         (whitelisted-empty-directory? dir)
         []

         (empty? entries)
         [(issue :empty-directory
                 :error
                 path
                 "Governed directories must not be empty unless explicitly whitelisted.")]

         :else
         [])))
   (governed-directories)))

(defn directory-size-issues []
  (mapcat
   (fn [dir]
     (let [path (ns-inspect/normalize-path dir)
           entries (count (list-visible-children dir))
           warn-threshold (:warn rules/directory-entry-thresholds)
           error-threshold (:error rules/directory-entry-thresholds)]
       (cond
         (> entries error-threshold)
         [(issue :directory-size
                 :error
                 path
                 (str "Directory has " entries " entries; limit is " error-threshold "."))]

         (> entries warn-threshold)
         [(issue :directory-size
                 :warning
                 path
                 (str "Directory has " entries " entries; warning threshold is " warn-threshold "."))]

         :else
         [])))
   (governed-directories)))

(defn all-issues []
  (->> [(project-layout-issues)
        (pre-commit-issues)
        (module-manifest-issues)
        (module-contract-issues)
        (namespace-issues)
        (sample-boundary-issues)
        (layer-dependency-issues)
        (file-length-issues)
        (empty-directory-issues)
        (directory-size-issues)]
       (apply concat)
       (sort-by (juxt :severity :path :id))))
