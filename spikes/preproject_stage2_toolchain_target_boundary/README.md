# Pre-Project Stage 2: Toolchain-Target Boundary

这是正式项目启动前的第二阶段探索，基于 [refine.md](/home/mikewong/proj/main/vibe-flow/docs/archives/refine.deprecated.md) 启动并已落成最小实现。

`preproject_stage2_toolchain_target_boundary` 的目标不再只是验证单个 workflow 是否能跑通，而是开始按更接近正式系统的边界设计：

* `System = Toolchain + Target`
* `target` 默认假设为一个 git 仓库
* workflow state 被安装到 target 仓库内部
* `toolchain` 负责 install、workflow command、task/run 推进、mgr/worker launch
* `mgr` 通过 workflow command 把控制权交回 toolchain

和 `preproject_stage1_minimal_workflow` 相比，这一轮最重要的设计差异是：

* 不再把“旁边的 runtime target 目录”当作抽象宿主
* 而是明确把目标 git 仓库本身视为 workflow target
* install 应视为“把 workflow 能力安装进一个 repo”

建议这一轮 spike 优先验证：

* target 安装模型
* workflow state root 在 repo 内的布局
* install metadata / target metadata / `.gitignore`
* workflow command 与 target state 的关系
* `task_type.prepare_run` 如何正式化

在这一轮开始前，默认遵循一个简单原则：

* 凡是不影响主干成立的分叉，先选更简单的方案

## 当前实现

当前 `preproject_stage2_toolchain_target_boundary` 已实现下面这些最小制品：

```text
spikes/preproject_stage2_toolchain_target_boundary/
  README.md
  plan.md
  toolchain/
  sample_target/
```

其中：

* [toolchain/README.md](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage2_toolchain_target_boundary/toolchain/README.md) 说明可运行命令
* `toolchain/` 提供 `install / seed-sample / run-loop / mgr-advance / smoke`
* `sample_target/` 是示例 target 仓库模板

这版实现已经验证：

* `target = git repo`
* workflow state 安装到 `target/.workflow/`
* install 会写 `install.edn`、`target.edn` 和 `.gitignore`
* `mgr -> workflow CLI -> toolchain regains control` 继续成立
* `task_type.prepare_run` 通过 target 内 command hook 正式化
