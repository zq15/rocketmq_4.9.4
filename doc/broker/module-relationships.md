# Broker 模块关系图

本文档描述 RocketMQ Broker 模块内部各组件之间的关系和交互。

## 1. 核心模块架构

### 1.1 BrokerController - 核心控制器
BrokerController 是 Broker 模块的核心，负责管理和协调所有其他组件：

```mermaid
graph TD
    A[BrokerController] --> B[MessageStore]
    A --> C[NettyServer]
    A --> D[NettyClient]
    A --> E[各种Manager]
    A --> F[各种Processor]
    A --> G[各种Service]

    E --> E1[TopicConfigManager]
    E --> E2[ConsumerOffsetManager]
    E --> E3[ConsumerManager]
    E --> E4[ProducerManager]
    E --> E5[SubscriptionGroupManager]

    F --> F1[SendMessageProcessor]
    F --> F2[PullMessageProcessor]
    F --> F3[AdminBrokerProcessor]
    F --> F4[ClientManageProcessor]

    G --> G1[PullRequestHoldService]
    G --> G2[ClientHousekeepingService]
    G --> G3[SlaveSynchronize]
```

### 1.2 消息处理流程
```mermaid
sequenceDiagram
    participant Client
    participant NettyServer
    participant Processor
    participant BrokerController
    participant MessageStore
    participant Manager

    Client->>NettyServer: 发送请求
    NettyServer->>Processor: 路由到对应处理器
    Processor->>BrokerController: 调用业务逻辑
    BrokerController->>Manager: 查询/更新管理数据
    BrokerController->>MessageStore: 存储或查询消息
    MessageStore-->>BrokerController: 返回结果
    BrokerController-->>Processor: 返回处理结果
    Processor-->>NettyServer: 返回响应
    NettyServer-->>Client: 返回响应
```

## 2. 存储模块关系

### 2.1 MessageStore 及相关组件
```mermaid
graph LR
    A[MessageStore] --> B[CommitLog]
    A --> C[ConsumeQueue]
    A --> D[IndexFile]

    B --> E[消息实际存储]
    C --> F[消息队列索引]
    D --> G[消息索引]

    H[DispatchService] --> B
    H --> C
    H --> D

    I[ReputMessageService] --> H
```

### 2.2 DLedger 集群存储
```mermaid
graph TB
    A[DLedgerCommitLog] --> B[DLedgerService]
    B --> C[DLedgerRpcService]
    B --> D[DLedgerEntryHandler]
    B --> E[DLedgerStore]

    F[Master-Slave集群] --> A
    G[State Machine] --> B
```

## 3. 客户端管理模块

### 3.1 Producer 和 Consumer 管理
```mermaid
graph TD
    A[ClientManager] --> B[ProducerManager]
    A --> C[ConsumerManager]
    A --> D[ClientHousekeepingService]

    B --> E[Producer信息表]
    C --> F[ConsumerGroupInfo]
    F --> G[ConsumerChannelInfo]

    D --> H[扫描并清理过期连接]

    I[ConsumerIdsChangeListener] --> C
    J[RebalanceLockManager] --> C
```

### 3.2 消费者组管理
```mermaid
graph LR
    A[ConsumerGroupInfo] --> B[Group Name]
    A --> C[Channel Info Table]
    A --> D[Subscription Data]
    A --> E[Last Update Timestamp]

    F[ConsumerManager] --> A
    G[RebalanceLockManager] --> A
```

## 4. 消息处理模块

### 4.1 请求处理器架构
```mermaid
graph TD
    A[NettyRequestProcessor] --> B[SendMessageProcessor]
    A --> C[PullMessageProcessor]
    A --> D[AdminBrokerProcessor]
    A --> E[ClientManageProcessor]
    A --> F[ConsumerManageProcessor]
    A --> G[EndTransactionProcessor]
    A --> H[QueryMessageProcessor]
    A --> I[ReplyMessageProcessor]

    B --> J[AbstractSendMessageProcessor]
    I --> J
```

