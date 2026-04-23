# Task-Type Driven Coding Agent Toolchain Design

这份文档是当前 repo 的正式设计基线。

它不再记录早期探索过程，而是基于 `preproject_stage1_minimal_workflow`、`preproject_stage2_toolchain_target_boundary`、`preproject_stage3_definition_externalization` 已经验证出来的边界，收敛出当前系统应该如何被理解、如何继续实现。

旧版设计草稿已归档到：

* [design.deprecated.md](/home/mikewong/proj/main/vibe-flow/docs/archives/design.deprecated.md)
* [architecture.deprecated.md](/home/mikewong/proj/main/vibe-flow/docs/archives/architecture.deprecated.md)
* [refine.deprecated.md](/home/mikewong/proj/main/vibe-flow/docs/archives/refine.deprecated.md)

## 1. 设计目标

系统目标不是构建一个通用 workflow engine，而是提供一个足够薄、但结构稳定的：

```text
task-type driven coding agent toolchain
```

它要解决的问题是：

* 把一批同类型 coding tasks 组织成可重复推进的 workflow
* 把定义层从代码常量中收敛出来
* 把 runtime 执行和 durable state 正式落到 target repo 中
* 让 mgr 和 worker 通过统一控制面推进任务，而不把系统做成大平台

系统追求：

* 清晰边界
* 低 ceremony
* 可恢复
* 可扩展 task_type
* 可替换 agent runtime
* target 内状态可管理

系统不追求：

* 通用工作流平台
* 企业级治理平台
* 强结构化 artifact schema
* 替代 CI/CD 或项目管理系统

## 2. 最外层边界

当前正式边界固定为：

```text
System
  = Toolchain
  + Target
```

更具体地说：

* `toolchain` 是 workflow control plane 的实现
* `target` 是被操作的 git repository
* `target` 同时也是 workflow installation target 和 runtime target

从用户视角看，这意味着：

* 用户先安装一次 `vibe-flow`
* 之后在任意 repo 中通过已安装的 `vibe-flow` 操作 target
* target 不应依赖“当前正在开发的 toolchain 源码 checkout”作为执行来源

这条边界已经不再是目录习惯，而是正式设计约束。

## 3. 核心判断

基于 `preproject_stage3_definition_externalization`，系统当前最重要的设计判断是：

```text
definition layer
  = externalized artifacts installed into target

control plane
  = built into toolchain
```

也就是说：

* `task_type`、prompt、最小 prepare-run 声明应从代码内建收敛到 target 内已安装 artifact
* workflow 主干仍由 toolchain 内建收口
* mgr 继续通过 workflow CLI 把控制权交回 toolchain

这条判断是当前系统的主轴。

## 4. 核心模型

当前正式模型分成 4 类。

### 4.1 System Models

用于描述 workflow 在 target 上的安装与治理状态。

最小包括：

* `install`
* `target`
* `layout`
* `registry`

### 4.2 Definition Models

用于描述可复用 workflow 定义层。

当前最小包括：

* `task_type`

### 4.3 Domain Models

用于描述被持续推进的业务对象。

当前最小包括：

* `collection`
* `task`

### 4.4 Runtime Models

用于描述运行态对象。

当前最小包括：

* `run`
* `mgr_run`
* worktree
* installed `agent_home`

## 5. 关键对象语义

### 5.1 `task_type`

`task_type` 是协议对象，不是标签。

它定义：

* mgr / worker home 绑定
* worker routing
* prompt 来源
* task definition schema
* prepare-run 策略

当前正式理解是：

```text
task_type
  = definition model
  = managed package
  = logical definition + metadata + layout contract
```

### 5.2 `collection`

`collection` 是同类任务池，而不是通用项目管理容器。

它把一组同 `task_type` 的任务聚合起来，作为 inspect、批量创建和未来调度扩展的自然边界。

### 5.3 `task`

`task` 是具体推进单元，也是 workflow state machine instance。

当前最小 durable schema 是：

* `goal`
* `scope`
* `constraints`
* `success-criteria`

这部分是 task 自己的业务定义，不应再和 task_type 协议或 run 派生状态混在一起。

### 5.4 `run`

`run` 不只是执行记录，而是 runtime container。

它最少应拥有：

* worker
* launcher
* input head
* output head
* worktree
* prompt
* output
* prepare metadata
* worker home

当前系统也已经开始把它当作：

```text
candidate change carrier
```

### 5.5 `mgr_run`

`mgr_run` 记录 mgr 一次具体决策与其 runtime 上下文。

它和 `run` 分开保留，是为了维持：

* mgr 决策轨迹
* worker 执行轨迹

