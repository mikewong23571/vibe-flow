# preproject_stage2_toolchain_target_boundary learnings

这份文档记录 `preproject_stage2_toolchain_target_boundary` 的主要结论。

目标不是罗列实现细节，而是沉淀这轮 spike 对正式系统边界的启发。

和 [spikes/preproject_stage1_minimal_workflow/learnings.md](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage1_minimal_workflow/learnings.md) 相比，`preproject_stage2_toolchain_target_boundary` 的重点不再是“最小 workflow 能不能跑通”，而是：

* `preproject_stage1_minimal_workflow` 已验证的 workflow 主干
* 能否迁移到 `toolchain + target` 的更正式外层切分
* `target = git repository`
* workflow state 安装到 target 内部
* `task_type.prepare_run` 是否需要正式进入架构

## 结论概览

`preproject_stage2_toolchain_target_boundary` 已经验证，这条主干在 repo-internal target 架构下仍然成立：

```text
target git repo
  -> install workflow state into target/.workflow
  -> seed collection/task
  -> mgr 判断下一步
  -> mgr 调 workflow CLI
  -> workflow 接管控制权
  -> prepare run
  -> launch worker
  -> 写 run / 更新 task
  -> 继续下一轮，直到 done 或 error
```

这说明下面这些点已经有了更高置信度：

* `target` 作为 git 仓库宿主是成立的
* workflow state 安装到 target 仓库内部是成立的
* install 写 metadata 和 `.gitignore` 的模型是成立的
* `mgr -> workflow CLI -> toolchain regains control` 在新架构下继续成立
* `task_type.prepare_run` 需要被显式建模，而不应继续隐含在 toolchain 内部散落逻辑里

## 被验证的设计点

### 1. `System = Toolchain + Target` 这条最外层切分是成立的

`preproject_stage2_toolchain_target_boundary` 最大的收获不是某个内部模块，而是外层边界开始稳定。

更明确地说：

```text
toolchain
  = workflow implementation

target
  = the git repository being worked on
  = workflow installation target
  = workflow runtime target
```

这意味着：

* `toolchain` 不再自己承载 workflow state
* target 不再只是“旁边的 runtime 目录”
* workflow state 跟着目标仓库走，而不是跟着 toolchain 目录走

### 2. `target = git repository` 是可行的

这轮 spike 证明，把目标 git 仓库本身当作 workflow target，并不会破坏主干。

这带来几个被实践支持的好处：

* repo head / worktree / commit 语义天然成立
* install 更像“把 workflow 能力安装进一个 repo”
* target 的源码树和 workflow 作用对象天然重合
* workflow 的 durable state 有了稳定宿主

也就是说，`refine.md` 中提出的更强 target 假设，不只是纸面方向，而是可以落成工作实现。

### 3. workflow state 放进 `target/.workflow/` 是成立的

这轮 spike 验证了 repo-internal state root 的最小布局是清晰且可运行的。

