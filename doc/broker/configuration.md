# Broker 配置详解

本文档详细说明 RocketMQ Broker 的配置参数、默认值和使用场景。

## 1. 配置文件位置

### 1.1 配置文件路径
```
${ROCKETMQ_HOME}/conf/broker.conf
```

### 1.2 启动时指定配置文件
```bash
sh mqbroker -c /path/to/your/custom/broker.conf
```

### 1.3 配置文件优先级
```
默认配置 < 配置文件 < 命令行参数 < JVM系统属性
```

## 2. 核心配置类

### 2.1 BrokerConfig
Broker的核心配置类，包含Broker的基本配置信息。

```java
public class BrokerConfig {
    // Broker所属集群名称
    private String brokerClusterName = "DefaultCluster";

    // Broker名称
    private String brokerName = MixAll.LOCAL_HOST_NAME;

    // BrokerID，0表示Master，>0表示Slave
    private int brokerId = MixAll.MASTER_ID;

    // NameServer地址，多个地址用分号分隔
    private String namesrvAddr;

    // Broker的IP地址，默认自动获取
    private String brokerIP1 = MixAll.LOCAL_HOST_NAME;

    // Broker对外服务的IP地址
    private String brokerIP2 = MixAll.LOCAL_HOST_NAME;

    // 监听端口
    private int listenPort = 10911;

    // HA监听端口，默认为listenPort + 1
    private int haListenPort = 10912;

    // 消息发送的超时时间，默认3000ms
    private int sendMessageTimeoutMillis = 3000;

    // 是否允许从Slave拉取消息
    private boolean slaveReadEnable = false;

    // 是否允许不同业务消息存储在不同commitlog
    private boolean commercialEnable = true;

    // 是否允许消息轨迹
    private boolean enableTraceMessage = false;

    // 消息轨迹主题名称
    private String traceTopic = MixAll.RMQ_SYS_TRACE_TOPIC;

    // 限制消息大小，默认4MB
    private int maxMessageSize = 1024 * 1024 * 4;
}
```

### 2.2 MessageStoreConfig
消息存储相关的配置。

```java
public class MessageStoreConfig {
    // 存储根路径
    private String storePathRootDir = System.getProperty("user.home") + File.separator + "store";

    // CommitLog存储路径
    private String storePathCommitLog = System.getProperty("user.home") + File.separator + "store" + File.separator + "commitlog";

    // 消息过期时间，默认72小时
    private int flushDiskType = FlushDiskType.SYNC_FLUSH;

    //刷盘方式：SYNC_FLUSH同步刷盘，ASYNC_FLUSH异步刷盘
    private int flushIntervalCommitLog = 1000;

    //刷盘间隔时间，单位毫秒
    private int flushIntervalConsumeQueue = 1000;

    //批量消费提交
    private int batchConsumeQueueMinBytes = 1;

    //最大消息大小
    private int maxMessageSize = 1024 * 1024 * 4;

    //同步复制还是异步复制
    private int syncFlushTimeoutMillis = 2500;

    // 刷CommitLog至少每页刷一次
    private int flushCommitLogLeastPages = 4;

    // 刷ConsumeQueue至少每页刷一次
    private int flushConsumeQueueLeastPages = 2;

    // 刷CommitLog的最长间隔时间
    private int flushCommitLogThoroughInterval = 1000 * 10;

    // 刷ConsumeQueue的最长间隔时间
    private int flushConsumeQueueThoroughInterval = 1000 * 60;

    // 最大发送消息线程池队列长度
    private int sendThreadPoolQueueCapacity = 10000;

    // 拉取消息线程池队列长度
    private int pullThreadPoolQueueCapacity = 100000;

    // 清理过期文件的间隔时间
    private int cleanFileForciblyInterval = 1000 * 60;

    // 删除过期文件的时间点
    private int deleteWhen = 04;

    // 文件保留时间，默认72小时
    private int fileReservedTime = 72;

    // Broker角色：ASYNC_MASTER、SYNC_MASTER、SLAVE
    private BrokerRole brokerRole = BrokerRole.ASYNC_MASTER;

    // 是否启用DLeger
    private boolean enableDLegerCommitLog = false;

    // DLeger分组的名称
    private String dLegerGroup = "default-group";

    // DLeger节点的Id
    private String dLegerSelfId;

    // DLeger节点列表
    private String dLegerPeers;

    // 是否启用CRC32校验
    private boolean checkCRC32 = false;

    // 是否启用批量append消息
    private boolean enableBatchPush = true;

    // 同步复制失败后重试次数
    private int syncFlushRetryCount = 0;
}
```

### 2.3 NettyServerConfig
Netty服务器配置。

