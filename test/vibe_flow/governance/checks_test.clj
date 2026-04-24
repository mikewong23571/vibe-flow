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

(deftest product-cli-governance-rule-requires-governed-system-functions
  (let [root (.toFile (java.nio.file.Files/createTempDirectory "vibe-flow-governance-cli" (make-array java.nio.file.attribute.FileAttribute 0)))
        system-file (io/file root "system.clj")]
    (try
      (spit system-file "(ns vibe-flow.system)\n(defn governed-cli-provider-whitelist [] nil)\n")
      (testing "product CLI governance fails when governed system functions are missing"
        (with-redefs [rules/product-cli-governance-path (ns-inspect/normalize-path system-file)
                      rules/product-cli-governance-required-defns ["governed-cli-provider-whitelist"
                                                                   "governed-cli-registry-contract"]]
          (let [issues (checks/product-cli-governance-issues)]
            (is (= 1 (count issues)))
            (is (= :product-cli-governance (:id (first issues))))
            (is (re-find #"governed-cli-registry-contract" (:message (first issues)))))))
      (testing "product CLI governance passes once all required system functions exist"
        (spit system-file
              (str "(ns vibe-flow.system)\n"
                   "(defn governed-cli-provider-whitelist [] nil)\n"
                   "(defn governed-cli-registry-contract [] nil)\n"))
        (with-redefs [rules/product-cli-governance-path (ns-inspect/normalize-path system-file)
                      rules/product-cli-governance-required-defns ["governed-cli-provider-whitelist"
                                                                   "governed-cli-registry-contract"]]
          (is (empty? (checks/product-cli-governance-issues)))))
      (finally
        (doseq [path [system-file root]]
          (when (.exists path)
            (.delete path)))))))

(deftest markdown-location-rule-keeps-documents-in-governed-areas
  (let [root (.toFile (java.nio.file.Files/createTempDirectory "vibe-flow-governance-docs" (make-array java.nio.file.attribute.FileAttribute 0)))
        docs-dir (io/file root "docs" "plan")
        spikes-dir (io/file root "spikes" "experiment")
        src-dir (io/file root "src" "feature")
        root-readme (io/file root "README.md")
        root-agents (io/file root "AGENTS.md")
        nested-agents (io/file src-dir "AGENTS.md")
        planned-doc (io/file docs-dir "decision.md")
        spike-doc (io/file spikes-dir "README.md")
        stray-doc (io/file root "runtime-decision.md")
        uppercase-stray-doc (io/file root "runtime-decision.MD")]
    (try
      (.mkdirs docs-dir)
      (.mkdirs spikes-dir)
      (.mkdirs src-dir)
      (spit root-readme "# Project\n")
      (spit root-agents "# Agents\n")
      (spit nested-agents "# Feature agents\n")
      (spit planned-doc "# Decision\n")
      (spit spike-doc "# Spike\n")
      (spit stray-doc "# Runtime decision\n")
      (spit uppercase-stray-doc "# Runtime decision\n")
      (testing "only whitelisted root docs and dedicated document roots are allowed"
        (let [issues (checks/markdown-location-issues root)
              issue-paths (set (map :path issues))]
          (is (= 2 (count issues)))
          (is (every? #(= :markdown-location (:id %)) issues))
          (is (= #{"runtime-decision.md" "runtime-decision.MD"} issue-paths))))
      (finally
        (doseq [path [uppercase-stray-doc stray-doc spike-doc planned-doc nested-agents
                      root-agents root-readme src-dir spikes-dir docs-dir (io/file root "src")
                      (io/file root "spikes") (io/file root "docs") root]]
          (when (.exists path)
            (.delete path)))))))
