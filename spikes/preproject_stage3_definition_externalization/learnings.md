# preproject_stage3_definition_externalization learnings

这份文档记录 `preproject_stage3_definition_externalization` 的主要结论。

目标不是复述实现细节，而是沉淀这轮 spike 对正式系统边界的启发。

和 [spikes/preproject_stage2_toolchain_target_boundary/learnings.md](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage2_toolchain_target_boundary/learnings.md) 相比，`preproject_stage3_definition_externalization` 的重点不再是“`toolchain + target` 这层外边界能不能成立”，而是：

* 定义层能否真正从代码里拆出来
* 同时 workflow 控制面能否继续内建并收口
* target 内模型是否需要进入更正式的管理语义

## 结论概览

`preproject_stage3_definition_externalization` 已经验证，下面这条主干在“定义层外置、控制面内建”的结构下仍然成立：

```text
target git repo
  -> install workflow into target/.workflow
  -> install task_type artifact into target state
  -> seed collection/task with minimal schema
  -> mgr 判断下一步
  -> mgr 调 workflow CLI
  -> workflow 重新接管控制权
  -> workflow 从 target 内已安装 artifact 读取定义
  -> prepare run
  -> launch worker
  -> 写 run / 更新 task
  -> 继续下一轮，直到 done 或 error
```

这说明下面这些点已经有了更高置信度：

* `task_type` 可以从代码常量收敛成安装型 artifact
* prompt 可以从内建字符串收敛成正式文件 artifact
* `prepare_run` 可以保持内建收口，同时读取 artifact 中的声明式定义
* `target/.workflow/` 需要进一步区分 system / definition / domain / runtime
* `task_type` 不能只被当成一堆文件，而应被视为 target 内正式管理模型

## 被验证的设计点

### 1. `definition layer externalized, control plane built-in` 这条主命题成立

这是 `preproject_stage3_definition_externalization` 最核心的结论。

这一轮已经不只是“把配置搬到文件里”，而是实际验证了：

* runtime 从 target 内安装后的 `task_type` artifact 读取定义
* mgr / worker prompt 从 artifact 文件读取
* workflow 的推进逻辑仍由 toolchain 内建收口
* `mgr -> workflow CLI -> toolchain regains control` 没有因为定义层外置而被打散

也就是说，更准确的结构已经开始稳定为：

```text
definition layer
  = installed artifacts in target

control plane
  = built into toolchain
```

### 2. `task_type` 已经从“代码内建 map”升级成“安装到 target 的 artifact”

这轮 spike 证明，`task_type` 不需要继续以代码常量作为运行时 source of truth。

更合理的理解是：

```text
source-side bundle
  -> install
  -> target-side artifact
  -> runtime loads only installed artifact
```

这带来两个重要收敛：

* 运行时 source of truth 进入 target，而不再散落在 toolchain 代码里
* 后续要做 inspect / create / update 时，有了正式落点

### 3. prompt 外置是有效外置，不只是换个存放位置

这轮 spike 也验证了 prompt 外置的关键点不在“文件化”本身，而在 source of truth 的迁移。

真正成立的是：

* prompt 存在于 task_type package 中
* install 会把 prompt 安装进 target
* runtime 只从 target 内 prompt artifact 读取内容

这意味着 prompt 开始成为定义层的一部分，而不是 workflow 代码的附属常量。

### 4. task definition 需要最小 durable schema，而不应继续只有 `goal`

`preproject_stage3_definition_externalization` 把 task 收敛到一个更正式但仍然很小的 durable schema：

* `goal`
* `scope`
* `constraints`
* `success-criteria`

这一步的意义不是引入复杂 schema framework，而是把下面三类东西分开：

* `task` 自己的业务描述
* `task_type` 的定义层协议
* `prepare_run` 派生出来的运行期结果

这让 task context 对 mgr / worker / review 都更清楚。

### 5. `.workflow/` 只分 durable / local 还不够，durable 内部也要进一步分层

这轮最大的结构性收获之一，是单纯把 `.workflow/` 切成 durable / local 仍然不够专业。

更清晰的布局是：

```text
.workflow/
  state/
    system/
    definitions/
    domain/
  local/
```

其中：

* `system` 表示 install、layout、registry 这类工具链自维护模型
* `definitions` 表示 `task_type` 这类可管理定义层对象
* `domain` 表示 collection、task 这类业务 durable 对象
* `local` 表示 run、mgr_run、worktree、agent_home 这类本地运行态

这比把所有 durable 内容平铺在 `state/` 下一层更可读，也更接近正式系统。

### 6. `task_type` 应被视为 target 内的 managed package，而不是散文件

这是这轮 spike 非常重要的新认识。

一个 `task_type` 不只是：

* 一个 `task_type.edn`
* 几个 prompt 文件

更准确地说，它是：

```text
task_type
  = definition model
  = managed package
  = metadata record + layout contract
```

因此这轮实现已经开始引入：

* `meta.edn`
* `task_types registry`
* `list-task-types`
* `inspect-task-type`
* `create-task-type`

这说明 target 内模型管理已经从概念进入最小实现。

### 7. target 内模型不能再统一叫“CRUD 对象”

这轮 spike 让一个更正式的分类开始稳定：

* `system models`
* `definition models`
* `domain models`
* `runtime models`

不同类型对象的生命周期语义并不相同。

例如：

* `task_type` 更接近 `create/update/deprecate/remove-if-safe`
* `task` 更接近 `create/update/cancel/close`
* `run` 更接近 `prepare/finalize/gc`

