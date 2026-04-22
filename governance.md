# Project Governance For The Formal Version

这份文档定义当前 repo 的正式工程治理基线。

它不讨论组织审批，而讨论**项目如何被持续演化而不失控**。

旧版偏探索性的治理沉淀仍保留在：

* [project_governance_design.md](/home/mikewong/proj/main/vibe-flow/spikes/preproject_stage3_definition_externalization/project_governance_design.md)

这份文档则收敛成正式版本应遵守的治理规则。

## 1. 治理目标

治理的目标不是“代码看起来整洁”，而是：

* 保证系统继续沿着正式设计路线生长
* 控制复杂性停留在少数已知热点
* 让新增能力知道应该落在哪一层
* 让稳定底座不被试验性逻辑污染

一句话说：

```text
把项目治理成
  稳定底座 + 高变热点 + 明确扩展模板 + 受控复杂模块
```

## 2. 核心治理原则

### 2.1 先保路线，再保局部优雅

任何改动先回答：

* 是否强化了 `toolchain + target` 的边界
* 是否保持了 `definition layer externalized / control plane built-in`
* 是否避免把系统推向通用 workflow engine

如果方向错误，局部代码再整洁也不合格。

### 2.2 模块按“变化原因”治理

一个模块允许复杂，但只能围绕一个主变化原因复杂。

例如：

* workflow 编排器因为 orchestration 变化
* definition 解释器因为 definition interpretation 变化
* install 因为 bootstrap / reconcile 变化

如果一个模块同时因为三四类完全不同的原因而变，就应视为治理预警。

### 2.3 允许复杂模块存在，但复杂性必须收口

项目里一定会有复杂模块。

治理要求不是“消灭复杂模块”，而是：

* 明确哪些模块是复杂模块
* 明确它们是编排器还是解释器
* 不让复杂性四处渗漏

### 2.4 高变模块和低变模块分区治理

高变模块允许频繁演化，但必须被特别审视。  
低变模块作为稳定底座，不承接试验性逻辑。

### 2.5 依赖方向比函数拆分更重要

项目失控通常不是从“大函数”开始，而是从层间反向污染开始。

所以治理主轴是：

* 高层依赖低层
* 低层不依赖高层
* store 不反向承接 control logic
* product surface 不沉淀核心业务

### 2.6 on-disk layout 是正式治理对象

对于当前系统，`.workflow/` 布局不是实现细节，而是产品协议的一部分。

因此：

* 路径布局
* package layout
* registry layout
* durable/local 分区

都属于正式治理范围。

## 3. 当前模块层次

当前 repo 应按下面层次理解和治理：

```text
L0 support
L1 target substrate
L2 state stores
L3 definition / management
L4 runtime integration
L5 workflow control
L6 product surface
L7 sample / debug only
```

对应当前代码：

* `support/` -> L0
* `target/repo` + `support/paths` -> L1
* `state/` -> L2
* `definition/` + `management/` + `target/install` -> L3
* `agent/` + `state/run_store` -> L4
* `core/` -> L5
* `toolchain.clj` -> L6
* `sample/` 和 smoke/debug -> L7

## 4. 依赖规则

### Rule 1. 高层可以依赖低层，低层不能依赖高层

例如：

* `core` 可以依赖 `state`、`definition`、`agent`
* `state` 不能依赖 `core`
* `support` 不依赖业务层

### Rule 2. `sample` 不能进入正式控制主线

`sample/` 只能服务于：

* smoke
* demo
* fixtures

不得继续进入正式 workflow control API。

### Rule 3. `state` store 默认只做 persistence

store 的默认职责是：

* path locate
* load
* save
* list / enumerate

如果需要 rule，只允许非常轻的 structural validation。

### Rule 4. `management` 负责 model lifecycle，不负责 runtime execution

`management` 模块负责：

* inspect
* create
* update
* deprecate
* reconcile
* registry maintenance

不负责：

* 启动 runtime
* 推进 workflow
* 写 run 执行结果

### Rule 5. `definition` 负责解释定义，不负责管理定义生命周期

* `definition` 负责 load / resolve / interpret
* `management` 负责 lifecycle / registry / authoring

### Rule 6. `toolchain` 只做 product surface

`toolchain.clj` 负责：

