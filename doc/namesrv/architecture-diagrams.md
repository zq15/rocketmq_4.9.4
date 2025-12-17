# RocketMQ NameServer 架构图解

## 概述

本文档通过详细的架构图解，直观展示 RocketMQ NameServer 的整体架构、组件关系、数据流、部署方式等关键设计要素，帮助读者更好地理解 NameServer 的架构设计思想。

## 目录

- [整体架构图](#整体架构图)
- [组件关系图](#组件关系图)
- [数据流架构图](#数据流架构图)
- [部署架构图](#部署架构图)
- [网络架构图](#网络架构图)
- [存储架构图](#存储架构图)
- [线程模型图](#线程模型图)
- [安全架构图](#安全架构图)
- [监控架构图](#监控架构图)
- [扩展架构图](#扩展架构图)

## 整体架构图

### NameServer 系统架构

```mermaid
graph TB
    subgraph "RocketMQ NameServer 整体架构"
        subgraph "外部接入层"
            C1[Client Producer]
            C2[Client Consumer]
            B1[Broker Master]
            B2[Broker Slave]
            A1[Admin Tool]
        end

        subgraph "网络接入层"
            LB[Load Balancer]
            FW[Firewall]
        end

        subgraph "NameServer 集群"
            NS1[NameServer-1]
            NS2[NameServer-2]
            NS3[NameServer-N]
        end

        subgraph "内部组件层"
            subgraph "NS1 组件"
                NC1[NamesrvController]
                RIM1[RouteInfoManager]
                KVM1[KVConfigManager]
                BHS1[BrokerHousekeepingService]
            end

            subgraph "NS2 组件"
                NC2[NamesrvController]
                RIM2[RouteInfoManager]
                KVM2[KVConfigManager]
                BHS2[BrokerHousekeepingService]
            end

            subgraph "NS3 组件"
                NC3[NamesrvController]
                RIM3[RouteInfoManager]
                KVM3[KVConfigManager]
                BHS3[BrokerHousekeepingService]
            end
        end

        subgraph "存储层"
            FS1[FileSystem-1]
            FS2[FileSystem-2]
            FS3[FileSystem-3]
        end
    end

    C1 --> LB
    C2 --> LB
    B1 --> LB
    B2 --> LB
    A1 --> LB

    LB --> FW
    FW --> NS1
    FW --> NS2
    FW --> NS3

    NS1 --> NC1
    NS2 --> NC2
    NS3 --> NC3

    NC1 --> RIM1
    NC1 --> KVM1
    NC1 --> BHS1

    NC2 --> RIM2
    NC2 --> KVM2
    NC2 --> BHS2

    NC3 --> RIM3
    NC3 --> KVM3
    NC3 --> BHS3

    KVM1 --> FS1
    KVM2 --> FS2
    KVM3 --> FS3
```

### NameServer 核心架构层次

```mermaid
graph TB
    subgraph "NameServer 分层架构"
        subgraph "接口层 (Interface Layer)"
            API[HTTP API]
            RPC[RPC Interface]
            CLI[Command Line]
        end

        subgraph "接入层 (Access Layer)"
            NS[NettyServer]
            RP[RequestProcessor]
            EP[ExecutorPool]
        end

        subgraph "业务层 (Business Layer)"
            RC[Route Controller]
            CC[Config Controller]
            BC[Broker Controller]
        end

        subgraph "服务层 (Service Layer)"
            RS[RouteService]
            KS[ConfigService]
            HS[HealthService]
        end

        subgraph "数据层 (Data Layer)"
            MT[Memory Tables]
            PS[Persistent Storage]
            CS[Cache Store]
        end

        subgraph "基础设施层 (Infrastructure Layer)"
            LOG[Logging]
            METRICS[Metrics]
            CONFIG[Configuration]
        end
    end

    API --> NS
    RPC --> NS
    CLI --> NS

    NS --> RP
    RP --> EP

    EP --> RC
    EP --> CC
    EP --> BC

    RC --> RS
    CC --> KS
    BC --> HS

    RS --> MT
    KS --> PS
    HS --> CS

    RC --> LOG
    CC --> METRICS
    BC --> CONFIG
```

## 组件关系图

### 核心组件关系

```mermaid
graph LR
    subgraph "核心组件关系图"
        NC[NamesrvController] --> RP[RemotingServer]
        NC --> RIM[RouteInfoManager]
        NC --> KVM[KVConfigManager]
        NC --> BHS[BrokerHousekeepingService]
        NC --> SE[ScheduledExecutor]

        RP --> DRP[DefaultRequestProcessor]
        RP --> CTRP[ClusterTestRequestProcessor]
        RP --> REP[RemotingExecutor]

        RIM --> TQT[TopicQueueTable]
        RIM --> BAT[BrokerAddrTable]
        RIM --> CAT[ClusterAddrTable]
        RIM --> BLT[BrokerLiveTable]
        RIM --> FST[FilterServerTable]

        KVM --> CT[ConfigTable]
        KVM --> FS[FileSystem]

        BHS --> RIM
        SE --> RIM
        SE --> KVM

        DRP --> RIM
        DRP --> KVM
        CTRP --> RIM
        CTRP --> KVM
    end
```

### 组件依赖关系

```mermaid
graph TD
    subgraph "组件依赖层次图"
        subgraph "控制层"
            NC[NamesrvController]
            NSU[NamesrvStartup]
        end

        subgraph "服务层"
            RP[RemotingServer]
            RIM[RouteInfoManager]
            KVM[KVConfigManager]
            BHS[BrokerHousekeepingService]
        end

        subgraph "处理层"
            DRP[DefaultRequestProcessor]
            CTRP[ClusterTestRequestProcessor]
        end

        subgraph "数据层"
            RT[RouteTables]
            KV[KVConfig]
            MEM[Memory]
            FILE[FileSystem]
        end

        subgraph "基础层"
            NETTY[Netty Framework]
            EXEC[ExecutorService]
            LOG[Logging]
            CFG[Configuration]
        end
    end

    NSU --> NC
    NC --> RP
    NC --> RIM
    NC --> KVM
    NC --> BHS

    RP --> DRP
    RP --> CTRP

    RIM --> RT
    KVM --> KV

    RT --> MEM
    KV --> FILE

    RP --> NETTY
    RP --> EXEC
    NC --> LOG
    NC --> CFG
```

## 数据流架构图

### Broker 注册数据流

```mermaid
flowchart TD
    subgraph "Broker 注册数据流"
        B[Broker] -->|REGISTER_BROKER| NS[NameServer]
        NS --> RP[RequestProcessor]
        RP --> RIM[RouteInfoManager]

        RIM --> CAT[ClusterAddrTable]
        RIM --> BAT[BrokerAddrTable]
        RIM --> TQT[TopicQueueTable]
        RIM --> BLT[BrokerLiveTable]
        RIM --> FST[FilterServerTable]

        CAT -->|更新| ClusterData[集群数据]
        BAT -->|更新| BrokerData[Broker数据]
        TQT -->|更新| TopicData[Topic数据]
        BLT -->|更新| LiveData[存活数据]
        FST -->|更新| FilterData[过滤数据]

        RIM -->|RegisterBrokerResult| RP
        RP -->|Response| B
    end
```

### 路由查询数据流

```mermaid
flowchart TD
    subgraph "路由查询数据流"
        C[Client] -->|GET_ROUTEINFO_BY_TOPIC| NS[NameServer]
        NS --> RP[RequestProcessor]
        RP --> RIM[RouteInfoManager]

        RIM --> TQT[TopicQueueTable]
        RIM --> BAT[BrokerAddrTable]
        RIM --> FST[FilterServerTable]

        TQT -->|QueueData| QD[QueueData]
        BAT -->|BrokerData| BD[BrokerData]
        FST -->|FilterData| FD[FilterData]

        QD --> TRD[TopicRouteData]
        BD --> TRD
        FD --> TRD

        RIM -->|TopicRouteData| RP
        RP -->|Response| C
    end
```

### 配置管理数据流

```mermaid
flowchart TD
    subgraph "配置管理数据流"
        A[Admin] -->|PUT_KV_CONFIG| NS[NameServer]
        NS --> RP[RequestProcessor]
        RP --> KVM[KVConfigManager]

        KVM --> CT[ConfigTable]
        KVM --> FS[FileSystem]

        CT -->|更新| MemConfig[内存配置]
        FS -->|持久化| FileConfig[文件配置]

        KVM -->|Result| RP
        RP -->|Response| A

        C[Client] -->|GET_KV_CONFIG| NS
        NS --> RP
        RP --> KVM
        KVM --> CT
        CT -->|查询| MemConfig
        MemConfig -->|Value| KVM
        KVM -->|Response| C
    end
```

## 部署架构图

### 单机部署架构

```mermaid
graph TB
    subgraph "单机部署架构"
        subgraph "物理机/虚拟机"
            subgraph "JVM Process"
                NC[NamesrvController]
                RP[RemotingServer]
                RIM[RouteInfoManager]
                KVM[KVConfigManager]
                BHS[BrokerHousekeepingService]
                SE[ScheduledExecutor]
            end
        end

        subgraph "外部组件"
            B[Broker Cluster]
            C[Client Cluster]
            FS[FileSystem]
        end

        B --> NC
        C --> NC
        NC --> FS
    end
```

### 集群部署架构

```mermaid
graph TB
    subgraph "NameServer 集群部署"
        subgraph "网络层"
            LB[Load Balancer]
            DNS[DNS Round Robin]
        end

        subgraph "NameServer 集群"
            NS1[NameServer-1<br/>192.168.1.101]
            NS2[NameServer-2<br/>192.168.1.102]
            NS3[NameServer-3<br/>192.168.1.103]
        end

        subgraph "Broker 集群"
            B1[Broker-1]
            B2[Broker-2]
            B3[Broker-3]
            B4[Broker-4]
        end

        subgraph "客户端"
            P1[Producer-1]
            P2[Producer-2]
            C1[Consumer-1]
            C2[Consumer-2]
        end
    end

    P1 --> DNS
    P2 --> DNS
    C1 --> DNS
    C2 --> DNS

    B1 --> NS1
    B1 --> NS2
    B1 --> NS3

    B2 --> NS1
    B2 --> NS2
    B2 --> NS3

    B3 --> NS1
    B3 --> NS2
    B3 --> NS3

    B4 --> NS1
    B4 --> NS2
    B4 --> NS3

    DNS --> NS1
    DNS --> NS2
    DNS --> NS3
```

### 高可用部署架构

```mermaid
graph TB
    subgraph "高可用部署架构"
        subgraph "机房 A"
            LB1[Load Balancer]
            NS1[NameServer-1]
            NS2[NameServer-2]
            B1[Broker Set A]
        end

        subgraph "机房 B"
            LB2[Load Balancer]
            NS3[NameServer-3]
            NS4[NameServer-4]
            B2[Broker Set B]
        end

        subgraph "机房 C"
            LB3[Load Balancer]
            NS5[NameServer-5]
            NS6[NameServer-6]
            B3[Broker Set C]
        end

        subgraph "全局DNS"
            GDNS[Global DNS]
        end

        subgraph "客户端"
            P[Producers]
            C[Consumers]
        end
    end

    P --> GDNS
    C --> GDNS

    GDNS --> LB1
    GDNS --> LB2
    GDNS --> LB3

    LB1 --> NS1
    LB1 --> NS2
    LB2 --> NS3
    LB2 --> NS4
    LB3 --> NS5
    LB3 --> NS6

    B1 --> NS1
    B1 --> NS2
    B1 --> NS3

    B2 --> NS3
    B2 --> NS4
    B2 --> NS5

    B3 --> NS5
    B3 --> NS6
    B3 --> NS1
```

## 网络架构图

### 网络通信架构

```mermaid
graph LR
    subgraph "网络通信架构"
        subgraph "客户端网络"
            C1[Client 1]
            C2[Client 2]
            C3[Client N]
        end

        subgraph "网络基础设施"
            FW[Firewall]
            LB[Load Balancer]
            SW[Switch]
        end

        subgraph "NameServer 网络"
            NS1[NameServer-1<br/>:9876]
            NS2[NameServer-2<br/>:9876]
            NS3[NameServer-3<br/>:9876]
        end

        subgraph "Broker 网络"
            B1[Broker-1<br/>:10911]
            B2[Broker-2<br/>:10911]
            B3[Broker-3<br/>:10911]
        end
    end

    C1 --> SW
    C2 --> SW
    C3 --> SW

    SW --> FW
    FW --> LB

    LB --> NS1
    LB --> NS2
    LB --> NS3

    B1 --> NS1
    B1 --> NS2
    B1 --> NS3

    B2 --> NS1
    B2 --> NS2
    B2 --> NS3

    B3 --> NS1
    B3 --> NS2
    B3 --> NS3
```

### Netty 网络架构

```mermaid
graph TB
    subgraph "Netty 网络架构"
        subgraph "Netty Server Pipeline"
            BOS[ServerSocketChannel]
            CH[Channel Pipeline]
            ENC[NettyEncoder]
            DEC[NettyDecoder]
            IDLE[IdleStateHandler]
            CMH[ConnectManageHandler]
            NSH[NettyServerHandler]
        end

        subgraph "线程模型"
            BOSS[EventLoopGroup Boss]
            WORKER[EventLoopGroup Worker]
            EXEC[Business Executor]
        end

        subgraph "业务处理"
            RP[RequestProcessor]
            RIM[RouteInfoManager]
            KVM[KVConfigManager]
        end
    end

    BOS --> CH
    CH --> ENC
    CH --> DEC
    CH --> IDLE
    CH --> CMH
    CH --> NSH

    BOSS --> BOS
    WORKER --> CH
    NSH --> EXEC
    EXEC --> RP
    RP --> RIM
    RP --> KVM
```

## 存储架构图

### 数据存储架构

```mermaid
graph TB
    subgraph "NameServer 存储架构"
        subgraph "内存存储"
            subgraph "路由数据"
                TQT[TopicQueueTable<br/>HashMap&lt;Topic, BrokerMap&gt;]
                BAT[BrokerAddrTable<br/>HashMap&lt;BrokerName, BrokerData&gt;]
                CAT[ClusterAddrTable<br/>HashMap&lt;Cluster, BrokerSet&gt;]
                BLT[BrokerLiveTable<br/>HashMap&lt;Addr, LiveInfo&gt;]
                FST[FilterServerTable<br/>HashMap&lt;Addr, FilterList&gt;]
            end

            subgraph "配置数据"
                CT[ConfigTable<br/>HashMap&lt;Namespace, KVMap&gt;]
            end
        end

        subgraph "持久化存储"
            KVCFG[kvConfig.json<br/>键值配置文件]
            LOGFILE[logs/namesrv.log<br/>日志文件]
            METRICS[metrics.log<br/>监控指标]
        end

        subgraph "备份存储"
            BACKUP[Backup Directory<br/>配置备份]
        end
    end

    TQT --> BLT
    BAT --> BLT
    CAT --> BLT

    CT --> KVCFG

    KVCFG --> BACKUP
    LOGFILE --> BACKUP
```

### 内存数据结构架构

```mermaid
graph LR
    subgraph "内存数据结构设计"
        subgraph "路由信息"
            Topic[Topic Name] --> BMap[BrokerName Map]
            BMap --> QD[QueueData<br/>read/write queues]

            BrokerN[Broker Name] --> BD[BrokerData<br/>cluster + addrs]

            ClusterN[Cluster Name] --> BSet[BrokerName Set]

            BrokerA[Broker Addr] --> BLI[BrokerLiveInfo<br/>timestamp + channel]
        end

        subgraph "配置信息"
            NS[Namespace] --> KVMap[Key-Value Map]
            Key[Config Key] --> Value[Config Value]
        end
    end

    QD -.->|引用| BD
    BD -.->|引用| BSet
    BSet -.->|引用| ClusterN
    BLI -.->|关联| BrokerA
```

## 线程模型图

### 线程架构模型

```mermaid
graph TB
    subgraph "NameServer 线程模型"
        subgraph "Main Thread"
            MT[Main Thread]
            MT -->|启动| NC[NamesrvController]
        end

        subgraph "Netty Boss Thread"
            BT[Boss Thread<br/>EventLoopGroup(1)]
            BT -->|Accept| CC[Client Connections]
            BT -->|Accept| BC[Broker Connections]
        end

        subgraph "Netty Worker Threads"
            WT1[Worker Thread-1]
            WT2[Worker Thread-2]
            WTN[Worker Thread-N]

            WT1 -->|I/O| RQ1[Read Queue]
            WT2 -->|I/O| RQ2[Read Queue]
            WTN -->|I/O| RQN[Read Queue]
        end

        subgraph "Business Thread Pool"
            BT1[Business Thread-1]
            BT2[Business Thread-2]
            BTN[Business Thread-N]

            BT1 -->|Process| RP1[Request Processing]
            BT2 -->|Process| RP2[Request Processing]
            BTN -->|Process| RPN[Request Processing]
        end

        subgraph "Scheduled Thread Pool"
            ST1[Scheduled Thread-1<br/>Broker Health Check]
            ST2[Scheduled Thread-2<br/>Config Print]
        end

        subgraph "File Watch Thread"
            FWT[File Watch Thread<br/>TLS Cert Reload]
        end
    end

    NC --> BT
    NC --> WT1
    NC --> WT2
    NC --> WTN

    RQ1 --> BT1
    RQ2 --> BT2
    RQN --> BTN

    NC --> ST1
    NC --> ST2
    NC --> FWT
```

### 请求处理线程流程

```mermaid
sequenceDiagram
    participant C as Client
    participant Boss as Boss Thread
    participant Worker as Worker Thread
    participant Executor as Business Thread
    participant RP as RequestProcessor
    participant RIM as RouteInfoManager

    C->>Boss: TCP Connection
    Boss->>Boss: Accept Connection
    Boss->>Worker: Register Channel

    C->>Worker: Request Data
    Worker->>Worker: Decode Request
    Worker->>Executor: Submit Task

    Executor->>RP: Process Request
    RP->>RIM: Access Route Data
    RIM-->>RP: Route Info
    RP-->>Executor: Response

    Executor->>Worker: Response Data
    Worker->>Worker: Encode Response
    Worker->>C: Response
```

## 安全架构图

### 安全防护架构

```mermaid
graph TB
    subgraph "NameServer 安全架构"
        subgraph "网络安全层"
            FW[Firewall]
            ACL[Access Control List]
            DDOS[DDoS Protection]
        end

        subgraph "传输安全层"
            TLS[TLS/SSL Encryption]
            AUTH[Authentication]
            CERT[Certificate Management]
        end

        subgraph "应用安全层"
            RBAC[Role Based Access Control]
            AUDIT[Audit Logging]
            VALID[Input Validation]
        end

        subgraph "数据安全层"
            ENCRYPT[Data Encryption]
            BACKUP[Secure Backup]
            WIPE[Secure Wipe]
        end
    end

    FW --> TLS
    ACL --> TLS
    DDOS --> TLS

    TLS --> RBAC
    AUTH --> RBAC
    CERT --> RBAC

    RBAC --> ENCRYPT
    AUDIT --> ENCRYPT
    VALID --> ENCRYPT
```

### TLS 安全架构

```mermaid
graph LR
    subgraph "TLS 安全架构"
        subgraph "证书管理"
            CA[CA Certificate]
            SC[Server Certificate]
            SK[Server Private Key]
            CC[Client Certificate]
        end

        subgraph "TLS 配置"
            TSC[TLS Server Config]
            TCC[TLS Client Config]
            CTX[SSL Context]
        end

        subgraph "文件监听"
            FWS[FileWatchService]
            FL[File Listener]
            RLD[Reload Logic]
        end
    end

    CA --> CTX
    SC --> CTX
    SK --> CTX
    CC --> TCC

    TSC --> CTX
    CTX --> FWS

    FWS --> FL
    FL --> RLD
    RLD --> CTX
```

## 监控架构图

### 监控系统架构

```mermaid
graph TB
    subgraph "NameServer 监控架构"
        subgraph "指标收集层"
            JMX[JMX Metrics]
            COUNTER[Counters]
            GAUGE[Gauges]
            HISTOGRAM[Histograms]
            TIMER[Timers]
        end

        subgraph "监控处理层"
            COLLECTOR[Metrics Collector]
            AGGREGATOR[Data Aggregator]
            ALERT[Alert Manager]
        end

        subgraph "存储层"
            TSDB[Time Series DB]
            LOGSTORE[Log Storage]
            METRICDB[Metrics DB]
        end

        subgraph "展示层"
            DASHBOARD[Monitoring Dashboard]
            GRAFANA[Grafana]
            PROMETHEUS[Prometheus]
        end

        subgraph "告警层"
            EMAIL[Email Alert]
            SMS[SMS Alert]
            WEBHOOK[Webhook Alert]
        end
    end

    JMX --> COLLECTOR
    COUNTER --> COLLECTOR
    GAUGE --> COLLECTOR
    HISTOGRAM --> COLLECTOR
    TIMER --> COLLECTOR

    COLLECTOR --> AGGREGATOR
    AGGREGATOR --> ALERT

    AGGREGATOR --> TSDB
    AGGREGATOR --> LOGSTORE
    AGGREGATOR --> METRICDB

    TSDB --> DASHBOARD
    METRICDB --> GRAFANA
    METRICDB --> PROMETHEUS

    ALERT --> EMAIL
    ALERT --> SMS
    ALERT --> WEBHOOK
```

### 日志架构图

```mermaid
graph TB
    subgraph "日志架构"
        subgraph "日志产生"
            APP[Application Logs]
            ACCESS[Access Logs]
            ERROR[Error Logs]
            PERF[Performance Logs]
        end

        subgraph "日志收集"
            LF[Logback Appender]
            ASYNC[Async Appender]
            FILTER[Log Filter]
        end

        subgraph "日志传输"
            KAFKA[Kafka Queue]
            FLUME[Flume Agent]
            BEATS[Filebeat]
        end

        subgraph "日志处理"
            STORM[Storm Processing]
            SPARK[Spark Streaming]
            LOGSTASH[Logstash]
        end

        subgraph "日志存储"
            ES[Elasticsearch]
            HDFS[HDFS Storage]
            S3[Object Storage]
        end

        subgraph "日志查询"
            KIBANA[Kibana]
            GRAFANA[Grafana]
            SPLUNK[Splunk]
        end
    end

    APP --> LF
    ACCESS --> LF
    ERROR --> LF
    PERF --> LF

    LF --> ASYNC
    ASYNC --> FILTER

    FILTER --> KAFKA
    FILTER --> FLUME
    FILTER --> BEATS

    KAFKA --> STORM
    FLUME --> LOGSTASH
    BEATS --> SPARK

    STORM --> ES
    LOGSTASH --> ES
    SPARK --> HDFS
    ES --> S3

    ES --> KIBANA
    ES --> GRAFANA
    HDFS --> SPLUNK
```

## 扩展架构图

### 插件化架构

```mermaid
graph TB
    subgraph "插件化架构"
        subgraph "核心框架"
            CORE[NameServer Core]
            API[Plugin API]
            LOADER[Plugin Loader]
            MANAGER[Plugin Manager]
        end

        subgraph "插件类型"
            AUTH[Auth Plugin]
            ROUTE[Route Plugin]
            METRIC[Metric Plugin]
            STORAGE[Storage Plugin]
            NETWORK[Network Plugin]
        end

        subgraph "插件实现"
            LDAP[LDAP Auth]
            JWT[JWT Auth]
            CONSOLE[Console Route]
            PROM[Prometheus Metric]
            INFLUX[InfluxDB Metric]
            REDIS[Redis Storage]
            ETCD[etcd Storage]
        end
    end

    CORE --> API
    API --> LOADER
    LOADER --> MANAGER

    MANAGER --> AUTH
    MANAGER --> ROUTE
    MANAGER --> METRIC
    MANAGER --> STORAGE
    MANAGER --> NETWORK

    AUTH --> LDAP
    AUTH --> JWT
    ROUTE --> CONSOLE
    METRIC --> PROM
    METRIC --> INFLUX
    STORAGE --> REDIS
    STORAGE --> ETCD
```

### 扩展点架构

```mermaid
graph LR
    subgraph "扩展点架构"
        subgraph "请求处理扩展"
            PRE[Pre Processor]
            POST[Post Processor]
            EXCEPTION[Exception Handler]
        end

        subgraph "数据扩展"
            ROUTE_EXT[Route Extension]
            CONFIG_EXT[Config Extension]
            FILTER[Filter Extension]
        end

        subgraph "存储扩展"
            MEMORY[Memory Store]
            DISK[Disk Store]
            DISTRIBUTED[Distributed Store]
        end

        subgraph "网络扩展"
            PROTOCOL[Protocol Extension]
            SERIALIZER[Serializer Extension]
            COMPRESS[Compress Extension]
        end
    end

    PRE --> ROUTE_EXT
    POST --> CONFIG_EXT
    EXCEPTION --> FILTER

    ROUTE_EXT --> MEMORY
    CONFIG_EXT --> DISK
    FILTER --> DISTRIBUTED

    MEMORY --> PROTOCOL
    DISK --> SERIALIZER
    DISTRIBUTED --> COMPRESS
```

## 容器化架构图

### Docker 容器架构

```mermaid
graph TB
    subgraph "Docker 容器化部署"
        subgraph "容器编排"
            K8S[Kubernetes]
            DOCKER[Docker Compose]
            SWARM[Docker Swarm]
        end

        subgraph "NameServer 容器"
            NS1[NameServer Pod-1]
            NS2[NameServer Pod-2]
            NS3[NameServer Pod-3]
        end

        subgraph "存储卷"
            CONFIG[Config Volume]
            LOGS[Logs Volume]
            DATA[Data Volume]
        end

        subgraph "服务发现"
            SVC[Service]
            INGRESS[Ingress]
            DNS[CoreDNS]
        end

        subgraph "监控"
            PROM[Prometheus]
            GRAFANA[Grafana]
            ALERT[AlertManager]
        end
    end

    K8S --> NS1
    K8S --> NS2
    K8S --> NS3

    NS1 --> CONFIG
    NS2 --> CONFIG
    NS3 --> CONFIG

    NS1 --> LOGS
    NS2 --> LOGS
    NS3 --> LOGS

    SVC --> NS1
    SVC --> NS2
    SVC --> NS3

    PROM --> NS1
    PROM --> NS2
    PROM --> NS3

    GRAFANA --> PROM
    ALERT --> PROM
```

### 微服务架构

```mermaid
graph TB
    subgraph "微服务架构"
        subgraph "API Gateway"
            GATEWAY[API Gateway]
            LB[Load Balancer]
        end

        subgraph "NameServer 微服务"
            NS_CORE[NameServer Core Service]
            NS_ROUTE[Route Service]
            NS_CONFIG[Config Service]
            NS_HEALTH[Health Service]
        end

        subgraph "数据服务"
            REDIS[Redis Cache]
            MYSQL[MySQL DB]
            ETCD[etcd Cluster]
        end

        subgraph "监控服务"
            JAEGER[Jaeger Tracing]
            PROM[Prometheus]
            GRAFANA[Grafana]
        end

        subgraph "配置服务"
            NACOS[Nacos Config]
            APOLLO[Apollo Config]
        end
    end

    GATEWAY --> LB
    LB --> NS_CORE
    LB --> NS_ROUTE
    LB --> NS_CONFIG
    LB --> NS_HEALTH

    NS_CORE --> REDIS
    NS_ROUTE --> MYSQL
    NS_CONFIG --> ETCD
    NS_HEALTH --> REDIS

    NS_CORE --> JAEGER
    NS_ROUTE --> PROM
    NS_CONFIG --> GRAFANA

    NS_CORE --> NACOS
    NS_ROUTE --> APOLLO
```

## 总结

通过以上架构图解，我们可以清晰地看到 RocketMQ NameServer 的设计精髓：

### 架构特点

1. **简洁高效**：架构简单，职责明确，性能优异
2. **高可用性**：支持集群部署，故障自动恢复
3. **可扩展性**：插件化设计，易于扩展功能
4. **安全性**：多层次安全防护，支持 TLS 加密

### 设计优势

1. **无状态设计**：每个 NameServer 实例独立，易于水平扩展
2. **内存优先**：全内存存储，提供极快的访问速度
3. **异步处理**：网络 I/O 和业务处理分离，提高并发能力
4. **自动恢复**：故障检测和自动清理机制

这些架构设计使得 NameServer 能够作为高性能、高可靠的注册中心，为 RocketMQ 集群提供稳定的服务发现和路由管理功能。