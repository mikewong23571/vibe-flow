# preproject_stage3_definition_externalization toolchain

这是 `preproject_stage3_definition_externalization` 的最小 toolchain 实现。

这版实现延续已验证的控制链：

* `mgr` 是独立 agent
* `mgr` 不直接拿 worker launcher
* `mgr` 调 workflow CLI
* toolchain 重新接管控制权

和 `preproject_stage2_toolchain_target_boundary` 的关键差异是：

* 定义层从代码里拆到 `task_type bundle` artifact
* prompt 从内建字符串改成 bundle 内文件
* task 使用最小 durable schema：`goal / scope / constraints / success-criteria`
* `.workflow/state/` 保存 durable state
* `.workflow/local/` 保存 run、mgr_run、agent_home、worktree 等本地运行态
* `prepare_run` 本体保持内建，但读取 artifact 中的声明式定义，并支持最小 `before_prepare_run`

## 源码分层

当前源码按职责分成下面几层：

* `spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/toolchain.clj`
  CLI 入口，只负责解析命令并调用 workflow/install。
* `spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/core/`
  主干控制面。`workflow.clj` 负责推进 task，`model.clj` 负责阶段协议，`task_lifecycle.clj` 负责 task 上的协议级状态演化。
* `spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/agent/`
  agent 相关代码。包含 `mgr`、prompt 渲染，以及统一 launcher 门面和具体 worker launcher。
* `spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/definition/`
  定义层读取逻辑。当前只有 `task_type` artifact 解析与 `prepare_run` 声明式收敛。
* `spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/management/`
  target 内正式模型的管理逻辑。当前先包含 `task_type` 的 meta、registry、inspect/create。
* `spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/state/`
  durable/local workflow state 的读写，不再夹带 sample fixture 或 task 生命周期语义。
* `spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/sample/`
  spike 专用演示夹具和 seed 逻辑。
* `spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/target/`
  target repo install 和 git runtime 操作。
* `spikes/preproject_stage3_definition_externalization/toolchain/src/spike_v3/support/`
  路径和通用工具。

## 最小命令

在当前目录执行：

```bash
bb -m spike-v3.toolchain init-sample-target
bb -m spike-v3.toolchain install
bb -m spike-v3.toolchain seed-sample
bb -m spike-v3.toolchain list-task-types
bb -m spike-v3.toolchain inspect-task-type --task-type impl
bb -m spike-v3.toolchain create-task-type --task-type doc-update
bb -m spike-v3.toolchain run-loop mock mock 4
bb -m spike-v3.toolchain show-state
```

一键 smoke test：

```bash
bb -m spike-v3.toolchain smoke
```

## 安装后布局

```text
sample_target/
  .git/
  .gitignore
  README.md
  feature.txt
  .workflow/
    state/
      system/
        install.edn
        target.edn
        layout.edn
        registries/
      definitions/
        task_types/
      domain/
        collections/
        tasks/
    local/
      runs/
      mgr_runs/
      agent_homes/
```
