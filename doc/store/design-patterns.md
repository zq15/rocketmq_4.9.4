# RocketMQ Store 设计模式分析

## 概述

RocketMQ Store 模块在存储系统设计中巧妙地运用了多种设计模式，这些模式的应用不仅提高了代码的可维护性和扩展性，还体现了现代存储系统设计的最佳实践。本文档深入分析这些设计模式的具体应用。

## 1. 服务化模式 (Service Pattern)

### 1.1 定义

服务化模式将系统功能拆分为独立的服务组件，每个服务运行在独立的线程中，通过消息或事件进行协作。

### 1.2 在 Store 中的应用

#### 多服务架构

```java
public class DefaultMessageStore implements MessageStore {
    // 存储服务组件
    private final ReputMessageService reputMessageService;
    private final CommitLog.CommitRealTimeService commitRealTimeService;
    private final FlushCommitLogService flushCommitLogService;
    private final FlushConsumeQueueService flushConsumeQueueService;
    private final CleanCommitLogService cleanCommitLogService;
    private final CleanConsumeQueueService cleanConsumeQueueService;
    private final ScheduleMessageService scheduleMessageService;
    private final StoreStatsService storeStatsService;

    @Override
    public void start() throws Exception {
        // 启动各个服务
        this.lock = lock;
        this.commitLog.start();
        this.indexService.start();
        this.reputMessageService.start();

        if (this.messageStoreConfig.isTransientStorePoolEnable()) {
            this.transientStorePool.start();
        }

        this.flushCommitLogService.start();
        this.flushConsumeQueueService.start();
        this.cleanCommitLogService.start();
        this.cleanConsumeQueueService.start();
        this.storeStatsService.start();
        this.scheduleMessageService.start();
    }
}
```

#### 服务线程抽象

```java
public abstract class ServiceThread implements Runnable {
    protected static final int JOIN_TIME = 90 * 1000;

    // 线程状态管理
    private volatile boolean stopped = false;
    protected final CountDownLatch2 waitPoint = new CountDownLatch2(1);
    protected final AtomicBoolean hasNotified = new AtomicBoolean(false);

    // 服务线程模板方法
    public abstract String getServiceName();
    public void shutdown() {
        this.stopped = true;
        this.hasNotified.compareAndSet(false, true);
        this.waitPoint.countDown();
    }

    protected void waitForRunning(long interval) {
        if (this.hasNotified.compareAndSet(true, false)) {
            this.onWaitEnd();
            return;
        }

        // 等待通知或超时
        waitPoint.reset();
        try {
            waitPoint.await(interval, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // 忽略中断
        } finally {
            this.hasNotified.set(false);
            this.onWaitEnd();
        }
    }

    protected void onWaitEnd() {}
}
```

### 1.3 优势

1. **职责分离**：每个服务专注于特定功能，降低耦合度
2. **并发处理**：多线程并行处理提高系统吞吐量
3. **故障隔离**：单个服务故障不影响其他服务
4. **扩展性强**：可以独立扩展特定服务

## 2. 资源管理模式 (Resource Management Pattern)

### 2.1 定义

资源管理模式通过引用计数和生命周期管理，确保资源的正确分配、使用和释放。

### 2.2 在 Store 中的应用

#### ReferenceResource 基础类

```java
public abstract class ReferenceResource {
    // 引用计数管理
    protected final AtomicLong refCount = new AtomicLong(1);
    protected volatile boolean available = true;
    protected volatile boolean cleanupOver = false;

    // 资源管理
    public synchronized boolean hold() {
        if (this.refCount.get() <= 0) {
            return false;
        }

        if (available) {
            this.refCount.incrementAndGet();
            return true;
        }

        return false();
    }

    public void release() {
        long currentRef = this.refCount.decrementAndGet();
        if (currentRef > 0) {
            return;
        }

        synchronized (this) {
            if (!this.cleanupOver) {
                this.cleanup();
                this.cleanupOver = true;
            }
        }
    }

    // 子类实现具体的资源清理逻辑
    protected abstract void cleanup();
}
```

