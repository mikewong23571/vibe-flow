# preproject_stage2_toolchain_target_boundary plan

这份文档定义 `preproject_stage2_toolchain_target_boundary` 的最小验证顺序。

目标不是马上实现正式系统，而是按 [refine.md](/home/mikewong/proj/main/vibe-flow/docs/archives/refine.deprecated.md) 已经收敛的边界，验证下一阶段最关键的结构是否成立。

`preproject_stage2_toolchain_target_boundary` 不是脱离 `preproject_stage1_minimal_workflow` 重新开始。

这一轮需要明确保留 `preproject_stage1_minimal_workflow` 相关制品的引用，把它们作为已验证基线，而不是把 `v1` 视为一次性原型后完全丢弃。

## 0. preproject_stage1_minimal_workflow references

`preproject_stage2_toolchain_target_boundary` 默认应持续参考下面这些 `preproject_stage1_minimal_workflow` 制品：

* [spikes/preproject_stage1_minimal_workflow/README.md](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage1_minimal_workflow/README.md)
* [spikes/preproject_stage1_minimal_workflow/toolchain/README.md](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage1_minimal_workflow/toolchain/README.md)
* [spikes/preproject_stage1_minimal_workflow/learnings.md](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage1_minimal_workflow/learnings.md)

尤其要保留下面这些已经被验证过的结论：

* `task` 会落成一串 `run`
* `run` 是 runtime container，而不只是执行记录
* `worktree` 归属于 `run`
* `mgr` 是独立 agent
* `mgr` 不直接拿 worker launcher
* `mgr -> workflow CLI -> toolchain regains control` 这条控制链成立

`preproject_stage2_toolchain_target_boundary` 的任务不是重新验证这些点本身，而是把这些结论迁移到更正式的 `toolchain + target` 架构中。

## 1. 这一轮要验证什么

`preproject_stage2_toolchain_target_boundary` 优先验证下面 5 件事：

1. `target` 作为 git 仓库宿主是否成立
2. workflow state 安装到 target 仓库内部是否成立
3. install 写 metadata 和 `.gitignore` 的模型是否成立
4. workflow command 是否能围绕 target 内状态完整接管控制权
5. `task_type.prepare_run` 是否能从概念收敛成正式 hook

## 2. 这一轮暂时不验证什么

为了保持主干简单，这一轮先不验证：

* 多 target 管理
* 并发 task / 并发 run
* 远程执行
* 多用户协作
* 完整恢复语义
* 正式 migration framework
* 通用 plugin system
* 权限模型精细化

原则很简单：

* 凡是不影响主干成立的分叉，先选更简单的方案

## 3. 默认简单假设

`preproject_stage2_toolchain_target_boundary` 先统一按下面这些简单假设推进：

* 一个 `target` 就是一个本地 git 仓库
* 一个 target 上只安装一个 workflow state root
* workflow state root 固定放在 target 内隐藏目录下
* 先只支持 repo root 级 target，不处理 subdir scope
* 先按单 active task stream 理解，不引入并发模型
* 大多数高频 runtime state 默认作为本地运行态处理
* `mgr_run` 先保持为独立记录，不和 `run` 强行统一
* `prepare_run` 先按脚本或命令 hook 理解，不做 declarative spec

## 4. 建议验证顺序

### Phase 1: Target Install Model

先验证 install 本身，而不是先验证 worker。

这一阶段仍然需要参考 `preproject_stage1_minimal_workflow` 已有的 install 和 target split 经验，但要把宿主从“旁边的 target 目录”收紧成“目标 git repo 本身”。

最小目标：

* 指定一个 git repo 作为 target
* toolchain 能安装 `.workflow/`
* toolchain 能写 `install.edn`
* toolchain 能写 `target.edn`
* toolchain 能补 `.gitignore`

如果这一步不成立，后面的 workflow 设计都不稳定。

### Phase 2: Target State Layout

在 install 成立后，再验证 target 内状态布局。

