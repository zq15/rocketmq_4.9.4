# RocketMQ Store 架构图集合

## 概述

本文档包含了 RocketMQ Store 模块的详细架构图和设计图，通过可视化的方式展示存储系统的内部结构和组件关系，帮助开发者更好地理解存储架构的设计思想。

## 1. 整体架构图

### 1.1 Store 模块整体架构

```mermaid
graph TB
    subgraph "RocketMQ Store 模块架构"
        subgraph "接口层"
            A[MessageStore Interface]
            B[MessageFilter Interface]
            C[CommitLogDispatcher Interface]
        end

        subgraph "核心实现层"
            D[DefaultMessageStore]
            E[CommitLog]
            F[ConsumeQueue]
            G[MappedFile]
            H[IndexService]
        end

        subgraph "服务层"
            I[ReputMessageService]
            J[FlushCommitLogService]
            K[FlushConsumeQueueService]
            L[CleanCommitLogService]
            M[ScheduleMessageService]
            N[HAService]
        end

        subgraph "存储文件层"
            O[CommitLog Files]
            P[ConsumeQueue Files]
            Q[Index Files]
            R[Checkpoint Files]
        end

        A --> D
        B --> D
        C --> D
        D --> E
        D --> F
        D --> G
        D --> H
        D --> I
        D --> J
        D --> K
        D --> L
        D --> M
        D --> N
        E --> O
        F --> P
        H --> Q
        D --> R
    end
```

### 1.2 分层存储架构

```mermaid
graph TD
    subgraph "存储层次结构"
        A[应用层<br/>Producer/Consumer]
        B[存储接口层<br/>MessageStore]
        C[存储逻辑层<br/>DefaultMessageStore]
        D[数据管理层<br/>CommitLog/ConsumeQueue]
        E[文件抽象层<br/>MappedFile]
        F[物理存储层<br/>磁盘文件]
    end

    A --> B
    B --> C
    C --> D
    D --> E
    E --> F

    subgraph "数据流向"
        G[消息写入流]
        H[消息读取流]
    end

    A --> G
    G --> F
    F --> H
    H --> A
```

## 2. 数据存储架构图

### 2.1 双层存储设计

```mermaid
graph LR
    subgraph "存储架构"
        A[Producer] --> B[CommitLog<br/>顺序存储]
        B --> C[ReputMessageService<br/>消息分发服务]
        C --> D[ConsumeQueue<br/>索引存储]
        C --> E[IndexService<br/>索引服务]

        F[Consumer] --> D
        G[Message Query] --> E
        H[Admin API] --> B
    end

    subgraph "存储特征"
        I[顺序写入<br/>高性能]
        J[索引查询<br/>快速定位]
        K[多维度查询<br/>灵活检索]
    end

    B -.-> I
    D -.-> J
    E -.-> K
```

### 2.2 文件组织结构

```
/store
├── commitlog/                          # 提交日志目录
│   ├── 00000000000000000000           # 第1个CommitLog文件 (1GB)
│   ├── 00000000001073741824           # 第2个CommitLog文件 (1GB)
│   ├── 00000000002147483648           # 第3个CommitLog文件 (1GB)
│   └── ...                           # 更多CommitLog文件
├── consumequeue/                      # 消费队列目录
│   ├── TopicTest/                     # 主题A
│   │   ├── 0/                        # 队列0
│   │   │   ├── 00000000000000000000  # 第1个队列文件 (~6MB, 30万条)
│   │   │   └── 00000000000000120000  # 第2个队列文件
│   │   ├── 1/                        # 队列1
│   │   │   ├── 00000000000000000000
│   │   │   └── 00000000000000120000
│   │   └── ...                       # 更多队列
│   ├── TopicOrder/                    # 主题B
│   │   ├── 0/
│   │   └── 1/
│   └── ...                           # 更多主题
├── index/                            # 索引目录
│   ├── 00000000000000000000          # 第1个索引文件 (~40MB)
│   ├── 00000000000000000001          # 第2个索引文件
│   └── ...                           # 更多索引文件
├── abort                             # 异常关闭标记文件
├── checkpoint                        # 检查点文件
└── lock                              # 文件锁文件
```

