(ns vibe-flow.platform.target.install
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [vibe-flow.platform.state.system-store :as system-store]
   [vibe-flow.platform.support.time :as time]
   [vibe-flow.platform.target.paths :as paths]
   [vibe-flow.platform.target.repo :as repo]))

(def gitignore-begin "# >>> vibe-flow workflow >>>")
(def gitignore-end "# <<< vibe-flow workflow <<<")

(def gitignore-block
  (str gitignore-begin "\n"
       "/.workflow/local/\n"
       gitignore-end "\n"))

(defn ensure-directory! [path]
  (.mkdirs (io/file path))
  path)

(defn target-layout [target-root]
  (paths/layout-map target-root))

(defn layout-record [target-root]
  {:layout-version paths/layout-version
   :updated-at (time/now)
   :state-layout
   {:system-root (str (paths/system-root target-root))
    :definitions-root (str (paths/definitions-root target-root))
    :domain-root (str (paths/domain-root target-root))
    :local-root (str (paths/local-root target-root))}
   :registries
   {:root (str (paths/registries-root target-root))}})

(defn install-record [target-root existing]
  (let [now (time/now)]
    {:install-version 1
     :status :installed
     :installed-at (or (:installed-at existing) now)
     :updated-at now
     :target-root (str (paths/resolve-target-root target-root))
     :workflow-root (str (paths/workflow-root target-root))
     :layout-version paths/layout-version
     :target-layout (target-layout target-root)}))

(defn target-record [target-root existing]
  {:target-root (str (paths/resolve-target-root target-root))
   :repo-kind :git
   :installed-at (or (:installed-at existing) (time/now))
   :current-branch (repo/current-branch target-root)
   :current-head (repo/current-head target-root)
   :workflow-root (str (paths/workflow-root target-root))})

(defn install-gitignore! [target-root]
  (let [path (io/file target-root ".gitignore")
        existing (if (.exists path) (slurp path) "")
        pattern (re-pattern
                 (str "(?s)"
                      (java.util.regex.Pattern/quote gitignore-begin)
                      ".*?"
                      (java.util.regex.Pattern/quote gitignore-end)
                      "\\n?"))
        cleaned (str/replace existing pattern "")
        base (if (str/blank? cleaned) "" (str (str/trimr cleaned) "\n\n"))]
    (spit path (str base gitignore-block))))

(defn materialize-layout! [target-root]
  (doseq [dir (paths/materialized-directories target-root)]
    (ensure-directory! dir))
  (install-gitignore! target-root)
  (target-layout target-root))

(defn reconcile! [target-root]
  (repo/require-git-repo! target-root)
  (materialize-layout! target-root)
  (let [existing-install (system-store/load-install target-root)
        existing-target (system-store/load-target target-root)
        install (install-record target-root existing-install)
        target (target-record target-root existing-target)
        layout (layout-record target-root)]
    (system-store/save-install! target-root install)
    (system-store/save-target! target-root target)
    (system-store/save-layout! target-root layout)
    {:install install
     :target target
     :layout layout}))

(defn install! [target-root]
  (reconcile! target-root))

(defn install-blueprint []
  {:install! install!
   :reconcile! reconcile!
   :load-install system-store/load-install
   :load-target system-store/load-target
   :load-layout system-store/load-layout})
