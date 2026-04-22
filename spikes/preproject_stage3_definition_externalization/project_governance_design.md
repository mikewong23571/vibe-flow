# Project Governance Design Based On preproject_stage3_definition_externalization

这份文档回答一个比“继续做功能”更底层的问题：

**基于当前 `preproject_stage3_definition_externalization` 的项目底座，应该怎样治理项目，才能让产品继续沿着正确路线、以足够严谨的方式生长？**

这里说的治理，不是组织流程或审批制度，而是更贴近当前代码和业务模块的工程治理：

* 单文件可读性
* 模块职责
* 模块间依赖
* 高变模块和低变模块
* 同类模块如何扩展
* 复杂业务模块如何收敛和演化

这份文档只讨论**当前 `preproject_stage3_definition_externalization` 已经存在的业务模块**，不做脱离代码现实的抽象架构空谈。

## 1. 当前项目底座分析

### 1.1 当前底座已经成立的部分

基于 [toolchain/README.md](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/README.md)、[learnings.md](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/learnings.md) 和当前源码，`preproject_stage3_definition_externalization` 的底座已经有几个关键优点：

* 最外层边界已经清楚：`toolchain + target`
* target 内状态布局已经正式切成 `system / definitions / domain / local`
* 定义层已经外置到 target 内已安装 artifact
* workflow 控制面仍然在 toolchain 内建收口
* 源码已经按职责分到 `core / agent / definition / management / state / target / sample / support`
* 真实 `codex` 已经能跑通一条从 mgr 到 review 的主链路

这意味着 `preproject_stage3_definition_externalization` 不是一堆散代码，而是已经有一个可继续生长的项目底座。

### 1.2 当前底座的真实模块分布

当前源码文件可以按职责分成下面几类：

* `toolchain.clj`
  CLI 入口和命令分发面
* `core/`
  workflow 控制主干
* `agent/`
  mgr、prompt、launcher 集成
* `definition/`
  已安装 definition artifact 的读取和 prepare-run 解释
* `management/`
  target 内 definition model 的管理逻辑
* `state/`
  domain/runtime record 持久化
* `target/`
  target repo install 和 git integration
* `sample/`
  sample fixture 和 smoke seed
* `support/`
  paths 和 util

这层分布本身是好的，因为已经能表达“控制面 / 定义层 / 状态层 / target 基础设施”的区别。

### 1.3 当前底座的主要强项

当前底座最重要的强项有 4 个：

#### 1. `paths` 已经开始成为真正的 layout substrate

[paths.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/support/paths.clj) 现在统一描述 `.workflow` 下的 durable/local 路径和 `task_type` 布局。

这很重要，因为：

* target layout 有了单一事实来源
* store / install / management / runtime 都不需要自己拼路径
* 后续做 reconcile / migrate 时有基础

#### 2. `definition` 和 `management` 已经分开

当前已经不是“一个 task_type 模块什么都干”。

* [definition/task_type.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/definition/task_type.clj) 负责加载 definition artifact 和解释 `prepare_run`
* [management/task_type_manager.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/management/task_type_manager.clj) 负责 registry / meta / inspect / create

这为后面把“定义解释”和“定义管理”彻底分开，已经打下了基础。

#### 3. `state` store 大体保持了薄层

虽然还不完全纯，但整体上：

* `collection_store`
* `task_store`
* `mgr_store`
* `run_store`

已经不再承载 sample fixture，也没有大面积夹带 workflow 控制逻辑。

#### 4. `run` 和 `mgr_run` 的 runtime envelope 理解是稳定的

当前的 `run` / `mgr_run` 持久化模型已经很接近正式系统：

* run 有 worktree、prompt、output、head、worker-home、prepare metadata
* mgr_run 有 prompt、output、workdir、CLI wrapper

这说明 runtime object 作为一等概念已经稳定。

## 2. 当前底座的治理风险

虽然底座已经成立，但当前代码里也有一些非常具体的治理风险。

这些风险不是抽象问题，而是已经能在当前模块中看到。