```java
public class NettyServerConfig {
    // 工作线程数
    private int serverWorkerThreads = 8;

    // 公共线程数
    private int serverCallbackExecutorThreads = 0;

    // Selector线程数
    private int serverSelectorThreads = 3;

    // Oneway信号通道
    private int serverOnewaySemaphoreValue = 256;

    // 异步信号通道
    private int serverAsyncSemaphoreValue = 64;

    // 通道最大空闲时间（秒）
    private int serverChannelMaxIdleTimeSeconds = 120;

    // 发送缓冲区大小
    private int serverSocketSndBufSize = NettySystemConfig.socketSndbufSize;

    // 接收缓冲区大小
    private int serverSocketRcvBufSize = NettySystemConfig.socketRcvbufSize;

    // 是否启用TLS
    private boolean useTLS = false;

    // 是否使用Epoll（Linux下）
    private boolean useEpollNativeSelector = false;

    // 连接数
    private int serverChannelMaxNotActiveValue = 3;
}
```

### 2.4 NettyClientConfig
Netty客户端配置。

```java
public class NettyClientConfig {
    // 工作线程数
    private int clientWorkerThreads = 4;

    // 公共线程数
    private int clientCallbackExecutorThreads = Runtime.getRuntime().availableProcessors();

    // 连接超时时间
    private int connectTimeoutMillis = 3000;

    // 通道最大空闲时间（秒）
    private int channelMaxIdleTimeSeconds = 120;

    // 同步超时时间
    private int syncTimeoutMillis = 5000;

    // 异步超时时间
    private int asyncTimeoutMillis = 5000;

    // 连接池最大连接数
    private int connectPoolMaxSize = 32;

    // 是否启用TLS
    private boolean useTLS = false;
}
```

## 3. 线程池配置

### 3.1 发送消息线程池
```properties
# 发送消息线程数
sendMessageThreadPoolNums=1

# 发送消息线程池队列容量
sendThreadPoolQueueCapacity=10000
```

### 3.2 拉取消息线程池
```properties
# 拉取消息线程数
pullMessageThreadPoolNums=4 + Runtime.getRuntime().availableProcessors()

# 拉取消息线程池队列容量
pullThreadPoolQueueCapacity=100000
```

### 3.3 查询消息线程池
```properties
# 查询消息线程数
queryMessageThreadPoolNums=8

# 查询消息线程池队列容量
queryThreadPoolQueueCapacity=20000
```

### 3.4 客户端管理线程池
```properties
# 客户端管理线程数
clientManagerThreadPoolNums=4

# 客户端管理线程池队列容量
clientManagerThreadPoolQueueCapacity=1000000
```

## 4. 性能调优配置

### 4.1 内存配置
```properties
# Java堆内存大小（建议设置）
-Xms4g -Xmx4g

# 元数据空间大小
-XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=256m

# 直接内存大小
-XX:MaxDirectMemorySize=8g
```

### 4.2 刷盘策略
```properties
# 刷盘方式：ASYNC_FLUSH（异步）或 SYNC_FLUSH（同步）
flushDiskType=ASYNC_FLUSH

# 异步刷盘间隔
flushIntervalCommitLog=1000

# 同步刷盘超时时间
syncFlushTimeoutMillis=5000
```

### 4.3 文件清理策略
```properties
# 文件保留时间（小时）
fileReservedTime=72

# 清理文件时间点（小时）
deleteWhen=04

# 强制清理文件间隔（毫秒）
cleanFileForciblyInterval=1000*60
```

## 5. 高可用配置

### 5.1 主从复制配置
```properties
# Broker角色
brokerRole=SYNC_MASTER  # ASYNC_MASTER, SYNC_MASTER, SLAVE

# BrokerID
brokerId=0  # Master为0，Slave>0

# 同步复制失败后重试次数
syncFlushRetryCount=0
```

### 5.2 DLedger集群配置
```properties
# 启用DLedger
enableDLegerCommitLog=true

# DLedger分组名称
dLederGroup=default-group

# 当前节点ID
dLedgerSelfId=n0

# 节点列表
dLedgerPeers=n0-192.168.1.10:20911;n1-192.168.1.11:20911;n2-192.168.1.12:20911
```

### 5.3 自动切换配置
```properties
# 允许从Slave拉取消息
slaveReadEnable=true

# 优先从哪个Broker拉取消息
preferBrokerId=0
```

## 6. 安全配置

### 6.1 ACL权限控制
```properties
# 启用ACL
aclEnable=true

# ACL配置文件路径
accessKey=ROCKETMQ_ACCESS_KEY
secretKey=ROCKETMQ_SECRET_KEY
```

### 6.2 TLS/SSL配置
```properties
# 启用TLS
tlsEnable=true

# TLS配置文件路径
tlsTestModeEnable=false
tlsClientAuthServer=false
```

## 7. 监控和统计配置

### 7.1 消息轨迹
```properties
# 启用消息轨迹
enableTraceMessage=true

# 消息轨迹主题
traceTopic=RMQ_SYS_TRACE_TOPIC
```

### 7.2 性能监控
```properties
# 启用性能监控
enableMeterPlugin=true

# 统计时间窗口
meterStatIntervalMinutes=10
```

## 8. 调试配置

