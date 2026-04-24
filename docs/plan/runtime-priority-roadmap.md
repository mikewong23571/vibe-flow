# Runtime Priority Roadmap

## Goal

Record the current optimization priorities for the formal workflow product surface,
with emphasis on reaching a usable autonomous loop before broadening the management
surface or extension system.

## Priority Order

### P0. Close the real workflow loop

These items unblock the shortest meaningful product loop.

#### 1. Candidate integration out of worktrees

Today a successful task produces candidate commits inside a run worktree, but the
main repository branch does not advance automatically. This is the largest product
gap because the system can produce work but cannot yet finish the job in a way that
becomes easy for a user to review or land.

#### 2. Better runtime observability

`mgr-start` currently provides too little foreground visibility. Users need to see:

* which task was selected
* which `mgr` decision was made
* which worker/run was launched
* whether the loop is idle, running, succeeded, or failed

Without this, even a correct runtime feels unreliable.

#### 3. Dead-run cleanup for interrupted execution

The current decision is to treat runs like lightweight disposable PODs rather than
introducing heavyweight `pause` / `resume` / `abort`. That means interrupted or
killed unfinished runs must be recognized as dead and cleaned up so they do not
block future scheduling.

### P1. Improve orchestration and product usability

These items should come immediately after the shortest loop is reliable.

#### 4. Introduce `track` as the orchestration unit

Flat task lists are not enough. A `track` should own cross-task scheduling policy
for a batch of related tasks, including whether tasks are:

* independent
* serial
* dependency-driven

It should also own policy such as whether downstream tasks inherit previous task
output or restart from the main repository head.

#### 5. Introduce a frontend inspection surface

Users should not need to inspect raw EDN records or local directories to understand
the system. A frontend should make it easy to inspect:

* goals and tracks
* tasks
* runs and `mgr_run`s
* manager decisions
* worker progress
* workflow status over time

#### 6. Governed extension seams for high-volatility modules

The project should support parallel feature development by moving a few hotspot
areas toward governed provider contracts plus explicit registries. The intended
extension seams are:

* product CLI providers
* worker launcher providers
* `mgr` runtime providers
* workflow scheduling policy providers

This is an architectural enabler, not the first product milestone.

### P2. Broaden management structure and polish

These items matter, but they should follow the main loop and orchestration work.

#### 7. Introduce `goal` as a higher-level grouping object

`goal` should answer why a set of tracks belongs together and provide a cleaner
management view than a flat task backlog.

#### 8. Expand formal CLI management surfaces

This includes governed resource-family command families such as:

* `tasks`
* `collections`
* `task-types`
* `agent-homes`
* `doctor`

#### 9. Expand governance around provider contracts

Once provider registries become real, governance should machine-check provider
ownership, contract fields, allowed provider families, and facade-vs-implementation
boundaries.

## Execution Order

Recommended near-term order:

1. candidate integration out of worktrees
2. runtime observability
3. dead-run cleanup
4. `track` orchestration model
5. frontend inspection/read-model surface
6. governed high-volatility extension seams
7. `goal` layer
8. broader CLI management surface

## Why This Order

The project should first become a trustworthy autonomous workflow loop:

* it should complete work in a reviewable way
* users should be able to see what it is doing
* interruptions should not leave the system blocked

Only after that should the project invest heavily in broader management views,
goal hierarchy, and more generalized extension architecture.