#### MappedFile 资源管理

```java
public class MappedFile extends ReferenceResource {
    private volatile boolean swapMapTime = true;
    private final CountDownLatch swapMapCount = new CountDownLatch(1);
    private final AtomicInteger swapMapCountValue = new AtomicInteger(0);

    @Override
    public void cleanup() {
        if (this.getMappedByteBuffer() != null) {
            try {
                // 释放内存映射资源
                AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                    try {
                        Method getCleanerMethod = this.getMappedByteBuffer().getClass()
                            .getMethod("cleaner");
                        cleaner = (Cleaner) getCleanerMethod.invoke(this.getMappedByteBuffer());
                        cleaner.clean();
                    } catch (Exception e) {
                        // 忽略异常
                    }
                    return null;
                });
            } finally {
                this.swapMapTime = false;
                this.swapMapCount.countDown();
            }
        }
    }
}
```

#### 文件队列资源管理

```java
public class MappedFileQueue {
    private final CopyOnWriteArrayList<MappedFile> mappedFiles = new CopyOnWriteArrayList<>();

    public MappedFile getLastMappedFile() {
        MappedFile mappedFileLast = null;
        while (!this.mappedFiles.isEmpty()) {
            try {
                mappedFileLast = this.mappedFiles.get(this.mappedFiles.size() - 1);
                break;
            } catch (IndexOutOfBoundsException e) {
                // 忽略并发访问异常
            }
        }
        return mappedFileLast;
    }

    public boolean load() {
        File dir = new File(this.storePath);
        File[] files = dir.listFiles();
        if (files != null) {
            // 按文件名排序
            Arrays.sort(files);

            for (File file : files) {
                // 加载文件并管理生命周期
                MappedFile mappedFile = new MappedFile(file.getPath(), this.mappedFileSize);
                mappedFile.setWrotePosition(this.mappedFileSize);
                if (mappedFile.getFileSize() >= this.mappedFileSize) {
                    mappedFile.setFlushedPosition(this.mappedFileSize);
                    mappedFile.setCommittedPosition(this.mappedFileSize);
                }

                this.mappedFiles.add(mappedFile);
                LOG.info("load " + file.getPath() + " OK");
            }
        }
        return true;
    }
}
```

### 2.3 优势

1. **内存安全**：防止内存泄漏和资源泄漏
2. **引用计数**：精确控制资源生命周期
3. **自动清理**：资源使用完毕自动释放
4. **并发安全**：支持多线程安全访问

## 3. 异步化模式 (Asynchronous Pattern)

### 3.1 定义

异步化模式通过非阻塞操作和回调机制，提高系统的并发处理能力和响应性能。

### 3.2 在 Store 中的应用

#### 异步刷盘服务

```java
class FlushRealTimeService extends FlushCommitLogService {
    @Override
    public void run() {
        while (!this.isStopped()) {
            // 获取刷盘间隔
            boolean flushCommitLogTimed = CommitLog.this.defaultMessageStore
                .getMessageStoreConfig().isFlushCommitLogTimed();
            int interval = CommitLog.this.defaultMessageStore
                .getMessageStoreConfig().getFlushIntervalCommitLog();

            // 异步执行刷盘
            int flushPhysicQueueLeastPages = CommitLog.this.defaultMessageStore
                .getMessageStoreConfig().getFlushCommitLogLeastPages();
            int flushConsumeQueueLeastPages = CommitLog.this.defaultMessageStore
                .getMessageStoreConfig().getFlushConsumeQueueLeastPages();

            boolean printFlushProgress = false;

            // 刷盘逻辑
            this.sleep(interval);

            // 验证刷盘结果
            long printFlushTime = System.currentTimeMillis();
            this.printFlushProgress(printFlushTime);
        }
    }
}
```

#### 批量提交服务

