# Broker 模块设计模式分析

本文档分析 RocketMQ Broker 模块中使用的设计模式及其应用场景。

## 1. 单例模式 (Singleton Pattern)

### 1.1 BrokerController
BrokerController 是 Broker 模块的核心控制器，采用了单例模式来确保全局唯一性。

```java
public class BrokerController {
    // 各种管理器组件，每个 Broker 实例只有一个
    private final ConsumerOffsetManager consumerOffsetManager;
    private final ConsumerManager consumerManager;
    private final ProducerManager producerManager;
    private final TopicConfigManager topicConfigManager;
    // ...
}
```

**应用场景：**
- 确保 Broker 实例的全局唯一性
- 统一管理 Broker 的所有核心组件
- 避免资源的重复创建

### 1.2 ScheduledExecutorService
BrokerController 中使用单线程的调度执行器：

```java
private final ScheduledExecutorService scheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor(new ThreadFactoryImpl(
        "BrokerControllerScheduledThread"));
```

**应用场景：**
- 处理定时任务（如状态报告、清理等）
- 确保任务的串行执行

## 2. 工厂模式 (Factory Pattern)

### 2.1 MessageStoreFactory
消息存储的工厂模式实现：

```java
public class MessageStoreFactory {
    public static MessageStore build(MessageStorePluginContext context,
                                     MessageStore messageStore) {
        // 根据配置创建不同的 MessageStore 实现
        if (messageStore instanceof DefaultMessageStore) {
            return new AbstractPluginMessageStore(context, (DefaultMessageStore) messageStore);
        }
        return messageStore;
    }
}
```

**应用场景：**
- 根据配置创建不同的存储实现
- 支持插件化扩展
- 解耦存储逻辑与业务逻辑

## 3. 模板方法模式 (Template Method Pattern)

### 3.1 AbstractSendMessageProcessor
抽象的消息发送处理器：

```java
public abstract class AbstractSendMessageProcessor implements NettyRequestProcessor {
    protected RemotingCommand msgCheck(Context ctx, SendMessageRequestHeader requestHeader) {
        // 通用的消息检查逻辑
    }

    protected abstract RemotingCommand sendMessage(Context ctx, RemotingCommand request);
}
```

**应用场景：**
- 定义消息处理的通用流程
- 子类实现具体的处理逻辑
- 避免重复代码

### 3.2 TransactionalMessageService
事务消息服务定义了模板方法：

```java
public abstract class TransactionalMessageService {
    public abstract OperationResult prepareMessage(MessageExtBrokerInner messageInner);

    public abstract boolean deletePrepareMessage(MessageExt msgExt);

    public abstract OperationResult commitMessage(MessageExt msgExt);

    public abstract OperationResult rollbackMessage(MessageExt msgExt);
}
```

## 4. 观察者模式 (Observer Pattern)

### 4.1 MessageArrivingListener
消息到达监听器：

```java
public class NotifyMessageArrivingListener implements MessageArrivingListener {
    @Override
    public void arriving(String topic, int queueId, long logicOffset, long tagsCode,
                        long msgStoreTime, byte[] filterBitMap, Map<String, String> properties) {
        // 通知有新消息到达
        pullRequestHoldService.notifyMessageArriving(topic, queueId, logicOffset, tagsCode);
    }
}
```

**应用场景：**
- 消息到达时通知长轮询客户端
- 解耦消息存储与消息拉取

### 4.2 ConsumerIdsChangeListener
消费者 ID 变化监听器：

```java
public interface ConsumerIdsChangeListener {
    void consumerGroupChanged(final String topic, final String group,
                             final String consumerIds);
}
```

**应用场景：**
- 监听消费者组变化
- 触发负载重平衡

## 5. 策略模式 (Strategy Pattern)

### 5.1 MessageStore 不同实现
Broker 支持多种存储策略：

```java
// 传统存储实现
DefaultMessageStore defaultMessageStore;

// DLedger 存储实现（支持主从切换）
DLedgerCommitLog dLedgerCommitLog;
```

**应用场景：**
- 根据配置选择不同的存储策略
- 支持 DLedger 集群模式
- 便于扩展新的存储方式

### 5.2 BrokerRole 枚举
Broker 角色策略：

```java
public enum BrokerRole {
    ASYNC_MASTER,
    SYNC_MASTER,
    SLAVE;
}
```

**应用场景：**
- 定义不同的 Broker 角色行为
- 支持同步/异步主从复制

## 6. 责任链模式 (Chain of Responsibility Pattern)

### 6.1 CommitLogDispatcher
CommitLog 调度器链：

```java
public interface CommitLogDispatcher {
    void dispatch(DispatchRequest request);
}

// 具体的调度器实现
public class CommitLogDispatcherCalcBitMap implements CommitLogDispatcher {
    // 计算位图
}

// 在 DefaultMessageStore 中构建调度器链
List<CommitLogDispatcher> dispatcherList = new ArrayList<>();
dispatcherList.add(new CommitLogDispatcherCalcBitMap(consumeQueueManager));
```

**应用场景：**
- 将 CommitLog 中的消息分发给不同的组件
- 支持可扩展的调度策略