### 2.3 CommitLog 文件结构

```
CommitLog File (1GB)
├── Message 1 (变长)
│   ├── TOTALSIZE (4字节)             # 消息总长度
│   ├── MAGICCODE (4字节)             # 消息魔数
│   ├── BODYCRC (4字节)               # 消息体CRC校验
│   ├── QUEUEID (4字节)               # 队列ID
│   ├── FLAG (4字节)                  # 消息标志
│   ├── BODYOFFSET (8字节)            # 消息体偏移量
│   ├── BODYLEN (4字节)               # 消息体长度
│   ├── STORETIMESTAMP (8字节)        # 存储时间戳
│   ├── BORNTIMESTAMP (8字节)         # 产生时间戳
│   ├── BORNHOST (8字节)              # 产生主机
│   ├── STOREHOST (8字节)             # 存储主机
│   ├── RECONSUMETIMES (4字节)        # 重试次数
│   ├── PreparedTransactionOffset (8字节) # 事务消息偏移量
│   ├── BODY (变长)                   # 消息体
│   └── PROPERTIES (变长)             # 消息属性
├── Message 2 (变长)
├── Message 3 (变长)
└── ...                              # 更多消息
```

### 2.4 ConsumeQueue 文件结构

```
ConsumeQueue File (~6MB)
├── Entry 1 (20字节)
│   ├── CommitLog Offset (8字节)      # 消息在CommitLog中的偏移量
│   ├── Message Size (4字节)          # 消息大小
│   └── Tags HashCode (8字节)         # 标签哈希码
├── Entry 2 (20字节)
├── Entry 3 (20字节)
└── ...                              # 更多条目 (最多30万条)
```

## 3. 内存管理架构图

### 3.1 MappedFile 内存结构

```mermaid
graph TD
    subgraph "MappedFile 内存布局"
        A[MappedByteBuffer<br/>操作系统内存映射]
        B[writePosition<br/>写入位置]
        C[committedPosition<br/>提交位置]
        D[flushedPosition<br/>刷盘位置]
        E[WriteBuffer<br/>可选的写缓冲区]
    end

    A --> B
    A --> C
    A --> D
    A -.-> E

    subgraph "位置关系"
        F[0 <= flushedPosition <= committedPosition <= wrotePosition <= fileSize]
    end

    E --> C
```

### 3.2 内存管理流程

```mermaid
sequenceDiagram
    participant App as Application
    participant MF as MappedFile
    participant WB as WriteBuffer
    participant MB as MappedByteBuffer
    participant OS as OS Page Cache
    participant Disk as Physical Disk

    App->>MF: appendMessage()
    MF->>WB: 写入数据到写缓冲区
    WB->>MF: commit() 提交到内存映射
    MF->>MB: 数据传输到映射缓冲区
    MB->>OS: 操作系统页面缓存
    MF->>MB: flush() 刷盘请求
    OS->>Disk: 异步写入磁盘
```

### 3.3 引用计数管理

```mermaid
graph TD
    subgraph "ReferenceResource 生命周期"
        A[创建<br/>refCount=1]
        B[hold()<br/>refCount++]
        C[使用资源]
        D[release()<br/>refCount--]
        E{refCount <= 0?}
        F[cleanup()<br/>资源清理]
        G[销毁]
    end

    A --> B
    B --> C
    C --> D
    D --> E
    E -->|是| F
    E -->|否| B
    F --> G
```

## 4. 服务架构图

### 4.1 服务线程架构

```mermaid
graph TB
    subgraph "DefaultMessageStore 服务组件"
        A[ReputMessageService<br/>消息分发服务]
        B[FlushCommitLogService<br/>CommitLog刷盘服务]
        C[FlushConsumeQueueService<br/>ConsumeQueue刷盘服务]
        D[CleanCommitLogService<br/>CommitLog清理服务]
        E[CleanConsumeQueueService<br/>ConsumeQueue清理服务]
        F[ScheduleMessageService<br/>定时消息服务]
        G[IndexService<br/>索引服务]
        H[HAService<br/>高可用服务]
        I[StoreStatsService<br/>统计服务]
    end

    subgraph "服务特性"
        J[独立线程]
        K[异步处理]
        L[周期性执行]
        M[事件驱动]
    end

    A --> J
    B --> J
    C --> J
    D --> L
    E --> L
    F --> L
    G --> M
    H --> M
    I --> M
```

