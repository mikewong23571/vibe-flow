# preproject_stage1_minimal_workflow learnings

这份文档记录 `preproject_stage1_minimal_workflow` 的主要结论。

目标不是总结代码，而是沉淀这轮 spike 对整体设计的启发。

## 结论概览

`preproject_stage1_minimal_workflow` 已经验证了这条最小 workflow 是可行的：

```text
task
  -> mgr 判断下一步
  -> mgr 调用 workflow CLI
  -> workflow 接管控制权
  -> prepare run
  -> launch worker
  -> 写 run / 更新 task
  -> 继续下一轮，直到 done 或 error
```

这说明设计里最关键的不确定性已经基本消除：

* `task` 可以真实落成一串 `run`
* `run` 适合作为 worker 的 runtime 容器
* `worktree` 放在 `run` 下是合理的
* `agent_home` 可以保持为工具链层的 opaque directory
* `mgr` 可以是独立 agent
* `mgr` 不需要直接拿 worker launcher
* `mgr` 调 workflow CLI，由 workflow 重新接管控制权，这个模型成立

## 被验证的设计点

### 1. `task` 的运行时体现是一串 `run`

这轮 spike 证明，`task` 在工具链里不需要再被实现成更复杂的东西。

更准确的理解是：

```text
task = 一个有状态的推进单元
run = task 上的一次具体执行
task lifecycle = 一串按 task_type 协议推进的 runs
```

也就是说，设计里最初“task 最终如何落地”的问题，已经有了明确答案。

### 2. `run` 不只是记录，而是 runtime envelope

这是本轮最重要的收敛之一。

`run` 不应该只是：

* 谁执行了
* 什么时候执行了
* 输出了什么

`run` 还应该拥有：

* worktree
* prompt
* output
* launch metadata
* 对应的 worker runtime 上下文

在 coding agent 场景里，这样的 `run` 模型明显比“纯日志记录”更贴近真实执行形态。

### 3. `worktree` 放在 `run` 下是合理的

实践下来，更自然的关系是：

```text
task
  -> many runs
run
  -> one worktree
```

而不是：

```text
task
  -> one shared worktree
```

原因是：

* 同一个 task 会经历 impl/review/refine 多轮执行
* 不同 run 的上下文和职责边界应该清晰
* 每个 run 拥有自己的 worktree，更容易隔离、调试、记录和恢复

### 4. worker 的主要业务可见范围应该是 run worktree

这轮 spike 支持这个方向。

更准确的表达不是“绝对只能看到 worktree”，而是：

* worker 的主要业务上下文应被限制在 run worktree 内
* 额外开放的目录应该尽量少，只保留 runtime 必需项

这有助于：

* 降低上下文泄漏
* 让行为更可复现
* 明确 worker 责任边界

### 5. `agent_home` 在工具链层应保持 opaque

这轮 spike 支持了这个边界。

工具链层只需要知道：

* launch 时需要传入哪个 `agent_home`
* runtime 使用 `CODEX_HOME` 或等价机制消费它

工具链层不需要理解：

* instruction
* tools
* permissions
* context policy

这些属于 agent runtime 自己的语义，不属于 workflow 核心模型。

### 6. `mgr` 可以是独立 agent

这轮 spike 不只是验证了 worker，还验证了 `mgr` 本身可以独立运行。

这意味着设计里“mgr 不是代码内部分支，而是独立 agent”这个方向是成立的。

### 7. `mgr` 不应直接拿 worker launcher

这是 spike 后最清晰的边界之一。

更合理的职责划分是：

* `mgr` 负责判断下一步
* `workflow` 负责把判断落地

如果 `mgr` 直接拿 worker launcher，它会开始侵入 runtime 细节，例如：

* 如何 prepare run
* 如何选择 worktree
* 如何绑定 agent_home
* 如何写 run 记录
* 如何处理 task 更新

这会把 `mgr` 从轻编排 agent 推向 runtime/controller，偏离设计初衷。

### 8. `mgr -> workflow CLI` 是目前最自洽的控制权边界

这轮 spike 最关键的结论就是这个。

正确的控制链不是：

```text
mgr -> direct worker launcher
```

而是：

```text
mgr -> workflow CLI -> workflow regains control
```

这意味着：

* `mgr` 只需要调用一个工具
* 这个工具本质上是 workflow 暴露出来的命令行入口
* 一旦 `mgr` 调用它，后续控制权立刻回到 workflow
* workflow 负责完成 `prepare_run / launch worker / write run / update task`

