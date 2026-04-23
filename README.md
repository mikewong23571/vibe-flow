# Vibe Flow

这是当前 repo 的正式入口文档。

它不再承载 spike 过程记录，而是作为正式版本的顶层导航。

## 当前状态

基于三个前项目探索阶段的连续验证，repo 当前已经收敛出一条清晰方向：

```text
build a task-type driven coding agent toolchain
with
  externalized definition layer
  + built-in workflow control plane
  + target repo as installation/runtime host
```

当前正式设计基线见：

* [design.md](/home/mikewong/proj/main/vibe-flow/design.md)
* [architecture.md](/home/mikewong/proj/main/vibe-flow/architecture.md)
* [governance.md](/home/mikewong/proj/main/vibe-flow/governance.md)

## Repo Structure

当前 repo 主要分成下面几部分：

* [Pre-Project Exploration Stages](/home/mikewong/proj/main/vibe-flow/spikes/README.md)
  前项目探索阶段索引和命名规则。
* [Pre-Project Stage 1: Minimal Workflow](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage1_minimal_workflow)
  Stage 1，验证最小 workflow 主干。
* [Pre-Project Stage 2: Toolchain-Target Boundary](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage2_toolchain_target_boundary)
  Stage 2，验证 `System = Toolchain + Target` 外层边界。
* [Pre-Project Stage 3: Definition Externalization](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization)
  Stage 3，验证 definition layer externalization、target model management 起步，以及真实 `codex` 接入。
* [docs/archives](/home/mikewong/proj/main/vibe-flow/docs/archives)
  已归档的旧版设计草稿和探索性文档。

## Recommended Reading Order

如果要理解当前正式版本，建议按这个顺序读：

1. [design.md](/home/mikewong/proj/main/vibe-flow/design.md)
2. [architecture.md](/home/mikewong/proj/main/vibe-flow/architecture.md)
3. [governance.md](/home/mikewong/proj/main/vibe-flow/governance.md)
4. [Stage 3 Learnings](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/learnings.md)
5. [Stage 3 Toolchain Growth Design](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/toolchain_growth_design.md)

## What Is Archived

早期根目录设计草稿已归档到：

* [design.deprecated.md](/home/mikewong/proj/main/vibe-flow/docs/archives/design.deprecated.md)
* [architecture.deprecated.md](/home/mikewong/proj/main/vibe-flow/docs/archives/architecture.deprecated.md)
* [refine.deprecated.md](/home/mikewong/proj/main/vibe-flow/docs/archives/refine.deprecated.md)

根目录之前没有正式 `README.md`，因此这里直接建立新的正式版本，而不是对旧版做 deprecated archive。

## Current Direction

当前最重要的正式判断可以压缩成一句话：

```text
target repo hosts workflow state,
task_type defines reusable workflow protocol,
toolchain owns control plane,
run carries runtime state and candidate change.
```

后续正式实现应优先沿下面几条主线推进：

* formal `task_type` lifecycle
* formal target model management
* workflow recovery and inspection
* candidate change lifecycle
* stable product surface

## Governance Tooling

当前 repo 已经落地一套通过 Clojure CLI 运行的治理校验。

常用命令：

```bash
clojure -M:cli install
clojure -M:cli install-target --target /path/to/repo
clojure -M:cli mgr-advance --target /path/to/repo --task-id task-1 --mgr-run-id mgr-1 --decision impl --reason "start implementation"
clojure -M:bootstrap
clojure -M:governance
clojure -M:test
```

用户视角的第一步是先安装一次 `vibe-flow` 命令：

```bash
clojure -M:cli install
```

安装后会生成：

* `~/.local/bin/vibe-flow`
* `~/.local/share/vibe-flow/toolchain/`

之后可以在任意 git repo 里执行：

```bash
vibe-flow install-target --target /path/to/repo
```

它会把 repo 初始化成 workflow target，并把控制面绑定到已安装的 `vibe-flow` 命令，而不是当前开发 checkout。

`clojure -M:bootstrap` 或 `vibe-flow bootstrap --target /path/to/repo` 会把目标 repo 初始化成一个带默认 `impl` task_type 和自优化 seed task 的 self-host workflow target：

* materialize `.workflow/` 正式布局
* 写入 `.workflow/state/system/toolchain.edn`
* 创建默认 `impl` task_type
* 创建 `self-improvement` collection
* 创建 `bootstrap-self-optimization` 初始任务

当 `mgr_run` 被创建后，`mgr` prompt 里会带一个 `workflow-advance` 脚本路径。该脚本最终会调用：

```bash
vibe-flow mgr-advance --target /path/to/repo --task-id <id> --mgr-run-id <id> --decision <impl|review|refine|done|error> --reason "<text>"
```

提交前拦截通过：

* [.pre-commit-config.yaml](/home/mikewong/proj/main/vibe-flow/.pre-commit-config.yaml)

正式治理规则见：

* [governance.md](/home/mikewong/proj/main/vibe-flow/governance.md)
