# Refine: Final System Layering And Module Split

这份文档基于 `design.md`、`architecture.md` 和 `preproject_stage1_minimal_workflow` 的 learnings，回答一个更接近正式实现的问题：

**如果把这个系统继续做下去，最终应该如何分层，如何切分模块？**

这次不先谈内部代码组织，而是先固定三件外部边界：

* `toolchain` 的职责
* workflow 状态保存在哪里
* workflow 最终作用于什么 target

如果这三件事不先讲清楚，内部的 `workflow/domain/runtime/storage` 分层会天然漂移。

## 1. 最外层切分

最终系统最先应该固定的，不是内部模块，而是：

```text
System
  = Toolchain
  + Target
```

更具体一点：

```text
toolchain/
  implementation

target/
  git repository
  workflow installation target
  workflow runtime target
```

这条切分必须进入正式设计，而不只是 spike 里的目录约定。

## 2. Target 的正式假设

我建议正式系统直接固定一个更强的假设：

```text
target = the git repository being worked on
workflow state = installed into that repository
```

也就是说，workflow 不是操作一个 repo 旁边的抽象目录，而是直接安装进目标仓库。

这会带来几个非常直接的好处：

* repo head / worktree / commit 这些语义天然成立
* install 行为更像“把 workflow 能力装进一个 repo”
* toolchain 和 target 的关系更清晰
* workflow 状态跟着目标仓库走，而不是跟着 toolchain 目录走

## 3. Toolchain 的职责

`toolchain` 不是“一个大平台”，而是 **workflow substrate + workflow controller**。

它的职责应固定为：

* 校验 target 是 git 仓库
* 安装 workflow 所需状态到 target
* 在 target 中写入 workflow metadata 和 ignore rules
* 读取和校验 task_type
* 读取和推进 collection/task/run 状态
* 暴露 workflow command / CLI
* 调用 mgr runtime
* 调用 worker runtime
* 执行 prepare_run
* 管理 run runtime assets
* 把 workflow 结果持久化到 target

它不负责：

* 定义 agent runtime 内部能力语义
* 承担企业级平台治理
* 替代项目管理系统
* 替代 CI/CD

换句话说：

```text
toolchain 负责 workflow 的安装、推进、接管和落盘
```

而不是：

```text
toolchain 负责所有 agent 和研发平台语义
```

## 4. 状态保存位置

workflow 状态不应该散落在 toolchain 自己目录中。

更稳的原则是：

* `toolchain` 是实现
* `target` 是一个 git 仓库
* `target` 是 installation target
* `target` 也是 runtime target
* workflow 的安装态和运行态都应作为该 git 仓库内的一部分落到 target 中

因此，以下内容都应持久化在 target 下：

* install metadata
* target metadata
* `.gitignore` updates
* task_type artifacts
* collections
* tasks
* runs
* mgr runs
* agent_home copies or references
* run worktrees

这点不是实现细节，而是最终架构的一部分。

## 5. Target 的内部形态

建议把 target 内部抽象成：

```text
target/
  .git/
  source tree...
  .workflow/
    install.edn
    target.edn
    task_types/
    collections/
    tasks/
    runs/
    mgr_runs/
    agent_homes/
  .gitignore
```

这里的关键点是：

* 源码树本身就是 workflow 作用对象
* `.workflow/` 是 toolchain 安装进去的状态根
* `.gitignore` 由 install 负责补齐合理规则

正式名字以后可以再收敛，但这层关系应该尽量稳定。

## 6. install 的正式语义

`install` 不只是“创建几个目录”，而是把 workflow 能力安装进一个 git 仓库。

它至少应完成：

* 校验 target 是 git repo
* 创建 workflow state root
* 写 install metadata
* 写 target metadata
* 安装 task_type artifacts
* 安装 agent_home copies 或 references
* 更新 `.gitignore`

`.gitignore` 的职责也应正式进入设计。

它至少要保证下面这些内容不会污染目标仓库的正常版本控制体验：

* runtime worktree
* run outputs
* 临时日志
* runtime snapshots

至于哪些 durable workflow state 要不要入库，是后续策略问题；但 install 负责维护 ignore rules 这件事，应当成为正式职责。

## 7. 正式系统目标

目标不是把系统做成通用 workflow engine。

目标是做一个足够薄、但结构稳定的 **task-type driven coding agent toolchain**，让一批同类 coding tasks 可以：

