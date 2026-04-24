# Task State And Runtime Split

## Decision

Task domain records under `.workflow/state/domain/tasks/` are shared workflow
state and may be committed. They should contain product-meaningful task
definition and progress fields only.

Local task runtime summaries live under `.workflow/local/task_runtime/` and are
ignored with the rest of `.workflow/local/`. These records may contain local run
ids, manager run ids, launcher outputs, counters, and machine-local paths.

## Domain Task Fields

The shared task record owns:

* `:id`
* `:collection-id`
* `:task-type`
* `:goal`
* `:scope`
* `:constraints`
* `:success-criteria`
* `:stage`
* `:repo-head`
* `:created-at`
* `:updated-at`
* `:review-count`

## Local Runtime Fields

The local task runtime record owns:

* `:latest-run`
* `:latest-mgr-run`
* `:latest-worktree`
* `:latest-worker`
* `:latest-worker-output`
* `:latest-worker-control`
* `:latest-review-output`
* `:latest-mgr-decision`
* `:latest-mgr-output`
* `:error-output`
* `:mgr-count`
* `:run-count`

## Read Model

Workflow control and product inspection surfaces may hydrate a task by merging
the domain task with its local runtime summary. Low-level task persistence stays
domain-only, and `task-store/save-task!` strips runtime fields defensively before
writing `.workflow/state/domain/tasks/<task-id>.edn`.