```java
class GroupCommitService extends FlushCommitLogService {
    private volatile List<GroupCommitRequest> requestsWrite = new ArrayList<>();
    private volatile List<GroupCommitRequest> requestsRead = new ArrayList<>();

    public synchronized void putRequest(final GroupCommitRequest request) {
        synchronized (this.requestsWrite) {
            this.requestsWrite.add(request);
        }
        // 唤醒刷盘线程
        this.wakeup();
    }

    @Override
    public void run() {
        while (!this.isStopped()) {
            try {
                this.waitForRunning(10);
                this.swapRequests();
                this.doCommit();
            } catch (Exception e) {
                CommitLog.this.log.warn("GroupCommitService exception", e);
            }
        }
    }

    private void doCommit() {
        synchronized (this.requestsRead) {
            if (!this.requestsRead.isEmpty()) {
                for (GroupCommitRequest req : this.requestsRead) {
                    // 执行刷盘操作
                    boolean flushOK = false;
                    try {
                        flushOK = CommitLog.this.mappedFileQueue.getFlushedWhere() >= req.getNextOffset();
                    } catch (Exception e) {
                        // 处理异常
                    } finally {
                        req.wakeupCustomer(flushOK);
                    }
                }
            }
        }
    }
}
```

### 2.3 优势

1. **高吞吐量**：异步处理避免阻塞主线程
2. **批量操作**：合并多个操作减少 I/O 开销
3. **响应性**：快速响应客户端请求
4. **资源利用率**：提高系统资源利用效率

## 4. 分层存储模式 (Layered Storage Pattern)

### 4.1 定义

分层存储模式将数据按不同维度分层存储，每层针对特定使用场景优化。

### 4.2 在 Store 中的应用

#### CommitLog 和 ConsumeQueue 分层

```java
public class CommitLog {
    // 顺序存储层 - 所有消息存储在这里
    public AppendMessageResult putMessage(final MessageExtBrokerInner msg) {
        // 设置消息属性
        MappedFile mappedFile = this.mappedFileQueue.getLastMappedFile();

        // 消息编码
        final AppendMessageResult result = new AppendMessageResult();
        if (mappedFile == null || mappedFile.isFull()) {
            mappedFile = this.mappedFileQueue.getLastMappedFile(0);
        }

        // 顺序写入
        result = mappedFile.appendMessagesInner(msg, this.appendMessageCallback);

        return result;
    }
}

public class ConsumeQueue {
    // 索引存储层 - 按主题队列组织
    public void putMessagePositionInfoWrapper(DispatchRequest request) {
        final int maxRetries = 30;
        boolean canWrite = this.defaultMessageStore.getRunningFlags().isCQWriteable();

        for (int i = 0; i < maxRetries && canWrite; i++) {
            long offsetPy = request.getCommitLogOffset();
            int sizePy = request.getMsgSize();
            long tagsCode = request.getTagsCode();

            // 构建索引条目：8字节偏移量 + 4字节大小 + 8字节标签码
            this.putMessagePositionInfo(request.getCommitLogOffset() + request.getMsgSize(),
                request.getTagsCode(), request.getStoreTimestamp());
        }
    }
}
```

#### 存储文件组织

```
/store
├── commitlog/
│   ├── 00000000000000000000    (1GB)
│   ├── 00000000001073741824    (1GB)
│   └── ...
├── consumequeue/
│   ├── TopicA/
│   │   ├── 0/
│   │   │   ├── 00000000000000000000    (6MB)
│   │   │   └── 00000000000000030000    (6MB)
│   │   └── 1/
│   │       └── ...
│   └── TopicB/
│       └── ...
└── index/
    ├── 00000000000000000000    (40MB)
    └── ...
```

### 4.3 优势

1. **写入性能**：CommitLog 顺序写入提供最佳性能
2. **查询效率**：ConsumeQueue 索引支持快速定位
3. **存储优化**：不同类型数据使用不同存储策略
4. **扩展性**：支持按主题和队列独立扩展

## 5. 模板方法模式 (Template Method Pattern)

### 5.1 定义

模板方法模式在基类中定义算法的骨架，将具体步骤延迟到子类中实现。

### 5.2 在 Store 中的应用

#### ServiceThread 抽象基类

