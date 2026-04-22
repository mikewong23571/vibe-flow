(ns vibe-flow.governance.checks-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [vibe-flow.governance.checks :as checks]
   [vibe-flow.governance.ns-inspect :as ns-inspect]))

(deftest expected-namespace-follows-path
  (testing "namespace expectation matches standard clojure path conventions"
    (is (= "vibe-flow.governance.checks-test"
           (ns-inspect/expected-namespace
            "test"
            (io/file "test/vibe_flow/governance/checks_test.clj"))))))

(deftest governance-checks-pass-on-current-layout
  (testing "current repo layout satisfies machine-enforced governance rules"
    (is (empty? (filter #(= :error (:severity %)) (checks/all-issues))))))
