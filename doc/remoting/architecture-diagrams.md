# RocketMQ Remoting 架构图

## 概述

本文档包含 RocketMQ Remoting 模块的详细架构图和设计图，帮助理解其整体结构和组件关系。

## 1. 整体架构图

### 1.1 分层架构图

```mermaid
graph TB
    subgraph "应用层 Application Layer"
        A1[Producer 生产者]
        A2[Consumer 消费者]
        A3[Broker 代理服务器]
        A4[NameServer 名称服务器]
    end

    subgraph "接口抽象层 Interface Layer"
        B1[RemotingClient Interface]
        B2[RemotingServer Interface]
        B3[RemotingService Interface]
        B4[RPCHook Interface]
        B5[ChannelEventListener Interface]
    end

    subgraph "协议层 Protocol Layer"
        C1[RemotingCommand 核心命令]
        C2[SerializeType 序列化类型]
        C3[CommandCustomHeader 自定义头部]
        C4[RemotingSerializable 序列化接口]
    end

    subgraph "Netty抽象层 Netty Abstract Layer"
        D1[NettyRemotingAbstract 抽象基类]
        D2[ResponseFuture 响应Future]
        D3[AsyncNettyRequestProcessor 异步处理器]
        D4[NettyEventExecutor 事件执行器]
    end

    subgraph "Netty实现层 Netty Implementation Layer"
        E1[NettyRemotingClient 客户端实现]
        E2[NettyRemotingServer 服务端实现]
        E3[NettyEncoder 编码器]
        E4[NettyDecoder 解码器]
        E5[ConnectionPool 连接池]
    end

    subgraph "网络层 Network Layer"
        F1[Netty Bootstrap]
        F2[Netty ServerBootstrap]
        F3[EventLoopGroup 线程组]
        F4[ChannelPipeline 处理管道]
    end

    A1 --> B1
    A2 --> B1
    A3 --> B2
    A4 --> B2

    B1 --> B3
    B2 --> B3
    B4 --> B3
    B5 --> B3

    B1 --> C1
    B2 --> C1
    C1 --> C2
    C1 --> C3
    C1 --> C4

    C1 --> D1
    D1 --> D2
    D1 --> D3
    D1 --> D4

    D1 --> E1
    D1 --> E2
    E1 --> E3
    E1 --> E4
    E2 --> E3
    E2 --> E4
    E1 --> E5

    E1 --> F1
    E2 --> F2
    F1 --> F3
    F2 --> F3
    F1 --> F4
    F2 --> F4
```

### 1.2 组件关系图

```mermaid
graph LR
    subgraph "客户端组件"
        C1[NettyRemotingClient]
        C2[ChannelTables 连接表]
        C3[NameServerList 名称服务器列表]
        C4[Timer 心跳定时器]
    end

    subgraph "服务端组件"
        S1[NettyRemotingServer]
        S2[ProcessorTable 处理器表]
        S3[DefaultProcessor 默认处理器]
        S4[ExecutorServices 线程池组]
    end

    subgraph "公共组件"
        P1[NettyRemotingAbstract]
        P2[ResponseTable 响应表]
        P3[SemaphoreOneway 单向信号量]
        P4[SemaphoreAsync 异步信号量]
    end

    C1 --> P1
    S1 --> P1
    P1 --> P2
    P1 --> P3
    P1 --> P4

    C1 --> C2
    C1 --> C3
    C1 --> C4

    S1 --> S2
    S1 --> S3
    S1 --> S4
```

## 2. 调用流程图

### 2.1 同步调用流程

```mermaid
sequenceDiagram
    participant Client as 客户端
    participant NRC as NettyRemotingClient
    participant NRA as NettyRemotingAbstract
    participant RT as ResponseTable
    participant Channel as Netty通道
    participant Server as 服务端
    participant Processor as 请求处理器

    Client->>NRC: invokeSync(addr, request, timeout)
    NRC->>NRC: getAndCreateChannel(addr)
    NRC->>NRA: invokeSyncImpl(channel, request, timeout)

    NRA->>NRA: 1. 生成opaque
    NRA->>NRA: 2. 创建ResponseFuture
    NRA->>RT: 3. responseTable.put(opaque, future)
    NRA->>Channel: 4. channel.writeAndFlush(request)

    Note over Channel: 网络传输
    Channel->>Server: RemotingCommand请求
    Server->>Processor: processRequest(ctx, request)
    Processor->>Processor: 业务逻辑处理
    Processor->>Server: RemotingCommand响应
    Server->>Channel: 返回响应

    Channel->>NRA: channelRead(response)
    NRA->>RT: 5. responseTable.get(opaque)
    NRA->>RT: 6. responseTable.remove(opaque)
    NRA->>RT: 7. 设置响应到ResponseFuture
    NRA->>RT: 8. countDownLatch.countDown()

    NRA->>Client: 返回RemotingCommand响应
```