```java
public abstract class ServiceThread implements Runnable {
    // 模板方法：定义服务线程的执行骨架
    @Override
    public void run() {
        // 前置处理
        this.beforeExecute();

        // 主执行循环
        while (!this.stopped) {
            try {
                // 等待执行条件
                this.waitForRunning(this.getWaitTime());

                // 执行具体业务逻辑 - 由子类实现
                this.execute();

                // 后置处理
                this.afterExecute();
            } catch (Exception e) {
                this.handleException(e);
            }
        }

        // 清理工作
        this.cleanup();
    }

    // 抽象方法 - 由子类实现
    protected abstract void execute() throws Exception;

    // 钩子方法 - 子类可选择性重写
    protected void beforeExecute() {}
    protected void afterExecute() {}
    protected void cleanup() {}
    protected long getWaitTime() { return 1000; }
    protected void handleException(Exception e) {}
}
```

#### 具体服务实现

```java
class ReputMessageService extends ServiceThread {
    @Override
    protected void execute() throws Exception {
        // 具体的消息分发逻辑
        DoMessageResult result = DefaultMessageStore.this.doReput(this.reputFromOffset);

        if (result != null) {
            this.reputFromOffset = result.getNextBeginOffset();
        }
    }

    @Override
    protected long getWaitTime() {
        return 1; // 快速响应
    }
}

class CleanCommitLogService extends ServiceThread {
    @Override
    protected void execute() throws Exception {
        // 具体的清理逻辑
        boolean existRc = true;
        this.firstDeleteFile = true;

        for (boolean diskFull = this.isSpaceFull(); diskFull || existRc;) {
            // 执行文件清理
            existRc = this.deleteExpiredFiles();

            if (diskFull) {
                diskFull = this.isSpaceFull();
            }

            if (!existRc && !diskFull) {
                break;
            }

            // 控制清理频率
            Thread.sleep(100);
        }
    }

    @Override
    protected long getWaitTime() {
        return 1000 * 60; // 每分钟执行一次
    }
}
```

### 5.3 优势

1. **代码复用**：通用的线程管理逻辑在基类中实现
2. **一致性**：保证所有服务线程遵循相同的执行模式
3. **扩展性**：子类只需关注业务逻辑实现
4. **维护性**：统一的异常处理和生命周期管理

## 6. 工厂模式 (Factory Pattern)

### 6.1 定义

工厂模式将对象的创建逻辑封装起来，客户端通过工厂接口创建对象，而不需要知道具体的创建细节。

### 6.2 在 Store 中的应用

#### AppendMessageCallback 工厂

```java
public class CommitLog {
    private final AppendMessageCallback appendMessageCallback;

    // 工厂方法：根据配置创建不同的消息追加回调
    private AppendMessageCallback getAppendMessageCallback() {
        if (this.defaultMessageStore.getMessageStoreConfig().isCompressRePut()) {
            return new CompressMessageAppendCallback();
        } else {
            return new DefaultAppendMessageCallback();
        }
    }

    // 默认消息追加回调实现
    class DefaultAppendMessageCallback implements AppendMessageCallback {
        private ByteBuffer msgIdMemoryBuffer;
        private ByteBuffer msgStoreItemMemoryBuffer;

        @Override
        public AppendMessageResult doAppend(final long fileFromOffset, final ByteBuffer byteBuffer,
            final int maxBlank, final MessageExtBrokerInner msgInner) {

            // 消息序列化逻辑
            long wroteOffset = fileFromOffset + byteBuffer.position();

            // 消息ID生成
            String msgId = MessageDecoder.createMessageId(
                this.msgIdMemoryBuffer, wroteOffset);

            // 消息编码
            final byte[] msgBody = msgInner.getBody();
            final int bodyLength = msgBody.length;

            // 写入缓冲区
            this.resetByteBuffer(this.msgStoreItemMemoryBuffer, msgInner.getMsgStoreItemLength());
            // ... 具体编码逻辑

            return result;
        }
    }
}
```

#### MappedFile 工厂

