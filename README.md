# Vibe Flow

Vibe Flow 是一个 Clojure CLI 工具链，用来在普通 git repo 里运行 task-type 驱动的 coding-agent workflow。

核心使用模型是：

```text
先安装一次 vibe-flow 命令
  -> 再把任意 git repo 初始化成 workflow target
  -> 后续由已安装的 toolchain 驱动 workflow 状态、task_type 和 agent handoff
```

target repo 持久化保存 `.workflow/` 状态；已安装的 toolchain 负责控制面。这样 workflow 执行绑定的是用户级安装命令，而不是某个正在开发中的源码 checkout。

## 当前状态

Vibe Flow 目前是早期的 local-first 工具链。面向用户的 CLI 已支持安装、target 初始化、self-host bootstrap 和 manager callback。更底层的 workflow / management 能力已经存在于 Clojure namespace 中，但并不是全部都已经暴露成稳定 CLI 命令。

当前建议把这个 repo 当作本地安装源使用。

## 前置要求

* Clojure CLI
* Git
* Bash-compatible shell
* 使用 Codex-backed launcher 时需要 `codex` CLI

## 安装

在这个 repo 中运行：

```bash
clojure -M:cli install
```

安装后会生成用户级文件：

* `~/.local/bin/vibe-flow`
* `~/.local/share/vibe-flow/toolchain/`

如果设置了 `XDG_DATA_HOME`，toolchain 会安装到 `$XDG_DATA_HOME/vibe-flow/toolchain/`，而不是 `~/.local/share/vibe-flow/toolchain/`。

确认 `~/.local/bin` 在 `PATH` 中，然后检查命令是否可用：

```bash
vibe-flow help
```

## 初始化 Target Repo

安装一次命令后，可以把任意 git repo 初始化成 workflow target：

```bash
vibe-flow install-target --target /path/to/repo
```

这个命令会在目标 repo 中创建 workflow layout，记录已安装的 toolchain 命令，并写入 target-local workflow state。之后这个 target 会由已安装的 `vibe-flow` 命令驱动，而不是由当前开发 checkout 驱动。

## 启动 Self-Hosted Workflow

如果希望给 target 初始化默认 self-improvement workflow：

```bash
vibe-flow bootstrap --target /path/to/repo
```

bootstrap 会在需要时先安装 target，然后写入：

* 默认 `impl` task_type
* `self-improvement` collection
* `bootstrap-self-optimization` 初始 task
* `.workflow/state/system/toolchain.edn`
* 标准 `.workflow/` runtime layout

如果 target 就是当前这个 repo，也可以运行：

```bash
clojure -M:bootstrap
```

## Manager Callback

创建 manager run 后，Vibe Flow 会为 manager 写入一个 callback wrapper。这个 wrapper 最终会调用：

```bash
vibe-flow mgr-advance --target /path/to/repo --task-id <id> --mgr-run-id <id> --decision <impl|review|refine|done|error> --reason "<text>"
```

这个命令会记录 manager 的决策，并把对应 task 推进到下一个 workflow step。

## CLI Reference

```bash
vibe-flow install
vibe-flow install-target [--target <repo>]
vibe-flow bootstrap [--target <repo>]
vibe-flow mgr-advance --target <repo> --task-id <id> --mgr-run-id <id> --decision <impl|review|refine|done|error> --reason <text>
```

也可以直接从源码 checkout 中通过 `clojure -M:cli` 运行：

```bash
clojure -M:cli install
clojure -M:cli install-target --target /path/to/repo
clojure -M:cli bootstrap --target /path/to/repo
clojure -M:cli mgr-advance --target /path/to/repo --task-id task-1 --mgr-run-id mgr-1 --decision impl --reason "start implementation"
```

## Target State

Vibe Flow 会把 target-local workflow state 放在 `.workflow/` 下：

* `.workflow/state/` 保存 system、task、run 和 manager-run records。
* `.workflow/local/` 保存 runtime-local artifacts，例如 agent homes。
* `.workflow/state/system/toolchain.edn` 记录驱动当前 target 的已安装命令。

不要提交机器本地 workflow state、凭据或生成的安装产物。修改 `.workflow/` 协议布局时，应先对照 governance 文档确认边界是否合理。

## Development

常用开发命令：

```bash
clojure -M:test
clojure -M:fmt
clojure -M:fmt-check
clojure -M:lint
clojure -M:governance
```

提交 PR 前建议运行 `fmt-check`、`lint`、`governance` 和 tests。

## Project Structure

生产代码位于 `src/vibe_flow/`。

* `platform/support` 提供低层 support utilities。
* `platform/target` 负责 target layout 和安装行为。
* `platform/state` 负责 durable state stores。
* `definition` 解释已安装的 task_type definitions。
* `management` 管理 task types、collections 和 tasks。
* `platform/runtime` 准备并启动 agent runs。
* `workflow` 负责 workflow control。
* `system.clj` 是 product surface 和 CLI entrypoint。

测试位于 `test/vibe_flow/`，由 `vibe-flow.test-runner` 统一运行。

## Design Docs

当前正式设计文档：

* [design.md](design.md)
* [architecture.md](architecture.md)
* [governance.md](governance.md)

历史探索保存在 `spikes/`：

* [Pre-Project Exploration Stages](spikes/README.md)
* [Stage 1: Minimal Workflow](spikes/preproject_stage1_minimal_workflow)
* [Stage 2: Toolchain-Target Boundary](spikes/preproject_stage2_toolchain_target_boundary)
* [Stage 3: Definition Externalization](spikes/preproject_stage3_definition_externalization)

归档草稿保存在 [docs/archives](docs/archives)。