* 参数解析
* command routing
* user-facing output

不负责：

* 业务状态推进
* runtime 决策
* definition lifecycle 规则

## 5. 单文件可读性规则

### 5.1 文件长度预算按模块类型治理

#### A. Store / adapter / util 文件

优先控制在：

* `<= 120` 行

适用：

* `state/*_store.clj`
* `support/*.clj`
* `agent/launcher/*.clj`

#### B. 解释器 / orchestrator 文件

优先控制在：

* `<= 180` 行

适用：

* workflow 编排器
* definition 解释器
* management manager
* install / reconcile 模块

#### C. 超过 220 行的文件必须有明确拆分轴

不是立刻强拆，但必须能回答未来怎么拆、沿什么职责拆。

### 5.2 单文件可读性的判断标准

每个文件都用下面四个问题审视：

1. 能否一句话说清主职责
2. 是否只有一个主变化原因
3. 是否能在 3 分钟内找到主入口
4. 依赖面是否符合所在层

如果四条中有两条失败，应视为治理预警。

## 6. 高变模块与低变模块

### 6.1 高变模块

当前应明确视为高变热点的模块包括：

* workflow 编排器
* install / reconcile
* task_type lifecycle manager
* task_type definition interpreter
* run prepare / finalize / candidate carrier
* mgr runtime bridge
* root product surface

对高变模块的治理要求：

* 不允许同时引入新职责和新依赖方向
* 改动必须解释“为什么应该落在这个热点”
* 一旦依赖面继续扩大，必须同步提出拆分方案

### 6.2 低变模块

当前应视为稳定底座的模块包括：

* paths substrate
* util
* stage model
* task lifecycle pure transition
* git repo substrate
* 薄 store

对低变模块的治理要求：

* 不承接试验性业务逻辑
* 接口少且稳定
* 不因临时方便扩大依赖面

## 7. 同类模块的扩展模板

### 7.1 新增 store

新 store 必须：

* 只做 load / save / list
* 默认不承接复杂业务规则
* 不调用 runtime
* 不做 orchestration

### 7.2 新增 management 模块

新 manager 必须：

* 负责 model lifecycle
* 围绕 registry / meta / inspect / reconcile
* 不直接启动 runtime
* 不直接推进 workflow

### 7.3 新增 runtime adapter

新 launcher 必须：

* 只实现 launcher contract
* 不决定 task stage
* 不修改 workflow 生命周期语义
* 不引入新的 target layout 语义

### 7.4 新增 definition package

新 definition package 必须：

* 通过 target-managed artifact 落地
* 有 registry 和 inspect 支持
* 不通过硬编码分支接入

## 8. 复杂模块治理

### 8.1 编排器模块

编排器允许复杂，但只能复杂在：

* 状态推进顺序
* 控制权交接
* runtime 调用顺序

不能继续混入：

* sample/demo
* CLI 展示细节
* definition authoring

### 8.2 解释器模块

解释器允许复杂，但只能复杂在：

* artifact load
* declarative resolve
* contract enforcement
* runtime input interpretation

不能继续混入：

* lifecycle management
* scaffolding
* product surface

### 8.3 bootstrap / reconcile 模块

这类模块允许复杂，但只能复杂在：

* install contract
* layout reconcile
* bootstrap sequence
* migration sequence

不能继续混入：

* workflow orchestration
* sample behavior
* definition authoring

## 9. 当前正式治理动作

从现在开始，建议按下面动作治理 repo。

### Action 1. 新模块必须先归类，再落文件

新增文件前，先说明它属于：

* support
* target substrate
* state
* definition
* management
* runtime integration
* workflow control
* product surface
* sample/debug

### Action 2. 任何高变模块改动都做依赖增量审查

至少检查：

* 是否新增跨层依赖
* 是否混入第二主职责
* 是否把 debug/demo 逻辑带入正式主线

### Action 3. 复杂模块必须先有拆分轴，再继续堆功能

不是要求立刻拆，而是要求先讲清：

* 未来沿哪条职责边界拆
* 现在为什么暂时保持在一起

### Action 4. 低变底座不承接试验性需求

任何试验性演化，默认不应优先落到：

* paths
* util
* stage model
* repo substrate
* 薄 store