```java
public class MappedFileQueue {
    // 工厂方法：创建新的映射文件
    public MappedFile getLastMappedFile(final long startOffset, boolean needCreate) {
        long createOffset = -1;
        MappedFile mappedFileLast = getLastMappedFile();

        if (mappedFileLast == null) {
            createOffset = startOffset - (startOffset % this.mappedFileSize);
        } else if (mappedFileLast.isFull()) {
            createOffset = mappedFileLast.getFileFromOffset() + this.mappedFileSize;
        }

        if (createOffset != -1 && needCreate) {
            String nextFilePath = this.storePath + File.separator + UtilAll.offset2FileName(createOffset);
            MappedFile mappedFile = null;

            // 根据配置创建不同类型的映射文件
            if (this.allocateMappedFileService != null) {
                mappedFile = this.allocateMappedFileService.putRequestAndReturnMappedFile(nextFilePath,
                    this.mappedFileSize);
            } else {
                mappedFile = new MappedFile(nextFilePath, this.mappedFileSize);
            }

            if (mappedFile != null) {
                if (this.mappedFiles.isEmpty()) {
                    mappedFile.setFirstCreateInQueue(true);
                }
                this.mappedFiles.add(mappedFile);
            }

            return mappedFile;
        }

        return mappedFileLast;
    }
}
```

### 6.3 优势

1. **封装创建逻辑**：复杂的对象创建逻辑被封装在工厂中
2. **配置驱动**：根据配置创建不同实现
3. **类型安全**：保证创建的对象符合接口规范
4. **易于扩展**：添加新的实现只需修改工厂

## 7. 观察者模式 (Observer Pattern)

### 7.1 定义

观察者模式定义了对象之间的一对多依赖关系，当一个对象状态改变时，所有依赖者都会收到通知。

### 7.2 在 Store 中的应用

#### CommitLogDispatcher 事件分发

```java
public interface CommitLogDispatcher {
    void dispatch(DispatchRequest request);
}

public class DefaultMessageStore {
    // 观察者列表
    private final List<CommitLogDispatcher> dispatcherList;

    // 通知所有观察者
    public void doDispatch(DispatchRequest request) {
        for (CommitLogDispatcher dispatcher : this.dispatcherList) {
            dispatcher.dispatch(request);
        }
    }

    // 注册观察者
    public void addDispatcher(CommitLogDispatcher dispatcher) {
        this.dispatcherList.add(dispatcher);
    }
}

// 具体观察者实现
class BuildConsumeQueue implements CommitLogDispatcher {
    @Override
    public void dispatch(DispatchRequest request) {
        // 构建消费队列
        final int tranType = MessageSysFlag.getTransactionValue(request.getSysFlag());
        switch (tranType) {
            case MessageSysFlag.TRANSACTION_NOT_TYPE:
            case MessageSysFlag.TRANSACTION_COMMIT_TYPE:
                DefaultMessageStore.this.putMessagePositionInfo(request);
                break;
            case MessageSysFlag.TRANSACTION_PREPARED_TYPE:
            case MessageSysFlag.TRANSACTION_ROLLBACK_TYPE:
                // 忽略事务消息
                break;
        }
    }
}

class BuildIndex implements CommitLogDispatcher {
    @Override
    public void dispatch(DispatchRequest request) {
        // 构建索引
        DefaultMessageStore.this.indexService.buildIndex(request);
    }
}
```

#### 消息到达监听

```java
public interface MessageArrivingListener {
    void arriving(String topic, int queueId, long logicOffset, long tagsCode,
        long msgStoreTime, byte[] filterBitMap, Map<String, String> properties);
}

public class DefaultMessageStore {
    private MessageArrivingListener messageArrivingListener;

    public void setMessageArrivingListener(MessageArrivingListener messageArrivingListener) {
        this.messageArrivingListener = messageArrivingListener;
    }

    // 通知消息到达
    private void notifyMessageArriving(final DispatchRequest request) {
        if (this.messageArrivingListener != null) {
            try {
                this.messageArrivingListener.arriving(
                    request.getTopic(),
                    request.getQueueId(),
                    request.getCommitLogOffset(),
                    request.getTagsCode(),
                    request.getStoreTimestamp(),
                    request.getBitMap(),
                    request.getPropertiesMap()
                );
            } catch (Exception e) {
                this.log.warn("notifyMessageArriving exception", e);
            }
        }
    }
}
```