* 被持续推进
* 被明确记录
* 被中断恢复
* 被不同 agent runtime 执行
* 被不同 task_type 复用
* 在明确 git target 上安装、运行和恢复

系统应优先保证：

* 核心模型简单
* 控制权边界清晰
* toolchain / target 边界清晰
* task_type 可以扩展
* agent runtime 可以替换
* run 作为 runtime 容器是一等概念

## 8. Toolchain 内部分层

在固定 `toolchain / target` 外层切分之后，toolchain 内部建议再分成 6 层：

```text
L1. Product Surface Layer
L2. Workflow Control Layer
L3. Domain State Layer
L4. Task-Type Definition Layer
L5. Runtime Integration Layer
L6. Target Integration Layer
```

关系可以写成：

```text
User / CLI / API
  -> Workflow Control
  -> Domain State
  -> Task-Type Definition
  -> Runtime Integration
  -> Target Integration
```

### L1. Product Surface Layer

只负责暴露入口。

包括：

* CLI
* 可选 TUI / Web UI
* 外部自动化入口

它不负责：

* 决定 workflow
* 解释 task_type
* 直接 launch worker

### L2. Workflow Control Layer

这是核心编排层。

它负责：

* 选择可推进 task
* 调用 mgr
* 接收 mgr 决策
* 通过 workflow command/CLI 重新接管控制权
* 驱动 prepare_run
* 驱动 worker launch
* 推进 task 状态

它不是状态存储层，也不是 runtime adapter 层。

### L3. Domain State Layer

只负责核心对象和状态语义。

包括：

* task_type
* collection
* task
* run
* task lifecycle
* run lifecycle

它不负责：

* prompt 渲染
* 脚本执行
* 目录布局
* 进程启动

### L4. Task-Type Definition Layer

承载“同类任务如何推进”的定义。

包括：

* mgr prompt
* worker prompts
* allowed workers
* stop conditions
* review/refine 协议
* prepare_run hook
* routing rules

它决定的是：

**某类 task 如何从抽象状态落地成具体 run。**

### L5. Runtime Integration Layer

承载和 agent runtime 的接缝。

包括：

* agent_home 引用传递
* mgr launch
* worker launch
* prompt 输入
* stdout/stderr/output 收集
* runtime-specific option mapping

它不应理解业务状态。

### L6. Target Integration Layer

这一层负责所有“对 target 的实际读写和操作”。

包括：

* target ref
* install/reset
* target metadata
* `.gitignore` 维护
* state store
* run 目录布局
* worktree 操作
* shell/script 执行
* 文件落盘

它不应承载 workflow 决策。

## 9. 最终核心对象

建议最终系统只把以下对象视为一等对象：

```text
target_ref
task_type
collection
task
run
agent_home_ref
```

其他东西都不应成为一级抽象。

例如：

* `mgr` 是角色，不是持久化核心对象
* `worker` 是角色，不是持久化核心对象
* `launcher` 是 runtime integration 组件，不是 domain 对象
* `worktree` 是 `run` 的 runtime asset，不是独立业务对象
* `target` 是整个 workflow 的宿主 git repo

### 9.1 `target_ref`

建议显式建模 target 引用。

它至少应包含：

* target root path
* git root path
* workflow state root path
* optional target kind

它的意义是：

* toolchain 永远在“对某个 target 操作”
* 各模块不应假设全局单例目录

### 9.2 `task_type`

`task_type` 是协议对象。

它至少需要定义：

* id
* mgr home reference
* worker homes
* allowed workers
* mgr prompt template
* worker prompt templates
* prepare_run hook
* stage transition rules
* terminal / error conditions

### 9.3 `collection`

`collection` 是同类 task 的调度上下文。

建议它只承载：

* id
* task_type id
* name
* metadata
* scheduling policy

不要让它膨胀成项目管理系统对象。

### 9.4 `task`

`task` 是流程状态机实例。

建议 task 至少包含：

* id
* collection id
* goal
* current stage
* latest run id
* latest mgr decision
* repo head / task head
* counters
* terminal state
* timestamps

### 9.5 `run`

`run` 是一次 worker 执行的 runtime container。

这是最终系统里最需要明确强化的对象。

建议 `run` 至少包含：

* id
* task id
* worker role
* launcher kind
* status
* input head
* output head
* worktree path
* prompt path
* output path
* launch metadata
* timestamps

### 9.6 `agent_home_ref`

工具链层只需要一个引用。

建议 `agent_home_ref` 只包含：

