(ns vibe-flow.management.task-type-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [vibe-flow.definition.task-type :as definition]
   [vibe-flow.management.task-type :as task-type-manager]
   [vibe-flow.platform.support.edn :as edn]
   [vibe-flow.platform.target.install :as install]
   [vibe-flow.platform.target.paths :as paths]
   [vibe-flow.target.install-test :as install-fixture]))

(deftest create-list-and-inspect-task-type-package
  (let [target-root (install-fixture/init-git-target! (install-fixture/make-temp-dir))]
    (try
      (install/install! target-root)
      (testing "create materializes the managed package contract inside the target"
        (let [{:keys [ok? task-type path]} (task-type-manager/create-task-type! target-root :impl)]
          (is ok?)
          (is (= :impl task-type))
          (is (= (str (paths/task-type-dir target-root :impl)) path))
          (is (.exists (paths/task-type-path target-root :impl)))
          (is (.exists (paths/task-type-meta-path target-root :impl)))
          (is (.isDirectory (paths/task-type-prompts-dir target-root :impl)))
          (is (.isDirectory (paths/task-type-hooks-dir target-root :impl)))
          (is (.canExecute (io/file (definition/hook-path target-root :impl :before_prepare_run))))))

      (testing "list and inspect are sourced from the installed artifact in target"
        (let [registry (task-type-manager/list-task-types target-root)
              inspect (task-type-manager/inspect-task-type target-root :impl)]
          (is (= 1 (count registry)))
          (is (= :impl (get-in registry [0 :id])))
          (is (= :impl (:task-type inspect)))
          (is (= :impl (get-in inspect [:definition :task-type])))
          (is (= :active (get-in inspect [:meta :status])))
          (is (= :task-type (get-in inspect [:registry :kind])))
          (is (= (str (paths/task-type-dir target-root :impl))
                 (get-in inspect [:layout :task-type-dir])))
          (is (some #{(str (definition/prompt-path target-root :impl :mgr))}
                    (get-in inspect [:layout :prompts])))))

      (testing "definition helpers resolve installed prompt and hook paths from target"
        (is (= (str (paths/task-type-path target-root :impl))
               (:task-type-path (definition/load-task-type-meta target-root :impl))))
        (is (.exists (definition/prompt-path target-root :impl :impl)))
        (is (.exists (definition/hook-path target-root :impl :before_prepare_run))))

      (testing "definition helpers reject prompt paths that escape the task_type package"
        (let [task-type-path (paths/task-type-path target-root :impl)
              installed (edn/read-edn task-type-path nil)]
          (edn/write-edn! task-type-path
                          (assoc-in installed [:prompts :mgr] "../meta.edn"))
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"must stay within the task_type package"
               (definition/prompt-path target-root :impl :mgr)))))

      (testing "task_type ids reject qualified or path-like names before creating files"
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"task_type must be unqualified"
             (task-type-manager/create-task-type! target-root "pkg/impl")))
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"task_type must be unqualified"
             (task-type-manager/register-installed-task-type! target-root "pkg/../../evil" {:kind :manual})))
        (is (= 1 (count (task-type-manager/list-task-types target-root)))))
      (finally
        (install-fixture/delete-tree! target-root)))))

(deftest register-installed-task-type-requires-installed-target
  (let [target-root (install-fixture/make-temp-dir)]
    (try
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Target is not installed yet"
           (task-type-manager/register-installed-task-type!
            target-root
            :impl
            {:kind :manual})))
      (is (not (.exists (paths/task-type-meta-path target-root :impl))))
      (finally
        (install-fixture/delete-tree! target-root)))))

(deftest register-installed-task-type-rejects-missing-package
  (let [target-root (install-fixture/init-git-target! (install-fixture/make-temp-dir))]
    (try
      (install/install! target-root)
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"task_type is not installed"
           (task-type-manager/register-installed-task-type!
            target-root
            :ghost
            {:kind :manual})))
      (is (empty? (task-type-manager/list-task-types target-root)))
      (is (not (.exists (paths/task-type-meta-path target-root :ghost))))
      (finally
        (install-fixture/delete-tree! target-root)))))