### 2.2 异步调用流程

```mermaid
sequenceDiagram
    participant Client as 客户端
    participant NRC as NettyRemotingClient
    participant NRA as NettyRemotingAbstract
    participant SA as SemaphoreAsync
    participant RT as ResponseTable
    participant CE as CallbackExecutor
    participant Channel as Netty通道
    participant Callback as InvokeCallback

    Client->>NRC: invokeAsync(addr, request, timeout, callback)
    NRC->>NRC: getAndCreateChannel(addr)
    NRC->>NRA: invokeAsyncImpl(channel, request, timeout, callback)

    NRA->>SA: 1. 获取异步信号量
    SA-->>NRA: 信号量获取成功
    NRA->>NRA: 2. 创建ResponseFuture(callback)
    NRA->>RT: 3. responseTable.put(opaque, future)
    NRA->>Channel: 4. channel.writeAndFlush(request)

    Note over Channel: 网络传输 - 异步返回响应
    Channel->>NRA: channelRead(response)
    NRA->>RT: 5. responseTable.get(opaque)
    NRA->>RT: 6. responseTable.remove(opaque)
    NRA->>RT: 7. 设置响应结果
    NRA->>CE: 8. 提交回调任务到线程池
    NRA->>SA: 9. 释放信号量

    CE->>Callback: 10. 执行异步回调
    Callback->>Client: 11. operationComplete(response)
```

### 2.3 服务端请求处理流程

```mermaid
sequenceDiagram
    participant Client as 客户端
    participant Channel as Netty通道
    participant Handler as NettyServerHandler
    participant NRA as NettyRemotingAbstract
    participant PT as ProcessorTable
    participant Processor as 具体处理器
    participant Executor as 线程池

    Client->>Channel: 发送RemotingCommand
    Channel->>Handler: channelRead(ctx, command)
    Handler->>NRA: processMessageReceived(ctx, command)

    NRA->>NRA: 1. 检查命令类型
    NRA->>NRA: 2. 获取requestCode

    alt 找到对应处理器
        NRA->>PT: 3. processorTable.get(requestCode)
        PT-->>NRA: 4. 返回Pair<Processor, Executor>
        NRA->>Executor: 5. 提交处理任务
        Executor->>Processor: 6. 执行processRequest()
        Processor->>Processor: 7. 业务逻辑处理
        Processor-->>Executor: 8. 返回RemotingCommand
        Executor-->>NRA: 9. 返回处理结果
        NRA->>Channel: 10. channel.writeAndFlush(response)
        Channel->>Client: 11. 返回响应
    else 未找到处理器
        NRA->>NRA: 使用默认处理器处理
        NRA->>Channel: 返回错误响应
    end
```

## 3. 数据结构图

### 3.1 RemotingCommand 结构

```mermaid
classDiagram
    class RemotingCommand {
        +int code
        +LanguageCode language
        +int version
        +int opaque
        +int flag
        +String remark
        +HashMap~String,String~ extFields
        +CommandCustomHeader customHeader
        +byte[] body
        +SerializeType serializeTypeCurrentRPC
        +RemotingCommandType type
        --
        +createRequestCommand(int, CommandCustomHeader) RemotingCommand
        +createResponseCommand(int, String) RemotingCommand
        +encodeHeader() byte[]
        +decode(byte[]) RemotingCommand
        +markOnewayRPC()
        +markResponseType()
        +isOnewayRPC() boolean
    }

    class CommandCustomHeader {
        <<interface>>
        +toMap() HashMap~String,String~
        +checkFields() void
    }

    class SerializeType {
        <<enumeration>>
        JSON
        ROCKETMQ
        --
        +getCode() byte
        +valueOf(byte) SerializeType
    }

    class RemotingCommandType {
        <<enumeration>>
        REQUEST_COMMAND
        RESPONSE_COMMAND
    }

    RemotingCommand --> CommandCustomHeader
    RemotingCommand --> SerializeType
    RemotingCommand --> RemotingCommandType
```

### 3.2 ResponseFuture 结构

