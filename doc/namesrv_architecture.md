# RocketMQ NameServer 核心架构

RocketMQ NameServer 的核心架构是围绕 `NamesrvController` 构建的，它作为中央控制器管理着各个组件的生命周期。

## 依赖关系图

```mermaid
classDiagram
    class NamesrvStartup {
        +main(String[] args)
        +start(NamesrvController)
    }

    class NamesrvController {
        -NamesrvConfig namesrvConfig
        -NettyServerConfig nettyServerConfig
        -KVConfigManager kvConfigManager
        -RouteInfoManager routeInfoManager
        -BrokerHousekeepingService brokerHousekeepingService
        -RemotingServer remotingServer
        +initialize()
        +start()
        +shutdown()
    }

    class RouteInfoManager {
        -topicQueueTable
        -brokerAddrTable
        -clusterAddrTable
        -brokerLiveTable
        +registerBroker()
        +unregisterBroker()
        +pickupTopicRouteData()
        +scanNotActiveBroker()
    }

    class KVConfigManager {
        -configTable
        +load()
        +putKVConfig()
        +persist()
    }

    class BrokerHousekeepingService {
        -NamesrvController namesrvController
        +onChannelClose()
        +onChannelException()
        +onChannelIdle()
    }

    class DefaultRequestProcessor {
        -NamesrvController namesrvController
        +processRequest()
        +registerBroker()
        +getRouteInfoByTopic()
    }

    class RemotingServer {
        <<interface>>
        +start()
        +registerDefaultProcessor()
    }

    class NettyRemotingServer {
        -ChannelEventListener channelEventListener
        -NettyRequestProcessor defaultRequestProcessor
    }
    
    class ChannelEventListener {
        <<interface>>
    }

    %% 启动流程
    NamesrvStartup ..> NamesrvController : Creates & Starts

    %% 核心聚合关系
    NamesrvController *-- RouteInfoManager : 核心路由数据管理
    NamesrvController *-- KVConfigManager : 配置持久化管理
    NamesrvController *-- BrokerHousekeepingService : 连接状态监听
    NamesrvController *-- RemotingServer : 网络通信服务

    %% 网络层实现与交互
    RemotingServer <|-- NettyRemotingServer
    BrokerHousekeepingService ..|> ChannelEventListener
    
    NettyRemotingServer o-- ChannelEventListener : 回调 (Listener)
    NettyRemotingServer o-- DefaultRequestProcessor : 回调 (Processor)
    
    %% 业务逻辑依赖
    BrokerHousekeepingService --> NamesrvController : 获取 RouteInfoManager 清理路由
    DefaultRequestProcessor --> NamesrvController : 获取各Manager 处理业务请求
```

## 核心组件说明

1.  **`NamesrvController`**: 核心控制器。负责初始化、启动和关闭 NameServer。它持有所有核心组件的引用，充当“上下文”对象。
2.  **`RouteInfoManager`**: **最核心的组件**。管理所有的路由信息（Topic 路由、Broker 列表、集群信息、活跃 Broker 状态）。它纯内存存储，不持久化。
3.  **`BrokerHousekeepingService`**: 负责“打扫卫生”。实现了 `ChannelEventListener` 接口，监听 Broker 的连接状态（关闭、异常、空闲）。一旦连接断开，它会通过 Controller 调用 `RouteInfoManager` 清理该 Broker 的路由信息。
4.  **`DefaultRequestProcessor`**: 业务请求处理器。处理来自 Broker（注册、心跳）和 Producer/Consumer（获取路由信息）的请求。它依赖 Controller 来获取 `RouteInfoManager` 进行读写操作。
5.  **`KVConfigManager`**: 负责一些通用 KV 配置的增删改查和持久化（如顺序消息配置）。
6.  **`RemotingServer` (NettyRemotingServer)**: 基于 Netty 的网络通信层。它将网络事件通知给 `BrokerHousekeepingService`，将网络请求转发给 `DefaultRequestProcessor`。

