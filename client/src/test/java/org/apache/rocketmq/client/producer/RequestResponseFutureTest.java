/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.rocketmq.client.producer;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.rocketmq.common.message.Message;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestResponseFutureTest {

    @Test
    public void testExecuteRequestCallback() throws Exception {
        final AtomicInteger cc = new AtomicInteger(0);
        RequestResponseFuture future = new RequestResponseFuture(UUID.randomUUID().toString(), 3 * 1000L, new RequestCallback() {
            @Override public void onSuccess(Message message) {
                cc.incrementAndGet();
            }

            @Override public void onException(Throwable e) {
            }
        });
        future.setSendRequestOk(true);
        future.executeRequestCallback();
        assertThat(cc.get()).isEqualTo(1);
    }

    /**
     * 测试阻塞和唤醒机制
     * 场景：主线程调用 waitResponseMessage 阻塞等待，子线程延迟后调用 putResponseMessage 唤醒
     */
    @Test
    public void testWaitAndNotify() throws Exception {
        // 创建 RequestResponseFuture，超时时间 5 秒
        final RequestResponseFuture future = new RequestResponseFuture(
            UUID.randomUUID().toString(),
            5000L,
            null
        );

        // 记录开始时间
        final long startTime = System.currentTimeMillis();

        // 创建响应消息
        final Message responseMessage = new Message("TestTopic", "Hello RocketMQ".getBytes());
        responseMessage.setKeys("test-key");

        // 启动子线程，延迟 1 秒后放入响应消息
        Thread notifyThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 模拟网络延迟，1 秒后收到响应
                    Thread.sleep(1000);

                    // 放入响应消息，唤醒等待线程
                    future.putResponseMessage(responseMessage);
                    System.out.println("子线程已放入响应消息并唤醒主线程");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        notifyThread.start();

        // 主线程阻塞等待响应（最多等待 3 秒）
        System.out.println("主线程开始阻塞等待响应...");
        Message result = future.waitResponseMessage(3000L);

        // 计算实际等待时间
        long waitTime = System.currentTimeMillis() - startTime;
        System.out.println("主线程被唤醒，实际等待时间: " + waitTime + " ms");

        // 验证结果
        assertThat(result).isNotNull();
        assertThat(result.getTopic()).isEqualTo("TestTopic");
        assertThat(new String(result.getBody())).isEqualTo("Hello RocketMQ");
        assertThat(result.getKeys()).isEqualTo("test-key");

        // 验证等待时间大约为 1 秒（允许误差 ±200ms）
        assertThat(waitTime).isGreaterThanOrEqualTo(900L);
        assertThat(waitTime).isLessThan(1500L);

        // 验证 CountDownLatch 的计数已归零
        assertThat(future.getCountDownLatch().getCount()).isEqualTo(0);
    }

    /**
     * 测试超时场景
     * 场景：没有响应消息放入，等待超时后返回 null
     */
    @Test
    public void testWaitTimeout() throws Exception {
        // 创建 RequestResponseFuture，超时时间 3 秒
        final RequestResponseFuture future = new RequestResponseFuture(
            UUID.randomUUID().toString(),
            3000L,
            null
        );

        final long startTime = System.currentTimeMillis();

        // 主线程阻塞等待响应，但没有任何线程放入响应消息
        System.out.println("主线程开始等待（预期超时）...");
        Message result = future.waitResponseMessage(1000L);

        long waitTime = System.currentTimeMillis() - startTime;
        System.out.println("等待超时，实际等待时间: " + waitTime + " ms");

        // 验证超时后返回 null
        assertThat(result).isNull();

        // 验证等待时间大约为 1 秒（允许误差）
        assertThat(waitTime).isGreaterThanOrEqualTo(900L);
        assertThat(waitTime).isLessThan(1500L);

        // CountDownLatch 计数仍为 1（没有被 countDown）
        assertThat(future.getCountDownLatch().getCount()).isEqualTo(1);
    }

}