当前最小稳定布局可以写成：

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
```

这不是纯目录设计，而是已经支撑 install、seed、run-loop、show-state 的实际运行。

### 4. install 的正式职责比 `preproject_stage1_minimal_workflow` 更清晰了

在 `preproject_stage2_toolchain_target_boundary` 里，install 不再只是“创建目录”。

更准确地说，install 的正式语义开始收敛成：

* 校验 target 是 git repo
* 创建 `.workflow/`
* 写 `install.edn`
* 写 `target.edn`
* 安装 task_type artifacts
* 安装 agent_home copies
* 更新 `.gitignore`
* reset prior runtime state，避免残留污染新一轮 spike

这说明 install 已经可以被视为正式系统的一等职责，而不是临时 setup 脚本。

### 5. `.gitignore` 进入正式模型是合理的

这轮 spike 明确支持一个结论：

* `.gitignore` 不是附属小事
* 它是 install 的正式职责之一

原因很实际：

* `.workflow/runs/` 里的 detached worktrees 不应污染目标仓库日常版本控制体验
* `.workflow/mgr_runs/`、`agent_homes/` 这类运行态目录也不应默认进入版本控制

至于哪些 durable state 最终要不要被提交，是后续策略问题；但 install 负责补齐 ignore rules，这件事本身已经被验证是合理的。

### 6. `mgr -> workflow CLI` 这条控制链在新架构下继续成立

这是 `preproject_stage2_toolchain_target_boundary` 最重要的继承性验证之一。

`preproject_stage1_minimal_workflow` 已经证明：

```text
mgr -> workflow CLI -> workflow regains control
```

`preproject_stage2_toolchain_target_boundary` 证明的是：

* 即使把 target 收紧成目标 git 仓库本身
* 即使把 workflow state 内置到 target/.workflow
* 这条控制链依然成立

这很关键，因为它说明 `preproject_stage2_toolchain_target_boundary` 在继承 `preproject_stage1_minimal_workflow` 的主干，而不是无意中推翻它。

### 7. `run` 作为 runtime container 的理解在 `preproject_stage2_toolchain_target_boundary` 中进一步稳定

这轮 spike 没有重新验证 `run` 的概念本身，但它验证了：

* `run` 持有自己的 worktree
* worktree 可以实际落在 `target/.workflow/runs/<run-id>/worktree`
* `run` 上仍然需要记录 prompt、output、prepare metadata、launcher metadata

这说明 `run` 不是 `preproject_stage1_minimal_workflow` 里的临时表达，而是在正式 target 架构下依然自然的一等概念。

### 8. `mgr_run` 保持独立记录是合理的

当前最简单但自洽的做法仍然是：

* `run` 记录 worker 执行
* `mgr_run` 记录 mgr 决策

这轮 spike 支持继续保留这个切分，而不急着把二者统一成一个更复杂的通用 runtime record。

至少在当前阶段：

* 这个 split 可读
* 这个 split 不妨碍控制权清晰
* 这个 split 有助于回看“mgr 决策”和“worker 执行”之间的边界

### 9. `task_type.prepare_run` 需要被显式建模

这是 `preproject_stage2_toolchain_target_boundary` 最重要的新结论之一。

在 `preproject_stage1_minimal_workflow` 里，run 的准备逻辑已经存在，但更多还是隐含在 toolchain 内部。

到了 `preproject_stage2_toolchain_target_boundary`，这一步开始显式收敛成正式阶段：

* 它发生在 worker launch 前
* 它决定 run 的关键准备项
* 它属于 `task_type` 协议的一部分

例如：

* `input head`
* `worktree strategy`
* `prompt inputs`
* `worker home selection`

这说明 `task_type` 开始从“prompt 分类”升级成“运行协议定义”。

## 被修正的认识

### 1. `sample_target/` 不是临时测试目录，而是架构表达的一部分

在 `preproject_stage2_toolchain_target_boundary` 之前，一个很自然的误读是：

* sample target 只是为了方便测一下 install

但这轮 spike 更准确地表明：

* sample target 的意义是明确体现 `target = git repository`

也就是说，它不只是测试夹具，而是系统边界的一个具体表达。

### 2. repo-internal worktree 布局比直觉上更可行

一开始一个自然担心是：

* 主 repo 本身已经是一个 worktree
* 那么再把 detached run worktrees 放进 `target/.workflow/runs/...` 会不会和 git worktree 语义冲突

这轮 spike 证明，这条路径是可行的。

也就是说：

* `.workflow/runs/<run-id>/worktree` 不是概念冲突
* 它可以成为正式状态布局的一部分

### 3. `prepare_run` 不能再继续只作为散落内建细节存在

`preproject_stage1_minimal_workflow` 时还可以接受“prepare run 只是 toolchain 内部代码的一部分”。

但 `preproject_stage2_toolchain_target_boundary` 之后，这种理解已经不够了。

因为一旦引入：

* `target = git repo`
* repo-internal workflow state
* `task_type` 作为协议对象

那么 `prepare_run` 就不再只是内部 plumbing，而是开始决定：

* 当前 run 从哪个 head 开始
* worktree 怎样 materialize
* worker runtime 如何绑定
* prompt 要拿哪些上下文

这意味着 `prepare_run` 必须进入正式模型。

## 目前更清晰的边界

### 1. 哪些状态属于 target

这轮 spike 让下面这些内容更明确地归属于 target：

* install metadata
* target metadata
* task_type artifacts
* collections
* tasks
* runs
* mgr_runs
* agent_homes
* run worktrees

换句话说：

* 这些不是 toolchain 自己的私有状态
* 它们是“安装到 target 里的 workflow state”

### 2. 哪些逻辑属于 workflow，而不是 mgr

这轮 spike 让下面这些职责继续稳固地留在 workflow/toolchain 一侧：

* prepare run
* 创建 run record
* 创建 worktree
* launch worker
* 持久化 run
* 更新 task

而 `mgr` 的职责仍然是：

* 读取 task context
* 选择下一步决策
* 调 workflow command

这说明 `mgr` 没有被重新推回 runtime/controller 角色。

## 关于 `prepare_run` 的进一步收敛

这里需要刻意区分两类东西。

### 1. `preproject_stage2_toolchain_target_boundary` 直接验证出来的知识

这轮 spike 直接支持下面这些结论：

* `prepare_run` 应该是显式阶段
* `task_type` 需要对 run 的关键准备项有定义权
* `prepare_run` 不应继续完全隐含在 toolchain 内部

### 2. 基于 spike 进一步抽出来的设计判断

但下面这些，不应冒充成 spike 已经直接证明的事实：

* `prepare_run` 最终应该是 hook 还是内建实现
* 要不要加 `before_prepare_run` / `after_prepare_run`
* hook 是否可以直接 materialize worktree
* 哪些 prepare 字段允许扩展点影响，哪些不允许

这些更准确地说是：

* spike 暴露了边界和风险
* 然后我们基于这些边界做出的设计收敛

目前较稳的设计判断是：

* `prepare_run` 本身应由 toolchain 内建收口
* 如果存在扩展点，更适合放在 `before_prepare_run`
* `before` 可以拿到 prepare 参数或草案输入
* 但它不应直接接管 run materialization
* `after` 如果存在，也应偏 observer，而不是重新改写 run 的关键准备结果

这部分属于“由 spike 支撑的设计知识”，不是“spike 已直接验证的实验事实”。

## 治理问题开始浮现，但本轮未验证

`preproject_stage2_toolchain_target_boundary` 虽然没有正式验证治理模型，但它已经把治理问题从“以后再说”推进到了“不能再忽略”的位置。

更准确地说，这轮 spike 的结论不是：

* 治理问题已经解决

而是：

* 主干一旦收敛成 `toolchain + target`
* workflow state 一旦正式安装进 target repo
* 治理问题就会立刻变成正式系统边界的一部分

### 1. 状态治理问题已经出现

`preproject_stage2_toolchain_target_boundary` 之后，`.workflow/` 不再只是临时目录，而是 target 内正式状态根。

这马上带来一个治理问题：

* 哪些状态是 durable workflow state
* 哪些状态只是本地 runtime state

例如，当前很自然的区分是：

更偏 durable state：

* `install.edn`
* `target.edn`
* `task_types/`
* `collections/`
* `tasks/`

更偏本地 runtime state：

* `runs/` 下的 worktree
* `mgr_runs/`
* `agent_homes/`

但这只是目前被暴露出来的问题，不是这轮 spike 已经完成的正式分类。

### 2. 版本控制治理问题已经出现

一旦 workflow state 安装到 target repo 内，马上就会遇到：

* 哪些 `.workflow/` 内容应进入 git
* 哪些内容必须被 `.gitignore` 隔离

`preproject_stage2_toolchain_target_boundary` 已经验证：

* install 必须负责维护 `.gitignore`

但 `preproject_stage2_toolchain_target_boundary` 还没有验证：

* durable workflow state 是否应该默认提交
* 本地运行态是否应该永远禁止入库
* 不同团队是否需要不同提交策略

所以，这轮 spike 只是把“版本控制治理”明确暴露出来了，还没有给出最终策略。

### 3. 变更治理问题已经出现

一旦 target 内已经存在正式 workflow state，就需要开始面对：

* 谁可以修改 `task_type`
* 谁可以执行 reinstall
* reinstall 是否允许清理旧 `runs/` / `mgr_runs/`
* install metadata 改变时，旧状态应如何对待

`preproject_stage2_toolchain_target_boundary` 当前仍然采取最简单策略：

* install/reset 优先服务于主干验证
* 不优先服务于正式治理语义

这对 spike 是合理的，但对正式系统已经不够。

### 4. 权限治理问题已经出现

`preproject_stage2_toolchain_target_boundary` 继续支持：

* `mgr` 是独立 agent
* worker 是独立 runtime
* toolchain 重新接管控制权

但一旦这三者都能作用于 target repo，就会自然引出权限治理问题：

* `mgr` 是否只应拥有 workflow command 调用权
* worker 是否只应主要看到 run worktree
* toolchain 是否是唯一允许写 task/run durable state 的控制者

这轮 spike 支持了“控制面应主要收口在 toolchain”这个方向，但没有正式验证权限模型本身。

### 5. 恢复与迁移治理问题已经出现

一旦 `.workflow/` 成为正式状态根，就不可能永远停留在 spike 阶段格式。

这会带来：

* state schema 演进怎么做
* install / reinstall / resume 的边界怎么定义
* 旧版本 `task` / `run` / `task_type` 记录怎么兼容

`preproject_stage2_toolchain_target_boundary` 当前明确没有验证 migration framework 和完整恢复语义。

所以这里的正确结论不是“暂时没有问题”，而是：

* 这些问题已经被正式架构暴露出来了
* 只是当前还没有进入验证范围

### 6. 协作治理问题已经出现

一旦 workflow state 放进目标仓库，协作问题就会被放大：

* 多用户是否共享同一个 `.workflow/`
* 多机执行时本地 runtime state 怎么处理
* 并发 task / 并发 run 时谁拥有写权
* target repo 中的 workflow state 是否需要锁或 ownership 语义

这些问题在 `preproject_stage2_toolchain_target_boundary` 中被显式推迟，但不是可以永久回避的问题。

### 7. `preproject_stage2_toolchain_target_boundary` 对治理问题真正给出的收获

这轮 spike 对治理问题最有价值的贡献，不是给出了解法，而是把问题边界照亮了。

更准确地说，`preproject_stage2_toolchain_target_boundary` 至少带来了下面这些高价值认识：

* 治理问题不再是 toolchain 之外的外围话题
* 只要 workflow state 被安装进 target repo，治理就是架构内问题
* `install`、`.gitignore`、durable state、local runtime state 这些都必须进入正式设计
* 治理问题应该围绕 `toolchain / target / task / run / task_type` 主线收敛，而不是额外长出一个模糊的平台层

### 8. 当前最值得优先收敛的治理问题

如果继续沿着 `preproject_stage2_toolchain_target_boundary` 推进，最值得优先收敛的治理问题其实很集中：

1. `.workflow/` 中哪些状态应被视为 durable state
2. 哪些状态应默认进入版本控制，哪些必须保持 local-only
3. install / reinstall / resume 对旧状态的处理规则应该是什么
4. toolchain、mgr、worker 三者的写权限边界应该如何固定

这些问题都直接承接 `preproject_stage2_toolchain_target_boundary` 已经暴露出来的结构性变化，而不是额外引入的新主题。

## 任务定义问题开始浮现，但本轮也未完全验证

和治理问题类似，`preproject_stage2_toolchain_target_boundary` 也把“任务定义问题”从模糊背景推进成了正式设计议题。

更准确地说，这轮 spike 并没有验证一整套通用 task definition model，但它已经清楚暴露出：

* `task` 不能只是一个随手塞进去的 prompt
* `task` 也不能膨胀成通用项目管理对象
* 一旦系统固定成 `task_type -> collection -> task -> run`
* 那么“任务究竟如何被定义”就必须进入正式边界

### 1. `task` 的最小定义已经比 `preproject_stage1_minimal_workflow` 更清晰

在当前 `preproject_stage2_toolchain_target_boundary` 最小实现里，一个 task 至少已经包含：

* `id`
* `collection-id`
* `task-type`
* `goal`
* `stage`
* `repo-head`
* `latest-run`
* `run-count`
* `review-count`

这说明 task 已经不是一段裸 prompt，而是：

* 一个和 target repo 绑定的推进单元
* 一个按 task_type 协议演化的状态机实例

也就是说，`task` 的定义开始从“输入文本”走向“协议化状态对象”。

### 2. `goal` 足以支持 spike，但不足以支撑正式系统

`preproject_stage2_toolchain_target_boundary` 当前主要靠一个 `goal` 字段驱动最小闭环，这对 spike 是够的。

但它也暴露出一个问题：

* 只有 `goal`，不足以长期支撑任务定义

因为正式系统里，任务往往还会隐含下面这些维度：

* 任务输入范围
* 目标文件或代码区域
* 成功判据
* 约束条件
* 相关上下文引用
* 是否允许修改 target 的哪些区域

`preproject_stage2_toolchain_target_boundary` 没有解决这些问题，但它已经证明：

* “只靠一句任务描述”在正式系统里大概率不够

### 3. `task_type` 和 `task` 的边界仍需继续收敛

这轮 spike 已经支持：

* `task_type` 定义协议
* `task` 是协议实例

但更细的边界还没有完全稳定。

仍然需要继续收敛的问题包括：

* 哪些内容属于 `task_type` 的固定协议
* 哪些内容属于单个 `task` 的实例输入
* 哪些内容属于一次具体 `run` 的准备结果

例如：

* workflow stage routing 明显更像 `task_type`
* 具体目标描述明显更像 `task`
* `input head`、`prompt inputs`、`worker home selection` 更像 `prepare_run` 产物

这轮 spike 的价值在于，它让这三层边界开始可讨论，而不是继续混在一起。

### 4. `collection` 的定义仍然偏弱

`preproject_stage2_toolchain_target_boundary` 已经把 `collection/` 拉进最小状态布局，这是对的。

但当前 collection 的定义仍然很轻，更多只是：

* 一组同 task_type 的 task 容器

这带来一个后续问题：

* collection 到底只是 task pool
* 还是还应该拥有更强的任务定义上下文

例如以后可能会需要：

* collection-level prompt context
* collection-level scope
* collection-level defaults
* collection-level policy

`preproject_stage2_toolchain_target_boundary` 没有验证这些能力，但已经证明 collection 不能永远停留在“只是个目录分组”。

### 5. 成功判据和失败判据仍然过于隐含

这轮 spike 仍然大量依赖：

* stage
* worker output
* mgr judgment

来推进 task。

这对最小 spike 是合理的，但它也暴露出任务定义层面的一个关键不足：

* task 自身的完成判据还没有被正式定义

换句话说，目前更多还是：

* impl 完成了
* review 说 pass 了
* mgr 决定 done

而不是：

* task definition 自身携带了更明确的 success criteria

这会影响后续：

* task 的可复用性
* mgr 的判断稳定性
* 恢复语义
* 自动化程度

### 6. 任务输入边界问题已经浮现

一旦 target 是真实 git repo，任务定义就不能只说“做某件事”，还要逐步面对：

* 允许改哪些文件
* 不应碰哪些区域
* 是否允许跨目录修改
* 是否允许提交 commit
* 是否要求保持某些 invariant

这些问题当前还没有进入 `task` schema，但 `preproject_stage2_toolchain_target_boundary` 已经证明它们迟早会进入正式模型。

因为：

* target 已经是真实源码仓库
* worker 已经作用于真实 repo worktree

这意味着任务定义最终必然需要某种 scope / constraint 语义。

### 7. `task` 不是项目管理对象，但也不是纯运行对象

这轮 spike 带来的一个重要认识是：

* `task` 不应膨胀成 Jira/Linear 那种项目管理对象
* 但它也不能退化成只为 runtime 服务的瞬时记录

更合理的理解是：

* `task` 是 workflow 中的一等推进单元
* 它位于 task_type 协议和 run runtime 之间

这意味着任务定义既要足够薄，避免变成通用 PM schema；又要足够实，避免所有语义都被推给 prompt 或 mgr。

### 8. `preproject_stage2_toolchain_target_boundary` 对任务定义问题真正给出的收获

这轮 spike 对任务定义最有价值的贡献，不是给出了最终 schema，而是把关键张力暴露出来了：

* task 不能只是 prompt
* task 不能只是 stage + status
* task 也不能直接膨胀成通用 artifact schema
* task_type / collection / task / run 之间必须开始明确分工

这意味着正式系统后续不能只补几个字段了事，而需要回答：

* 任务定义的最小必要字段是什么
* 哪些字段是 durable definition
* 哪些字段是运行时派生值
* 哪些判断属于 task definition
* 哪些判断属于 task_type protocol

### 9. 当前最值得优先收敛的任务定义问题

如果继续沿着 `preproject_stage2_toolchain_target_boundary` 推进，最值得优先收敛的任务定义问题主要是：

1. 一个正式 `task` 至少应包含哪些 durable fields
2. `goal` 之外是否需要 `scope`、`constraints`、`success criteria`
3. `collection` 是否需要承担部分默认定义或共享上下文
4. `task_type`、`task`、`prepare_run` 三层之间的字段边界应如何固定
5. 哪些任务定义应进入版本控制，哪些应保持可派生

这些问题和治理问题一样，都不是额外话题，而是 `preproject_stage2_toolchain_target_boundary` 在正式化过程中自然暴露出来的主线问题。

## 当前被验证的最小架构

基于 `preproject_stage1_minimal_workflow` 和 `preproject_stage2_toolchain_target_boundary`，目前最稳的一版最小架构可以写成：

```text
toolchain
  -> installs workflow state into target
  -> exposes workflow CLI
  -> regains control after mgr decision
  -> prepares run
  -> launches worker
  -> persists run/task state