## 组件初始化顺序和依赖关系

### 启动流程依赖图

```mermaid
graph TD
    %% 启动入口
    START[NamesrvStartup.main0]

    %% 第一阶段：配置初始化
    A1[解析命令行参数]
    A2[创建 NamesrvConfig]
    A3[创建 NettyServerConfig]
    A4[加载配置文件]

    %% 第二阶段：控制器创建
    B1[创建 NamesrvController]
    B2[创建 KVConfigManager]
    B3[创建 RouteInfoManager]
    B4[创建 BrokerHousekeepingService]
    B5[创建 Configuration]
    B6[创建 ScheduledExecutorService]

    %% 第三阶段：组件初始化
    C1[KVConfigManager.load]
    C2[创建 NettyRemotingServer]
    C3[创建 RemotingExecutor]
    C4[注册 DefaultRequestProcessor]
    C5[启动定时任务]
    C6[创建 FileWatchService]

    %% 第四阶段：服务启动
    D1[启动 NettyRemotingServer]
    D2[启动 FileWatchService]

    %% 流程依赖
    START --> A1
    A1 --> A2
    A1 --> A3
    A1 --> A4
    A2 --> B1
    A3 --> B1
    A4 --> B1

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
    C5 --> C6

    C6 --> D1
    D1 --> D2

    style START fill:#ffcccc
    style B1 fill:#ccffcc
    style D2 fill:#ccccff
```

### 整体架构依赖图

```mermaid
graph TB
    %% 外部客户端
    Client[Client/Producer/Consumer]
    Broker[Broker Server]

    %% 配置层
    NC[NamesrvConfig]
    NSC[NettyServerConfig]
    C[Configuration]

    %% 核心控制器
    CTRL[NamesrvController]

    %% 管理层组件
    RIM[RouteInfoManager]
    KVM[KVConfigManager]
    BHS[BrokerHousekeepingService]

    %% 网络层组件
    NRS[NettyRemotingServer]
    RE[RemotingExecutor]
    SES[ScheduledExecutorService]

    %% 处理器组件
    DRP[DefaultRequestProcessor]
    FWS[FileWatchService]

    %% 依赖关系
    Client --> NRS
    Broker --> NRS

    NC --> CTRL
    NSC --> CTRL
    C --> CTRL

    CTRL --> RIM
    CTRL --> KVM
    CTRL --> BHS
    CTRL --> C
    CTRL --> SES

    CTRL --> NRS
    NRS --> BHS
    NRS --> RE

    NRS --> DRP
    RE --> DRP

    DRP --> RIM
    DRP --> KVM

    SES --> RIM
    SES --> KVM

    CTRL --> FWS

    style CTRL fill:#ff9999
    style RIM fill:#99ccff
    style KVM fill:#99ff99
    style BHS fill:#ffcc99
    style DRP fill:#cc99ff
```

### 运行时调用关系图

```mermaid
graph LR
    %% 请求来源
    subgraph "请求源"
        P[Producer]
        C[Consumer]
        B[Broker]
    end

    %% 网络层
    subgraph "网络层"
        NR[NettyRemotingServer]
        RE[RemotingExecutor]
    end

    %% 处理层
    subgraph "处理层"
        DRP[DefaultRequestProcessor]
    end

    %% 业务层
    subgraph "业务层"
        RIM[RouteInfoManager]
        KVM[KVConfigManager]
    end

    %% 监控层
    subgraph "监控层"
        BHS[BrokerHousekeepingService]
        SES[ScheduledExecutorService]
    end

    %% 请求流
    P --> NR
    C --> NR
    B --> NR

    NR --> RE
    RE --> DRP

    DRP --> RIM
    DRP --> KVM

    NR --> BHS
    BHS --> RIM

    SES --> RIM
    SES --> KVM

    %% 响应流（虚线表示返回）
    RIM -.-> DRP
    KVM -.-> DRP
    DRP -.-> RE
    RE -.-> NR
    NR -.-> P
    NR -.-> C
    NR -.-> B

    style DRP fill:#ffcccc
    style RIM fill:#ccffcc
    style KVM fill:#ccccff
```

