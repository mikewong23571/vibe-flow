# Task-Type Driven Coding Agent Toolchain Architecture

> 本文档基于 `design.md` 的设计理念，对系统架构进行图示化整理。
> 它是“理解版架构图”，用于审阅设计是否表达准确，不替代正式规范。

## 1. 设计目标

系统的目标不是构建一个通用 workflow engine，而是提供一个足够薄的工具链，让一批同类型的 coding agent 任务可以被持续推进、复用和恢复。

核心判断：

* `task_type` 是复用单位
* `collection` 是同类任务池
* `task` 是具体推进单元
* `mgr` 负责轻量编排
* `worker` 负责具体执行
* `run` 记录一次 worker 执行
* `error tool` 是唯一显式失败出口

---

## 2. 系统总览

```mermaid
flowchart LR
  U[User]
  TT[Task Type]
  C[Collection]
  T[Task]
  MHome[mgr_home]
  WHome[worker_home]
  M[mgr Agent]
  W[worker Agent]
  R[Run Record]
  E[error tool]

  U -->|create / inspect| C
  TT -->|defines workflow| C
  C -->|contains| T

  MHome -->|instantiates| M
  WHome -->|instantiates| W

  TT -->|binds mgr_home and allowed workers| M
  TT -->|defines worker order and routing rules| W

  M -->|select next task and next worker| W
  W -->|produces execution result| R
  W -->|cannot continue| E
  E -->|error recorded for routing| M
  R -->|latest run result| M
  M -->|updates task state| T
  U -->|review status and outputs| T
```

这个总览表达的是：

* 框架只承载任务对象、agent 实例化、worker 调用和 run 记录
* `mgr` 不直接做实现工作，只做路由和状态推进
* `worker` 既可以产出代码，也可以产出 review/refine/test/doc 结果
* 成功路径依赖 worker 正常结束，失败路径依赖 `error tool`

---

## 3. 分层架构

```mermaid
flowchart TB
  subgraph L1[User Layer]
    U1[User]
    U2[Collection Inspection]
    U3[Task Creation]
  end

  subgraph L2[Workflow Definition Layer]
    T1[task_type]
    T2[workflow prompt]
    T3[worker prompt]
    T4[allowed workers]
    T5[routing rules]
  end

  subgraph L3[Runtime Profile Layer]
    A1[mgr_home]
    A2[worker_home]
    A3[tools / permissions / instructions]
  end

  subgraph L4[Execution Layer]
    M[mgr Agent]
    W1[impl worker]
    W2[review worker]
    W3[refine worker]
    W4[test/doc/... worker]
  end

  subgraph L5[State Layer]
    C[collection]
    K[task]
    R[run]
    X[error state]
    D[done state]
  end

  U1 --> U2
  U1 --> U3
  U3 --> C
  C --> K

  T1 --> T2
  T1 --> T3
  T1 --> T4
  T1 --> T5

  A1 --> A3
  A2 --> A3

  T1 --> M
  T4 --> W1
  T4 --> W2
  T4 --> W3
  T4 --> W4

  A1 --> M
  A2 --> W1
  A2 --> W2
  A2 --> W3
  A2 --> W4

  M --> K
  M --> W1
  M --> W2
  M --> W3
  M --> W4

  W1 --> R
  W2 --> R
  W3 --> R
  W4 --> R

  R --> K
  M --> X
  M --> D
```

分层含义：

* `Workflow Definition Layer` 承载任务类型差异
* `Runtime Profile Layer` 承载 agent 能力边界
* `Execution Layer` 只负责推进和执行
* `State Layer` 只负责记录和流转，不负责深度解释产物

---

## 4. 核心对象关系

```mermaid
classDiagram
  class TaskType {
    +name
    +mgr_home
    +worker_home
    +allowed_workers
    +workflow_prompt
    +routing_rules
  }

  class Collection {
    +id
    +task_type
    +name
    +status_pool
  }

  class Task {
    +id
    +collection_id
    +goal
    +current_stage
    +latest_run
    +state
  }

  class Run {
    +id
    +task_id
    +worker_name
    +started_at
    +ended_at
    +error_flag
    +output_ref
  }

  class AgentHome {
    +instruction
    +tools
    +permissions
    +cwd_policy
    +context_policy
    +output_style
  }

  class MgrAgent
  class WorkerAgent
  class ErrorTool {
    +reason
    +next_step?
  }

  TaskType --> Collection : defines type for
  Collection --> Task : contains
  Task --> Run : has many
  TaskType --> AgentHome : binds mgr_home / worker_home
  AgentHome --> MgrAgent : instantiates
  AgentHome --> WorkerAgent : instantiates
  MgrAgent --> WorkerAgent : invokes
  WorkerAgent --> Run : creates
  WorkerAgent --> ErrorTool : may call
```

