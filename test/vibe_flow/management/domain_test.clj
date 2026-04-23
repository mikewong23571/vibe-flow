(ns vibe-flow.management.domain-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [vibe-flow.management.domain :as domain]
   [vibe-flow.management.task-type :as task-type-manager]
   [vibe-flow.platform.target.install :as install]
   [vibe-flow.target.install-test :as install-fixture]))

(use-fixtures :each install-fixture/with-fake-toolchain-command)

(deftest create-and-load-collections-and-tasks
  (let [target-root (install-fixture/init-git-target! (install-fixture/make-temp-dir))]
    (try
      (install/install! target-root)
      (task-type-manager/create-task-type! target-root :impl)
      (testing "collection and task creation persist minimal domain records"
        (let [collection (domain/create-collection!
                          target-root
                          {:id "impl-backlog"
                           :task-type :impl
                           :name "Implementation backlog"})
              task (domain/create-task!
                    target-root
                    {:id "impl-task-1"
                     :collection-id "impl-backlog"
                     :task-type :impl
                     :goal "Implement the feature."
                     :scope ["Edit src/ only."]
                     :constraints ["Do not change workflow metadata."]
                     :success-criteria ["Feature behavior is implemented."]})]
          (is (= "impl-backlog" (:id collection)))
          (is (= :impl (:task-type collection)))
          (is (= "impl-task-1" (:id task)))
          (is (= "impl-backlog" (:collection-id task)))
          (is (= :impl (:task-type task)))
          (is (= 1 (count (domain/list-collections target-root))))
          (is (= 1 (count (domain/list-tasks target-root))))
          (is (= collection (domain/inspect-collection target-root "impl-backlog")))
          (is (= task (domain/inspect-task target-root "impl-task-1")))))

      (testing "task validation rejects missing required fields from task_type schema"
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"missing required fields"
             (domain/create-task!
              target-root
              {:id "impl-task-invalid"
               :collection-id "impl-backlog"
               :task-type :impl
               :goal "Incomplete task"
               :scope ["Only scope present."]
               :constraints ["Still missing success criteria."]}))))

      (testing "string and keyword task_type ids bind to the same installed package"
        (let [string-collection (domain/create-collection!
                                 target-root
                                 {:id "impl-string-backlog"
                                  :task-type "impl"
                                  :name "String-backed backlog"})
              task (domain/create-task!
                    target-root
                    {:id "impl-task-string-binding"
                     :collection-id "impl-string-backlog"
                     :task-type :impl
                     :goal "Normalize task_type ids."
                     :scope ["Accept CLI-style string ids."]
                     :constraints ["Keep stored ids canonical."]
                     :success-criteria ["Task persists successfully."]})]
          (is (= :impl (:task-type string-collection)))
          (is (= :impl (:task-type task)))
          (is (= :impl (:task-type (domain/inspect-collection target-root "impl-string-backlog"))))
          (is (= :impl (:task-type (domain/inspect-task target-root "impl-task-string-binding"))))))

      (testing "task creation rejects duplicate task ids instead of overwriting existing state"
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"Task already exists"
             (domain/create-task!
              target-root
              {:id "impl-task-1"
               :collection-id "impl-backlog"
               :task-type :impl
               :goal "Attempt to overwrite the existing task."
               :scope ["Edit src/ only."]
               :constraints ["Do not change workflow metadata."]
               :success-criteria ["Feature behavior is implemented."]})))
        (is (= "Implement the feature."
               (:goal (domain/inspect-task target-root "impl-task-1")))))

      (testing "collection creation rejects duplicate ids instead of overwriting existing state"
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"Collection already exists"
             (domain/create-collection!
              target-root
              {:id "impl-backlog"
               :task-type :impl
               :name "Overwritten backlog"})))
        (is (= "Implementation backlog"
               (:name (domain/inspect-collection target-root "impl-backlog")))))

      (testing "task validation enforces task_type binding with its collection"
        (task-type-manager/create-task-type! target-root :review)
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"must match its collection task_type"
             (domain/create-task!
              target-root
              {:id "review-task-wrong-binding"
               :collection-id "impl-backlog"
               :task-type :review
               :goal "Review work."
               :scope ["Inspect current diff."]
               :constraints ["Do not modify artifacts."]
               :success-criteria ["A review result exists."]}))))

      (testing "domain ids reject path-like values before hitting the filesystem"
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"Collection :id must use only letters, numbers"
             (domain/create-collection!
              target-root
              {:id "../../system/install"
               :task-type :impl
               :name "Escaped backlog"})))
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"Task :id must use only letters, numbers"
             (domain/create-task!
              target-root
              {:id "../escaped-task"
               :collection-id "impl-backlog"
               :task-type :impl
               :goal "Attempt path traversal."
               :scope ["Edit src/ only."]
               :constraints ["Do not change workflow metadata."]
               :success-criteria ["Feature behavior is implemented."]})))
        (is (= 2 (count (domain/list-collections target-root))))
        (is (= 2 (count (domain/list-tasks target-root)))))
      (finally
        (install-fixture/delete-tree! target-root)))))
