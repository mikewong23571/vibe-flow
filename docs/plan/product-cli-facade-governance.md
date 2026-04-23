# Product CLI Facade Governance Plan

## Goal

Keep the formal `vibe-flow` CLI extensible without letting `src/vibe_flow/system.clj`
grow into a business-logic module or an ad hoc command registry.

The command surface should evolve as a governed facade:

* `system.clj` remains the product-facing CLI facade
* command families are exposed through governed provider modules
* command exposure is controlled by a governed provider whitelist
* cross-cutting commands such as `doctor` stay explicit exceptions

## Problem

The product surface is a high-volatility area. New user-facing commands are easy to
add, but they are also easy to implement in the wrong layer.

Without a formal plan, the likely failure modes are:

* `system.clj` keeps accumulating resource-specific logic
* new commands bypass governance because routing and implementation are mixed
* singleton commands and resource-family commands drift into inconsistent naming
* product-surface changes become harder to inspect mechanically

## Design Direction

### 1. CLI commands are facades

`system.clj` should expose commands, but each command should delegate to a lower
module or a governed registry contract that delegates further downward.

`system.clj` is responsible for:

* parsing CLI arguments
* selecting a governed command from the whitelist
* formatting user-facing output
* routing into internal handlers

`system.clj` is not responsible for:

* task business rules
* collection business rules
* task_type lifecycle rules
* agent_home inspection logic
* doctor diagnostic implementation details

Facade adapters that exist under a future `vibe-flow.product.cli.*` namespace are
still adapters, not business-logic owners. Their job is to translate a user-facing
CLI shape into calls into lower-layer modules.

The actual behavior should stay in lower modules such as:

* `management.*` for collection/task/task_type lifecycle
* `platform.runtime.*` for runtime inspection concerns
* a dedicated diagnostic module for `doctor`

### 2. Prefer resource families over ad hoc verbs

The default product CLI shape should be organized around stable resource families:

* `tasks`
* `collections`
* `task-types`
* `agent-homes`

These resource families can then own subcommands such as:

* `:list`
* `:show`

### 3. Treat cross-cutting commands as explicit exceptions

Commands such as `doctor` are not resource families. They should stay rare and be
governed separately from the default resource-family surface.

### 4. Govern the registry, not just the router

The long-term product surface should move from a hand-grown `case` form toward a
governed registry/provider contract.

The steady-state source of truth should be:

* governance owns the whitelist of allowed CLI providers
* the registry is derived from those allowed providers
* `system.clj` consumes the registry and does not directly own future command-family handlers

That registry/provider contract should be machine-checkable and explicit about:

* command id
* command kind
* implementation status
* provider namespace
* provider contract function
* linked design document

## Planned Namespace Shape

This document does not require immediate implementation, but it sets the intended
landing zones.

Candidate namespace split:

* `vibe-flow.system`
* `vibe-flow.product.cli.registry`
* `vibe-flow.product.cli.tasks`
* `vibe-flow.product.cli.collections`
* `vibe-flow.product.cli.task-types`
* `vibe-flow.product.cli.agent-homes`
* `vibe-flow.product.cli.doctor`

Target responsibility split:

* `vibe-flow.system`
  facade, routing, top-level CLI integration
* `vibe-flow.product.cli.registry`
  governed whitelist consumption and registry assembly
* `vibe-flow.product.cli.<resource>`
  facade adapters for resource-family command translation
* `vibe-flow.product.cli.doctor`
  facade adapter for singleton diagnostic command translation

These `vibe-flow.product.cli.*` namespaces should not absorb lower-layer business
logic. They remain part of the CLI facade family even after the split.

## Governance Requirements

Before the formal CLI surface expands, the governed command metadata should remain
explicit and machine-checked.

At minimum, governance should keep enforcing:

* the formal CLI routing surface must expose the governed whitelist metadata
* the formal CLI routing surface must expose the registry/provider contract
* the formal CLI routing surface must expose the design document path for this plan

As implementation continues, governance should likely expand to check:

* resource-family ids are unique
* singleton-command ids are unique
* only approved command kinds exist
* provider namespaces are explicitly whitelisted before they can be collected
* the facade depends on the registry, not directly on every command implementation
* facade adapter namespaces do not become owners of lower-layer business rules

## Current Staging Decision

For now, the governed provider whitelist and registry contract are intentionally
represented as planned functions in `system.clj`.

This is a staging shape, not the intended steady-state source of truth for command
implementation. It exists to make the future facade/registry contract explicit
before the internal registry/provider split is implemented.
