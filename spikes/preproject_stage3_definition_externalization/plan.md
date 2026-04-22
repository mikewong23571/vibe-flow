# preproject_stage3_definition_externalization plan

这份文档定义 `preproject_stage3_definition_externalization` 的最小验证顺序。

`preproject_stage3_definition_externalization` 的目标不是继续横向扩系统能力，而是沿着 [spikes/preproject_stage2_toolchain_target_boundary/plan.md](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage2_toolchain_target_boundary/plan.md) 和 [spikes/preproject_stage2_toolchain_target_boundary/learnings.md](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage2_toolchain_target_boundary/learnings.md) 已经暴露出来的问题，进一步把“定义层”从内建实现里拆出来。

一句话说：

```text
preproject_stage3_definition_externalization
  = externalize definition layer
  while keeping workflow control built-in
```

这轮所有决策都默认采取简单方案。

也就是说：

* 只做影响主干成立的最小拆解
* 不提前引入通用 plugin system
* 不为了未来扩展性牺牲当前边界清晰度

## 0. preproject_stage1_minimal_workflow / preproject_stage2_toolchain_target_boundary references

`preproject_stage3_definition_externalization` 默认应持续参考下面这些制品：

* [spikes/preproject_stage1_minimal_workflow/learnings.md](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage1_minimal_workflow/learnings.md)
* [spikes/preproject_stage2_toolchain_target_boundary/plan.md](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage2_toolchain_target_boundary/plan.md)
* [spikes/preproject_stage2_toolchain_target_boundary/learnings.md](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage2_toolchain_target_boundary/learnings.md)

尤其要保留下面这些已经被验证过的结论：

* `task` 会落成一串 `run`
* `run` 是 runtime container，而不只是执行记录
* `worktree` 归属于 `run`
* `target = git repository`
* workflow state 安装在 `target/.workflow/`
* `mgr` 是独立 agent
* `mgr` 不直接拿 worker launcher
* `mgr -> workflow CLI -> toolchain regains control` 这条控制链成立
* `prepare_run` 必须进入正式模型

`preproject_stage3_definition_externalization` 的任务不是重新验证这些点本身，而是把它们继续推进到更稳定的“定义层外置化”结构中。

## 1. 这一轮要验证什么

`preproject_stage3_definition_externalization` 优先验证下面 4 件事：

1. `task_type` 能否从内建代码 map 收敛成正式 artifact
2. prompt 能否从内建字符串收敛成正式文件 artifact
3. task definition 能否从单个 `goal` 收敛成更正式的最小 schema
4. `prepare_run` 能否在保持 toolchain 内建控制面的前提下收敛成“声明式定义 + 最小 before 扩展点”

这 4 件事实际上都指向同一个问题：

* 定义层能否外置
* 同时不把 workflow 控制面打散

## 2. 这一轮暂时不验证什么

为了保持主干简单，这一轮先不验证：

* 通用 plugin system
* 第三方 task_type marketplace
* 多 target 管理
* 并发 task / 并发 run
* 多用户协作
* 远程执行
* 完整恢复语义
* 正式 migration framework
* 精细权限模型
* 丰富 UI / 可视化

原则很简单：

* 凡是会让定义层外置化变成“平台化”的分叉，先不做

## 3. 默认简单假设

`preproject_stage3_definition_externalization` 先统一按下面这些简单假设推进：

* 仍然只处理一个本地 git repo target
* 仍然只处理一个 workflow state root：`target/.workflow/`
* 仍然只支持单 active task stream
* 仍然只保留一个最小内建 task_type bundle：`impl`
* 先不支持从外部仓库下载 task_type
* task_type artifact 先直接跟随 toolchain 源码分发
* prompt 先按纯文本文件理解，不做模板语言升级
* task definition 先只增加少量 durable 字段，不追求通用 schema
* `prepare_run` 本体仍由 toolchain 内建收口
* 如果需要扩展点，先只允许 `before_prepare_run`
* `before_prepare_run` 先只允许返回有限字段，不允许直接 materialize run

## 4. 建议验证顺序

### Phase 1: Task-Type Artifact Model

先验证 task_type 定义本身能不能脱离代码内建 map。

这一阶段要验证的不是开放扩展，而是：

* task_type 是否能被表达成安装型 artifact
* install 是否能把 artifact 安装到 `target/.workflow/task_types/`
* runtime 是否能只依赖安装后的 artifact，而不是依赖代码里的内建常量

最小目标：

* `toolchain` 内存在一个 `task_type bundle`
* bundle 内有 `task_type.edn`
* install 会复制 bundle 到 target
* runtime 从 target 内 artifact 读取 task_type 定义

如果这一步不成立，后面的 prompt 外置和 task definition 收敛都不稳定。

### Phase 2: Prompt Artifact Model

在 task_type artifact 成立后，再验证 prompt 从代码常量收敛成文件。

这一阶段的重点不是 prompt 内容本身，而是 source of truth 的迁移。

最小目标：

