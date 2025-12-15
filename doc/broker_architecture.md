# RocketMQ Broker 核心架构

RocketMQ Broker 的核心架构是围绕 `BrokerController` 构建的，它作为消息存储和转发的核心组件，负责处理生产者消息投递、消费者消息拉取以及消息的持久化存储。

## 核心组件依赖图

```mermaid
classDiagram
    class BrokerStartup {
        +main(String[] args)
        +start(BrokerController)
        +createBrokerController(String[] args)
    }

    class BrokerController {
        -BrokerConfig brokerConfig
        -MessageStoreConfig messageStoreConfig
        -NettyServerConfig nettyServerConfig
        -TopicConfigManager topicConfigManager
        -ConsumerOffsetManager consumerOffsetManager
        -MessageStore messageStore
        -ConsumerManager consumerManager
        -ProducerManager producerManager
        +initialize()
        +start()
        +shutdown()
    }

    class DefaultMessageStore {
        -CommitLog commitLog
        -ConsumeQueueTable consumeQueueTable
        -IndexService indexService
        -HAService haService
        +load()
        +start()
        +putMessage()
        +getMessage()
    }

    class SendMessageProcessor {
        -BrokerController brokerController
        +processRequest()
        +asyncProcessRequest()
    }

    class PullMessageProcessor {
        -BrokerController brokerController
        +processRequest()
        +asyncProcessRequest()
    }

    class TopicConfigManager {
        -BrokerController brokerController
        +load()
        +persist()
    }

    class ConsumerManager {
        -ConsumerIdsChangeListener listener
        +registerConsumer()
        +unregisterConsumer()
    }

    class ProducerManager {
        +registerProducer()
        +unregisterProducer()
    }

    class MessageStore {
        <<interface>>
        +load()
        +start()
        +putMessage()
        +getMessage()
    }

    class CommitLog {
        +putMessage()
        +getMessage()
        +flush()
    }

    class ConsumeQueue {
        +putMessagePositionInfo()
        +getMessagePositionInfo()
    }

    %% 启动流程
    BrokerStartup ..> BrokerController : Creates & Starts

    %% 核心聚合关系
    BrokerController *-- DefaultMessageStore : 消息存储
    BrokerController *-- TopicConfigManager : Topic配置管理
    BrokerController *-- ConsumerOffsetManager : 消费进度管理
    BrokerController *-- ConsumerManager : 消费者管理
    BrokerController *-- ProducerManager : 生产者管理

    %% 消息存储实现
    MessageStore <|-- DefaultMessageStore
    DefaultMessageStore *-- CommitLog : CommitLog存储
    DefaultMessageStore *-- ConsumeQueue : 消费队列
    DefaultMessageStore *-- IndexService : 索引服务

    %% 请求处理器依赖
    BrokerController *-- SendMessageProcessor : 消息发送处理
    BrokerController *-- PullMessageProcessor : 消息拉取处理

    SendMessageProcessor --> BrokerController : 获取存储服务
    PullMessageProcessor --> BrokerController : 获取存储服务
```

## Broker 核心组件说明

### 1. **BrokerController** (核心控制器)
- **功能**: Broker 的核心控制器，负责初始化、启动和关闭所有组件
- **职责**:
  - 管理配置对象 (BrokerConfig, MessageStoreConfig, NettyServerConfig)
  - 初始化和管理各种管理器 (TopicConfigManager, ConsumerManager, ProducerManager)
  - 创建和管理线程池 (发送、拉取、查询等)
  - 注册请求处理器
  - 管理与 NameServer 的连接

### 2. **DefaultMessageStore** (消息存储引擎)
- **功能**: 负责消息的持久化存储和检索
- **核心组件**:
  - **CommitLog**: 存储所有消息的主体数据
  - **ConsumeQueue**: 存储消息在 CommitLog 中的索引信息
  - **IndexService**: 提供基于 Key 的消息索引功能
  - **HAService**: 主从复制服务
- **关键操作**:
  - `putMessage()`: 存储消息
  - `getMessage()`: 检索消息
  - `load()`: 启动时加载存储数据

