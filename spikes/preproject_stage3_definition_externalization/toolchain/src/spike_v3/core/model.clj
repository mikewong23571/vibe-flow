(ns spike-v3.core.model)

(def max-run-count 6)

(def max-review-count 3)

(defn terminal-stage? [stage]
  (contains? #{:done :error} stage))

(defn task-overrun? [task]
  (or (>= (:run-count task 0) max-run-count)
      (and (= :awaiting-refine (:stage task))
           (>= (:review-count task 0) max-review-count))))

(defn next-stage [worker result]
  (cond
    (not (:ok? result)) :error
    (= worker :impl) :awaiting-review
    (= worker :refine) :awaiting-review
    (= worker :review)
    (case (:control result)
      :pass :done
      :needs-refine :awaiting-refine
      :error :error
      :unknown :error)
    :else :error))