### Action 5. layout 变更必须同步更新设计和 paths substrate

任何 `.workflow` 布局变更，必须同时更新：

* 正式设计文档
* 路径基线
* install / reconcile 行为

## 10. 机器校验治理规则

当前正式版本已经把一组最基础、最关键的治理规则落成可执行校验，并通过 Clojure CLI 与 pre-commit 运行。

运行命令：

```bash
clojure -M:governance
clojure -M:test
```

pre-commit 配置在 repo 根目录：

* [.pre-commit-config.yaml](/home/mikewong/proj/main/vibe-flow/.pre-commit-config.yaml)

当前机器校验的作用域故意先收敛到：

* `src/`
* `test/`

原因是：

* 这是正式版本的 Clojure 源码边界
* 归档目录和 spike 目录不应反向干扰正式项目治理
* repo 根目录本身是组合边界，不适合作为 fan-out 规则的直接治理对象

### Machine Rule 1. Formal Project Skeleton

要求下面这些路径存在：

* `deps.edn`
* `.pre-commit-config.yaml`
* `src/`
* `test/`

异常时会暴露：

* 规则意图
* 修复应放在 repo 根目录哪里

### Machine Rule 2. Pre-commit Must Invoke Governance

要求 `.pre-commit-config.yaml` 中包含：

* `clojure -M:governance`

这样治理校验才会在提交前自动运行。

### Machine Rule 3. Namespace Must Match Path

要求 `src/` 和 `test/` 下的 `*.clj` 文件满足：

* namespace 与文件路径一致
* `_` 与 `-` 的转换符合 Clojure 约定

### Machine Rule 4. Every Formal Module Must Be Registered

要求 `src/` 和 `test/` 下的每个正式 namespace 都必须在：

* `resources/vibe_flow/governance/module_manifest.edn`

中注册。

这样后续新增模块时，不允许“先加文件，后补治理归类”。

### Machine Rule 5. Module Contract Metadata Must Be Complete

每个正式模块都必须显式声明最小治理元信息：

* `:layer`
* `:volatility`
* `:module-kind`
* `:complexity`
* `:responsibility`

并且：

* 高变模块必须声明 `:split-axis`
* 复杂模块必须声明 `:split-axis`
* 低变模块必须声明 `:stability-role`

### Machine Rule 6. Layer Dependency Direction

每个正式模块的 project-internal 依赖都必须符合层次方向。

当前通过 manifest 中的 layer 分类，校验：

* 高层依赖低层
* 低层不能反向依赖高层
* runtime / state / definition / governance 各层不会互相漂移

### Machine Rule 7. No Sample Or Debug Code In Formal Source

正式 `src/` 中不能出现：

* `sample`
* `debug`

相关路径或层次标记。

这条规则的目的，是确保 formal source tree 不被 spike/demo 逻辑反向污染。

### Machine Rule 8. Single File Length

当前正式阈值为：

* `> 300` 行：告警
* `> 400` 行：错误

异常时的修复指导明确要求：

* 优先在同一模块家族附近拆分子命名空间
* 不要把职责拆到错误层次

### Machine Rule 9. Directory Fan-out

当前正式阈值为：

* `> 7` 个直接子项：告警
* `> 11` 个直接子项：错误

这里的“子项”包括：

* 文件
* 子目录

但只统计 `src/` 和 `test/` 下面的正式源码目录。

异常时的修复指导明确要求：

* 在相邻 namespace subtree 中拆出更清晰的子模块目录
* 不要继续平铺同类模块

## 11. 评审检查单

后续每次较大改动，至少回答下面这些问题：

1. 这次改动落在正确层吗？
2. 有没有把高层语义塞进低层模块？
3. 有没有引入新的跨层依赖？
4. 这个模块现在是否出现第二主变化原因？
5. 这个改动是加深 definition layer，还是把 definition 重新塞回 control plane？
6. 它是增强 target-managed models，还是绕开 registry / layout contract 走捷径？
7. 它是在强化 candidate lifecycle，还是让 integration 语义继续混乱？

## 12. 一句话总结

正式版本的治理理念可以压缩成一句话：

```text
用稳定底座保护方向，
用高变热点承载演化，
用依赖规则和扩展模板控制复杂性，
让系统继续沿着既定设计严谨生长。
```
