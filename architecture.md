# Task-Type Driven Coding Agent Toolchain Architecture

这份文档是当前 repo 的正式架构说明。

它建立在 [design.md](/home/mikewong/proj/main/vibe-flow/design.md) 的正式设计基线上，负责回答：

* 系统由哪些层构成
* 它们之间如何依赖
* 控制权如何流动
* target 内状态如何组织

旧版探索性架构文档已归档到：

* [architecture.deprecated.md](/home/mikewong/proj/main/vibe-flow/docs/archives/architecture.deprecated.md)

## 1. System Boundary

当前正式系统边界固定为：

```text
System
  = Toolchain
  + Target
```

其中：

* `toolchain` 是 workflow control plane 的实现
* `target` 是被工作的 git repository
* `target` 同时承载 workflow installation state 和 runtime state

从运行边界看，还应补上一层用户环境约束：

```text
user installs vibe-flow once
  -> invokes installed vibe-flow command
  -> installed vibe-flow operates on target
```

也就是说，target 不应把“当前开发中的源码 checkout”当作稳定执行来源。

## 2. Architecture Summary

当前系统可以压缩成下面这条链：

```text
definition package
  -> installed into target
  -> interpreted by toolchain
  -> mgr decides next step
  -> workflow regains control
  -> run is prepared
  -> worker is launched
  -> run/task state is persisted
```

这条链的关键点是：

* definition layer 已外置到 target
* workflow control plane 仍内建在 toolchain
* mgr 是独立 agent，但不接管 runtime orchestration

## 3. Layering

当前正式分层建议固定为：

```text
L1 Product Surface
L2 Workflow Control
L3 Definition / Model Management
L4 Runtime Integration
L5 State Persistence
L6 Target Substrate
L7 Support Substrate
```

### L1 Product Surface

负责：

* CLI
* 未来可能的 API / TUI / Web surface

当前对应：

* root-level user docs
* `spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/toolchain.clj`

### L2 Workflow Control

负责：

* 选择可推进 task
* 调用 mgr
* 接收 mgr 决策
* 重新接管控制权
* 推进 run / task lifecycle

当前对应：

* `spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/core/`

### L3 Definition / Model Management

负责：

* definition artifact load / resolve / interpret
* target-managed model lifecycle
* registry / meta / inspect / reconcile

当前对应：

* `spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/definition/`
* `spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/management/`
* `spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/target/install.clj`

### L4 Runtime Integration

负责：

* mgr runtime bridge
* worker launcher adapter
* prompt rendering
* runtime binding with `agent_home`

当前对应：

* `spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/agent/`
* `spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/state/run_store.clj`

### L5 State Persistence

负责：

* collection/task/run/mgr_run record 的 load / save / list

当前对应：

* `spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/state/`

### L6 Target Substrate

负责：

* target repo git semantics
* target layout bootstrap
* on-disk workflow state contract

当前对应：

* `spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/target/repo.clj`
* `spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/support/paths.clj`

### L7 Support Substrate

负责：

* 通用 util
* 文本渲染
* edn read/write
* 时间、uuid、hash 等基础能力

当前对应：

* `spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/support/util.clj`

## 4. Dependency Direction

当前正式依赖方向应固定为：

```text
Product Surface
  -> Workflow Control
  -> Definition / Model Management
  -> Runtime Integration
  -> State Persistence
  -> Target Substrate
  -> Support Substrate
```

几条关键约束：

* 高层可以依赖低层
* 低层不能反向依赖高层
* state store 不应反向承接 control logic
* runtime adapter 不应决定 workflow lifecycle
* definition interpreter 不应承担 lifecycle management

## 5. Target State Architecture

当前 target 内状态架构为：

```text
target/
  .workflow/
    state/
      system/
      definitions/
      domain/
    local/
```

每层语义如下：

### `state/system`

承载工具链自维护模型。

最小包括：

* `install.edn`
* `target.edn`
* `layout.edn`
* `toolchain.edn`
* `registries/`

### `state/definitions`

承载 target-managed definition packages。

当前最小包括：

* `task_types/<task-type-id>/`

### `state/domain`

承载 durable business models。

当前最小包括：

* `collections/`
* `tasks/`

### `local`

承载本地 runtime state。

当前最小包括：

* `runs/`
* `mgr_runs/`
* `agent_homes/`

## 6. Core Runtime Chain

当前正式运行链路是：

```text
task
  -> mgr_run
  -> mgr decides worker
  -> workflow CLI
  -> run prepare
  -> worker launch
  -> run finalize
  -> task update
  -> next stage or done/error
```

这里的控制权边界很关键：

* `mgr` 不直接掌握 worker orchestration
* `mgr` 通过 workflow CLI 归还控制权
* workflow control layer 执行真正的 prepare / launch / persist

## 7. Definition Package Architecture

当前 `task_type` 已经是 managed package，而不是散文件。

最小 contract 为：

```text
task_type package
  = task_type.edn
  + meta.edn
  + prompts/
  + hooks/
```

其中：

* `task_type.edn` 负责协议定义
* `meta.edn` 负责管理信息
* `prompts/` 负责 prompt artifacts
* `hooks/` 负责最小扩展点

这意味着 definition layer 已经进入正式架构，不再是代码内建 map。

## 8. Candidate Change Architecture

当前系统已形成一个重要但尚未完全闭环的架构事实：

* worker 在 run worktree 中产出 candidate change
* run 记录 output head
* task 更新 repo head
* review 基于 candidate 给出控制结果

但当前系统尚未自动完成 integration。

因此当前正式边界是：

```text
run
  = runtime container
  + candidate carrier

workflow
  = candidate producer
  not yet integration engine
```

## 9. Formalization Direction

如果继续沿这份架构推进，最应该继续正式化的部分是：

1. definition package lifecycle
2. target model management
3. workflow recovery
4. candidate change lifecycle
5. stable control surface

## 10. One-Line Summary

当前正式架构可以压缩成一句话：

```text
toolchain owns the control plane,
target owns the installed state,
task_type owns the reusable workflow protocol,
run owns the runtime envelope and candidate change.
```