我对对象关系的理解是：

* `TaskType` 是协议对象，不是纯标签
* `Collection` 是调度上下文，不是通用项目管理容器
* `Task` 是状态机实例
* `Run` 是 task 上的一次 worker 执行事件
* `AgentHome` 收敛 agent 能力，而不是承载业务语义

---

## 5. 运行时交互图

```mermaid
sequenceDiagram
  participant User
  participant Collection
  participant Mgr as mgr Agent
  participant Worker as worker Agent
  participant Run
  participant Error as error tool

  User->>Collection: create tasks under a task_type
  Mgr->>Collection: inspect runnable tasks
  Mgr->>Worker: start selected worker with task context

  alt worker completes normally
    Worker->>Run: write run record and output ref
    Run-->>Mgr: latest run result
    Mgr->>Collection: advance task to next stage or done
  else worker cannot continue
    Worker->>Error: emit failure reason
    Error-->>Mgr: explicit failure signal
    Mgr->>Collection: mark task error or route by task_type rule
  end

  Collection-->>User: updated task state and run history
```

这个运行时交互图体现两个关键点：

* 框架识别的是“正常结束”与“显式失败”
* `mgr` 读取结果是为了路由，不是为了充当代码质量裁判

---

## 6. 标准 `impl` 任务流

```mermaid
flowchart TB
  A[task created]
  B[mgr selects task from collection]
  C[mgr starts impl worker]
  D[impl worker finishes]
  E[mgr starts review worker]
  F[review worker finishes]
  G{review control signal}
  H[task done]
  I[mgr starts refine worker]
  J[refine worker finishes]
  X[worker calls error tool]
  Z[task error]

  A --> B
  B --> C
  C --> D
  D --> E
  E --> F
  F --> G
  G -->|pass| H
  G -->|needs refine| I
  I --> J
  J --> E

  C --> X
  E --> X
  I --> X
  X --> Z
```

这个任务流对应的是文档中的标准示例：

* `impl worker` 负责第一次实现
* `review worker` 负责给出是否通过的控制信号
* `refine worker` 基于 review 结果继续修正
* 任何阶段都可以通过 `error tool` 终止正常推进

---

## 7. 状态视角

```mermaid
stateDiagram-v2
  [*] --> Todo
  Todo --> InImpl : mgr starts impl worker
  InImpl --> InReview : impl worker completed
  InImpl --> Error : impl worker error

  InReview --> Done : review says pass
  InReview --> InRefine : review says needs refine
  InReview --> Error : review worker error

  InRefine --> InReview : refine worker completed
  InRefine --> Error : refine worker error
```

这里的重点不是状态机复杂度，而是状态机足够薄：

* 阶段流转由 `task_type` 决定
* 状态记录由框架承载
* 复杂判定不下沉到框架

---

## 8. 责任边界图

```mermaid
flowchart LR
  F[Framework]
  M[mgr]
  W[worker]
  T[task_type prompt]

  F --> F1[stores task, collection, run]
  F --> F2[starts worker]
  F --> F3[records state transitions]
  F --> F4[recognizes normal end vs error]

  M --> M1[select task]
  M --> M2[select next worker]
  M --> M3[route according to workflow]

  W --> W1[implement]
  W --> W2[review]
  W --> W3[refine]
  W --> W4[emit error when blocked]

  T --> T1[define routing semantics]
  T --> T2[define required context]
  T --> T3[define control signal format]
  T --> T4[define stopping conditions]
```

责任边界总结：

* 框架不做 task_type 专属裁决
* `mgr` 不做深度质量审查
* `worker` 不负责全局编排
* `task_type prompt` 承载大部分流程语义

---

## 9. 需要你确认的推断

以下内容是我基于 `design.md` 做的补充性理解，文档原文没有完全显式写死，审阅时建议重点确认：

* `collection` 更像“调度上下文”，`task` 才是具体状态机实例
* `run` 应当从属于 `task`，并记录本次由哪个 worker 执行
* 工具链虽然不要求统一 artifact schema，但至少需要最小控制信号，例如 `pass` / `needs refine`
* `mgr` 不做业务质量裁决，但会做协议级路由判断
* `error tool` 是唯一显式失败出口，但 error 后是否直接终止，仍应由 `task_type` 决定

---

## 10. 一句话架构结论

```text
这是一个以 task_type 为协议中心、以 mgr 为轻量编排器、以 worker 为执行器、
以 collection/task/run 为状态载体的薄型 coding agent workflow toolchain。
```
