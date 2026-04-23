(ns vibe-flow.platform.toolchain.install
  (:require
   [clojure.java.io :as io]
   [vibe-flow.platform.support.edn :as edn]
   [vibe-flow.platform.support.shell :as shell]
   [vibe-flow.platform.support.time :as time]
   [vibe-flow.platform.toolchain.paths :as paths]))

(def install-assets
  ["deps.edn" "src" "resources"])

(defn canonical-file [path]
  (.getCanonicalFile ^java.io.File (io/file path)))

(defn delete-tree! [file]
  (let [^java.io.File file* (io/file file)]
    (when (.exists file*)
      (doseq [^java.io.File child (reverse (file-seq file*))]
        (.delete child)))))

(defn ensure-directory! [path]
  (.mkdirs ^java.io.File (io/file path))
  path)

(defn copy-file! [source target]
  (let [^java.io.File target-file (io/file target)]
    (io/make-parents target-file)
    (with-open [in (io/input-stream source)
                out (io/output-stream target-file)]
      (io/copy in out))
    target-file))

(defn copy-tree! [source-dir target-dir]
  (let [^java.io.File source-dir-file (io/file source-dir)]
    (doseq [^java.io.File child (file-seq source-dir-file)
            :when (not= (.getCanonicalPath child)
                        (.getCanonicalPath source-dir-file))]
      (let [relative (.relativize (.toPath source-dir-file)
                                  (.toPath child))
            ^java.io.File target (io/file target-dir (str relative))]
        (if (.isDirectory child)
          (.mkdirs target)
          (copy-file! child target))))))

(defn copy-asset! [source-root relative-path install-root]
  (let [^java.io.File source (io/file source-root relative-path)
        target (io/file install-root relative-path)]
    (when-not (.exists source)
      (throw (ex-info "Toolchain install asset is missing."
                      {:source-root (str (canonical-file source-root))
                       :asset relative-path
                       :path (str source)})))
    (if (.isDirectory source)
      (copy-tree! source target)
      (copy-file! source target))))

(defn ensure-source-root! [source-root]
  (let [^java.io.File root (canonical-file source-root)]
    (doseq [asset install-assets]
      (when-not (.exists ^java.io.File (io/file root asset))
        (throw (ex-info "Toolchain source root is missing required install assets."
                        {:source-root (str root)
                         :asset asset}))))
    root))

(defn install-record [source-root]
  {:kind :user-install
   :installed-at (time/now)
   :source-root (str (canonical-file source-root))
   :toolchain-root (str (paths/toolchain-root))
   :shim-path (str (paths/shim-path))
   :command paths/command-name})

(defn shim-script [toolchain-root]
  (str "#!/usr/bin/env bash\n"
       "set -euo pipefail\n"
       "export VIBE_FLOW_CLI_CWD=\"${PWD}\"\n"
       "cd -- " (shell/quote-arg (str toolchain-root)) "\n"
       "exec " (shell/join-command ["clojure" "-M:cli"]) " \"$@\"\n"))

(defn write-shim! [toolchain-root]
  (let [^java.io.File shim (paths/shim-path)]
    (ensure-directory! (paths/bin-root))
    (spit shim (shim-script toolchain-root))
    (.setExecutable shim true)
    shim))

(defn make-staging-root []
  (.toFile (java.nio.file.Files/createTempDirectory
            "vibe-flow-toolchain-install"
            (make-array java.nio.file.attribute.FileAttribute 0))))

(defn stage-install-source! [source-root]
  (let [staging-root (make-staging-root)]
    (doseq [asset install-assets]
      (copy-asset! source-root asset staging-root))
    staging-root))

(defn install! [source-root]
  (let [^java.io.File source-root* (ensure-source-root! source-root)
        ^java.io.File toolchain-root (paths/toolchain-root)
        in-place-install? (= (.getCanonicalPath source-root*)
                             (.getCanonicalPath ^java.io.File (canonical-file toolchain-root)))
        staged-root (when in-place-install?
                      (stage-install-source! source-root*))
        copy-root (or staged-root source-root*)]
    (try
      (delete-tree! toolchain-root)
      (ensure-directory! toolchain-root)
      (doseq [asset install-assets]
        (copy-asset! copy-root asset toolchain-root))
      (let [record (install-record source-root*)]
        (edn/write-edn! (paths/install-record-path) record)
        (write-shim! toolchain-root)
        record)
      (finally
        (when staged-root
          (delete-tree! staged-root))))))
