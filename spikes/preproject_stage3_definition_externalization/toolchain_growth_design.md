# Toolchain Growth Design After preproject_stage3_definition_externalization

这份文档回答一个在 `preproject_stage3_definition_externalization` 之后必须正面处理的问题：

**如果继续沿着当前方向推进，这个 toolchain 会如何生长？它应该产出哪些正式能力，而不应该长成什么？**

这份文档不是 roadmap，也不是立即实现清单。

它的目标是：

* 固定 `preproject_stage3_definition_externalization` 之后的生长方向
* 区分“必须补齐的内核能力”和“可以后做的扩展能力”
* 避免系统在下一轮演化中漂移成通用 workflow engine 或泛平台

## 1. 起点

基于 [design.md](/home/mikewong/proj/main/vibe-flow/docs/design.md)、[refine.md](/home/mikewong/proj/main/vibe-flow/docs/archives/refine.deprecated.md) 和 [spikes/preproject_stage3_definition_externalization/learnings.md](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/learnings.md)，`preproject_stage3_definition_externalization` 已经让下面这些边界开始稳定：

* `toolchain` 是 workflow control plane 的实现
* `target` 是 git repository，也是 workflow installation target 和 runtime target
* definition layer 已经开始从代码内建收敛到 target 内已安装 artifact
* `task_type` 开始从“代码常量”升级成“target 内 managed definition package”
* `run` 已经不只是记录，而是 runtime container 和 candidate change carrier
* 当前系统负责生成 candidate change，但不负责自动集成回 target 主分支

因此，`preproject_stage3_definition_externalization` 之后的关键问题已经不是：

* 这条主干能不能跑

而是：

* 这条主干如何继续长成正式系统

## 2. 核心判断

我对后续生长方向的判断是：

```text
toolchain 应该继续长成
  task-type driven coding agent toolchain

而不是长成
  generic workflow platform
```

更准确地说，后续演化应继续强化下面这些特征：

* definition layer externalized
* control plane built-in
* target-managed models formalized
* runtime integration standardized
* candidate change lifecycle clarified
* product surface operationalized

同时应避免下面这些漂移：

* 不为了通用性把系统做成抽象 workflow engine
* 不为了平台感过早引入过重 plugin / marketplace / governance 体系
* 不把工具链升级成企业级研发平台

## 3. toolchain 会长出的核心能力

`preproject_stage3_definition_externalization` 之后，toolchain 的生长主要会围绕 5 组能力展开：

1. target 内正式模型管理
2. workflow 控制与恢复
3. runtime 集成适配
4. candidate change 到 integration 的生命周期管理
5. 面向用户的产品面和可观察性

这 5 组能力不是并列 feature list，而是同一个系统逐步正式化时必然长出的部分。

## 4. 第一组生长：Target Model Management

这是最应该优先长出来的能力。

`preproject_stage3_definition_externalization` 已经证明，target 内对象不能再被混在一起理解成“几个文件和目录”。  
toolchain 要继续长下去，就必须把 target 内模型管理做正式。

### 4.1 会长出的正式模型分类

后续系统会继续固定下面这组分类：

* `system models`
* `definition models`
* `domain models`
* `runtime models`

对应地，toolchain 会长出下面这些管理能力：

* system model inspect / validate / reconcile / migrate
* definition model create / update / deprecate / remove-if-safe
* domain model create / update / cancel / archive / inspect
* runtime model prepare / finalize / gc / recover

### 4.2 `task_type` 会长成真正的 definition package system

`preproject_stage3_definition_externalization` 现在只是让 `task_type` 初步进入 managed package 形态。

后面它会继续长出：

* `update-task-type`
* `deprecate-task-type`
* `remove-task-type-if-safe`
* `validate-task-type`
* `reconcile-task-type-layout`
* `task_type` 引用检查
* `task_type` version / status / checksum 演化

也就是说，`task_type` 不会只停留在：

* bundle copy
* inspect
* create skeleton

而会继续长成：

```text
definition package
  + management lifecycle
  + registry/index
  + layout contract
  + validation rules
```