### 3. **SendMessageProcessor** (消息发送处理器)
- **功能**: 处理来自 Producer 的消息投递请求
- **核心流程**:
  - 消息校验 (Topic、权限等)
  - 消息存储 (调用 MessageStore.putMessage())
  - 响应构建和返回

### 4. **PullMessageProcessor** (消息拉取处理器)
- **功能**: 处理来自 Consumer 的消息拉取请求
- **核心流程**:
  - 消费者权限校验
  - 从 ConsumeQueue 获取消息位置
  - 从 CommitLog 读取消息数据
  - 消息过滤和返回

### 5. **TopicConfigManager** (Topic 配置管理器)
- **功能**: 管理 Topic 的配置信息
- **职责**:
  - 加载和持久化 Topic 配置
  - 提供 Topic 配置查询服务
  - 支持动态创建 Topic

### 6. **ConsumerManager** (消费者管理器)
- **功能**: 管理连接到 Broker 的所有消费者
- **职责**:
  - 消费者注册和注销
  - 消费者心跳检测
  - 消费者状态管理
  - 消费者组信息维护

### 7. **ProducerManager** (生产者管理器)
- **功能**: 管理连接到 Broker 的所有生产者
- **职责**:
  - 生产者注册和注销
  - 生产者心跳检测
  - 生产者状态管理

## 启动流程依赖图

```mermaid
graph TD
    %% 启动入口
    START[BrokerStartup.main]

    %% 第一阶段：配置初始化
    A1[解析命令行参数]
    A2[创建 BrokerConfig]
    A3[创建 NettyServerConfig]
    A4[创建 MessageStoreConfig]
    A5[加载配置文件]

    %% 第二阶段：控制器创建
    B1[创建 BrokerController]
    B2[创建 TopicConfigManager]
    B3[创建 ConsumerOffsetManager]
    B4[创建 ConsumerManager]
    B5[创建 ProducerManager]
    B6[创建各种处理器]

    %% 第三阶段：存储初始化
    C1[加载 Topic 配置]
    C2[加载消费进度]
    C3[加载订阅组配置]
    C4[创建 DefaultMessageStore]
    C5[加载存储文件]

    %% 第四阶段：网络和线程池初始化
    D1[创建 NettyRemotingServer]
    D2[创建各种线程池]
    D3[注册请求处理器]

    %% 第五阶段：服务启动
    E1[启动 MessageStore]
    E2[启动网络服务]
    E3[启动定时任务]
    E4[注册到 NameServer]

    %% 流程依赖
    START --> A1
    A1 --> A2
    A1 --> A3
    A1 --> A4
    A1 --> A5

    A2 --> B1
    A3 --> B1
    A4 --> B1
    A5 --> B1

    B1 --> B2
    B1 --> B3
    B1 --> B4
    B1 --> B5
    B1 --> B6

    B1 --> C1
    C1 --> C2
    C2 --> C3
    C3 --> C4
    C4 --> C5

    C5 --> D1
    D1 --> D2
    D2 --> D3

    D3 --> E1
    E1 --> E2
    E2 --> E3
    E3 --> E4

    style START fill:#ffcccc
    style B1 fill:#ccffcc
    style E4 fill:#ccccff
```

## 消息存储架构图

```mermaid
graph TB
    %% 外部组件
    Producer[Producer]
    Consumer[Consumer]

    %% 网络层
    NettyServer[NettyRemotingServer]

    %% 处理器层
    SendProcessor[SendMessageProcessor]
    PullProcessor[PullMessageProcessor]

    %% 存储引擎层
    MessageStore[DefaultMessageStore]

    %% 存储组件层
    CommitLog[CommitLog<br/>消息主体存储]
    ConsumeQueue[ConsumeQueue<br/>消息索引队列]
    IndexService[IndexService<br/>消息索引服务]

    %% 文件存储层
    CommitLogFiles[CommitLog文件<br/>存储完整消息]
    ConsumeQueueFiles[ConsumeQueue文件<br/>存储消息索引]
    IndexFiles[Index文件<br/>存储消息Key索引]

    %% 消息发送流程
    Producer --> NettyServer
    NettyServer --> SendProcessor
    SendProcessor --> MessageStore
    MessageStore --> CommitLog
    CommitLog --> CommitLogFiles
    MessageStore --> ConsumeQueue
    ConsumeQueue --> ConsumeQueueFiles
    MessageStore --> IndexService
    IndexService --> IndexFiles

    %% 消息拉取流程
    Consumer --> NettyServer
    NettyServer --> PullProcessor
    PullProcessor --> MessageStore
    MessageStore --> ConsumeQueue
    MessageStore --> CommitLog
    MessageStore --> IndexService

    style SendProcessor fill:#ffcccc
    style PullProcessor fill:#ccffcc
    style MessageStore fill:#ccccff
    style CommitLog fill:#ffcc99
```

