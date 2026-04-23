(ns vibe-flow.target.install-test
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [vibe-flow.platform.state.system-store :as system-store]
   [vibe-flow.platform.target.install :as install]
   [vibe-flow.platform.target.paths :as paths]
   [vibe-flow.platform.toolchain.paths :as toolchain-paths]))

(def ^:dynamic *fake-toolchain-command* nil)

(defn shell! [dir & args]
  (let [{:keys [exit out err]}
        (apply shell/sh (concat args [:dir (str dir)]))]
    (when-not (zero? exit)
      (throw (ex-info "Shell command failed."
                      {:dir (str dir)
                       :args args
                       :exit exit
                       :out out
                       :err err})))
    out))

(defn make-temp-dir []
  (.toFile (java.nio.file.Files/createTempDirectory
            "vibe-flow-install-test"
            (make-array java.nio.file.attribute.FileAttribute 0))))

(defn delete-tree! [file]
  (when (.exists file)
    (doseq [child (reverse (file-seq file))]
      (.delete child))))

(defn init-git-target! [target-root]
  (spit (io/file target-root "README.md") "# target\n")
  (shell! target-root "git" "init" "-b" "main")
  (shell! target-root "git" "add" "README.md")
  (shell! target-root
          "git" "-c" "user.name=vibe-flow-test"
          "-c" "user.email=vibe-flow-test@example.com"
          "commit" "-m" "Initial target commit")
  target-root)

(defn with-fake-toolchain-command [test-fn]
  (let [root (make-temp-dir)
        command-file (io/file root "bin" toolchain-paths/command-name)]
    (try
      (.mkdirs (.getParentFile command-file))
      (spit command-file "#!/usr/bin/env bash\nexit 0\n")
      (.setExecutable command-file true)
      (binding [*fake-toolchain-command* (str (.getCanonicalFile command-file))]
        (with-redefs [toolchain-paths/shim-path (fn [] command-file)]
          (test-fn)))
      (finally
        (delete-tree! root)))))

(use-fixtures :each with-fake-toolchain-command)

(deftest install-materializes-workflow-layout-and-system-models
  (let [target-root (init-git-target! (make-temp-dir))]
    (try
      (testing "install creates the formal workflow layout and writes system records"
        (let [{:keys [install target layout toolchain]} (install/install! target-root)]
          (is (= :installed (:status install)))
          (is (= paths/layout-version (:layout-version install)))
          (is (= (str (paths/resolve-target-root target-root)) (:target-root install)))
          (is (= :git (:repo-kind target)))
          (is (= paths/layout-version (:layout-version layout)))
          (is (= :user-installed-command (:kind toolchain)))
          (is (= *fake-toolchain-command* (:command toolchain)))
          (is (system-store/installed? target-root))
          (is (= install (system-store/load-install target-root)))
          (is (= target (system-store/load-target target-root)))
          (is (= layout (system-store/load-layout target-root)))
          (is (= toolchain (system-store/load-toolchain target-root)))
          (doseq [dir (paths/materialized-directories target-root)]
            (is (.isDirectory (io/file dir))))
          (is (str/includes? (slurp (io/file target-root ".gitignore"))
                             "/.workflow/local/"))))

      (testing "reconcile restores missing runtime layout without resetting installed-at"
        (let [installed-at (:installed-at (system-store/load-install target-root))]
          (.delete (paths/runs-root target-root))
          (install/reconcile! target-root)
          (is (.isDirectory (paths/runs-root target-root)))
          (is (= installed-at
                 (:installed-at (system-store/load-install target-root))))))
      (finally
        (delete-tree! target-root)))))

(deftest install-rejects-missing-toolchain-command
  (let [target-root (init-git-target! (make-temp-dir))
        missing-command (io/file target-root "missing-vibe-flow")]
    (try
      (with-redefs [toolchain-paths/shim-path (fn [] missing-command)]
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"Installed vibe-flow command is unavailable"
             (install/install! target-root))))
      (is (not (.exists (paths/workflow-root target-root))))
      (is (not (.exists (io/file target-root ".gitignore"))))
      (finally
        (delete-tree! target-root)))))