### 数据存储依赖图

```mermaid
graph TB
    %% 内存数据结构
    subgraph "RouteInfoManager 内存结构"
        TQT[topicQueueTable<br/>Topic路由表]
        BAT[brokerAddrTable<br/>Broker地址表]
        CAT[clusterAddrTable<br/>集群地址表]
        BLT[brokerLiveTable<br/>Broker存活表]
        FST[filterServerTable<br/>过滤服务器表]
    end

    subgraph "KVConfigManager 内存结构"
        CT[configTable<br/>配置表<br/>Namespace -> Key -> Value]
    end

    %% 文件存储
    subgraph "文件存储"
        KF[kvConfig.json<br/>KV配置文件]
        CF[namesrv.properties<br/>配置文件]
        LF[logback_namesrv.xml<br/>日志配置]
    end

    %% 组件管理
    subgraph "管理组件"
        RIM[RouteInfoManager]
        KVM[KVConfigManager]
        CONF[Configuration]
    end

    %% 依赖关系
    RIM --> TQT
    RIM --> BAT
    RIM --> CAT
    RIM --> BLT
    RIM --> FST

    KVM --> CT

    KVM --> KF
    CONF --> CF
    CONF --> LF

    style TQT fill:#ffcccc
    style BAT fill:#ccffcc
    style CAT fill:#ccccff
    style BLT fill:#ffcc99
    style FST fill:#ff99cc
    style CT fill:#99ffcc
```

### 定时任务依赖图

```mermaid
graph TD
    %% 定时调度器
    SES[ScheduledExecutorService]

    %% 定时任务
    T1[scanNotActiveBroker<br/>每10秒执行<br/>清理不活跃Broker]
    T2[printAllPeriodically<br/>每10分钟执行<br/>打印KV配置信息]

    %% 任务执行组件
    RIM[RouteInfoManager]
    KVM[KVConfigManager]

    %% 监控事件
    subgraph "监控事件"
        BE[Broker异常断开]
        BC[Broker连接关闭]
        BI[Broker连接空闲]
    end

    %% 事件处理
    BHS[BrokerHousekeepingService]

    %% 依赖关系
    SES --> T1
    SES --> T2

    T1 --> RIM
    T2 --> KVM

    BE --> BHS
    BC --> BHS
    BI --> BHS

    BHS --> RIM

    %% 反馈循环
    RIM -.-> SES
    KVM -.-> SES

    style SES fill:#ffcccc
    style T1 fill:#ccffcc
    style T2 fill:#ccccff
    style BHS fill:#ffcc99
```

### 核心依赖层级总结

1. **配置层**: `NamesrvConfig`, `NettyServerConfig`
2. **控制层**: `NamesrvController` (中央协调器)
3. **管理层**: `RouteInfoManager`, `KVConfigManager`, `BrokerHousekeepingService`
4. **网络层**: `NettyRemotingServer`, `RemotingExecutor`
5. **处理层**: `DefaultRequestProcessor`
6. **调度层**: `ScheduledExecutorService`

### 关键依赖特点

- **中心化设计**: `NamesrvController` 作为依赖注入的核心容器
- **分层架构**: 清晰的分层依赖关系，避免循环依赖
- **事件驱动**: 通过接口实现松耦合的事件处理机制
- **异步处理**: 网络请求和定时任务异步执行

### 数据流向

- **请求流**: Client → Netty → DefaultRequestProcessor → Manager Components
- **响应流**: Manager Components → DefaultRequestProcessor → Netty → Client
- **监控流**: Netty → BrokerHousekeepingService → RouteInfoManager

