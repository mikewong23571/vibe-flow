# Formal Implementation Task

这份文档定义当前 repo 进入正式编码阶段时的任务描述。

它的目标不是重复设计文档，而是把下面三件事说清楚：

* 开始编码前应该参考哪些文档
* 当前应优先落地哪些已经足够清晰的设计
* 在治理规则限制下，第一轮正式实现应如何推进

## 1. 任务目标

当前正式实现的核心目标是：

```text
under governance constraints,
implement the parts of the formal design
that are already clear, stable, and actionable
```

换句话说：

* 不从头重新发明系统
* 不继续扩张探索范围
* 不把尚未收敛的问题一起编码
* 先把正式文档中已经清晰的设计部分落到正式代码骨架

## 2. 文档参考顺序

开始编码时，文档参考必须遵循层级，不要在探索文档和正式文档之间来回跳。

### 2.1 一级参考文档：正式权威来源

下面三份文档是正式实现的直接依据：

1. [design.md](/home/mikewong/proj/main/vibe-flow/docs/design.md)
2. [architecture.md](/home/mikewong/proj/main/vibe-flow/docs/architecture.md)
3. [governance.md](/home/mikewong/proj/main/vibe-flow/docs/governance.md)

它们分别回答：

* `design.md`
  系统目标、边界、核心模型、target 内布局、definition layer、candidate change 边界
* `architecture.md`
  分层、依赖方向、运行链路、target state architecture、definition package architecture
* `governance.md`
  模块职责、依赖规则、单文件和目录约束、复杂模块治理、扩展模板、机审规则

正式实现时，**这三份文档优先级最高**。

### 2.2 二级参考文档：最近一次探索结论

当一级文档没有展开某个实现细节时，参考下面几份 Stage 3 文档：

1. [learnings.md](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/learnings.md)
2. [toolchain_growth_design.md](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain_growth_design.md)
3. [project_governance_design.md](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/project_governance_design.md)
4. [target_model_management_design.md](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/target_model_management_design.md)

这些文档的作用是：

* 补充最近一轮 spike 已验证的经验
* 帮助理解为什么正式设计做出当前取舍
* 为一级文档尚未展开的局部设计提供背景

### 2.3 三级参考文档：历史背景

下面文档只作为背景，不作为正式实现直接依据：

* [docs/archives/design.deprecated.md](/home/mikewong/proj/main/vibe-flow/docs/archives/design.deprecated.md)
* [docs/archives/architecture.deprecated.md](/home/mikewong/proj/main/vibe-flow/docs/archives/architecture.deprecated.md)
* [docs/archives/refine.deprecated.md](/home/mikewong/proj/main/vibe-flow/docs/archives/refine.deprecated.md)
* `spikes/preproject_stage1_*`
* `spikes/preproject_stage2_*`

使用原则：

* 只在需要理解设计演化时查看
* 不得用来推翻正式文档
* 不得把早期试验性结构重新带回正式代码

## 3. 当前实现的总边界

正式版本的第一轮代码落地，边界固定为：

```text
System
  = Toolchain
  + Target
```

必须保持：

* `toolchain` 是 control plane
* `target` 是 installation host + runtime host
* definition layer 外置到 target
* control plane 内建在 toolchain

不得重新引入：

* 独立于 target 的另一套正式 runtime host 抽象
* 把 `task_type` 退回代码内 hardcoded map
* 把 workflow orchestration 下沉到 runtime adapter

## 4. 当前明确可实现的设计范围

这部分是“已经足够清晰，可以正式编码”的范围。

### 4.1 正式 Clojure 项目骨架

当前正式代码应继续落在：

* [src/vibe_flow](/home/mikewong/proj/main/vibe-flow/src/vibe_flow)
* [test/vibe_flow](/home/mikewong/proj/main/vibe-flow/test/vibe_flow)
* [resources/vibe_flow](/home/mikewong/proj/main/vibe-flow/resources/vibe_flow)