## 7. 代理模式 (Proxy Pattern)

### 7.1 AbstractPluginMessageStore
插件消息存储代理：

```java
public class AbstractPluginMessageStore extends MessageStore {
    private final MessageStorePluginContext context;
    private final DefaultMessageStore messageStore;

    // 代理所有方法，可以在前后添加插件逻辑
    @Override
    public PutMessageResult putMessage(MessageExtBrokerInner msg) {
        // 前置插件处理
        // 调用实际存储
        // 后置插件处理
        return messageStore.putMessage(msg);
    }
}
```

**应用场景：**
- 为消息存储添加插件功能
- 不修改原有代码的情况下扩展功能

## 8. 装饰器模式 (Decorator Pattern)

### 8.1 BrokerFixedThreadPoolExecutor
线程池装饰器：

```java
public class BrokerFixedThreadPoolExecutor extends ThreadPoolExecutor {
    // 添加了监控和管理功能
    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        // 执行前的监控逻辑
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        // 执行后的监控逻辑
    }
}
```

**应用场景：**
- 为标准线程池添加额外功能
- 监控任务执行情况

## 9. 状态模式 (State Pattern)

### 9.1 Broker 运行状态管理
BrokerController 的状态管理：

```java
public class BrokerController {
    private volatile boolean initialized = false;

    public boolean initialize() {
        // 初始化逻辑
        this.initialized = true;
        return true;
    }

    public void start() {
        if (!this.initialized) {
            throw new IllegalStateException("Broker not initialized");
        }
        // 启动逻辑
    }
}
```

**应用场景：**
- 管理 Broker 的生命周期状态
- 确保操作的合法性

## 10. 门面模式 (Facade Pattern)

### 10.1 BrokerController 作为门面
BrokerController 为所有 Broker 功能提供统一的接口：

```java
public class BrokerController {
    // 封装所有内部组件的操作
    public void registerProcessor() {
        // 注册各种处理器
        this.remotingServer.registerProcessor(RequestCode.SEND_MESSAGE,
            sendMessageProcessor, this.sendMessageExecutor);
        this.remotingServer.registerProcessor(RequestCode.PULL_MESSAGE,
            pullMessageProcessor, this.pullMessageExecutor);
        // ...
    }
}
```

**应用场景：**
- 为复杂的子系统提供简单接口
- 隐藏内部实现细节
- 降低客户端与子系统的耦合度

## 11. 组合模式 (Composite Pattern)

### 11.1 RequestProcessor 组合
请求处理器的组合使用：

```java
// 将多个处理器组合在一起使用
SendMessageProcessor sendMessageProcessor;
PullMessageProcessor pullMessageProcessor;
AdminBrokerProcessor adminBrokerProcessor;
// ...
```

**应用场景：**
- 统一处理不同类型的请求
- 便于添加新的处理器

## 12. 命令模式 (Command Pattern)

### 12.1 RemotingCommand 封装
所有网络请求都被封装为命令对象：

```java
public class RemotingCommand {
    private int code;           // 请求代码
    private LanguageCode language; // 语言
    private int version;        // 版本
    private int opaque;         // 请求ID
    private int flag;           // 标志
    private String remark;      // 备注
    private SerializeType serializeTypeCurrentRPC = SerializeType.JSON;
    // ...
}
```

**应用场景：**
- 将请求封装为对象
- 支持请求的排队、记录和撤销
- 解耦请求发送者和接收者

## 13. 享元模式 (Flyweight Pattern)

### 13.1 ThreadPoolQueue 复用
不同类型任务共享线程池队列：

```java
private final BlockingQueue<Runnable> sendThreadPoolQueue;
private final BlockingQueue<Runnable> pullThreadPoolQueue;
private final BlockingQueue<Runnable> replyThreadPoolQueue;
// 这些队列可以被多个任务共享
```

**应用场景：**
- 共享队列对象
- 减少内存开销

## 14. 中介者模式 (Mediator Pattern)

### 14.1 BrokerController 作为中介者
BrokerController 协调各个组件之间的交互：

```java
public class BrokerController {
    // 协调生产者和消费者
    private final ProducerManager producerManager;
    private final ConsumerManager consumerManager;

    // 协调存储和消息处理
    private final MessageStore messageStore;
    private final PullMessageProcessor pullMessageProcessor;

    // 协调配置管理
    private final TopicConfigManager topicConfigManager;
    private final ConsumerOffsetManager consumerOffsetManager;
}
```

**应用场景：**
- 集中管理组件间的交互
- 降低组件间的直接耦合
- 便于维护和扩展

## 总结

RocketMQ Broker 模块通过合理运用各种设计模式，实现了：

1. **高内聚低耦合**：组件职责明确，相互依赖最小化
2. **可扩展性**：支持插件化扩展和功能增强
3. **可维护性**：代码结构清晰，便于理解和修改
4. **高性能**：通过合理的架构设计优化性能
5. **高可靠性**：通过状态管理和错误处理保证系统稳定性

这些设计模式的综合运用，使得 Broker 成为一个功能强大、性能优异、易于扩展和维护的消息中间件核心组件。