这意味着后续设计里不应再用一句“target 模型增删改查”概括全部对象。

### 8. 源码组织必须对应架构语义，而不能继续平铺

这轮 spike 也暴露了一个实现层问题：

* 如果源码继续平铺，阅读者很难抓住控制面、定义层、状态层、target 基础设施之间的边界

因此这轮重组后的源码分层是有必要的：

* `core/`
* `agent/`
* `definition/`
* `management/`
* `state/`
* `target/`
* `sample/`
* `support/`

这不是单纯的目录美化，而是为了让代码结构和系统职责对齐。

### 9. 真实 `codex` 端到端执行已经成立

这轮不只跑通了 mock。

在 `2026-04-22T01:00:30.894634875Z` 对应的真实 run 中，`bb -m spike-v3.toolchain run-loop codex codex 4` 已经完成了一次真实的：

* mgr codex 决策
* worker codex impl
* mgr codex review 决策
* worker codex review
* task 进入 `:done`

这说明：

* `CODEX_HOME` 安装态 agent home 的用法成立
* `codex exec` 可以真实接入这条 workflow 主链
* “定义层外置 + 控制面内建”不是只在 mock 下自洽

## 被修正的认识

### 1. 本轮业务目标是“产出 candidate change”，不是“自动集成到 target 主分支”

真实 `codex` 验证后，一个边界变得很清楚：

* worker 的改动发生在 run 的 detached worktree 中
* task 会记录新的 `repo-head`
* review 可以基于这个 candidate change 给出 `pass`

但当前系统**不会自动把结果落回 target 主工作树**。

这个边界现在应被理解成：

* workflow 当前负责生成 candidate change
* workflow 当前不负责自动 merge / integrate back

这不是偶然遗漏，而是当前语义尚未继续推进到“集成层”。

### 2. “模型管理”不只是 record 管理，还包括目录 contract 管理

这轮 spike 证明，只管理 EDN record 是不够的。

对 `task_type` 来说，真正要管理的是两件事：

* logical model
* on-disk layout contract

也就是说，后续的 create / update / inspect / remove 都不能只盯着一个文件，而必须一起考虑：

* metadata
* prompts
* hooks
* 目录结构完整性
* registry 对齐

### 3. install 的幂等性比之前想的更重要

这轮实现里实际踩到了 install 清理 `.workflow/local` 时的稳定性问题。

这暴露出一个很现实的要求：

* install 不只是“能把东西装进去”
* install 还必须能在已有 runtime 痕迹的 target 上稳定重跑

所以 install 的正式职责里，应继续包含：

* 清理 prior runtime state
* 清理 legacy layout
* 幂等更新 install metadata / layout metadata / registries

### 4. registry 不只是方便查看，而是正式管理的索引入口

如果只靠扫目录，系统仍然可以运行，但“管理”会很弱。

这轮 spike 之后更清楚的是：

* registry 是 target 内的 management index
* storage directory 负责存放制品
* registry 负责列出、校验、对齐和演化

至少对 `task_type` 来说，这个模式已经开始成立。

## 当前仍未解决的问题

### 1. `task_type` 还没有完整 lifecycle

当前已经有：

* `list`
* `inspect`
* `create`

但还没有：

* `update`
* `deprecate`
* `remove-if-safe`
* reference check

也就是说，管理层已经起步，但还没有进入完整生命周期。

### 2. 还没有正式的 candidate integration 语义

当前 run 结束后，会得到：

* candidate worktree
* output head
* task 上更新后的 `repo-head`

但系统尚未回答：

* 什么时候把 candidate change 合回 target 主分支
* 由谁执行 merge / fast-forward / cherry-pick
* review pass 后是否一定自动集成

这部分当前明确属于待定边界。

### 3. target model management 还只覆盖了 `task_type`

目前进入正式管理层的主要是 `task_type`。

但后面还需要继续回答：

* `collection` 是否需要 registry
* `task` 是否需要更正式的 command surface
* `layout` 的 reconcile 是否要独立成 manager

也就是说，target-managed models 的方向已经明确，但还没完全铺开。

## 当前更清晰的架构表达

基于 `preproject_stage3_definition_externalization`，目前最稳的一版结构可以写成：

```text
toolchain
  = built-in workflow control plane

target
  = git repository being worked on
  = workflow installation target
  = workflow runtime host

definition layer
  = installed artifacts inside target/.workflow/state/definitions

task_type
  = managed definition package
  = task_type.edn + meta.edn + prompts/ + hooks/

task
  = minimal durable business definition

run
  = runtime container + candidate change carrier

mgr
  = independent agent
  = decides next action
  = calls workflow CLI

workflow
  = regains control from mgr
  = loads installed definitions
  = prepares run
  = launches worker
  = persists state
```

## 对下一轮的建议

如果继续往正式系统推进，`preproject_stage3_definition_externalization` 之后最值得优先做的不是继续加更多 worker 能力，而是收紧下面三件事：

1. 完成 `task_type` 的 lifecycle，至少补上 `deprecate` 和 `remove-if-safe`
2. 继续把 target model management 从 `task_type` 扩展到其他正式模型
3. 明确 candidate integration 边界，决定“review pass 之后是否以及如何落回主分支”

一句话总结这轮收获：

```text
preproject_stage3_definition_externalization
  validated that the definition layer can be externalized into target-installed artifacts
  without giving up a built-in workflow control plane
```