当前已存在的占位入口是：

* [core.clj](/home/mikewong/proj/main/vibe-flow/src/vibe_flow/core.clj)
* [system.clj](/home/mikewong/proj/main/vibe-flow/src/vibe_flow/system.clj)
* [workflow/control.clj](/home/mikewong/proj/main/vibe-flow/src/vibe_flow/workflow/control.clj)
* [definition/task_type.clj](/home/mikewong/proj/main/vibe-flow/src/vibe_flow/definition/task_type.clj)
* [target/install.clj](/home/mikewong/proj/main/vibe-flow/src/vibe_flow/target/install.clj)

第一轮实现必须沿这个正式骨架推进，不再继续把新代码落进 spike 目录。

### 4.2 Target layout 与 install substrate

下面设计已经明确，应优先落地：

* target 内 `.workflow/` 正式布局
* `state/system`
* `state/definitions`
* `state/domain`
* `local`
* `install.edn`
* `target.edn`
* `layout.edn`
* `registries/`

因此应先实现：

* target path substrate
* install/reconcile 最小主线
* system model 的读写
* layout version 的持久化

### 4.3 `task_type` managed package

下面设计已经明确，应优先落地：

* `task_type` 是 definition model
* `task_type` 是 managed package
* runtime 读取 target 内已安装 artifact
* `task_type` package contract 包含：
  * `task_type.edn`
  * `meta.edn`
  * `prompts/`
  * `hooks/`

因此应先实现：

* task_type load / inspect / resolve
* task_type package path locate
* task_type meta 读取
* task_type registry 读写
* 最小 create / list / inspect 命令支撑

### 4.4 Domain models: `collection` 和 `task`

下面设计已经明确，应优先落地：

* `collection` 是同类任务池
* `task` 是 workflow state machine instance
* `task` 最小 durable schema 为：
  * `goal`
  * `scope`
  * `constraints`
  * `success-criteria`

因此应先实现：

* collection/task 的最小 record schema
* load / save / list
* task schema validation
* task 与 task_type 的最小绑定

### 4.5 Workflow control 主干

下面设计已经明确，应优先落地：

* workflow control 在 toolchain 内建
* `mgr` 通过 workflow CLI 归还控制权
* `run` / `mgr_run` 分开保留
* workflow control 负责真正的 prepare / launch / persist

因此应先实现：

* select runnable task
* create `mgr_run`
* 接收 mgr 决策并推进 task stage
* create `run`
* finalize `run`
* 持久化 task/run/mgr_run state

### 4.6 Runtime integration 的最小接口

下面设计已经明确，应优先落地：

* runtime adapter 不决定 lifecycle
* worker launcher 只是 runtime bridge
* `run` 是 runtime container
* `run` 至少拥有：
  * worker
  * launcher
  * prompt
  * output
  * worktree
  * heads

因此应先实现：

* launcher protocol
* `mock` launcher
* 预留 `codex` launcher adapter 接口
* `run` prepare/finalize 最小路径

## 5. 当前不进入首轮正式实现的部分

下面问题虽然重要，但当前还不应和第一轮正式实现混在一起：

### 5.1 Candidate change 自动集成

当前明确边界是：

* workflow 产出 candidate change
* 当前不负责自动落回 target 主分支

因此暂不实现：

* auto merge
* auto cherry-pick
* integration queue
* review pass 后自动 apply

### 5.2 平台化能力

当前不做：

* 通用 workflow engine
* 团队级 governance platform
* 多 target orchestration
* UI surface
* 远程 definition distribution

### 5.3 过早抽象

当前不做：

* 大而全的 plugin system
* 过重的 generic repository layer
* 过度抽象的 domain framework

## 6. 模块落位要求

正式实现必须按根文档指定的层次落位。

建议落位如下：

* `src/vibe_flow/support/`
  通用 util、edn、time、uuid、text
* `src/vibe_flow/target/`
  target substrate、layout、install