```mermaid
classDiagram
    class ResponseFuture {
        -int opaque
        -Channel processChannel
        -long timeoutMillis
        -InvokeCallback invokeCallback
        -long beginTimestamp
        -CountDownLatch countDownLatch
        -SemaphoreReleaseOnlyOnce once
        -AtomicBoolean executeCallbackOnlyOnce
        -volatile RemotingCommand responseCommand
        -volatile boolean sendRequestOK
        -volatile Throwable cause
        --
        +waitResponse(long) RemotingCommand
        +putResponse(RemotingCommand) void
        +setResponseCommand(RemotingCommand) void
        +release() void
        +isTimeout() boolean
    }

    class InvokeCallback {
        <<interface>>
        +operationComplete(RemotingCommand) void
        +onException(Throwable) void
    }

    class SemaphoreReleaseOnlyOnce {
        -Semaphore semaphore
        -AtomicBoolean released
        --
        +release() void
    }

    ResponseFuture --> InvokeCallback
    ResponseFuture --> SemaphoreReleaseOnlyOnce
```

### 3.3 处理器映射结构

```mermaid
classDiagram
    class Pair {
        -NettyRequestProcessor processor
        -ExecutorService executor
        --
        +getObject1() NettyRequestProcessor
        +getObject2() ExecutorService
    }

    class NettyRequestProcessor {
        <<interface>>
        +processRequest(ChannelHandlerContext, RemotingCommand) RemotingCommand
        +rejectRequest() boolean
    }

    class AsyncNettyRequestProcessor {
        <<abstract>>
        +processRequest(RemotingCommand) RemotingCommand
        +asyncProcessRequest(ChannelHandlerContext, RemotingCommand, AsyncCallback) void
    }

    class ProcessorTable {
        <<HashMap>>
        +Integer requestCode
        +Pair~NettyRequestProcessor, ExecutorService~ processorExecutorPair
    }

    Pair --> NettyRequestProcessor
    Pair --> ExecutorService
    ProcessorTable --> Pair
    AsyncNettyRequestProcessor --|> NettyRequestProcessor
```

## 4. Netty Pipeline 图

### 4.1 客户端 Pipeline

```mermaid
graph TB
    subgraph "Netty Client Pipeline"
        P1[IdleStateHandler<br/>空闲检测]
        P2[NettyEncoder<br/>消息编码]
        P3[NettyDecoder<br/>消息解码]
        P4[NettyClientConnectManageHandler<br/>连接管理]
        P5[NettyClientHandler<br/>业务处理]
    end

    subgraph "处理职责"
        R1[心跳检测<br/>触发重连]
        R2[协议编码<br/>RemotingCommand -> ByteBuf]
        R3[协议解码<br/>ByteBuf -> RemotingCommand]
        R4[连接事件<br/>建立/关闭/异常]
        R5[请求处理<br/>同步/异步/单向]
    end

    P1 --> R1
    P2 --> R2
    P3 --> R3
    P4 --> R4
    P5 --> R5

    style P1 fill:#e1f5fe
    style P2 fill:#f3e5f5
    style P3 fill:#f3e5f5
    style P4 fill:#e8f5e8
    style P5 fill:#fff3e0
```

### 4.2 服务端 Pipeline

```mermaid
graph TB
    subgraph "Netty Server Pipeline"
        S1[IdleStateHandler<br/>空闲检测]
        S2[NettyEncoder<br/>消息编码]
        S3[NettyDecoder<br/>消息解码]
        S4[NettyServerConnectManageHandler<br/>连接管理]
        S5[NettyServerHandler<br/>业务处理]
    end

    subgraph "处理职责"
        SR1[客户端心跳<br/>空闲断开]
        SR2[响应编码<br/>RemotingCommand -> ByteBuf]
        SR3[请求解码<br/>ByteBuf -> RemotingCommand]
        SR4[连接管理<br/>客户端接入/断开]
        SR5[请求分发<br/>路由到具体处理器]
    end

    S1 --> SR1
    S2 --> SR2
    S3 --> SR3
    S4 --> SR4
    S5 --> SR5

    style S1 fill:#e1f5fe
    style S2 fill:#f3e5f5
    style S3 fill:#f3e5f5
    style S4 fill:#e8f5e8
    style S5 fill:#fff3e0
```

## 5. 性能优化架构图

### 5.1 连接池管理

```mermaid
graph TB
    subgraph "连接池架构"
        CP[ChannelTables<br/>连接表]
        LM[LockChannelTables<br/>连接表锁]
        HT[HousekeepingService<br/>心跳维护]
        CR[ConnectionReaper<br/>连接清理]
    end

    subgraph "连接生命周期"
        LC[连接创建<br/>createChannel()]
        KV[连接验证<br/>validateChannel()]
        MA[连接维护<br/>maintainConnection()]
        CL[连接清理<br/>closeChannel()]
    end

    subgraph "连接状态"
        ACT[活跃连接]
        IDL[空闲连接]
        EXP[过期连接]
        ERR[错误连接]
    end

    CP --> LC
    LC --> KV
    KV --> MA
    MA --> CL

    HT --> MA
    CR --> CL

    MA --> ACT
    MA --> IDL
    CL --> EXP
    CL --> ERR
```