### 4.3 registry 会从“方便查看”长成“正式管理索引”

当前 registry 已经出现，但还很薄。

后面它会继续承担：

* 列表索引
* layout 和 logical model 对齐检查
* safe remove 前置校验
* migration 入口
* definition source tracking
* install / reconcile 的一致性检查

也就是说，toolchain 后续会显式区分：

```text
storage directory
  = artifact storage

registry
  = management index
```

### 4.4 install 会长成 reconcile / migrate 入口

`install` 不会永远只表示“一次性安装”。

随着 target model 正式化，它自然会长成：

* install target
* validate target
* reconcile target layout
* migrate target state
* cleanup legacy runtime state

这意味着 install 的正式语义会进一步收紧为：

```text
install
  = target state bootstrap
  + target state reconcile
  + target state migration entry
```

## 5. 第二组生长：Workflow Control And Recovery

第二组会长出的，是把当前能跑的 workflow 主干变成更完整的正式控制面。

### 5.1 command surface 会继续扩展

当前 CLI 主要还围绕 spike 验证组织。

后面 toolchain 会自然长出一组更完整的操作面，例如：

* `list-collections`
* `inspect-collection`
* `create-collection`
* `list-tasks`
* `inspect-task`
* `create-task`
* `cancel-task`
* `retry-task`
* `list-runs`
* `inspect-run`
* `list-mgr-runs`
* `gc-runtime`
* `validate-target`

这组命令的意义不是“命令越多越好”，而是：

* 把 target 内状态变成可操作对象
* 把当前需要读文件才能确认的信息，提升成正式 product surface

### 5.2 recovery 会成为正式能力

spike 阶段可以接受“中断了就重新跑”。

正式系统不能长期停留在这个水平。

后面 toolchain 会需要正式回答：

* 如何识别未完成的 `run`
* 如何识别未完成的 `mgr_run`
* 如何恢复可继续推进的 task
* 如何识别 runtime 残留与脏状态
* 如何决定 replay / retry / abandon

所以 recovery 最终会长成至少下面几类能力：

* recover target runtime state
* inspect unfinished runs
* retry failed task / run
* gc abandoned runtime assets

### 5.3 observability 会从工程视角长成产品视角

当前 `show-state` 更像工程调试入口。

后面这层会生长成：

* task timeline
* latest run summary
* latest review summary
* candidate change summary
* failure reason summary
* task readiness / blocking reason

也就是说，toolchain 会逐渐提供：

```text
inspection views
  rather than
  raw file dumps only
```

## 6. 第三组生长：Runtime Integration

这部分能力不是 workflow 语义本身，但如果不长出来，toolchain 仍然会停留在 demo 水平。

### 6.1 launcher 会长成正式 adapter layer

当前主要有：

* `mock`
* `codex`

后面 runtime integration 会进一步收敛成一个正式 adapter 层，负责：

* 不同 runtime 的启动协议
* `agent_home` 绑定方式
* prompt 传递方式
* output 落盘方式
* runtime metadata 记录方式
* capability / limitation 声明

也就是说，后续并不是简单增加几个 shell command，而是会形成：

```text
runtime adapter
  = launcher contract
  + runtime-specific binding
```

### 6.2 `agent_home` 管理会继续正式化

当前 install 已经把 `agent_home` copy 进 target。

后面这部分还会长出：

* home source tracking
* refresh / reinstall home
* inspect installed home
* validate required files
* runtime compatibility checks

这不是因为工具链要理解 `agent_home` 内部语义，而是因为：

* toolchain 必须能稳定绑定 runtime inputs

## 7. 第四组生长：Candidate Change Lifecycle

这是 `preproject_stage3_definition_externalization` 之后最关键、但尚未解决的一块。

真实 `codex` 已经证明：

* worker 可以在 run worktree 中产出 candidate change
* review 可以基于 candidate change 给出 `pass`

但当前系统没有正式回答：

* candidate change 如何被定义
* review pass 之后如何进入 integration
* integration 由谁负责
* integration 是否自动发生