* logical name
* path
* maybe runtime kind

不要在工具链核心层建模：

* instruction
* tools
* permissions
* context policy

这些属于 runtime 自己的语义。

## 10. 最终模块切分

最终系统建议拆成下面这些模块：

```text
surface/
workflow/
domain/
task_types/
runtime/
target/
```

再展开一层：

```text
surface/
  cli/
  optional ui/

workflow/
  scheduler
  mgr_control
  task_advance
  recovery

domain/
  task_type_model
  collection_model
  task_model
  run_model
  lifecycle_rules

task_types/
  registry
  prompt_loader
  prepare_run
  routing_rules

runtime/
  mgr_runtime
  worker_runtime
  launcher_interface
  codex_adapter
  future adapters

target/
  target_ref
  target_metadata
  gitignore
  state_store
  run_store
  collection_store
  install
  target_repo
  shell
```

## 11. 每个模块的职责

### 11.1 `surface.cli`

CLI 应成为正式系统的第一产品接口。

至少要有这些命令族：

* install
* collection
* task
* run
* workflow
* recover

其中最关键的是 workflow 接管命令，例如：

```text
workflow advance
workflow fail
workflow retry
```

`mgr` 不应该拿到底层 launcher，而应该调用这类命令。

### 11.2 `workflow.scheduler`

负责：

* 从 collection 中选 task
* 判断 task 是否 runnable
* 决定何时调 mgr
* 控制 run loop

它不直接写 target 文件，也不直接做 runtime 调用。

### 11.3 `workflow.mgr_control`

负责：

* 构造 mgr 输入上下文
* 调 mgr runtime
* 记录 mgr run
* 接收 mgr 通过 workflow CLI 回传的推进结果

重点是：

**mgr 只负责判断，真正推进 task 的命令仍由 workflow 层掌控。**

### 11.4 `workflow.task_advance`

这是最关键的用例模块。

它负责：

* 校验 task 当前状态
* 读取 task_type
* 执行 prepare_run hook
* 创建 run
* 调用 worker runtime
* 写回 run
* 更新 task

这部分应是系统最稳定的核心流程，不应散落在 CLI 或 runtime adapter 里。

### 11.5 `workflow.recovery`

正式系统必须把恢复语义做成独立模块。

它负责：

* 扫描 incomplete runs
* 判断可恢复状态
* 标记 orphaned runs
* 重新驱动 task

不建议把恢复逻辑混在 scheduler 或 target state store 里。

### 11.6 `domain.*`

domain 模块只负责：

* 数据结构
* 生命周期规则
* transition validation

例如：

* task 何时可进入 terminal
* review result 如何映射成下一个 stage
* run status 如何流转

这部分应该是最纯的一层。

### 11.7 `task_types.registry`

需要一个明确 registry，负责：

* 加载 task_type 定义
* 校验 task_type 是否完整
* 提供 task_type 给 workflow 层使用

不要把 task_type 读取逻辑散在多个模块里。

### 11.8 `task_types.prepare_run`

这是 spike 之后必须正式化的模块。

prepare_run 应作为 task_type 的一等 hook 存在。

它负责：

* 选择 input head
* 选择 worktree strategy
* 生成 prompt inputs
* 绑定 worker home
* 预创建 run runtime assets

换句话说：

**prepare_run 是 task_type 将抽象 task 落地成具体 run 的桥。**

### 11.9 `runtime.launcher_interface`

建议做一个很薄的 launcher 接口层。

它只需要抽象：

* launch mgr
* launch worker
* collect output

不要在这里做 workflow 决策。

### 11.10 `runtime.codex_adapter`

Codex 应该只是一个 runtime adapter。

它负责：

* 把 `agent_home_ref` 翻译成 `CODEX_HOME`
* 设置 cwd
* 传入 prompt
* 收集 last message / stderr / stdout

不要让 codex adapter 直接读 task state 或决定下一步。

### 11.11 `target.target_ref`

这个模块应成为所有 target 路径和 target 元信息的统一入口。

它负责：

* 解析 target root
* 暴露 git root / state root / install root
* 避免路径规则散落在系统各处

### 11.12 `target.target_metadata`

这个模块负责目标仓库内的 workflow 元信息。

它负责：

* install metadata
* target metadata
* workflow version marker

它的作用是让一个 git repo 明确知道自己是否已被该 workflow toolchain 安装。

### 11.13 `target.gitignore`

建议单独有一个很薄的模块负责 `.gitignore` 维护。

