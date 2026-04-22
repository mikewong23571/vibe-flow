(ns spike-v2.toolchain
  (:require
   [spike-v2.install :as install]
   [spike-v2.paths :as paths]
   [spike-v2.task-store :as task-store]
   [spike-v2.task-type :as task-type]
   [spike-v2.target-repo :as target-repo]
   [spike-v2.util :as util]
   [spike-v2.workflow :as workflow]))

(defn usage! []
  (println "Usage:")
  (println "  bb -m spike-v2.toolchain init-sample-target [--target <path>]")
  (println "  bb -m spike-v2.toolchain install [--target <path>]")
  (println "  bb -m spike-v2.toolchain seed-sample [--target <path>]")
  (println "  bb -m spike-v2.toolchain task-type-prepare-run --target <path> --task-id <id> --worker <impl|review|refine> --launcher <mock|codex>")
  (println "  bb -m spike-v2.toolchain mgr-advance --target <path> --task-id <id> --mgr-run-id <id> --worker-launcher <mock|codex> --decision <impl|review|refine|done|error> --reason <text>")
  (println "  bb -m spike-v2.toolchain run-once [worker-launcher] [mgr-launcher] [--target <path>]")
  (println "  bb -m spike-v2.toolchain run-loop [worker-launcher] [mgr-launcher] [max-steps] [--target <path>]")
  (println "  bb -m spike-v2.toolchain show-state [--target <path>]")
  (println "  bb -m spike-v2.toolchain smoke [--target <path>]"))

(defn parse-launcher [s]
  (case s
    "mock" :mock
    "codex" :codex
    :mock))

(defn parse-kv-args [args]
  (loop [remaining args
         parsed {}]
    (if (empty? remaining)
      parsed
      (let [[k v & more] remaining]
        (when-not (and k v)
          (throw (ex-info "expected key/value args" {:args args})))
        (recur more (assoc parsed k v))))))

(defn extract-target [args]
  (loop [remaining args
         positional []
         target nil]
    (if (empty? remaining)
      {:target-root (paths/resolve-target-root target)
       :args positional}
      (let [arg (first remaining)]
        (if (= "--target" arg)
          (recur (nnext remaining) positional (second remaining))
          (recur (next remaining) (conj positional arg) target))))))

(defn parse-run-once-args [args]
  (let [{:keys [target-root args]} (extract-target args)
        [arg1 arg2] args]
    {:target-root target-root
     :worker-launcher (parse-launcher arg1)
     :mgr-launcher (parse-launcher (or arg2 arg1))}))

(defn parse-run-loop-args [args]
  (let [{:keys [target-root args]} (extract-target args)
        [arg1 arg2 arg3] args]
    (if (#{"mock" "codex"} arg2)
      {:target-root target-root
       :worker-launcher (parse-launcher arg1)
       :mgr-launcher (parse-launcher arg2)
       :max-steps (Long/parseLong (or arg3 "4"))}
      {:target-root target-root
       :worker-launcher (parse-launcher arg1)
       :mgr-launcher (parse-launcher arg1)
       :max-steps (Long/parseLong (or arg2 "4"))})))

(defn -main [& raw-args]
  (let [[cmd & more] raw-args]
    (case cmd
      "init-sample-target" (let [{:keys [target-root]} (extract-target more)]
                             (target-repo/ensure-sample-target! target-root)
                             (println "Initialized sample target git repo at" (str target-root)))
      "install" (let [{:keys [target-root]} (extract-target more)]
                  (install/install! target-root))
      "seed-sample" (let [{:keys [target-root]} (extract-target more)]
                      (task-store/seed-sample! target-root))
      "task-type-prepare-run" (let [opts (parse-kv-args more)
                                    target-root (paths/resolve-target-root (get opts "--target"))
                                    task (task-store/load-task target-root (get opts "--task-id"))
                                    _ (when-not task
                                        (throw (ex-info "task not found for prepare_run"
                                                        {:task-id (get opts "--task-id")
                                                         :target-root (str target-root)})))
                                    result (task-type/prepare-run-spec target-root
                                                                       task
                                                                       (keyword (get opts "--worker"))
                                                                       (parse-launcher (get opts "--launcher")))]
                                (println (util/pp-str result)))
      "mgr-advance" (let [opts (parse-kv-args more)
                          target-root (paths/resolve-target-root (get opts "--target"))
                          summary (workflow/mgr-advance! target-root
                                                         (get opts "--task-id")
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
      "run-once" (let [{:keys [target-root worker-launcher mgr-launcher]}
                       (parse-run-once-args more)]
                   (workflow/run-once! target-root worker-launcher mgr-launcher))
      "run-loop" (let [{:keys [target-root worker-launcher mgr-launcher max-steps]}
                       (parse-run-loop-args more)]
                   (workflow/run-loop! target-root worker-launcher mgr-launcher max-steps))
      "show-state" (let [{:keys [target-root]} (extract-target more)]
                     (workflow/show-state! target-root))
      "smoke" (let [{:keys [target-root]} (extract-target more)]
                (workflow/smoke! target-root))
      (usage!))))
