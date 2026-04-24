# Repository Guidelines

## Project Structure & Module Organization

This is a Clojure CLI project for a task-type driven coding-agent workflow toolchain. Production code lives in `src/vibe_flow/`, with layers split by responsibility: `platform/support`, `platform/target`, `platform/state`, `definition`, `management`, `platform/runtime`, `workflow`, and the product surface in `system.clj`. Tests live in `test/vibe_flow/` and should mirror the source namespace being exercised. Runtime and definition resources live under `resources/`, especially `resources/vibe_flow/governance/module_manifest.edn`. Historical exploration belongs in `spikes/`; do not route new production behavior through spike code. Architecture and governance context is documented in `docs/design.md`, `docs/architecture.md`, and `docs/governance.md`.

## Build, Test, and Development Commands

- `clojure -M:test`: runs the custom `clojure.test` suite via `vibe-flow.test-runner`.
- `clojure -M:fmt`: formats Clojure and governed EDN files with `cljfmt`.
- `clojure -M:fmt-check`: checks formatting without modifying files; this runs in pre-commit.
- `clojure -M:lint`: runs `clj-kondo` over `src`, `test`, and `dev`; this runs in pre-commit.
- `clojure -M:governance`: checks required project layout, namespace paths, pre-commit wiring, module manifest entries, and governance rules.
- `clojure -M:cli install`: installs the local `vibe-flow` command and toolchain files.
- `clojure -M:cli install-target --target /path/to/repo`: initializes a target repository for workflow state.
- `clojure -M:bootstrap`: bootstraps this checkout as a self-hosted workflow target.

Run `fmt-check`, `lint`, `governance`, and tests before opening a PR.

## Coding Style & Naming Conventions

Use idiomatic Clojure with two-space indentation and small, data-oriented functions. Let `cljfmt` own whitespace and namespace formatting, and let `clj-kondo` flag unused requires and static issues. Namespace names use kebab-case, while file paths use underscores to match Clojure conventions, for example `vibe-flow.platform.state.task-store` in `src/vibe_flow/platform/state/task_store.clj`. Keep lower layers independent from higher layers: support/state modules should not absorb workflow-control logic. When adding a namespace, add or update its entry in the governance manifest.

## Testing Guidelines

Tests use `clojure.test`. Place test namespaces under `test/vibe_flow/` with a `-test` suffix and add new namespaces to `test/vibe_flow/test_runner.clj`, otherwise `clojure -M:test` will not run them. Prefer focused unit tests for stores, management functions, runtime decisions, and governance behavior. Cover durable filesystem changes with temporary directories rather than modifying the repository workflow state.

## Commit & Pull Request Guidelines

Recent commits use short imperative subjects, sometimes with a conventional prefix, such as `Add workflow state stores`, `docs: add formal implementation task`, and `chore: expand clojure gitignore`. Keep subjects specific and under one line. PRs should describe the behavior change, list test/governance commands run, link related tasks or design notes, and include screenshots only when a visible workflow artifact changes.

## Security & Configuration Tips

Do not commit `.workflow/local/` machine-local runtime artifacts, credentials, or generated install artifacts. Shared durable state under `.workflow/state/domain/` and `.workflow/state/definitions/` is normally commit-worthy, while `.workflow/state/system/` records should be judged by whether they encode machine-local installation details. Treat `.workflow/` layout changes as product protocol changes and justify them against `docs/governance.md`.
