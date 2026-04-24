# Runtime Decision

Date: 2026-04-24
Repo: vibe-flow

Decision

- Treat workflow execution more like a POD: lightweight, disposable, and short-lived.
- Do not design or implement heavyweight `pause` / `resume` / `abort` semantics for individual runs.
- If `mgr-start` or a worker process is interrupted, treat that run as disposable rather than resumable.
- A killed or interrupted unfinished run should later be marked dead / discarded / cleaned up, not resumed in place.
- Re-dispatch should create a fresh run and fresh worktree instead of trying to continue an interrupted one.

Implications

- `task` remains the durable unit.
- `mgr_run` and `run` records remain durable audit trail.
- Worktrees are disposable execution sandboxes.
- Process interruption is not a product-level workflow control surface.

Priority

- Do not prioritize run-control features.
- Prioritize better observability for long-running workflow execution, especially clearer foreground logging and run-level status output from `mgr-start`.
- Prioritize lightweight dead-run detection / cleanup semantics.
- Prioritize the real completion gap: how a successful candidate commit is integrated back into the main repo branch.

Operator Experience

- `mgr-start` should be observable while it is running.
- Users should be able to see which task was selected, which `mgr` decision was made, which worker/run was launched, and whether the loop is idle, running, succeeded, or failed.
- Lack of `pause` / `resume` does not remove the need for strong runtime visibility.

Product Direction

- Introduce a flexible frontend surface so users can inspect tasks, runs, manager decisions, worker progress, and workflow state without relying on raw EDN files or local directory inspection.
- The frontend should optimize for simple everyday use while still exposing enough detail for debugging and governance review.
- CLI remains important for execution and automation, but routine inspection should become much easier through a dedicated UI.

Task Organization And Scheduling

- Flat task lists are not enough for real usage; the product needs a higher-level grouping model so users can manage multiple separate goals without reading one long stream of unrelated tasks.
- Introduce `goal` as the higher-level business objective or product outcome.
- Introduce `track` as the execution-planning unit under a goal.
- A `goal` may contain multiple `track`s.
- A `track` may represent either a set of independent peer tasks or a chain / graph of dependent tasks.
- Keep `task_type` focused on execution protocol for a single task, not cross-task orchestration semantics.
- Keep `task` as the smallest durable execution unit.
- Cross-task execution behavior should be driven primarily by `track`, not by `task_type`.

Role Boundaries

- `goal` answers why a set of tasks belongs together.
- `track` answers how a set of tasks should be advanced together.
- `task_type` answers how an individual task is executed.
- `task` remains the durable work unit that produces and records concrete workflow progress.

Track Policy Direction

- `track` should be able to express whether tasks are independent, serial, or dependency-driven.
- `track` should be able to express whether a downstream task should inherit the previous task output commit or start from the main repo head.
- Example future policy directions include:
  - `:execution-mode :independent`
  - `:execution-mode :serial`
  - `:execution-mode :dag`
  - `:input-head-policy :repo-head`
  - `:input-head-policy :previous-task-output`
  - `:input-head-policy :track-head`
- Task-level dependency fields may still be needed, but the scheduling policy should be owned by the enclosing `track`.

Architecture Direction For Parallel Feature Development

- Introduce more inversion-of-control only at a few intentional high-volatility extension seams.
- Prefer governed provider contracts plus explicit registries over free-form runtime registration.
- The main target seams are:
  - product CLI providers
  - worker launcher providers
  - mgr runtime providers
  - workflow scheduling policy providers
  - later, frontend read-model or query providers if needed
- `system` and `workflow.control` should evolve toward facade/orchestration roles that depend on governed registries instead of directly depending on every concrete implementation.
- Stable low-level substrate modules such as stores, paths, repo helpers, and support utilities should remain direct and simple rather than being providerized.

Anti-Abuse Constraints

- Do not allow open-ended or side-effect-based provider registration.
- Do not use classpath scanning or implicit self-registration as the source of truth for shipped product capabilities.
- Every provider family must be explicitly whitelisted and governed.
- Governance should validate provider contracts, provider ownership, and allowed provider families.
- Provider modules should implement narrow contracts only and must not absorb unrelated workflow, CLI, or persistence logic.
- New provider families should be rare design-level changes, not casual per-feature abstractions.
- The project should support governed extension seams, not a generic plugin free-for-all.

IoC Clarification

- Inversion of control is the architectural principle that high-level modules should not directly own concrete implementation selection and wiring.
- `registry + provider` is a practical implementation pattern for IoC in this project, but it is not the full definition of IoC.
- Other IoC styles exist in general, such as dependency injection or plugin hosts, but they are not the preferred model here.
- For this codebase, the intended meaning of IoC is:
- move concrete implementation selection out of facade and orchestration hotspots
- expose governed provider contracts
- resolve implementations through explicit registries
- So the project should treat `registry + provider + governance` as the preferred local realization of IoC.

Pre-Coding Flexibility Principle

- Keep flexibility in the conversation and pre-coding phase.
- Keep specificity in the formal task and code-execution phase.
- Exploration, ambiguity, reframing, spike thinking, and intent refinement should happen before formal task execution starts.
- Once work is handed into formal task execution, the task should be concrete about goal, scope, constraints, and success criteria.
- Workers should consume clarified work rather than carrying broad exploratory ambiguity during implementation.
- The product should avoid mixing open-ended discovery with governed code execution in the same execution surface.