## 线程池架构图

```mermaid
graph TB
    %% 请求分类
    subgraph "请求类型"
        SendReq[发送消息请求]
        PullReq[拉取消息请求]
        QueryReq[查询消息请求]
        AdminReq[管理请求]
        ClientReq[客户端管理请求]
        HeartbeatReq[心跳请求]
        ReplyReq[回复消息请求]
    end

    %% 线程池
    subgraph "业务线程池"
        SendExec[SendMessageExecutor<br/>发送消息线程池]
        PullExec[PullMessageExecutor<br/>拉取消息线程池]
        QueryExec[QueryMessageExecutor<br/>查询消息线程池]
        AdminExec[AdminBrokerExecutor<br/>管理请求线程池]
        ClientExec[ClientManageExecutor<br/>客户端管理线程池]
        HeartbeatExec[HeartbeatExecutor<br/>心跳线程池]
        ReplyExec[ReplyMessageExecutor<br/>回复消息线程池]
        PutFutureExec[PutMessageFutureExecutor<br/>异步消息存储线程池]
    end

    %% 队列
    subgraph "任务队列"
        SendQueue[发送消息队列]
        PullQueue[拉取消息队列]
        QueryQueue[查询消息队列]
        ClientQueue[客户端管理队列]
        HeartbeatQueue[心跳队列]
        ReplyQueue[回复消息队列]
        PutFutureQueue[异步存储队列]
    end

    %% 请求分配
    SendReq --> SendQueue
    PullReq --> PullQueue
    QueryReq --> QueryQueue
    AdminReq --> AdminExec
    ClientReq --> ClientQueue
    HeartbeatReq --> HeartbeatQueue
    ReplyReq --> ReplyQueue
    SendReq --> PutFutureQueue

    %% 队列到线程池
    SendQueue --> SendExec
    PullQueue --> PullExec
    QueryQueue --> QueryExec
    ClientQueue --> ClientExec
    HeartbeatQueue --> HeartbeatExec
    ReplyQueue --> ReplyExec
    PutFutureQueue --> PutFutureExec

    style SendExec fill:#ffcccc
    style PullExec fill:#ccffcc
    style QueryExec fill:#ccccff
    style AdminExec fill:#ffcc99
```

## 核心依赖层级总结

1. **配置层**: `BrokerConfig`, `MessageStoreConfig`, `NettyServerConfig`
2. **控制层**: `BrokerController` (中央协调器)
3. **管理层**: `TopicConfigManager`, `ConsumerOffsetManager`, `ConsumerManager`, `ProducerManager`
4. **存储层**: `DefaultMessageStore`, `CommitLog`, `ConsumeQueue`, `IndexService`
5. **处理层**: `SendMessageProcessor`, `PullMessageProcessor`, 各种业务处理器
6. **网络层**: `NettyRemotingServer`, 线程池
7. **调度层**: `ScheduledExecutorService`, 各种定时任务

## 关键依赖特点

- **分层存储**: CommitLog 存储完整消息，ConsumeQueue 存储索引，提高查询效率
- **异步处理**: 消息存储支持同步和异步两种模式
- **高并发**: 通过多种线程池处理不同类型的请求
- **高可用**: 支持主从复制和消息故障恢复
- **可扩展**: 支持插件化的消息存储实现

## 数据流向

- **消息发送**: Producer → Netty → SendMessageProcessor → MessageStore → CommitLog
- **消息拉取**: Consumer → Netty → PullMessageProcessor → ConsumeQueue → CommitLog
- **消息索引**: MessageStore → IndexService → Index文件