(ns vibe-flow.system
  (:require
   [vibe-flow.management.domain :as domain]
   [vibe-flow.management.task-type :as task-type-manager]
   [vibe-flow.platform.runtime.launcher :as launcher]
   [vibe-flow.platform.state.system-store :as system-store]
   [vibe-flow.platform.target.install :as install]
   [vibe-flow.workflow.control :as workflow-control]))

(defn system-blueprint []
  {:install {:install! install/install!
             :reconcile! install/reconcile!}
   :domain-management {:create-collection! domain/create-collection!
                       :create-task! domain/create-task!
                       :list-collections domain/list-collections
                       :list-tasks domain/list-tasks
                       :inspect-collection domain/inspect-collection
                       :inspect-task domain/inspect-task
                       :validate-task! domain/validate-task!}
   :task-type-management {:create! task-type-manager/create-task-type!
                          :list task-type-manager/list-task-types
                          :inspect task-type-manager/inspect-task-type
                          :register! task-type-manager/register-installed-task-type!}
   :runtime {:launch! launcher/launch!}
   :workflow-control {:select-runnable-task workflow-control/select-runnable-task
                      :create-mgr-run! workflow-control/create-mgr-run!
                      :advance-task! workflow-control/advance-task!
                      :run-once! workflow-control/run-once!
                      :run-loop! workflow-control/run-loop!}
   :system-state {:installed? system-store/installed?
                  :load-install system-store/load-install
                  :load-target system-store/load-target
                  :load-layout system-store/load-layout}})
