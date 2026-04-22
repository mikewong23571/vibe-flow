# preproject_stage1_minimal_workflow toolchain

这是 `preproject_stage1_minimal_workflow` 的最小 toolchain 实现。

## 目录角色

* 当前目录 `toolchain/`：实现 workflow
* 邻居目录 `../spike_target/`：安装态与运行态目标目录

toolchain 不在自己目录里保存 runtime 状态。
所有安装结果、task 状态、run 记录、prompt 模板、agent_home 目录，都安装到 `spike_target/.spike-v1/` 下。
重新执行 `install` 时，toolchain 会清理旧的 `runs/`、`mgr_runs/` 和已注册 worktree，让 target 回到干净的 spike 起点。

这版 spike 进一步收敛为：

* `run` 不只是执行记录
* `run` 还是 worker runtime 容器
* 每个 run 都拥有自己的 worktree：`.spike-v1/runs/<run-id>/worktree`
* worker 的主要业务可见范围就是这个 worktree
* `mgr` 也是独立 agent，会写自己的 `mgr_run`
* `mgr` 和 worker 可以使用不同 launcher
* `mgr` 不直接拿 worker launcher，而是调用 workflow CLI 让 toolchain 重新接管

## 最小命令

在当前目录执行：

```bash
bb -m spike-v1.toolchain install
bb -m spike-v1.toolchain seed-sample
bb -m spike-v1.toolchain run-loop mock mock 4
bb -m spike-v1.toolchain show-state
```

一键 smoke test：

```bash
bb -m spike-v1.toolchain smoke
```

## codex launcher

这个 spike 同时实现了两种 launcher：

* `mock`
  * 完全本地执行
  * 用来验证 workflow 本身
* `codex`
  * 通过 `CODEX_HOME=<agent_home_dir> codex exec ...` 启动真实 worker
  * worker 的 cwd 会直接指向对应 run 的 worktree
  * 用来验证 launcher 边界是否成立
  * 当前 spike 为了优先验证 workflow，可对 `mgr` 和 worker 使用无 sandbox 的 codex exec

现在 `mgr` 和 worker 的 launcher 已经拆开了，命令形式是：

```bash
bb -m spike-v1.toolchain run-once <worker-launcher> <mgr-launcher>
bb -m spike-v1.toolchain run-loop <worker-launcher> <mgr-launcher> <max-steps>
```

例如：

```bash
bb -m spike-v1.toolchain run-once codex codex
bb -m spike-v1.toolchain run-loop mock codex 4
```

如果省略 `mgr-launcher`，默认跟 `worker-launcher` 一样。

`mgr` 调用的最小 workflow CLI 是：

```bash
bb -m spike-v1.toolchain mgr-advance --task-id <id> --mgr-run-id <id> --worker-launcher <mock|codex> --decision <impl|review|refine|done|error> --reason "<text>"
```

在 codex mgr 路径里，toolchain 会把一个 `workflow-advance` wrapper 脚本放到 `mgr_run` workdir 中，prompt 会要求 mgr 通过这个脚本推进 task。

这里有一个明确约束：

* install 阶段会把 `~/.codex/config.toml` 复制到 `spike_target/.spike-v1/agent_homes/<home>/config.toml`
* runtime 阶段只使用复制后的这份 `config.toml`
* launcher 不应再直接读取 `~/.codex/config.toml`

当前环境里，真实 `codex` 是否能成功采样，取决于复制后的 `agent_home` 中是否已经具备可用的 codex runtime/auth 配置。
这正是本 spike 想验证的边界之一：这些都属于 `agent_home`/runtime，而不是 toolchain。

## 当前 mgr 能力

这版 `mgr` 还不是通用 orchestrator，但已经不再只是代码里的静态分支：

* `mgr` 会看到 task 当前 stage、run/review 次数、最近一次 worker 输出、最近几次 run 摘要
* `mgr` 会把自己的决策单独记录到 `.spike-v1/mgr_runs/<mgr-run-id>/`
* `mgr` 有最小 guardrail：run 次数过多，或者反复 review/refine 仍未收敛时，可以直接给出 `error`