### 4.2 消息分发流程

```mermaid
graph LR
    subgraph "ReputMessageService 处理流程"
        A[CommitLog<br/>消息写入] --> B[ReputMessageService<br/>扫描新消息]
        B --> C[消息解码<br/>DispatchRequest]
        C --> D[分发器列表<br/>dispatcherList]
        D --> E[BuildConsumeQueue<br/>构建消费队列]
        D --> F[BuildIndex<br/>构建索引]
        D --> G[MessageArrivingListener<br/>消息到达通知]
    end

    subgraph "处理结果"
        E --> H[ConsumeQueue<br/>索引更新]
        F --> I[IndexFile<br/>索引更新]
        G --> J[Consumer<br/>拉取通知]
    end
```

### 4.3 刷盘策略架构

```mermaid
graph TD
    subgraph "刷盘策略选择"
        A[FlushDiskType<br/>配置] --> B{刷盘模式?}
        B -->|SYNC_FLUSH| C[GroupCommitService<br/>同步刷盘]
        B -->|ASYNC_FLUSH| D[FlushRealTimeService<br/>异步刷盘]
    end

    subgraph "同步刷盘流程"
        C --> E[GroupCommitRequest<br/>刷盘请求]
        E --> F[等待刷盘完成]
        F --> G[通知生产者]
    end

    subgraph "异步刷盘流程"
        D --> H[定时刷盘]
        D --> I[批量刷盘]
        H --> J[后台线程处理]
        I --> J
    end
```

## 5. 高可用架构图

### 5.1 HA 主从同步架构

```mermaid
graph TB
    subgraph "主节点 (Master)"
        A[CommitLog]
        B[HAService]
        C[HAConnection]
        D[HAServer]
    end

    subgraph "从节点 (Slave)"
        E[CommitLog]
        F[HAService]
        G[HAClient]
        H[连接管理]
    end

    subgraph "同步方式"
        I[同步复制<br/>SYNC_MASTER]
        J[异步复制<br/>ASYNC_MASTER]
    end

    A --> B
    B --> C
    C --> D
    D --> G
    G --> F
    F --> E

    C --> I
    C --> J
```

### 5.2 DLedger 一致性架构

```mermaid
graph TB
    subgraph "DLedger 集群"
        A[Leader<br/>主节点]
        B[Follower1<br/>从节点1]
        C[Follower2<br/>从节点2]
        D[新节点]
    end

    subgraph "协议层"
        E[DLedgerServer]
        F[DLedgerCommitLog]
        G[DLedgerRpcService]
    end

    subgraph "存储层"
        H[CommitLog]
        I[ConsumeQueue]
        J[IndexService]
    end

    A --> B
    A --> C
    B --> C
    C --> B
    A --> D
    B --> D
    C --> D

    F --> H
    H --> I
    H --> J
```

## 6. 索引架构图

### 6.1 IndexFile 文件结构

```
IndexFile (~40MB)
├── IndexHeader (40字节)
│   ├── beginTimestamp (8字节)         # 索引开始时间
│   ├── endTimestamp (8字节)           # 索引结束时间
│   ├── beginPhyOffset (8字节)         # 起始物理偏移量
│   ├── endPhyOffset (8字节)           # 结束物理偏移量
│   ├── hashSlotCount (4字节)          # 哈希槽数量
│   └── indexCount (4字节)             # 索引条目数量
├── Hash Slot Area (20MB)
│   ├── Slot[0] (4字节)                # 指向第一个索引条目
│   ├── Slot[1] (4字节)
│   ├── Slot[2] (4字节)
│   └── ...                          # 最多500万个哈希槽
└── Index Link Area (20MB)
    ├── IndexEntry[0] (20字节)         # 索引条目
    │   ├── KeyHash (4字节)           # 键哈希值
    │   ├── PhyOffset (8字节)         # 物理偏移量
    │   ├── TimeDiff (4字节)          # 时间差
    │   └── NextIndex (4字节)         # 下一个索引位置
    ├── IndexEntry[1] (20字节)
    └── ...                          # 最多400万个索引条目
```