这个边界既保留了 `mgr` 的独立性，也避免引入额外 dispatcher 概念。

## 被修正的认识

### 1. 不需要额外引入 dispatcher 概念

spike 过程中，一个自然想法是：

* `mgr` 只负责把状态推进成 `prepared run`
* 再由独立 dispatcher 扫描并 launch worker

但这和最开始想要的“用 mgr 取代额外编排角色”是冲突的。

经过收敛后，更合适的做法是：

* 把“状态推进工具”直接暴露给 `mgr`
* 这个工具以 workflow CLI 的形式存在
* `mgr` 调用它时，workflow 直接接管后续执行

所以本轮 spike 的结论是：

* 不需要单独再造 dispatcher
* workflow CLI 就足以承担“重新拿回控制权”的作用

### 2. `mgr` 看到的不是“原始状态”，而是“可用于裁决的 task context”

如果 `mgr` 只看当前 `stage`，它其实和硬编码分支没有本质区别。

spike 后更合理的输入是：

* 当前 stage
* run count
* review count
* latest worker output
* latest review output
* recent run summary

这使得 `mgr` 至少具备最小的协议级判断能力，而不是纯路由表。

### 3. `install` 必须有明确 reset 语义

如果重新 install 时保留旧 run、旧 mgr_run、旧 worktree，新的 task 很容易误读上轮残留。

所以这轮 spike 也验证了一个实现层面的关键要求：

* install 需要把 prior runs、mgr_runs 和 detached worktrees 清理掉

否则 spike 结果会被历史状态污染。

## 当前被验证的最小架构

基于 `preproject_stage1_minimal_workflow`，目前最稳的一版架构可以写成：

```text
task_type
  -> defines task protocol
  -> defines how prepare_run should happen

task
  -> state machine instance
  -> many runs

mgr
  -> reads task context
  -> decides next action
  -> calls workflow CLI

run
  -> one worker execution
  -> runtime envelope
  -> owns worktree

workflow CLI
  -> regains control from mgr
  -> prepare_run
  -> launch worker
  -> persist run
  -> update task
```

## 对 `task_type` 的新理解

spike 之后，`task_type` 的意义变得更具体了。

它不只是定义：

* prompt 风格
* 阶段协议

它还应该定义：

* 这类 task 如何 prepare run
* 是否需要 worktree
* 用哪个 worker home
* worker 顺序与停止条件

也就是说，`task_type` 更像“这类任务如何落地为运行过程”的定义，而不是单纯 prompt 分类。

## 已知仍未回答的问题

这轮 spike 已经足够证明 workflow 可行，但仍有一些问题没有回答。

### 1. `run` lifecycle 还不完整

目前已经有 `run` 目录、`run.edn` 和结果记录，但还缺少更正式的 lifecycle，例如：

* prepared
* running
* completed
* error

如果后续要做恢复和故障处理，这部分需要补。

### 2. 恢复语义还没有正式定义

设计里强调“可中断恢复”，但 spike 目前只做到了：

* 状态有落盘
* run 有目录

还没有定义：

* 中断后从哪里恢复
* 如何识别半完成 run
* 是否允许重放 run
* 如何避免重复 launch

### 3. 并发策略没有验证

当前 spike 基本是单 task、串行推进。

还没有回答：

* 一个 collection 内能否并行多个 task
* 多个 run 是否可同时执行
* 多个 mgr 是否可并发裁决

### 4. `task_type.prepare_run` 还只是概念收敛

虽然 spike 已经证明了“prepare_run 应该属于 workflow/toolchain 控制链”，但目前还没有把它抽成明确可配置的 task_type hook。

这应该是下一阶段比较自然的演进点。

### 5. 权限和隔离策略还没有进入正式设计

当前 spike 为了优先验证 workflow，本质上采用了更放开的 codex 运行方式。

这说明：

* workflow 模型成立

但不说明：

* 最终权限设计已经定型

这部分应留到正式工程化阶段再讨论。

## 最终判断

`preproject_stage1_minimal_workflow` 已经足够支持以下判断：

* 这个 workflow 方向是可行的
* 设计的核心抽象没有走偏
* `run owns runtime/worktree` 是正确方向
* `mgr as independent agent` 是正确方向
* `mgr -> workflow CLI` 是当前最自洽的控制权边界

因此，`preproject_stage1_minimal_workflow` 可以视为一次成功的 feasibility spike。

下一步更合适的动作，不是继续扩 spike，而是把这些 learnings 反写回正式设计文档。
