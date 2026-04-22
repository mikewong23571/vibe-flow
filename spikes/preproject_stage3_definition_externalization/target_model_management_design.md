# Target Model Management Initial Design

这份文档回答一个在 `preproject_stage3_definition_externalization` 之后必须进入正式设计的问题：

* 当 workflow 状态已经安装进 `target` repo 后
* 哪些对象应被视为 target 内正式模型
* 工具链应该如何在 target 内对这些模型做管理
* 尤其是 `task_type` 这种“定义层对象”，应该怎样同时管理模型和目录结构

这份文档当前只做初步设计，不直接引入完整 migration/framework/plugin system。

## 1. 设计目标

目标不是把 target 内所有内容都抽象成统一 CRUD 数据库，而是：

* 明确 target 内有哪些正式模型
* 明确每类模型的 source of truth
* 明确每类模型的生命周期操作
* 明确每类模型对应的目录布局 contract
* 让工具链能够以受控方式创建、更新、检查和清理这些模型

这份设计默认延续 `preproject_stage3_definition_externalization` 已成立的边界：

* `target = git repository`
* workflow state 安装进 `target/.workflow/`
* 定义层外置
* workflow 控制面仍由 toolchain 内建收口

## 2. 核心判断

target 内对象不能都按同一种“文件 CRUD”理解。

更准确地说，至少要区分 4 类模型：

1. `system models`
2. `definition models`
3. `domain models`
4. `runtime models`

不同类型的模型，应有不同的管理语义。

例如：

* `task_type` 需要 `create/update/deprecate/remove-if-safe`
* `task` 需要 `create/update/close/cancel`
* `run` 需要 `prepare/finalize/gc`
* `install metadata` 不应该被普通业务命令直接修改

所以正式设计不应该继续用一句“对 target 内模型做增删改查”来概括全部对象。

## 3. Target 内模型分类

### 3.1 System Models

这类模型描述 target 上 workflow 安装本身。

最小包括：

* `install`
* `target`
* `registry`
* `layout`

它们的特点是：

* 由 toolchain 维护
* 不应由 task workflow 任意修改
* 用于 install、校验、升级、恢复、管理索引

### 3.2 Definition Models

这类模型描述“workflow 定义层”，而不是某个具体任务实例。

最小包括：

* `task_type`

后续可能包括：

* prompt-set
* collection-template
* policy-set

它们的特点是：

* 是可复用定义
* 会被 domain models 引用
* 往往有目录布局，而不只是单个文件
* 需要正式的 create/update/deprecate/remove 语义

### 3.3 Domain Models

这类模型是 workflow 中真正被推进的业务对象。

最小包括：

* `collection`
* `task`

它们的特点是：

* durable
* 被用户或 workflow 显式操作
* 生命周期与具体任务推进相关

### 3.4 Runtime Models

这类模型是运行态对象，不适合按普通 durable CRUD 理解。

最小包括：

* `run`
* `mgr_run`
* worktree
* agent_home copy

它们的特点是：

* 主要由 workflow runtime 驱动
* 生命周期更接近 `prepare/finalize/gc`
* 目录结构往往比 record 更重要

## 4. 建议的 Target 内目录布局

当前建议把 target 内状态布局正式收紧为：

```text
target/
  .workflow/
    state/
      system/
        install.edn
        target.edn
        layout.edn
        registries/
          task_types.edn
          collections.edn
          tasks.edn
      definitions/
        task_types/
          <task-type-id>/
            task_type.edn
            meta.edn
            prompts/
            hooks/
      domain/
        collections/
        tasks/
    local/
      runs/
      mgr_runs/
      agent_homes/
```

这层布局的意图是：

* `system/` 表示工具链自维护模型
* `definitions/` 表示可管理定义层
* `domain/` 表示业务 durable state
* `local/` 表示本地 runtime state

这会比把所有 durable 内容都平铺在 `state/` 下一层更清晰。

## 5. `task_type` 的正式模型

`task_type` 不应再被理解成“一个目录里放几个文件”。

更准确地说：

```text
task_type
  = definition model
  = managed package
  = metadata record + layout contract
```

### 5.1 `task_type` 的 source of truth

`task_type` 的 source of truth 应由两部分组成：

1. `task_type.edn`
2. `meta.edn`

其中：

* `task_type.edn` 负责表达业务定义
* `meta.edn` 负责表达管理信息

### 5.2 `task_type.edn`

`task_type.edn` 最小包含：

* `:task-type`
* `:mgr-home`
* `:workers`
* `:worker-homes`
* `:prompts`
* `:task-schema`
* `:prepare-run`

这部分是定义层语义。

### 5.3 `meta.edn`

`meta.edn` 最小建议包含：

* `:id`
* `:version`
* `:status`
* `:managed-by`
* `:layout-version`
* `:source`
* `:installed-at`
* `:updated-at`
* `:checksum`

这部分是管理层语义。

`meta.edn` 的意义是：

* 给 toolchain 一个正式管理入口
* 让 inspect/list/update/remove 不需要只靠扫目录猜状态
* 为后续 migration 和 reconcile 留出位置

### 5.4 `task_type` 目录 contract

当前最小 contract 建议为：

```text
definitions/task_types/<task-type-id>/
  task_type.edn
  meta.edn
  prompts/
    mgr.txt
    impl.txt
    review.txt
    refine.txt
  hooks/
    before_prepare_run
```

其中：

* `task_type.edn` 必选
* `meta.edn` 必选
* `prompts/` 必选
* `hooks/` 可选，但目录建议固定存在

也就是说，目录布局本身是模型 contract 的一部分，而不是纯实现细节。

## 6. Registry 设计

