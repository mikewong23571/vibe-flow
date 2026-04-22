# Pre-Project Stage 1: Minimal Workflow

这是正式项目启动前的第一阶段探索。

它的目标不是代码质量，而是尽快验证最小 workflow 主干是否成立：

* `task` 是否真的可以落地成一串 `worker launch -> run`
* 工具链是否可以把自己的安装态和运行态都落到 target 中
* `agent_home` 是否可以只作为 launcher/runtime 的输入目录，而不进入工具链核心模型
* `run` 是否应该拥有自己的 worktree/runtime 容器
* `mgr` 是否应该作为独立 agent 参与编排，而不是内嵌分支逻辑

补充约束：

* install 阶段允许从 `~/.codex/config.toml` 复制模板配置到 target 内部
* runtime 阶段不再依赖 `~/.codex/config.toml`
* runtime 只使用安装到 `spike_target` 里的 `agent_home`
* 重新 install 时，旧 run/mgr_run/worktree 会被清理，避免新一轮 spike 读到上轮残留

## 目录约束

`preproject_stage1_minimal_workflow` 固定拆成两个目录：

* `toolchain/`
  * workflow 的最小实现
  * 负责安装状态、选择 task、选择 worker、记录 run、调用 launcher
* `spike_target/`
  * workflow installation target
  * workflow runtime target
  * toolchain 会把自己的状态安装到这里
  * seed git repo、agent_home 目录、task/run 状态都落在这里
  * 每个 run 的 worktree 都落在 `.spike-v1/runs/<run-id>/worktree`
  * 每次 `mgr` 决策也会落一个 `.spike-v1/mgr_runs/<mgr-run-id>/`

这个 split 是本 spike 最先验证的原则，不是后置实现细节。

## 运行方式

进入 [toolchain/README.md](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage1_minimal_workflow/toolchain/README.md) 查看具体命令。