它负责：

* 声明哪些 workflow state 应忽略
* 更新目标仓库的 ignore entries
* 避免 install 重复写入脏内容

这样可以把 ignore 规则从 install 过程里显式提出来。

### 11.14 `target.state_store`

建议显式拆出 target 内的 state store。

它负责：

* collection 读写
* task 读写
* run 读写
* mgr run 读写

这里不应包含业务规则，只负责 target 内状态持久化。

### 11.15 `target.target_repo`

目标仓库 / worktree / git 相关逻辑应该集中在一个模块。

它负责：

* 校验 target 是 git repo
* input head 查询
* detached worktree create/remove
* commit output
* reset/prune

这部分不应和 task store 混在一起。

### 11.16 `target.install`

install 应保留独立模块。

它负责：

* 安装 toolchain state
* 安装 task_type
* 安装 prompts
* 安装 agent_home copies
* 写 target metadata
* 更新 `.gitignore`
* reset target

install 本质上是 target lifecycle，不是 workflow runtime。

## 12. 建议保留的控制权边界

### 12.1 `mgr` 只拿 workflow command，不拿 launcher

这是最需要坚持的边界。

`mgr` 应只拥有：

* workflow advance command
* workflow fail command

不要让 `mgr` 直接拿：

* worker launch API
* state store write API
* worktree API

否则 `mgr` 会变成 runtime controller。

### 12.2 workflow command 一旦被调用，控制权立刻回到 workflow 层

这意味着：

* `mgr` 负责做出 decision
* workflow command 负责执行 decision
* command 背后可以触发 `prepare_run -> launch worker -> update task`

这个边界比“mgr 输出纯文本 decision，外层再自己理解”更稳。

### 12.3 runtime adapter 不做业务判断

runtime adapter 只做：

* 参数映射
* 进程启动
* 输出采集

任何 task 语义都应停留在 workflow/domain/task_type 层。

### 12.4 target integration 不做 workflow 判断

target 层只做：

* 路径规则
* 目录布局
* 持久化
* repo/worktree 操作

不要把 task 推进规则混到 target 层。

## 13. 建议的目录组织

如果未来真的开始正式实现，我建议目录组织接近这样：

```text
src/
  surface/
    cli/
  workflow/
    scheduler/
    mgr_control/
    task_advance/
    recovery/
  domain/
    task_type/
    collection/
    task/
    run/
  task_types/
    registry/
    prompt/
    prepare_run/
  runtime/
    launcher/
    codex/
  target/
    ref/
    metadata/
    gitignore/
    state/
    repo/
    install/
    shell/
```

如果项目规模不大，也可以压成：

```text
src/
  surface/
  workflow/
  domain/
  task_types/
  runtime/
  target/
```

但职责边界最好不要消失。

## 14. 不应该提前引入的模块

### 14.1 通用 dispatcher

这和当前设计方向冲突。

系统已经选择：

* 用 `mgr` 作为编排胶水
* 用 workflow command 作为控制权回收点

再单独引入 dispatcher，会重新制造一个中间编排角色。

### 14.2 通用 plugin system

正式系统当然可以有扩展点，但不要过早抽象成 plugin platform。

短期只需要：

* task_type registry
* runtime adapter interface

就够了。

### 14.3 复杂事件总线

当前系统规模不需要。

状态推进本身已经足够明确：

* task
* run
* mgr decision
* workflow advance

没有必要在早期引入额外事件系统。

### 14.4 企业级权限系统

这是 runtime 和部署环境后期才需要考虑的问题，不应进入当前核心架构。

## 15. 我建议的最终主线

如果只保留一句话，我建议最终系统按下面这条主线收敛：

```text
Toolchain acts on a Target.
Target is a Git repository.
Workflow state is installed into that repository.
Domain keeps task/run semantics pure.
TaskType defines how tasks become runs.
Mgr decides.
Workflow command regains control.
Runtime adapters only launch agents.
Target layer persists state and repo assets.
```

翻成更直接的话就是：

* toolchain 负责安装、推进、接管和调度
* target 是被作用的 git 仓库
* workflow state 被安装进 target
* install 负责写 metadata 和 `.gitignore`
* domain 负责语义
* task_type 负责协议
* mgr 负责判断
* workflow 负责执行判断
* runtime 负责启动 agent
* target 层负责落盘和 repo/worktree 资产

这样收敛后，外层系统边界和内层模块边界都会更稳定。