### 7.3 优势

1. **松耦合**：发布者和订阅者之间松耦合
2. **可扩展**：可以动态添加新的订阅者
3. **异步通知**：支持异步事件处理
4. **多播通信**：一个事件可以被多个订阅者处理

## 8. 策略模式 (Strategy Pattern)

### 8.1 定义

策略模式定义了一系列算法，把它们封装起来，并且使它们可相互替换。

### 8.2 在 Store 中的应用

#### 刷盘策略

```java
public enum FlushDiskType {
    SYNC_FLUSH(1),
    ASYNC_FLUSH(2);

    private int value;

    FlushDiskType(int value) {
        this.value = value;
    }

    public int getValue() {
        return this.value;
    }
}

public class CommitLog {
    private FlushCommitLogService flushCommitLogService;

    // 根据策略创建不同的刷盘服务
    private void createFlushCommitLogService() {
        if (FlushDiskType.SYNC_FLUSH == this.defaultMessageStore.getMessageStoreConfig()
            .getFlushDiskType()) {
            // 同步刷盘策略
            this.flushCommitLogService = new GroupCommitService();
        } else {
            // 异步刷盘策略
            this.flushCommitLogService = new FlushRealTimeService();
        }
    }
}
```

#### 消息过滤策略

```java
public interface MessageFilter {
    boolean isMatchedByConsumeQueue(ConsumeQueueExt.CqExtUnit cqExtUnit);
    boolean isMatchedByCommitLog(ByteBuffer msgBody, Map<String, String> properties);
}

// 默认消息过滤器
public class DefaultMessageFilter implements MessageFilter {
    @Override
    public boolean isMatchedByConsumeQueue(ConsumeQueueExt.CqExtUnit cqExtUnit) {
        return true; // 不过滤
    }

    @Override
    public boolean isMatchedByCommitLog(ByteBuffer msgBody, Map<String, String> properties) {
        return true; // 不过滤
    }
}

// 自定义消息过滤器示例
public class TagMessageFilter implements MessageFilter {
    private String tag;

    public TagMessageFilter(String tag) {
        this.tag = tag;
    }

    @Override
    public boolean isMatchedByCommitLog(ByteBuffer msgBody, Map<String, String> properties) {
        String messageTag = properties.get(MessageConst.PROPERTY_TAGS);
        return tag.equals(messageTag);
    }

    @Override
    public boolean isMatchedByConsumeQueue(ConsumeQueueExt.CqExtUnit cqExtUnit) {
        return true;
    }
}
```

### 8.3 优势

1. **算法独立**：不同的策略可以独立变化
2. **运行时切换**：可以在运行时动态选择策略
3. **易于扩展**：添加新策略不需要修改现有代码
4. **配置驱动**：通过配置文件控制策略选择

## 9. 总结

RocketMQ Store 模块通过巧妙地运用多种设计模式，构建了一个高性能、高可靠、易扩展的存储系统：

1. **服务化模式**：将系统功能拆分为独立服务，提高并发处理能力
2. **资源管理模式**：通过引用计数和生命周期管理，确保资源正确使用
3. **异步化模式**：通过非阻塞操作和批量处理，提高系统吞吐量
4. **分层存储模式**：将数据分层存储，优化读写性能
5. **模板方法模式**：定义通用的执行骨架，保证代码一致性
6. **工厂模式**：封装对象创建逻辑，提高代码可维护性
7. **观察者模式**：实现松耦合的事件通知机制
8. **策略模式**：支持多种算法策略的灵活切换

这些设计模式的综合运用，使得 RocketMQ Store 模块在保持高性能的同时，具备了优秀的架构质量和扩展能力，是现代分布式存储系统设计的典范。