(ns vibe-flow.governance.checks-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [vibe-flow.governance.checks :as checks]
   [vibe-flow.governance.ns-inspect :as ns-inspect]
   [vibe-flow.governance.rules :as rules]))

(deftest expected-namespace-follows-path
  (testing "namespace expectation matches standard clojure path conventions"
    (is (= "vibe-flow.governance.checks-test"
           (ns-inspect/expected-namespace
            "test"
            (io/file "test/vibe_flow/governance/checks_test.clj"))))))

(deftest governance-checks-pass-on-current-layout
  (testing "current repo layout satisfies machine-enforced governance rules"
    (is (empty? (filter #(= :error (:severity %)) (checks/all-issues))))))

(deftest empty-directory-rule-requires-whitelist
  (let [root (.toFile (java.nio.file.Files/createTempDirectory "vibe-flow-governance-empty-dir" (make-array java.nio.file.attribute.FileAttribute 0)))
        empty-dir (io/file root "src_tree/empty")
        whitelisted-dir (io/file root "src_tree/whitelisted")
        non-empty-dir (io/file root "src_tree/non_empty")]
    (try
      (.mkdirs empty-dir)
      (.mkdirs whitelisted-dir)
      (.mkdirs non-empty-dir)
      (spit (io/file non-empty-dir "owned.clj") "(ns owned)")
      (testing "empty directories fail unless explicitly whitelisted"
        (let [whitelist #{(ns-inspect/normalize-path whitelisted-dir)}]
          (with-redefs [rules/governed-roots [(ns-inspect/normalize-path root)]
                        rules/empty-directory-whitelist whitelist]
            (let [issues (checks/empty-directory-issues)]
              (is (= 1 (count issues)))
              (is (= (ns-inspect/normalize-path empty-dir) (:path (first issues))))
              (is (= :empty-directory (:id (first issues))))))))
      (finally
        (doseq [path [(io/file non-empty-dir "owned.clj")
                      non-empty-dir
                      whitelisted-dir
                      empty-dir
                      (io/file root "src_tree")
                      root]]
          (when (.exists path)
            (.delete path)))))))
