(ns vibe-flow.management.system-task-type-test
  (:require
   [clojure.edn :as clojure-edn]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [vibe-flow.management.task-type :as task-type-manager]
   [vibe-flow.platform.target.install :as install]
   [vibe-flow.platform.target.paths :as paths]
   [vibe-flow.system :as system]
   [vibe-flow.target.install-test :as install-fixture]))

(use-fixtures :each install-fixture/with-fake-toolchain-command)

(deftest system-task-type-management-surface-exposes-installed-task-types
  (let [target-root (install-fixture/init-git-target! (install-fixture/make-temp-dir))]
    (try
      (install/install! target-root)
      (task-type-manager/create-task-type! target-root :impl)
      (testing "system API routes to task_type list and inspect operations"
        (let [listed (system/list-task-types target-root)
              inspected (system/inspect-task-type target-root :impl)]
          (is (= 1 (count listed)))
          (is (= :impl (get-in listed [0 :id])))
          (is (= :task-type (get-in listed [0 :kind])))
          (is (= (str (paths/task-type-dir target-root :impl))
                 (get-in listed [0 :path])))
          (is (= paths/layout-version
                 (get-in listed [0 :layout-version])))
          (is (= :impl (get-in inspected [:definition :task-type])))
          (is (= :active (get-in inspected [:meta :status])))))

      (testing "CLI commands print the installed task_type registry and package inspection"
        (let [list-output (with-out-str
                            (system/-main "list-task-types"
                                          "--target" (str target-root)))
              inspect-output (with-out-str
                               (system/-main "inspect-task-type"
                                             "--target" (str target-root)
                                             "--task-type" "impl"))
              listed (clojure-edn/read-string list-output)
              inspected (clojure-edn/read-string inspect-output)]
          (is (= [:impl] (mapv :id listed)))
          (is (= :active (get-in inspected [:meta :status])))
          (is (= :task-type (get-in inspected [:registry :kind])))
          (is (= :impl (get-in inspected [:definition :task-type])))))

      (testing "inspect-task-type CLI requires a task_type id"
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"Missing required CLI option"
             (system/-main "inspect-task-type"
                           "--target" (str target-root)))))
      (finally
        (install-fixture/delete-tree! target-root)))))
