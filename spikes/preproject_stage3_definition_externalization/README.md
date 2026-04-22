# Pre-Project Stage 3: Definition Externalization

这是正式项目启动前的第三阶段探索，基于 [spikes/preproject_stage2_toolchain_target_boundary/plan.md](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage2_toolchain_target_boundary/plan.md) 和 [spikes/preproject_stage2_toolchain_target_boundary/learnings.md](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage2_toolchain_target_boundary/learnings.md) 继续推进。

`preproject_stage3_definition_externalization` 的目标不是继续横向扩系统能力，而是把已经被 `preproject_stage2_toolchain_target_boundary` 验证过的 workflow 主干，推进到“定义层外置、控制面内建”的更稳定结构：

* `task_type` 从代码常量收敛成安装型 artifact
* prompt 从内建字符串收敛成正式文件
* task 从单 `goal` 收敛成最小 durable schema
* `prepare_run` 保持 toolchain 内建收口，但允许 task_type 用声明式定义和最小 `before_prepare_run` 扩展点影响 prepare

## 当前实现

当前 `preproject_stage3_definition_externalization` 已实现下面这些最小制品：

```text
spikes/preproject_stage3_definition_externalization/
  README.md
  plan.md
  toolchain/
  sample_target/
```

其中：

* [toolchain/README.md](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain/README.md) 说明可运行命令
* `toolchain/task_type_bundles/impl/` 是源码侧 task_type bundle
* `sample_target/` 是示例 target 仓库模板

这版实现重点验证：

* runtime 只从 target 内已安装 artifact 读取 `task_type` 和 prompt
* `task` 至少包含 `goal / scope / constraints / success-criteria`
* `.workflow/` 被切分为 durable state 和 local runtime state
* `prepare_run` 继续由 toolchain 控制，但会读取 task_type artifact 的声明式定义，并支持最小 `before_prepare_run`
* `mgr -> workflow CLI -> toolchain regains control` 主干继续成立