* mgr / worker prompts 以文件形式存在于 task_type bundle 中
* install 会把 prompt 文件安装到 target
* runtime 只从 target 内 prompt artifact 读取内容
* 不再依赖代码内建字符串 map 作为运行时 source of truth

这一步决定“定义层外置化”是不是在真实发生，而不只是换一种写法。

### Phase 3: Minimal Task Definition Schema

在 prompt 外置后，再验证 task definition 的最小 schema。

这一阶段不是要做通用任务描述语言，而是要回答：

* 一个正式 task 至少应该有哪些 durable fields

最小目标：

* 在 `goal` 之外，引入极少数最必要字段
* 明确哪些字段属于 `task`
* 明确哪些字段属于 `task_type`
* 明确哪些字段属于 `prepare_run` 的派生结果

建议先只考虑下面这些字段：

* `goal`
* `scope`
* `constraints`
* `success_criteria`

这里继续采取最简单方案：

* 全部按可读文本或简单 EDN 字段理解
* 不引入复杂 schema framework

### Phase 4: Minimal Governance Split

在 task definition 开始稳定后，再做最小治理切分。

这一阶段不是为了完成正式治理，而是为了先把 `.workflow/` 内状态分成两类：

* durable workflow state
* local runtime state

最小目标：

* 初步列出哪些目录或文件属于 durable state
* 初步列出哪些目录或文件属于 local-only runtime state
* install / `.gitignore` 行为和这个切分保持一致

这一步的意义是：

* 先把治理边界说清楚
* 而不是先把治理机制做复杂

### Phase 5: `prepare_run` Definition Split

在定义层和最小治理切分都初步稳定后，再回到 `prepare_run`。

这一阶段要验证的不是“任意 hook 能不能工作”，而是：

* `prepare_run` 能否保持内建收口
* `task_type` 能否通过声明式定义影响 prepare
* 如果需要扩展点，`before_prepare_run` 是否足够

最小目标：

* `prepare_run` 本体仍由 toolchain 执行
* task_type artifact 能声明 prepare 相关策略
* 如需扩展点，只先支持 `before_prepare_run`
* `before` 只能返回有限字段
* `before` 不允许直接创建 worktree
* `before` 不允许直接写 run 最终状态

这一步决定：

* 定义层外置后，控制面是否仍然稳固

### Phase 6: End-to-End Loop Under Externalized Definition

最后再拼完整闭环。

这一阶段不是为了增加更多 runtime 能力，而是为了确认：

* 当 task_type / prompt / task definition 已经部分外置后
* `preproject_stage2_toolchain_target_boundary` 已验证的 workflow 主干是否仍然成立

最小目标：

* install target
* install task_type bundle
* seed collection/task with minimal new schema
* mgr 判断下一步
* mgr 调 workflow command
* toolchain 读取已安装定义
* toolchain prepare run
* worker 执行
* run / task 更新
* task 进入 done 或 error

如果这一轮成立，`preproject_stage3_definition_externalization` 就完成了主要目标。

## 5. 建议的最小目录结构

当前先只建议保留这几个文件或目录：

```text
spikes/preproject_stage3_definition_externalization/
  plan.md
  README.md
  toolchain/
  sample_target/
```

其中：

* `toolchain/` 是本轮最小实现
* `sample_target/` 仍然是示例 git repo
* `README.md` 只负责解释这轮验证目标

建议在 `toolchain/` 内显式加入：

```text
toolchain/
  task_type_bundles/
    impl/
      task_type.edn
      prompts/
      README.md
```

这一步的意义不是做 plugin system，而是：

* 先让“内建 task_type”也以 artifact 形态存在

## 6. 设计检查点

在每个 phase 完成后，都建议用下面这些问题做检查：

* 当前拆出去的是定义层，还是把控制层也一起拆散了？
* 当前新增内容是在继承 `preproject_stage2_toolchain_target_boundary` 的边界，还是在偷偷回退成代码内建分支？
* 当前 artifact 是真正的 source of truth，还是只是 install 时复制一份摆设？
* 当前字段到底属于 `task_type`、`task`，还是属于 `prepare_run` 派生结果？
* 当前变化是在澄清治理边界，还是在过早引入治理平台？
* 当前设计是不是在为未来 plugin system 预支复杂度？

如果回答开始变模糊，说明 spike 偏题了。

## 7. 当前建议

`preproject_stage3_definition_externalization` 的第一步不应该直接做 hook system，也不应该直接做 plugin system。

更好的开始方式是：

1. 先把当前唯一的内建 `impl` task_type 做成 bundle
2. 再把 prompts 从代码常量迁移成文件
3. 再让 runtime 只依赖安装后的 artifact
4. 再收敛最小 task schema
5. 最后才回到 `prepare_run` 的扩展边界

也就是说：

* `preproject_stage2_toolchain_target_boundary` 是先稳定 `toolchain + target` 外层切分
* `preproject_stage3_definition_externalization` 应该先稳定“定义层外置，控制层内建”的中层切分