只靠扫目录可以工作，但不足以支撑正式管理。

因此建议引入 registry：

```text
target/.workflow/state/system/registries/task_types.edn
```

最小 registry item 建议包含：

* `:id`
* `:kind`
* `:path`
* `:version`
* `:status`
* `:layout-version`
* `:source`
* `:installed-at`
* `:updated-at`

### 6.1 为什么需要 registry

原因有四个：

* list 不必依赖目录扫描推断
* update/remove 可以先做引用校验
* layout 和 logical record 可以互相校验
* 未来 migration 可以有明确入口

### 6.2 registry 的边界

registry 不是新的 source of truth。

这里的建议是：

```text
package directory
  = definition source of truth

registry
  = management index
```

registry 负责索引和校验，不负责替代 package 内容。

## 7. 生命周期操作设计

### 7.1 `task_type`

建议正式支持下面这些操作：

* `create-task-type`
* `update-task-type`
* `inspect-task-type`
* `list-task-types`
* `deprecate-task-type`
* `remove-task-type-if-safe`

当前不建议直接支持裸 `delete-task-type`。

### 7.2 `task_type` 的状态

建议先只支持：

* `:active`
* `:deprecated`

其中：

* `active` 可被新 task 引用
* `deprecated` 不允许新 task 使用，但允许旧 task 继续回看历史

如果后续要删，应通过 `remove-if-safe` 判断：

* 是否仍被 task 引用
* 是否仍被 run 引用

### 7.3 为什么不建议硬删除

因为一旦历史 task/run 仍引用这个 `task_type`，直接删目录会破坏：

* 历史可读性
* 恢复语义
* 调试语义

因此更稳的策略是：

```text
active -> deprecated -> removable-if-safe
```

## 8. Layout Reconciler

管理 target 内模型时，不能把目录维护散落在各个命令里。

建议显式引入一个 layout reconcile 概念：

```text
desired model state
  -> reconcile target layout
  -> create missing files/dirs
  -> verify required entries
  -> optionally clean orphan entries
```

这意味着后续真正操作 `task_type` 时，不应由上层业务逻辑直接：

* `mkdir`
* `spit`
* `delete-tree`

而应统一进入 target integration / management 层。

### 8.1 reconcile 的职责

最小职责包括：

* 确保必选文件存在
* 确保目录结构满足 contract
* 检查 record 和 registry 是否一致
* 检查 meta/layout version 是否可接受

### 8.2 当前不建议做的事情

当前阶段先不建议做：

* 自动 patch 用户手改内容
* 跨版本复杂 migration
* 通用 plugin marketplace
* 多 source overlay 合并

## 9. 管理层模块建议

如果按这套设计继续推进，toolchain 内建议新增一层：

```text
management/
```

它专门负责：

* model create/update/deprecate/remove
* registry maintenance
* layout reconciliation
* safe removal checks

当前最小可拆为：

* `management/task_type_manager.clj`
* `management/registry.clj`
* `management/layout_reconciler.clj`

这样可以避免把“模型管理”散落到：

* install
* definition loader
* state store
* CLI

## 10. 不是所有模型都该支持统一 CRUD

初步建议如下：

* `system models`
  `install / refresh / reconcile / migrate`
* `definition models`
  `create / update / inspect / deprecate / remove-if-safe`
* `domain models`
  `create / update / inspect / archive / close`
* `runtime models`
  `prepare / finalize / gc / inspect`

这里故意避免用统一的“增删改查”覆盖所有对象。

因为系统语义本来就不是一个普通对象存储层。

## 11. 初步 CLI 设计

围绕 `task_type`，后续可先考虑增加下面这些命令：

```text
bb -m spike-v3.toolchain list-task-types
bb -m spike-v3.toolchain inspect-task-type --task-type <id>
bb -m spike-v3.toolchain create-task-type --task-type <id>
bb -m spike-v3.toolchain deprecate-task-type --task-type <id>
```

其中：

* `create-task-type`
  创建 package skeleton + `meta.edn` + registry entry
* `inspect-task-type`
  同时展示 definition、meta、registry status
* `deprecate-task-type`
  更新 meta 和 registry，不触碰历史 task/run

真正的 `update-task-type` 可以在下一步再设计。

## 12. 建议的最小落地顺序

如果把这个设计推进成下一轮 spike，建议按下面顺序实现：

### Phase 1: State Layout Split Refinement

把 durable state 从：

* `task_types/`
* `collections/`
* `tasks/`

收紧成：

* `system/`
* `definitions/`
* `domain/`

### Phase 2: `task_type.meta`

为已安装 `task_type` 引入 `meta.edn`。

### Phase 3: `task_types` Registry

引入：

* `system/registries/task_types.edn`

### Phase 4: Minimal Management Commands

最小实现：

* `list-task-types`
* `inspect-task-type`
* `create-task-type`

### Phase 5: Deprecation And Safe Removal Check

支持：

* `deprecate-task-type`
* `remove-task-type-if-safe`

## 13. 当前结论

当前可以先正式固定下面这个设计判断：

```text
Target-managed models
  must declare:
  1. model kind
  2. source of truth
  3. lifecycle operations
  4. on-disk layout contract
  5. registry/index strategy
  6. safe deletion policy
```

对于 `task_type`，对应就是：

* kind: `definition model`
* source of truth: `task_type package directory + meta.edn`
* lifecycle: `create/update/deprecate/remove-if-safe`
* layout: `task_type.edn + prompts/ + hooks/ + meta.edn`
* registry: `task_types registry`
* deletion policy: no hard delete while still referenced

这就是后续把 target 从“能存文件”推进成“能管理正式模型”的最小起点。
