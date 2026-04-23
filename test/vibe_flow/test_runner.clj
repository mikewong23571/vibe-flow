(ns vibe-flow.test-runner
  (:require
   [clojure.test :as test]
   [vibe-flow.core-test]
   [vibe-flow.governance.checks-test]
   [vibe-flow.management.domain-test]
   [vibe-flow.management.task-type-test]
   [vibe-flow.runtime.launcher-test]
   [vibe-flow.target.install-test]
   [vibe-flow.workflow.control-test]))

(defn -main [& _]
  (let [{:keys [fail error]}
        (test/run-tests 'vibe-flow.core-test
                        'vibe-flow.governance.checks-test
                        'vibe-flow.management.domain-test
                        'vibe-flow.management.task-type-test
                        'vibe-flow.runtime.launcher-test
                        'vibe-flow.target.install-test
                        'vibe-flow.workflow.control-test)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