### 4.2 消息发送处理流程
```mermaid
graph LR
    A[SendMessageProcessor] --> B[消息验证]
    B --> C[事务消息处理]
    C --> D[消息存储]
    D --> E[Hook处理]
    E --> F[返回响应]

    G[AbstractSendMessageProcessor] --> B
    G --> H[消息重试处理]
    G --> I[消息编码]
```

### 4.3 消息拉取处理流程
```mermaid
graph LR
    A[PullMessageProcessor] --> B[请求验证]
    B --> C[消息过滤]
    C --> D[从存储读取]
    D --> E[长轮询处理]
    E --> F[返回响应]

    G[PullRequestHoldService] --> E
    H[MessageArrivingListener] --> E
```

## 5. 配置管理模块

### 5.1 配置管理器关系
```mermaid
graph TD
    A[Configuration] --> B[BrokerConfig]
    A --> C[MessageStoreConfig]
    A --> D[NettyServerConfig]
    A --> E[NettyClientConfig]

    F[TopicConfigManager] --> G[TopicConfig]
    H[SubscriptionGroupManager] --> I[SubscriptionGroupConfig]
    J[ConsumerOffsetManager] --> K[Offset Table]
```

### 5.2 Topic 和订阅配置
```mermaid
graph LR
    A[TopicConfigManager] --> B[Topic配置表]
    B --> C[Topic Name]
    B --> D[Queue Num]
    B --> E[Permission]

    F[SubscriptionGroupManager] --> G[订阅组配置]
    G --> H[Group Name]
    G --> I[Consume Mode]
    G --> J[Retry Times]
```

## 6. 长轮询模块

### 6.1 PullRequestHoldService
```mermaid
graph TD
    A[PullRequestHoldService] --> B[PullRequest Hold Table]
    B --> C[Key: Topic + QueueId]
    B --> D[Value: ManyPullRequest]

    E[ManyPullRequest] --> F[PullRequest Queue]
    F --> G[PullRequest]

    H[NotifyMessageArrivingListener] --> A
    I[PullMessageProcessor] --> A
```

### 6.2 长轮询工作流程
```mermaid
sequenceDiagram
    participant Consumer
    participant Broker
    participant HoldService
    participant NotifyListener

    Consumer->>Broker: 发送拉取请求
    Broker->>HoldService: 检查是否有消息
    alt 有新消息
        HoldService->>Broker: 立即返回消息
    else 无新消息
        HoldService->>HoldService: 保存请求
        NotifyListener->>HoldService: 通知新消息到达
        HoldService->>Broker: 返回消息
    end
    Broker->>Consumer: 返回响应
```

## 7. 事务消息模块

### 7.1 事务消息组件
```mermaid
graph TD
    A[TransactionalMessageService] --> B[TransactionalMessageBridge]
    A --> C[TransactionalMessageCheckService]

    B --> D[Prepare消息存储]
    B --> E[Commit/Rollback处理]

    C --> F[状态检查]
    C --> G[回查Producer]

    H[DefaultTransactionalMessageCheckListener] --> C
    I[EndTransactionProcessor] --> B
```

### 7.2 事务消息处理流程
```mermaid
graph LR
    A[发送半消息] --> B[存储到prepare队列]
    B --> C[执行本地事务]
    C --> D[提交/回滚]
    D --> E[更新消息状态]

    F[状态检查] --> G[检查未决消息]
    G --> H[回查Producer]
    H --> I[根据结果处理]
```

## 8. 主从同步模块

### 8.1 SlaveSynchronize
```mermaid
graph TD
    A[SlaveSynchronize] --> B[配置同步]
    A --> C[Topic配置同步]
    A --> D[ConsumerOffset同步]
    A --> E[DelayOffset同步]
    A --> F[SubscriptionGroup同步]

    G[HAService] --> H[Master]
    H --> I[Slave]
    I --> A
```

