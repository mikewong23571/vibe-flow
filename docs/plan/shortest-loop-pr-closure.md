# Shortest Loop PR Closure Plan

## Goal

Define the shortest meaningful end-to-end workflow closure for the current system.

The target is not full autonomous merge. The target is:

```text
task
  -> worktree commits
  -> task reaches :done
  -> final candidate commit becomes a branch
  -> branch is pushed
  -> PR is created
```

## Current State

Today the workflow can already:

* create a run worktree
* let a worker produce candidate commits in that worktree
* carry the same task through `impl`, `review`, and `refine`
* persist the resulting candidate commit as the task `:repo-head`

But it still stops short of a real closure because:

* the main repository branch does not advance
* no formal integration branch is created
* no PR is opened automatically

So the system currently behaves more like a candidate-change generator than a fully
closed task workflow.

## Why Use PR Closure First

The first closure target should be branch + PR, not direct auto-merge.

Reasons:

* `impl` is often followed by `review` and `refine`
* opening a PR after every intermediate run would create noisy half-finished PRs
* direct auto-merge would skip an important review boundary too early in the product
* a PR is a natural handoff surface for review, audit, and later governance

So the correct closure point is not "worker run finished". The correct closure point
is "task reached successful terminal state".

## Proposed Product Behavior

### On successful terminal completion

When a task reaches `:done`:

1. read the task's final `:repo-head`
2. create a durable branch ref that points at that commit
3. push that branch to the remote
4. create a PR against the main branch
5. persist integration metadata back onto the task

Example persisted integration metadata shape:

```clojure
{:integration
 {:status :pr-open
  :branch "vibe-flow/task/<task-id>"
  :base-branch "main"
  :head "<commit>"
  :remote "origin"
  :pr-url "https://..."
  :opened-at "<timestamp>"}}
```

### On failure before branch or PR creation

If branch creation, push, or PR creation fails:

* the task should not silently remain "done but not actually exposed"
* the failure should be recorded explicitly
* the user should be able to see which integration step failed

Example status directions:

* `:branch-created`
* `:push-failed`
* `:pr-open`
* `:integration-error`

## Scope Boundary

This shortest-loop plan intentionally does not require:

* direct auto-merge into `main`
* heavyweight run-control features
* generalized multi-remote or multi-forge abstraction
* early support for every Git hosting provider

The first version only needs a practical, reviewable closure path.

## Suggested First Implementation

### Step 1. Treat task `:done` as the only PR trigger

Do not push or open PRs for intermediate `impl` / `review` / `refine` runs.

### Step 2. Materialize a branch from the final task head

Use the final task `:repo-head` as the source of truth for the candidate change.
Promote that commit into a durable named branch such as:

* `vibe-flow/task/<task-id>`

### Step 3. Push to the default remote

Default to `origin` and fail explicitly if it is unavailable.

### Step 4. Create the PR

Use a single formal integration path first, such as GitHub CLI if it is already a
supported local dependency.

### Step 5. Persist integration state

Store branch, push, and PR metadata on the task so the system and future frontend
can report the true end-to-end state.

## Relationship To Other Planned Work

This shortest-loop closure depends on:

* candidate integration being treated as a first-class product concern
* better observability so users can see closure progress

It does not need to wait for:

* `goal` and `track` hierarchy
* broad CLI surface expansion
* fully generalized provider registries

## Decision

The shortest meaningful workflow closure for the current system should be:

```text
task reaches :done
  -> promote final candidate commit to branch
  -> push branch
  -> open PR
```

That is the smallest product step that turns internal worktree output into a
reviewable, externally visible workflow result.