### 6.2 索引查询流程

```mermaid
sequenceDiagram
    participant Client as 查询客户端
    participant IS as IndexService
    participant IF as IndexFile
    participant CL as CommitLog

    Client->>IS: queryMessage(key, timeRange)
    IS->>IS: 计算key的哈希值
    IS->>IF: 遍历索引文件

    loop 每个索引文件
        IF->>IF: 检查时间范围
        IF->>IF: 计算哈希槽位置
        IF->>IF: 遍历索引链表
        IF->>IF: 匹配时间范围和哈希值
        IF-->>IS: 返回物理偏移量列表
    end

    loop 根据偏移量读取消息
        IS->>CL: getData(offset, size)
        CL-->>IS: 返回消息内容
    end

    IS-->>Client: 返回查询结果
```

## 7. 定时消息架构图

### 7.1 定时消息处理流程

```mermaid
graph TD
    subgraph "定时消息架构"
        A[Producer<br/>发送延迟消息] --> B[CommitLog<br/>存储到原主题]
        B --> C[ScheduleMessageService<br/>识别延迟消息]
        C --> D[SCHEDULE_TOPIC_XXXX<br/>转发到定时主题]
        D --> E[延迟队列处理]
        E --> F{延迟时间到期?}
        F -->|否| E
        F -->|是| G[重新投递到原主题]
        G --> H[Consumer<br/>正常消费]
    end

    subgraph "延迟级别"
        I[Level 1: 1s]
        J[Level 2: 5s]
        K[Level 3: 10s]
        L[Level 4: 30s]
        M[Level 5: 1m]
        N[Level 18: 2h]
    end
```

### 7.2 延迟队列组织

```
SCHEDULE_TOPIC_XXXX
├── Queue1 (延迟级别1: 1s)
├── Queue2 (延迟级别2: 5s)
├── Queue3 (延迟级别3: 10s)
├── Queue4 (延迟级别4: 30s)
├── Queue5 (延迟级别5: 1m)
├── Queue6 (延迟级别6: 2m)
├── Queue7 (延迟级别7: 3m)
├── Queue8 (延迟级别8: 4m)
├── Queue9 (延迟级别9: 5m)
├── Queue10 (延迟级别10: 6m)
├── Queue11 (延迟级别11: 7m)
├── Queue12 (延迟级别12: 8m)
├── Queue13 (延迟级别13: 9m)
├── Queue14 (延迟级别14: 10m)
├── Queue15 (延迟级别15: 20m)
├── Queue16 (延迟级别16: 30m)
├── Queue17 (延迟级别17: 1h)
└── Queue18 (延迟级别18: 2h)
```

## 8. 数据恢复架构图

### 8.1 启动恢复流程

```mermaid
graph TD
    subgraph "启动恢复流程"
        A[Broker 启动] --> B[加载配置文件]
        B --> C[并行加载存储文件]

        C --> D[加载 CommitLog]
        C --> E[加载 ConsumeQueue]
        C --> F[加载 IndexFile]
        C --> G[加载 Checkpoint]

        D --> H{正常关闭?}
        E --> H
        F --> H
        G --> H

        H -->|是| I[从检查点恢复]
        H -->|否| J[从文件末尾恢复]

        I --> K[设置 reputOffset]
        J --> L[扫描文件末尾]
        L --> M[重建 ConsumeQueue]
        M --> K

        K --> N[启动 ReputMessageService]
        N --> O[开始消息分发]
        O --> P[恢复完成]
    end
```

### 8.2 文件完整性检查