### 2.1 `core/workflow.clj` 已经成为第一个 orchestration hotspot

[workflow.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/core/workflow.clj#L1) 当前同时承担：

* mgr 决策落地
* run 准备与 finalize
* task 更新
* run-loop
* show-state
* smoke

这在当前规模可以接受，但已经出现两个信号：

* 它依赖面很宽，直接依赖了 `agent`、`state`、`target`、`management`、`sample`
* 它同时混入了正式控制逻辑和 spike/debug 逻辑

这说明它已经是当前项目里最典型的高变 orchestration module。

### 2.2 `target/install.clj` 目前承担了过多职责

[install.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/target/install.clj#L45) 当前同时负责：

* install metadata
* target metadata
* layout metadata
* `agent_home` 安装
* `task_type` bundle 安装
* runtime 清理
* legacy layout 清理
* `.gitignore` 更新

这说明 install 现在已经不是一个简单模块，而是：

```text
bootstrap + reconcile + runtime cleanup + artifact install
```

如果后面继续长 migration / validation / refresh，这个文件会很快变成第二个核心热点。

### 2.3 `management/task_type_manager.clj` 现在混了三类责任

[task_type_manager.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/management/task_type_manager.clj#L23) 当前同时承担：

* registry 维护
* meta 生成
* inspect
* `create-task-type`
* skeleton definition 生成
* prompt skeleton 生成

这说明它已经同时混入：

* management index 逻辑
* package metadata 逻辑
* authoring/scaffolding 逻辑

这在当前阶段方便，但在正式系统里会让“管理”和“脚手架生成”耦在一起。

### 2.4 `state/task_store.clj` 还带着 definition-aware validation

[task_store.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/state/task_store.clj#L22) 在保存 task 时直接依赖 `definition/task_type` 做 schema 校验。

这说明当前 `task_store` 还不是纯 persistence layer，而是：

* persistence
* + 一部分 definition-aware domain rule

这不是大问题，但从治理角度看，后续应决定：

* store 是否允许继续带轻校验
* 还是统一把 validation 收回 domain/service 层

### 2.5 `state/mgr_store.clj` 对 CLI 入口名有硬绑定

[mgr_store.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/state/mgr_store.clj#L7) 生成的 wrapper 里直接写死：

* `bb -m spike-v3.toolchain mgr-advance`

这意味着：

* runtime bridge 逻辑埋在 store 里
* store 不再只是 record 持久化

这个耦合当前可接受，但后续如果有 API surface、不同 CLI entry 或多前端入口，这里会成为扩展阻力。

### 2.6 `definition/task_type.clj` 已经开始变成复杂业务解释器

[definition/task_type.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/definition/task_type.clj#L63) 现在承担：

* task_type 读取
* prompt path 解析
* task definition validation
* prompt input resolve
* before hook 调用
* prepare-run spec 组装

这说明这个模块已经不再只是“definition loader”，而是在演化成：

```text
definition interpreter
```

这是合理的，但也是必须重点治理的复杂业务模块。

## 3. 治理目标

基于上面的底座现状，我建议当前项目治理明确追求下面 6 件事：

1. 单文件继续可读，不让核心模块无控制膨胀
2. 每个模块只有一个主变化原因
3. 依赖方向稳定，不允许层间反向污染
4. 高变模块和低变模块明确分区治理
5. 同类模块扩展有统一模板，不靠临场发挥
6. 复杂业务模块允许存在，但必须按“解释器/编排器”方式被收口

## 4. 模块分层治理

这里不是重新发明分层，而是把当前源码结构变成正式治理规则。

### 4.1 当前建议固定的层次

建议把当前 `preproject_stage3_definition_externalization` 固定成下面这组层级：

```text
L0 support
L1 target substrate
L2 state stores
L3 definition / management
L4 runtime integration
L5 workflow control
L6 product surface
L7 sample / debug only
```

对应当前目录：

* `support/` -> L0
* `target/repo.clj` + `support/paths.clj` -> L1
* `state/` -> L2
* `definition/` + `management/` + `target/install.clj` -> L3
* `agent/launcher*` + `agent/mgr.clj` + `agent/prompt.clj` + `state/run_store.clj` -> L4
* `core/` -> L5
* `toolchain.clj` -> L6
* `sample/` + `smoke` / debug path -> L7

### 4.2 依赖规则

建议从现在开始明确下面几条依赖治理规则：

#### Rule 1. 高层可以依赖低层，低层不能依赖高层

这是最基本规则。

例如：

* `core` 可以依赖 `state`、`definition`、`agent`
* `state` 不能依赖 `core`
* `support` 不能依赖任何业务层

#### Rule 2. `sample` 不能进入正式控制主线

当前 [workflow.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/core/workflow.clj#L136) 中的 `smoke!` 直接依赖 `sample-demo`。

这对 spike 可以接受，但治理上应明确：

* `sample/` 只能被 CLI smoke command、demo script、test fixture 使用
* 不得被正式 workflow control API 继续引用

#### Rule 3. `state` store 默认不持有复杂业务规则

store 的职责默认应是：

* path locate
* load
* save
* file enumeration

如果要带 rule，只允许极轻的 structural validation。

像 [task_store.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/state/task_store.clj#L22) 这种 definition-aware validation，后续要么保留为特许例外，要么收回 domain/service 层，但不能无限扩张。

#### Rule 4. `management` 负责 model lifecycle，不负责 runtime execution

`management/` 后续会继续长。

必须明确它负责：

* inspect
* create
* update
* deprecate
* reconcile
* registry maintenance

但不负责：

* 启动 runtime
* 直接推进 workflow
* 直接写 run 执行结果

#### Rule 5. `definition` 负责解释定义，不负责管理定义生命周期

这个边界已经开始形成。

后续应彻底固定：

* `definition` 负责 load / resolve / interpret
* `management` 负责 lifecycle / registry / authoring

#### Rule 6. `toolchain.clj` 只做 product surface，不沉淀业务逻辑

[toolchain.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/toolchain.clj#L73) 现在大体符合这个方向。

后续要继续守住：

* 解析参数可以放这里
* 打印结果可以放这里
* 业务判断和状态推进不能回流到这里

## 5. 单文件可读性治理

这里给的是当前项目能落地的标准，而不是泛泛而谈。

### 5.1 文件长度预算

建议从现在开始用“模块类型”而不是统一数字治理文件长度。

#### A. Store / adapter / util 文件

目标：

* `<= 120` 行优先

适用：

* `state/*_store.clj`
* `support/*.clj`
* `agent/launcher/*.clj`

原因：

* 这类文件应该非常薄
* 一旦超过这个规模，通常说明混入了额外职责

#### B. 解释器 / orchestrator 文件

目标：

* `<= 180` 行优先

适用：

* `definition/task_type.clj`
* `management/task_type_manager.clj`
* `core/workflow.clj`
* `target/install.clj`

如果超过 `180` 行，不必强拆，但必须满足一条规则：

* 文档中能清楚说明该文件只承载一个复杂职责，而不是多职责堆叠

#### C. 超过 220 行的文件必须有拆分方案

当前 [install.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/target/install.clj#L1) 已经在这个量级。

对这种文件，治理要求不是“立刻拆”，而是：

* 必须明确未来拆分轴
* 不允许继续无限往里堆新职责

### 5.2 单文件可读性的判断标准

单文件可读性不是只看行数。

我建议当前项目用下面 4 条判断：

* 文件能否一句话说清主职责
* 文件里是否存在两类以上完全不同的变化原因
* 读者能否在 3 分钟内找到“主入口函数”
* 文件依赖面是否明显大于同层文件

如果这 4 条里有 2 条失败，就应视为治理预警。

## 6. 高变模块与低变模块治理

这里不强调历史 git 统计，而强调**按系统职责预期的变化频率**来治理。

### 6.1 高变模块

当前最明确的高变模块是：

* [core/workflow.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/core/workflow.clj)
* [target/install.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/target/install.clj)
* [management/task_type_manager.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/management/task_type_manager.clj)
* [definition/task_type.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/definition/task_type.clj)
* [toolchain.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/toolchain.clj)
* [agent/mgr.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/agent/mgr.clj)
* [state/run_store.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/state/run_store.clj)

原因很清楚：

* 它们承载 orchestration、install、definition lifecycle、runtime integration、candidate carrier 等核心演化点

#### 高变模块治理规则

* 不允许同时引入新职责和新依赖方向
* 每次改动必须能回答“这个模块为何是变化承载点”
* 如果一次改动让依赖面继续扩大，必须同步给出拆分方案

### 6.2 低变模块

当前更适合作为低变底座的模块是：

* [support/paths.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/support/paths.clj)
* [support/util.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/support/util.clj)
* [core/model.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/core/model.clj)
* [core/task_lifecycle.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/core/task_lifecycle.clj)
* [state/collection_store.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/state/collection_store.clj)
* [state/task_store.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/state/task_store.clj)
* [agent/launcher.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/agent/launcher.clj)
* [target/repo.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/target/repo.clj)

这些模块适合作为低变底座，不代表永远不改，而是：

* 修改频率应低
* 一旦修改，应尽量保持向后兼容
* 不应承接业务试验性逻辑

#### 低变模块治理规则

* 不把新业务规则塞进去
* 不为了方便而扩大依赖面
* 对外接口尽量少而稳定

## 7. 同类模块的扩展治理

这里直接落到当前 `preproject_stage3_definition_externalization` 的模块类型。

### 7.1 如果要新增一个 `state` store

新 store 必须遵守：

* 只依赖 `paths`、`util`，必要时依赖极少量 definition structural validation
* 只提供 `load / save / list` 这一类能力
* 不生成脚手架
* 不做 workflow orchestration
* 不调用 runtime

也就是说，新 store 应该长得像：

* `collection_store`
* `task_store`

而不应长得像“半个 controller”。

### 7.2 如果要新增一个 `management` 模块

例如未来新增：

* `collection_manager`
* `registry_manager`
* `layout_reconciler`

它们应遵守：

* 负责模型 lifecycle，不负责执行 runtime
* 优先围绕 registry / meta / inspect / create / update / deprecate
* 不把 CLI 参数解析写进 manager

### 7.3 如果要新增一个 runtime launcher

例如未来新增新的 agent runtime adapter。

应遵守：

* 只实现 launcher contract
* 不直接修改 workflow state
* 不自己决定 task stage
* 不引入 target layout 新语义

这样 `launcher` 扩展才不会反向污染控制面。

### 7.4 如果要新增同类 definition package

例如新的 `task_type`。

应优先通过：

* target-side package
* registry
* inspect / validate

而不是通过：

* 新增硬编码分支
* 在 `workflow.clj` 里写 task_type 特判

## 8. 复杂业务模块的治理办法

这里专门回答你关心的“某些复杂的业务模块如何治理”。

复杂模块不应该被简单理解成“应该拆小”。

更合理的治理方式是：

* 允许复杂模块存在
* 但必须明确它是“解释器”还是“编排器”
* 每个复杂模块只能复杂在一个地方

### 8.1 `core/workflow.clj` 的治理方式

当前它是典型的 **编排器模块**。

它允许复杂，但只能复杂在：

* workflow decision 落地
* mgr / worker / run / task 的推进顺序

它不应该继续复杂在：

* sample/demo
* debug/show-state
* CLI message formatting

因此建议未来拆分方向固定为：

* `core/workflow_advance.clj`
* `core/workflow_observe.clj`
* `sample/smoke.clj`

至少要先做到：

* `smoke!` 不再放在正式 workflow 主文件
* `show-state!` 与推进逻辑分开

### 8.2 `definition/task_type.clj` 的治理方式

当前它是典型的 **定义解释器模块**。

它允许复杂，但只能复杂在：

* artifact load
* definition resolve
* prepare-run interpretation
* before-hook contract enforcement

它不应该继续复杂在：

* task_type authoring
* registry lifecycle
* package scaffolding

未来建议拆分轴固定为：

* `definition/task_type_loader.clj`
* `definition/prepare_run.clj`
* `definition/before_hook.clj`

### 8.3 `target/install.clj` 的治理方式

当前它是典型的 **bootstrap / reconcile 模块**。

它允许复杂，但只能复杂在：

* install contract
* layout reconcile
* bootstrap sequence

它不应该继续复杂在：

* CLI surface
* sample behavior
* definition authoring

未来建议拆分轴固定为：

* `target/layout.clj`
* `target/install_manifest.clj`
* `target/agent_home_install.clj`
* `target/task_type_install.clj`

### 8.4 `management/task_type_manager.clj` 的治理方式

当前它是典型的 **lifecycle manager**，但已经混入 authoring。

治理方法不是简单拆文件，而是先固定内部子责任：

* registry
* meta
* inspect
* authoring scaffold

未来建议拆分轴固定为：

* `management/task_type_registry.clj`
* `management/task_type_inspector.clj`
* `management/task_type_authoring.clj`

### 8.5 `state/run_store.clj` 的治理方式

当前它是 **runtime record + candidate carrier store**。

它未来很可能继续变复杂，因为 candidate lifecycle 会长在这里附近。

治理方式应是：

* 允许它复杂
* 但只复杂在 run materialization / finalize / candidate metadata

它不应继续承接：

* workflow routing
* review policy
* integration policy

未来建议拆分轴固定为：

* `runtime/run_prepare.clj`
* `state/run_store.clj`
* `runtime/candidate.clj`

## 9. 当前项目应采用的治理动作

如果要把这份治理设计落到当前 `preproject_stage3_definition_externalization`，我建议先执行下面 5 个动作。

### Action 1. 固定模块类别和层级，不再随意新增“杂项模块”

以后新增模块时，必须先回答它属于：

* support
* target
* state
* definition
* management
* runtime integration
* workflow control
* sample/debug

不能先写文件，再事后解释。

### Action 2. 把 `sample` 和正式控制面彻底隔离

第一步建议就是：

* 把 `smoke!` 从 [workflow.clj](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/core/workflow.clj#L136) 移走
* 把 sample/demo 只保留在 sample 或 test command

这是当前最容易做、收益也最大的治理动作。

### Action 3. 把复杂模块的拆分轴先写死，再继续加功能

不是立刻拆，而是先在设计上固定：

* `workflow` 如何拆
* `install` 如何拆
* `task_type_manager` 如何拆
* `definition/task_type` 如何拆

否则功能会继续往热点模块里堆。

### Action 4. 对高变模块采用“依赖增量审查”

当前如果继续开发，我建议每次改动下面这些文件时都问一遍：

* 有没有新增跨层依赖
* 有没有混入第二主职责
* 有没有把 debug/demo 逻辑带进正式主线

适用文件：

* `core/workflow.clj`
* `target/install.clj`
* `management/task_type_manager.clj`
* `definition/task_type.clj`
* `state/run_store.clj`

### Action 5. 低变模块不承接试验逻辑

后面任何试验性需求，都不应该优先塞进：

* `paths`
* `util`
* `core/model`
* `task_lifecycle`
* `target/repo`

这些模块应保持稳定和瘦身。

## 10. 一句话总结

基于当前 `preproject_stage3_definition_externalization`，最重要的治理结论不是“把代码拆小”，而是：

```text
把项目治理成
  稳定底座 + 高变热点 + 明确扩展模板 + 受控复杂模块
```

更具体地说：

* `support / target substrate / thin stores` 作为稳定底座
* `workflow / install / task_type management / definition interpreter / run carrier` 作为高变热点
* 用清楚的依赖规则和同类模块模板控制扩展
* 对复杂模块采取“允许复杂，但只复杂在一个地方”的治理方法

如果这套治理能落下去，`preproject_stage3_definition_externalization` 就不会只是一个能跑的 spike，而会成为一个可以严谨生长的项目底座。