target
  -> is the git repository being worked on
  -> contains source tree
  -> contains .workflow state root

task_type
  -> defines task protocol
  -> defines prepare_run-relevant inputs

task
  -> state machine instance
  -> many runs

mgr
  -> reads task context
  -> decides next action
  -> calls workflow CLI

run
  -> one worker execution
  -> runtime container
  -> owns worktree under target/.workflow/runs/<run-id>/worktree
```

## 这一轮暂时没有证明什么

为了防止过度解读，这里也要明确列出来：

`preproject_stage2_toolchain_target_boundary` 目前还没有证明：

* 多 target 管理应该怎么做
* 并发 task / 并发 run 模型应该怎么做
* 正式恢复语义应该怎么做
* migration framework 应该怎么做
* plugin system 应该怎么做
* 权限模型应该怎么做
* `prepare_run` 的最终 API 应该长什么样

所以这轮 spike 的正确价值不是“正式系统已经设计完”，而是：

* 最外层边界开始稳定
* target 内状态模型开始稳定
* workflow 主干在新架构下继续成立
* `prepare_run` 已经被逼到必须正式收敛的位置

## 下一步最值得继续收敛的问题

如果继续沿这条线推进，最值得优先收敛的问题是：

1. `prepare_run` 最终应以内建阶段、hook interface，还是“内建收口 + before 扩展点”的混合模型进入正式系统
2. 哪些 `.workflow/` 状态应默认进入版本控制，哪些只应保留为本地运行态
3. install / reinstall / resume 的边界怎样才能在不引入复杂 migration framework 的前提下保持稳定

这几个问题都直接承接 `preproject_stage2_toolchain_target_boundary` 的真实收获，而不是另起新题。