这一阶段要显式参考 `preproject_stage1_minimal_workflow` 已经存在的 `runs/`、`mgr_runs/`、`agent_homes/`、prompt/task_type 安装产物，但重新放到 repo-internal target state 视角下收敛。

最小目标：

* `task_types/`
* `collections/`
* `tasks/`
* `runs/`
* `mgr_runs/`
* `agent_homes/`

这些目录和文件的最小布局先稳定下来。

这一步的重点不是功能，而是：

* target 内状态是否有清晰、固定、可读的结构

### Phase 3: Workflow Command Model

在 target 安装模型成立后，再验证 workflow command。

这一阶段必须延续 `preproject_stage1_minimal_workflow` 已验证过的控制链：

* `mgr` 不直接拿 launcher
* `mgr` 调 workflow command
* toolchain 重新接管控制权

最小目标：

* 存在一个正式 command 用于推进 task
* `mgr` 不直接拿 launcher
* command 被调用后，toolchain 重新拿回控制权
* task 和 run 状态能在 target 内正确变化

这一步是 `preproject_stage2_toolchain_target_boundary` 的控制权核心。

### Phase 4: `prepare_run` Hook

在 command 模型成立后，再验证 `task_type.prepare_run`。

这一阶段要把 `preproject_stage1_minimal_workflow` 中已经隐含存在的 run 准备逻辑，显式提炼成正式 hook。

最小目标：

* task_type 能定义 prepare_run hook
* toolchain 在 worker launch 前调用它
* hook 能决定 run 的关键准备项

例如：

* input head
* worktree strategy
* prompt inputs
* worker home selection

这一步决定 `task_type` 是否真的从“prompt 分类”升级为“运行协议定义”。

### Phase 5: End-to-End Loop

最后再拼完整闭环。

这一阶段不是为了重做 `preproject_stage1_minimal_workflow` 的完整跑通，而是为了确认：

* `preproject_stage1_minimal_workflow` 已验证的 workflow 主干
* 能否在 `preproject_stage2_toolchain_target_boundary` 的 `target = git repo` 架构下保持成立

最小目标：

* install target
* seed minimal collection/task
* mgr 判断下一步
* mgr 调 workflow command
* toolchain prepare run
* worker 执行
* run / task 更新
* task 进入 done 或 error

如果这一轮成立，`preproject_stage2_toolchain_target_boundary` 就完成了主要目标。

## 5. 建议的最小目录结构

当前先只建议保留这几个文件或目录：

```text
spikes/preproject_stage2_toolchain_target_boundary/
  README.md
  plan.md
  toolchain/
  sample_target/
```

其中：

* `toolchain/` 是本轮最小实现
* `sample_target/` 是被安装和运行的示例 git 仓库

`sample_target/` 的意义不是临时测试目录，而是：

* 明确体现 `target = git repository`

## 6. 设计检查点

在每个 phase 完成后，都建议用下面这些问题做检查：

* 当前验证的是 system 主干，还是只是某个实现细节？
* 这个变化是在继承 `preproject_stage1_minimal_workflow` 的已验证结论，还是在无意中推翻它？
* 这个新增概念是不是能并回 `toolchain / target / task / run / task_type` 主线？
* 这个设计是不是在偷引入新的中间控制者？
* 这个状态到底属于 toolchain，还是属于 target？
* 这个逻辑到底属于 workflow，还是属于 runtime adapter？

如果回答开始变模糊，说明 spike 偏题了。

## 7. 当前建议

`preproject_stage2_toolchain_target_boundary` 的第一步不应该直接抄 `preproject_stage1_minimal_workflow`。

更好的开始方式是：

1. 先建一个最小 `sample_target/` git repo
2. 再建 `toolchain/`
3. 先只做 install
4. install 稳定后再引入 task / run / mgr

也就是说：

* `preproject_stage1_minimal_workflow` 是从 workflow 跑通开始
* `preproject_stage2_toolchain_target_boundary` 应该从 target install model 开始