### 8.2 DLedger 集群
```mermaid
graph TB
    A[DLedgerRoleChangeHandler] --> B[角色变更处理]
    B --> C[Follower转Candidate]
    B --> D[Candidate转Leader]
    B --> E[Leader转Follower]

    F[DLedgerCommitLog] --> A
    G[DLedgerService] --> F
```

## 9. 过滤器模块

### 9.1 ConsumerFilterManager
```mermaid
graph TD
    A[ConsumerFilterManager] --> B[FilterData by Topic]
    A --> C[FilterData by Group]

    B --> D[ExpressionFilterManager]
    C --> E[FilterData]

    F[ExpressionMessageFilter] --> A
    G[ExpressionForRetryMessageFilter] --> A
```

### 9.2 消息过滤流程
```mermaid
graph LR
    A[消息存储时] --> B[构建FilterBitMap]
    B --> C[写入CommitLog]

    D[消息拉取时] --> E[读取FilterBitMap]
    E --> F[应用过滤条件]
    F --> G[返回过滤后结果]
```

## 10. 监控和统计模块

### 10.1 BrokerStats
```mermaid
graph TD
    A[BrokerStats] --> B[BrokerStatsManager]
    A --> C[ProducerLatencyStats]
    A --> D[ConsumerLatencyStats]

    B --> E[存储统计]
    B --> F[网络统计]
    B --> G[TPS统计]

    H[MomentStatsItem] --> A
    I[DefaultMQPullConsumerStatsManager] --> A
```

### 10.2 Lmq 特殊统计
```mermaid
graph LR
    A[LmqBrokerStatsManager] --> B[Lmq特有统计]
    B --> C[TopicConfigManager]
    B --> D[ConsumerOffsetManager]
    B --> E[SubscriptionGroupManager]
```

## 11. 线程池管理

### 11.1 线程池配置
```mermaid
graph TD
    A[BrokerFastFailure] --> B[Send线程池]
    A --> C[Pull线程池]
    A --> D[Reply线程池]
    A --> E[Query线程池]
    A --> F[ClientManager线程池]
    A --> G[ConsumerManager线程池]
    A --> H[Heartbeat线程池]

    I[BrokerFixedThreadPoolExecutor] --> B
    I --> C
    I --> D
    I --> E
```

### 11.2 任务队列管理
```mermaid
graph LR
    A[TaskQueue] --> B[SendThreadPoolQueue]
    A --> C[PutThreadPoolQueue]
    A --> D[PullThreadPoolQueue]
    A --> E[ReplyThreadPoolQueue]

    F[FutureTaskExt] --> A
```

## 12. 插件系统

### 12.1 MessageStorePlugin
```mermaid
graph TD
    A[MessageStorePluginContext] --> B[Plugin配置]
    A --> C[MessageStore实例]

    D[AbstractPluginMessageStore] --> A
    D --> E[插件Hook]

    F[MessageStoreFactory] --> D
```

### 12.2 ServiceProvider
```mermaid
graph LR
    A[ServiceProvider] --> B[加载插件]
    A --> C[管理插件生命周期]
    B --> D[ServiceLoader机制]
```

## 13. 错误处理和保护机制

### 13.1 BrokerFastFailure
```mermaid
graph TD
    A[BrokerFastFailure] --> B[线程池保护]
    A --> C[流量控制]

    B --> D[拒绝策略]
    B --> E[超时处理]

    C --> F[限流检查]
    C --> G[系统保护]
```

## 总结

Broker 模块的关系图展示了：

1. **分层架构**：清晰的分层设计，各层职责明确
2. **模块化设计**：功能模块独立，便于维护和扩展
3. **组件协作**：组件之间通过接口协作，降低耦合
4. **异步处理**：大量使用线程池和异步机制提高性能
5. **可扩展性**：通过插件机制支持功能扩展
6. **高可用性**：通过主从同步和DLedger保证高可用

这些模块和组件的有机组合，使 Broker 成为功能强大、性能优异、可靠性高的消息中间件核心组件。