之间的清晰边界。

## 6. Target 内状态布局

当前正式布局固定为：

```text
target/
  .git/
  source tree...
  .workflow/
    state/
      system/
        install.edn
        target.edn
        layout.edn
        toolchain.edn
        registries/
      definitions/
        task_types/
          <task-type-id>/
            task_type.edn
            meta.edn
            prompts/
            hooks/
      domain/
        collections/
        tasks/
    local/
      runs/
      mgr_runs/
      agent_homes/
```

这层布局的正式含义是：

* `system/` 是工具链自维护模型
* `definitions/` 是 target-managed definition artifacts
* `domain/` 是 durable business state
* `local/` 是本地 runtime state

其中 `toolchain.edn` 的作用不是表达版本矩阵，而是显式声明：

* 这个 target 由用户已安装的 `vibe-flow` 命令驱动
* workflow 回调不应退回到当前源码 checkout

## 7. Definition Layer

定义层当前以 target 内已安装 artifact 为运行时 source of truth。

### 7.1 `task_type` package contract

最小 package contract 为：

```text
definitions/task_types/<task-type-id>/
  task_type.edn
  meta.edn
  prompts/
    mgr.txt
    impl.txt
    review.txt
    refine.txt
  hooks/
    before_prepare_run
```

其中：

* `task_type.edn` 表达协议定义
* `meta.edn` 表达管理信息
* `prompts/` 是 prompt artifact
* `hooks/` 是最小扩展点目录

### 7.2 prepare-run 边界

`prepare_run` 本体保持 toolchain 内建收口。

当前允许 `task_type` 通过声明式定义影响：

* input head
* worker home
* prompt inputs
* worktree strategy

当前唯一扩展点是：

* `before_prepare_run`

并且 `before_prepare_run` 只能返回有限字段，不允许直接 materialize run 或接管 workflow。

## 8. Workflow Control

当前正式控制链固定为：

```text
mgr
  -> workflow CLI
  -> toolchain regains control
  -> prepare run
  -> launch worker
  -> persist run
  -> update task
```

这条控制链表达下面几个正式边界：

* `mgr` 是独立 agent
* `mgr` 不直接拿 worker launcher 细节
* toolchain 负责 workflow 控制面和状态落盘
* worker 只负责本轮执行

## 9. Runtime Integration

toolchain 不解释 agent runtime 内部能力模型，但负责稳定绑定 runtime 所需输入。

当前 runtime integration 至少负责：

* launcher contract
* `agent_home` 绑定
* prompt materialization
* output 落盘
* runtime metadata 记录

当前已验证的 launcher 包括：

* `mock`
* `codex`

## 10. Candidate Change Boundary

当前系统已经能生成 candidate change，但尚未把 candidate integration 作为正式主线实现。

当前正式边界是：

* worker 在 run worktree 中产出 candidate change
* task 记录新的 `repo-head`
* review 可以基于 candidate 给出 `pass`
* workflow 当前不自动把结果落回 target 主工作树

因此，当前系统的语义是：

```text
workflow
  = candidate producer

not yet
  = automatic integration engine
```

后续如果推进 integration，需要单独定义：

* candidate identity
* integration policy
* integration actor
* integration failure handling

## 11. Toolchain 的正式职责

当前正式职责应固定为：

* 校验 target 是 git repository
* 安装 workflow 所需状态到 target
* 维护 target layout、metadata、registry
* 读取并校验 definition artifacts
* 推进 collection/task/run 状态
* 调用 mgr runtime
* 调用 worker runtime
* 执行 prepare-run
* 管理 run runtime assets
* 把 workflow 结果持久化到 target

它不负责：

* 定义 agent runtime 内部能力语义
* 替代项目管理系统
* 替代 CI/CD
* 提供通用工作流平台抽象

## 12. 当前后续实现方向

基于当前设计，正式版本应优先补齐下面几类能力：

1. `task_type` lifecycle
   包括 `update / deprecate / remove-if-safe / validate / reconcile`
2. target model management
   把 `system / definitions / domain / runtime` 的管理面做正式
3. workflow recovery
   支持 unfinished run / mgr_run 的 inspect、retry、gc、recover
4. candidate lifecycle
   把 candidate change 的 record、summary、integration policy 做正式
5. product surface
   把当前 spike CLI 收敛成更稳定的正式 control surface

## 13. 一句话总结

当前正式设计可以压缩成一句话：

```text
在一个 target git repo 中，
把 definition layer 作为 installed artifacts 外置，
把 workflow control plane 保持为 toolchain 内建，
用 task_type 驱动一组 coding agent tasks 的持续推进。
```