* `src/vibe_flow/state/`
  system/definition/domain/runtime record 的 persistence
* `src/vibe_flow/definition/`
  task_type artifact load / resolve / interpret
* `src/vibe_flow/management/`
  task_type lifecycle、registry、inspect、reconcile
* `src/vibe_flow/runtime/`
  launcher protocol、mock/codex adapter、prompt/runtime binding
* `src/vibe_flow/workflow/`
  task selection、stage transition、run/mgr_run orchestration
* `src/vibe_flow/product/` 或 `src/vibe_flow/cli/`
  product surface、参数解析、命令路由

当前已经存在的占位文件只是起点，不是最终目录的全部边界。

## 7. 治理约束

正式实现必须满足 [governance.md](/home/mikewong/proj/main/vibe-flow/docs/governance.md) 和机审规则。

### 7.1 硬约束

必须满足：

* namespace 与路径一致
* manifest 注册完整
* 模块声明 layer / volatility / module-kind / complexity / responsibility
* 高变和复杂模块声明 `split-axis`
* 低变模块声明 `stability-role`
* 层间依赖方向合法
* `sample/debug` 不进入正式主线
* 单文件长度：
  * `> 300` 告警
  * `> 400` 报错
* 单目录直接子项：
  * `> 7` 告警
  * `> 11` 报错

### 7.2 设计性约束

同时必须遵守：

* store 默认只做 persistence
* management 不启动 runtime
* definition 不负责 lifecycle
* toolchain/product surface 不承接业务推进
* 复杂性必须停留在少数已命名热点

## 8. 第一轮正式实现建议顺序

推荐按下面顺序推进，而不是多线并发扩张。

### Step 1. Target substrate 与 system models

先完成：

* path substrate
* install/reconcile
* system model store
* `.workflow` layout materialization

交付标志：

* 能初始化 target
* 能写出 `install.edn / target.edn / layout.edn`
* 能建立 `definitions/ domain/ local/ registries/`

### Step 2. `task_type` definition package management

再完成：

* task_type package locate/load
* meta/load
* registry
* list/inspect/create

交付标志：

* target 内能创建一个最小 task_type package
* runtime 能只从 target 内已安装 artifact 读取 task_type

### Step 3. Domain models

再完成：

* collection/task schema
* store
* task_type binding
* task validation

交付标志：

* 能创建、保存、读取 collection/task
* task schema 与 task_type 最小绑定成立

### Step 4. Workflow control minimal mainline

再完成：

* runnable task selection
* mgr_run / run create/finalize
* stage transition
* mock launcher path

交付标志：

* mock 下能完成最小 run loop
* `mgr -> workflow -> worker -> persist` 主干成立

### Step 5. Inspect 与 recovery surface

最后补：

* show state
* inspect task
* inspect run
* inspect task_type

交付标志：

* 关键 durable state 可读
* 中断后可重新理解系统状态

## 9. 任务完成定义

当前任务不是“把整个系统做完”，而是完成下面这条：

```text
formal codebase
  can host
  the clear and governed subset
  of the current formal design
```

具体完成定义：

* 正式代码不再依赖 spike 目录才能表达主干
* `docs/design.md / docs/architecture.md / docs/governance.md` 中清晰部分有正式代码对应
* 治理校验始终通过
* pre-commit 始终可拦截违规变更
* 仍然待定的问题被明确留在边界外，而不是被偷偷编码

## 10. 执行时的判断标准

每次开始实现一个模块前，都先回答下面四个问题：

1. 这部分是否已经被正式文档清晰定义
2. 这次改动应落在哪一层
3. 这次改动是否会破坏治理约束
4. 这次改动是否把未收敛问题过早编码

如果第 1 条回答不清楚，应先补设计，而不是直接写代码。  
如果第 2 条回答不清楚，应先补模块边界。  
如果第 3 条或第 4 条回答为“会”，则不应继续实现。
