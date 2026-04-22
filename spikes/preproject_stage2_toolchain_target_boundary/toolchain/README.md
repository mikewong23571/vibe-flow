# preproject_stage2_toolchain_target_boundary toolchain

这是 `preproject_stage2_toolchain_target_boundary` 的最小 toolchain 实现。

这版实现延续 `preproject_stage1_minimal_workflow` 已验证的控制链：

* `mgr` 是独立 agent
* `mgr` 不直接拿 worker launcher
* `mgr` 调 workflow CLI
* toolchain 重新接管控制权

和 `preproject_stage1_minimal_workflow` 的核心差异是：

* `target` 现在就是被处理的 git 仓库
* workflow state 安装到 `target/.workflow/`
* `task_type.prepare_run` 被收敛为正式 command hook

## 最小命令

在当前目录执行：

```bash
bb -m spike-v2.toolchain init-sample-target
bb -m spike-v2.toolchain install
bb -m spike-v2.toolchain seed-sample
bb -m spike-v2.toolchain run-loop mock mock 4
bb -m spike-v2.toolchain show-state
```

一键 smoke test：

```bash
bb -m spike-v2.toolchain smoke
```

## 当前验证点

这版 spike 直接覆盖 `plan.md` 的主干验证点：

* target install model
* repo-internal `.workflow/` state layout
* install metadata / target metadata / `.gitignore`
* workflow command 围绕 target 内状态推进 task
* `task_type.prepare_run` command hook

## 目录布局

安装后，`sample_target/` 的最小布局如下：

```text
sample_target/
  .git/
  .gitignore
  README.md
  feature.txt
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