```mermaid
graph LR
    subgraph "文件完整性检查"
        A[CommitLog 文件] --> B[从后向前扫描]
        B --> C[读取消息头]
        C --> D{魔数检查}
        D -->|无效| E[截断到正确位置]
        D -->|有效| F[CRC校验]
        F --> G{CRC通过?}
        G -->|否| E
        G -->|是| H[继续前一条]
        H --> C
    end

    subgraph "ConsumeQueue 修复"
        E --> I[根据 CommitLog 重建]
        I --> J[重新生成索引条目]
    end
```

## 9. 性能优化架构图

### 9.1 并发控制架构

```mermaid
graph TB
    subgraph "并发控制策略"
        A[消息写入<br/>自旋锁] --> B[CommitLog 顺序写入]
        C[消息读取<br/>无锁设计] --> D[ConsumeQueue 并发读取]
        E[文件操作<br/>分段锁] --> F[不同主题队列]
        G[统计信息<br/>原子操作] --> H[高性能计数器]
    end

    subgraph "锁机制选择"
        I[PutMessageSpinLock<br/>高并发短时间]
        J[PutMessageReentrantLock<br/>低并发长时间]
        K[CAS操作<br/>无锁化]
    end

    A -.-> I
    A -.-> J
    G -.-> K
```

### 9.2 内存优化架构

```mermaid
graph TD
    subgraph "内存优化策略"
        A[零拷贝<br/>MappedByteBuffer]
        B[页面缓存<br/>OS Page Cache]
        C[内存池<br/>TransientStorePool]
        D[批量操作<br/>减少系统调用]
    end

    subgraph "内存使用模式"
        E[写入模式<br/>Write Buffer + MappedByteBuffer]
        F[读取模式<br/>直接内存映射]
        G[临时存储<br/>堆外内存]
    end

    A --> E
    B --> F
    C --> G
    D --> A
    D --> B
    D --> C
```

## 10. 监控和统计架构图

### 10.1 统计服务架构

```mermaid
graph TB
    subgraph "StoreStatsService 统计维度"
        A[吞吐量统计<br/>TPS指标]
        B[延迟统计<br/>响应时间分布]
        C[存储统计<br/>空间使用情况]
        D[错误统计<br/>失败率监控]
    end

    subgraph "数据采集"
        E[消息写入统计]
        F[消息读取统计]
        G[文件操作统计]
        H[网络传输统计]
    end

    subgraph "指标输出"
        I[实时指标<br/>内存计数器]
        J[聚合指标<br/>定时计算]
        K[历史指标<br/>时间序列]
    end

    E --> A
    F --> B
    G --> C
    H --> D

    A --> I
    B --> J
    C --> K
    D --> K
```

### 10.2 监控指标体系

```mermaid
graph LR
    subgraph "核心监控指标"
        A[业务指标]
        B[性能指标]
        C[资源指标]
        D[错误指标]
    end

    subgraph "具体指标"
        A --> A1[消息写入TPS]
        A --> A2[消息读取TPS]
        A --> A3[存储消息量]

        B --> B1[平均延迟]
        B --> B2[P99延迟]
        B --> B3[队列长度]

        C --> C1[磁盘使用率]
        C --> C2[内存使用率]
        C --> C3[文件描述符]

        D --> D1[写入失败率]
        D --> D2[读取失败率]
        D --> D3[重试次数]
    end
```

## 总结

通过这些架构图，我们可以清晰地看到 RocketMQ Store 模块的整体设计思想和实现细节：

1. **分层架构**：清晰的接口层、实现层和服务层划分
2. **存储优化**：双层存储设计兼顾性能和查询效率
3. **内存管理**：高效的内存映射和引用计数机制
4. **服务化设计**：独立的服务线程实现异步处理
5. **高可用支持**：主从同步和DLedger一致性协议
6. **索引系统**：多维度索引支持快速查询
7. **定时消息**：灵活的延迟消息处理机制
8. **数据恢复**：完善的故障恢复和数据一致性保证
9. **性能优化**：多层次的并发控制和内存优化
10. **监控统计**：全面的指标采集和监控体系

这些设计使得 RocketMQ Store 模块能够在高并发、大数据量的场景下提供稳定可靠的存储服务。