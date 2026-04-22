(ns vibe-flow.platform.support.time)

(defn now []
  (str (java.time.Instant/now)))
