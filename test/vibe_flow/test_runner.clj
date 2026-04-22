(ns vibe-flow.test-runner
  (:require
   [clojure.test :as test]
   [vibe-flow.governance.checks-test]))

(defn -main [& _]
  (let [{:keys [fail error]} (test/run-tests 'vibe-flow.governance.checks-test)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
