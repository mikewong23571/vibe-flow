(ns vibe-flow.platform.toolchain.paths
  (:require
   [clojure.java.io :as io]))

(def command-name "vibe-flow")

(defn getenv [name]
  (System/getenv name))

(defn xdg-data-home []
  (getenv "XDG_DATA_HOME"))

(defn home-dir []
  (or (getenv "HOME")
      (System/getProperty "user.home")))

(defn data-root []
  (if-let [xdg-data-home (xdg-data-home)]
    (io/file xdg-data-home "vibe-flow")
    (io/file (home-dir) ".local" "share" "vibe-flow")))

(defn toolchain-root []
  (io/file (data-root) "toolchain"))

(defn install-record-path []
  (io/file (toolchain-root) "install.edn"))

(defn bin-root []
  (io/file (home-dir) ".local" "bin"))

(defn shim-path []
  (io/file (bin-root) command-name))