### 8.1 日志配置
```properties
# 日志级别
brokerLogLevel=INFO

# 日志文件大小
logFileSizeGB=1

# 日志文件数量
logFileNum=10
```

### 8.2 调试模式
```properties
# 启用调试模式
debugEnable=true

# 打印发送消息的详细信息
printSendMessageDetail=true

# 打印拉取消息的详细信息
printPullMessageDetail=true
```

## 9. 集群配置示例

### 9.1 Master节点配置
```properties
# 集群配置
brokerClusterName=DefaultCluster
brokerName=broker-a
brokerId=0

# 网络配置
namesrvAddr=192.168.1.10:9876;192.168.1.11:9876
listenPort=10911
haListenPort=10912

# 存储配置
storePathRootDir=/opt/rocketmq/store
flushDiskType=ASYNC_FLUSH
fileReservedTime=48

# 角色配置
brokerRole=ASYNC_MASTER
deleteWhen=02
```

### 9.2 Slave节点配置
```properties
# 集群配置
brokerClusterName=DefaultCluster
brokerName=broker-a
brokerId=1

# 网络配置
namesrvAddr=192.168.1.10:9876;192.168.1.11:9876
listenPort=10913
haListenPort=10914

# 存储配置
storePathRootDir=/opt/rocketmq/store
flushDiskType=ASYNC_FLUSH
fileReservedTime=48

# 角色配置
brokerRole=SLAVE
deleteWhen=02

# 从节点配置
slaveReadEnable=true
```

### 9.3 DLedger集群配置示例

**节点1 (n0):**
```properties
brokerClusterName=RaftCluster
brokerName=RaftNode00
brokerId=-1
namesrvAddr=192.168.1.10:9876;192.168.1.11:9876

enableDLegerCommitLog=true
dLegerGroup=RaftNode00
dLegerPeers=n0-192.168.1.10:20911;n1-192.168.1.11:20911;n2-192.168.1.12:20911
dLegerSelfId=n0

storePathRootDir=/opt/rocketmq/store
```

**节点2 (n1):**
```properties
brokerClusterName=RaftCluster
brokerName=RaftNode00
brokerId=-1
namesrvAddr=192.168.1.10:9876;192.168.1.11:9876

enableDLegerCommitLog=true
dLegerGroup=RaftNode00
dLegerPeers=n0-192.168.1.10:20911;n1-192.168.1.11:20911;n2-192.168.1.12:20911
dLegerSelfId=n1

storePathRootDir=/opt/rocketmq/store
```

**节点3 (n2):**
```properties
brokerClusterName=RaftCluster
brokerName=RaftNode00
brokerId=-1
namesrvAddr=192.168.1.10:9876;192.168.1.11:9876

enableDLegerCommitLog=true
dLegerGroup=RaftNode00
dLegerPeers=n0-192.168.1.10:20911;n1-192.168.1.11:20911;n2-192.168.1.12:20911
dLegerSelfId=n2

storePathRootDir=/opt/rocketmq/store
```

## 10. 最佳实践

### 10.1 生产环境配置建议
1. **JVM内存**: 建议8GB以上，使用G1GC
2. **存储**: 使用SSD，RAID10
3. **网络**: 万兆网卡
4. **刷盘**: 生产环境建议SYNC_FLUSH
5. **文件保留**: 根据业务需求调整，建议不小于24小时

### 10.2 性能优化建议
```properties
# 增加发送线程数
sendMessageThreadPoolNums=4

# 增加拉取线程数
pullMessageThreadPoolNums=16

# 增加队列容量
sendThreadPoolQueueCapacity=10000
pullThreadPoolQueueCapacity=200000

# 优化网络参数
serverSocketSndBufSize=65536
serverSocketRcvBufSize=65536
```

### 10.3 安全建议
```properties
# 启用ACL
aclEnable=true

# 启用TLS
tlsEnable=true

# 限制消息大小
maxMessageSize=4194304

# 启用消息轨迹
enableTraceMessage=true
```

## 11. 配置命令

### 11.1 查看配置
```bash
# 打印所有配置
sh mqbroker -c broker.conf -p

# 打印重要配置
sh mqbroker -c broker.conf -m
```

### 11.2 运行时修改配置
```bash
# 修改Broker配置
mqadmin updateBrokerConfig -n 127.0.0.1:9876 -b 127.0.0.1:10911 -k <key> -v <value>

# 查看Broker配置
mqadmin getBrokerConfig -n 127.0.0.1:9876 -b 127.0.0.1:10911
```

## 总结

Broker配置是RocketMQ运行的基础，合理配置能够：

1. **提高性能**: 通过调整线程池、内存、刷盘策略等
2. **保障可靠性**: 通过主从复制、同步刷盘等
3. **增强安全性**: 通过ACL、TLS等
4. **便于监控**: 通过消息轨迹、统计信息等

在实际使用中，需要根据业务需求、硬件配置和运维要求进行合理配置，并进行充分的测试验证。