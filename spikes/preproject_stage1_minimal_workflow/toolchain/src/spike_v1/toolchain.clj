(ns spike-v1.toolchain
  (:require
   [spike-v1.install :as install]
   [spike-v1.task-store :as task-store]
   [spike-v1.workflow :as workflow]))

(defn usage! []
  (println "Usage:")
  (println "  bb -m spike-v1.toolchain install")
  (println "  bb -m spike-v1.toolchain seed-sample")
  (println "  bb -m spike-v1.toolchain mgr-advance --task-id <id> --mgr-run-id <id> --worker-launcher <mock|codex> --decision <impl|review|refine|done|error> --reason <text>")
  (println "  bb -m spike-v1.toolchain run-once [worker-launcher] [mgr-launcher]")
  (println "  bb -m spike-v1.toolchain run-loop [worker-launcher] [mgr-launcher] [max-steps]")
  (println "  bb -m spike-v1.toolchain show-state")
  (println "  bb -m spike-v1.toolchain smoke"))

(defn parse-launcher [s]
  (case s
    "mock" :mock
    "codex" :codex
    :mock))

(defn parse-run-once-args [arg1 arg2]
  {:worker-launcher (parse-launcher arg1)
   :mgr-launcher (parse-launcher (or arg2 arg1))})

(defn parse-run-loop-args [arg1 arg2 arg3]
  (if (#{"mock" "codex"} arg2)
    {:worker-launcher (parse-launcher arg1)
     :mgr-launcher (parse-launcher arg2)
     :max-steps (Long/parseLong (or arg3 "4"))}
    {:worker-launcher (parse-launcher arg1)
     :mgr-launcher (parse-launcher arg1)
     :max-steps (Long/parseLong (or arg2 "4"))}))

(defn parse-kv-args [args]
  (loop [remaining args
         parsed {}]
    (if (empty? remaining)
      parsed
      (let [[k v & more] remaining]
        (when-not (and k v)
          (throw (ex-info "expected key/value args" {:args args})))
        (recur more (assoc parsed k v))))))

(defn -main [& args]
  (let [[cmd arg1 arg2 arg3 & more] args]
    (case cmd
      "install" (install/install!)
      "seed-sample" (task-store/seed-sample!)
      "mgr-advance" (let [opts (parse-kv-args (cons arg1 (cons arg2 (cons arg3 more))))
                          summary (workflow/mgr-advance! (get opts "--task-id")
                                                         (get opts "--mgr-run-id")
                                                         (parse-launcher (get opts "--worker-launcher"))
                                                         (keyword (get opts "--decision"))
                                                         (get opts "--reason"))]
                      (println (:message summary))
                      (println "task-id:" (:task-id summary))
                      (println "mgr-run-id:" (:mgr-run-id summary))
                      (when-let [run-id (:run-id summary)]
                        (println "run-id:" run-id))
                      (println "next-stage:" (:next-stage summary)))
      "run-once" (let [{:keys [worker-launcher mgr-launcher]} (parse-run-once-args arg1 arg2)]
                   (workflow/run-once! worker-launcher mgr-launcher))
      "run-loop" (let [{:keys [worker-launcher mgr-launcher max-steps]}
                       (parse-run-loop-args arg1 arg2 arg3)]
                   (workflow/run-loop! worker-launcher mgr-launcher max-steps))
      "show-state" (workflow/show-state!)
      "smoke" (workflow/smoke!)
      (usage!))))