### 7.1 `run` 会继续长成 candidate carrier

当前 `run` 已经携带：

* worktree
* output head
* output
* prompt
* runtime metadata

后面它会继续长出更明确的 candidate 语义，例如：

* candidate status
* candidate head
* candidate base
* candidate diff summary
* candidate integration state

这意味着 `run` 不只是 worker 执行记录，而会进一步承担：

```text
candidate change carrier
```

### 7.2 integration layer 会成为独立问题域

这部分不一定马上实现，但迟早要进入正式设计。

至少会出现下面几种选择之一：

* workflow 只产出 candidate，由外部人或工具执行 integration
* workflow 提供显式 `apply-candidate` / `merge-candidate` 命令
* review pass 后进入 integration queue，再由单独步骤消费

无论选哪条路，toolchain 最终都需要把下面这些问题正式化：

* integration policy
* integration actor
* integration preconditions
* integration failure handling

## 8. 第五组生长：Product Surface

如果前面几组能力长出来，toolchain 最后自然会长出更像“产品”的表面。

### 8.1 CLI 会从 spike command set 长成正式 control surface

这意味着：

* 命令按模型类型组织
* inspect 输出更稳定
* 不再依赖直接看 EDN 文件判断状态
* 有更明确的 user-facing success / failure message

### 8.2 可能会出现轻量 UI，但不应先于内核能力

以后可以有：

* TUI
* very light web UI
* summary dashboard

但这类 surface 不应先于：

* target model management
* recovery
* candidate lifecycle

否则只是在给 spike 加壳。

## 9. 它不会健康地长成什么

为了防止方向漂移，必须明确下面这些不是优先生长方向：

### 9.1 不应长成通用 workflow engine

如果开始引入：

* 任意节点图
* 任意插件编排
* 任意状态机 DSL
* 与 coding agent 场景无关的抽象

那系统很容易失去当前最有价值的边界。

### 9.2 不应长成企业级平台

例如：

* 重权限体系
* 重审批体系
* 重组织管理
* 大而全的多租户平台层

这些都不是当前工具链要解决的问题。

### 9.3 不应过早长成“plugin universe”

定义层外置不等于现在就要做完整 marketplace / plugin ecosystem。

在真正做这些事情之前，应该先把下面这些基础能力做稳：

* definition package lifecycle
* registry
* reconcile
* validation
* recovery

## 10. 按阶段看，toolchain 最可能这样生长

为了避免“什么都重要”，可以把后续演化分成 4 个阶段。

### Phase A: Completion

先把 `preproject_stage3_definition_externalization` 已经露头的内核补完整。

这一阶段最应该产出的功能：

* `task_type update / deprecate / remove-if-safe`
* registry 和 layout reconcile
* install validate / migrate
* inspect task / run / collection
* runtime gc / recover

### Phase B: Integration

把 candidate change 的生命周期正式化。

这一阶段最应该产出的功能：

* candidate record / candidate summary
* integration policy
* apply / merge / integrate commands
* review-to-integration transition

### Phase C: Product Surface

把命令和 inspection 打磨成正式工具面。

这一阶段最应该产出的功能：

* 更稳定的 CLI
* explain / dry-run 能力
* summary views
* clearer failure reporting

### Phase D: Distribution

最后才考虑更高层的复用和协作。

这一阶段可能产出的功能：

* task_type versioning
* definition distribution
* multi-target management
* team-oriented usage pattern

## 11. 一句话总结

`preproject_stage3_definition_externalization` 之后，toolchain 最合理的生长方向不是继续堆更多 worker 能力，而是把已经出现的几个核心概念做正式化：

```text
task_type
  -> grows into a managed definition package system

target/.workflow
  -> grows into a formal target model system

run
  -> grows into a candidate change carrier

install
  -> grows into bootstrap + reconcile + migrate entry

CLI
  -> grows into a real workflow control surface
```

如果这条路径走通，toolchain 会从 spike 演化成一个最小但结构稳定的正式系统。