### 5.2 内存管理架构

```mermaid
graph TB
    subgraph "对象复用"
        OP[ObjectPool<br/>对象池]
        CC[CommandCache<br/>命令缓存]
        FC[FieldCache<br/>反射缓存]
    end

    subgraph "内存回收"
        RT[ResponseTable<br/>响应表清理]
        FT[FutureTable<br/>Future表清理]
        GC[GarbageCollection<br/>垃圾回收]
    end

    subgraph "零拷贝技术"
        ZC[ZeroCopy<br/>零拷贝]
        FR[FileRegion<br/>文件区域传输]
        DB[DirectBuffer<br/>直接内存]
    end

    OP --> CC
    OP --> FC

    RT --> FT
    FT --> GC

    ZC --> FR
    ZC --> DB

    style ZC fill:#ffeb3b
    style OP fill:#4caf50
    style RT fill:#f44336
```

## 6. 异常处理架构图

### 6.1 异常分类和处理

```mermaid
graph TB
    subgraph "异常类型"
        CE[ConnectionException<br/>连接异常]
        SE[SendRequestException<br/>发送异常]
        TE[TimeoutException<br/>超时异常]
        RE[TooMuchRequestException<br/>请求过多异常]
        CO[CommandException<br/>命令异常]
    end

    subgraph "处理策略"
        RT[Retry<br/>重试机制]
        CB[CircuitBreaker<br/>熔断机制]
        FB[Fallback<br/>降级处理]
        AL[Alert<br/>告警通知]
    end

    subgraph "恢复机制"
        RC[Reconnection<br/>重连]
        LB[LoadBalance<br/>负载均衡]
        HT[HealthCheck<br/>健康检查]
        GR[Graceful<br/>优雅降级]
    end

    CE --> RT
    CE --> RC
    SE --> RT
    TE --> CB
    RE --> FB
    CO --> AL

    RT --> LB
    CB --> GR
    FB --> HT
    RC --> HT
```

## 7. 线程模型架构图

### 7.1 客户端线程模型

```mermaid
graph TB
    subgraph "用户线程"
        UT[User Thread<br/>业务线程]
        BT[Callback Thread<br/>回调线程]
    end

    subgraph "Netty I/O线程"
        IO[EventLoopGroup<br/>I/O线程组]
        BW[Boss EventLoop<br/>连接处理]
        WR[Worker EventLoop<br/>读写处理]
    end

    subgraph "业务处理线程"
        BP[Business Processor<br/>业务处理器]
        CR[Callback Executor<br/>回调执行器]
    end

    UT --> IO
    IO --> BW
    BW --> WR
    WR --> BP

    BP --> CR
    CR --> BT
```

### 7.2 服务端线程模型

```mermaid
graph TB
    subgraph "客户端连接"
        C1[Client 1]
        C2[Client 2]
        C3[Client N]
    end

    subgraph "Netty Boss线程"
        B1[Boss EventLoop 1]
        B2[Boss EventLoop 2]
    end

    subgraph "Netty Worker线程"
        W1[Worker EventLoop 1]
        W2[Worker EventLoop 2]
        W3[Worker EventLoop N]
    end

    subgraph "业务处理线程池"
        EP1[Executor Pool 1<br/>请求类型A]
        EP2[Executor Pool 2<br/>请求类型B]
        EP3[Default Executor<br/>默认处理]
    end

    C1 --> B1
    C2 --> B2
    C3 --> B1

    B1 --> W1
    B1 --> W2
    B2 --> W2
    B2 --> W3

    W1 --> EP1
    W2 --> EP2
    W3 --> EP3

    style EP1 fill:#e3f2fd
    style EP2 fill:#e8f5e8
    style EP3 fill:#fff3e0
```

## 8. 总结

RocketMQ Remoting 模块的架构设计体现了以下核心特点：

1. **分层清晰**：从接口层到网络层，职责明确，层次分明
2. **模块化设计**：各组件独立，松耦合，易于扩展和维护
3. **高性能优化**：通过连接池、对象池、零拷贝等技术提升性能
4. **容错机制**：完善的异常处理和恢复机制保证系统稳定性
5. **线程模型优化**：合理的线程池隔离和异步处理提升并发能力

这些架构设计使得 RocketMQ Remoting 模块能够在大规模分布式系统中提供稳定、高效、可扩展的远程通信服务。