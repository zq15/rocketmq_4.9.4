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

package org.apache.rocketmq.common;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ServiceThreadTest {

    @Test
    public void testShutdown() {
        /**
         * 非守护线程的服务，一些关键服务，需要join等待
         * 不需要中断 重平衡服务
         * // client/src/main/java/org/apache/rocketmq/client/impl/consumer/RebalanceService.java:39
         *   while (!this.isStopped()) {
         *       this.waitForRunning(waitInterval);  // 等待 20 秒
         *       this.mqClientFactory.doRebalance();
         *   }
         *   阻塞在 io 上的服务，需要中断 拉取消息服务
         * client/src/main/java/org/apache/rocketmq/client/impl/consumer/PullMessageService.java
         *   @Override
         *   public void run() {
         *       while (!this.isStopped()) {
         *           PullRequest pullRequest = this.pullRequestQueue.take();  // ← 阻塞在队列上！
         *           this.pullMessage(pullRequest);
         *       }
         *   }
         * 守护线程的服务，后台清理的一些服务
         */
        shutdown(false, false);
        shutdown(false, true);
        shutdown(true, false); // 监控统计服务
        shutdown(true, true); // 后台日志清理、统计上报
    }

    @Test
    public void testStop() {
        stop(true);
        stop(false);
    }

    @Test
    public void testMakeStop() {
        ServiceThread testServiceThread = startTestServiceThread();
        testServiceThread.makeStop();
        assertEquals(true, testServiceThread.isStopped());
    }

    @Test
    public void testWakeup() {
        ServiceThread testServiceThread = startTestServiceThread();
        testServiceThread.wakeup(); // 修改 notified，countDown
        assertEquals(true, testServiceThread.hasNotified.get());
        assertEquals(0, testServiceThread.waitPoint.getCount());
    }

    @Test
    public void testWaitForRunning() {
        ServiceThread testServiceThread = startTestServiceThread();
        // test waitForRunning
        testServiceThread.waitForRunning(1000); // 阻塞等待 1s count 1
        assertEquals(false, testServiceThread.hasNotified.get());
        assertEquals(1, testServiceThread.waitPoint.getCount());
        // test wake up
        testServiceThread.wakeup(); // 唤醒 countDown 此时 count 0
        assertEquals(true, testServiceThread.hasNotified.get());
        assertEquals(0, testServiceThread.waitPoint.getCount());
        // repeat waitForRunning
        testServiceThread.waitForRunning(1000); // 已修改过，直接返回，并且 modified 设置回 1
        assertEquals(false, testServiceThread.hasNotified.get());
        assertEquals(0, testServiceThread.waitPoint.getCount());
        // repeat waitForRunning again
        testServiceThread.waitForRunning(1000); // 阻塞等待
        assertEquals(false, testServiceThread.hasNotified.get());
        assertEquals(1, testServiceThread.waitPoint.getCount());
    }

    private ServiceThread startTestServiceThread() {
        return startTestServiceThread(false);
    }

    private ServiceThread startTestServiceThread(boolean daemon) {
        ServiceThread testServiceThread = new ServiceThread() {

            @Override
            public void run() {
                doNothing();
            }

            private void doNothing() {}

            @Override
            public String getServiceName() {
                return "TestServiceThread";
            }
        };
        testServiceThread.setDaemon(daemon); // false
        // test start
        testServiceThread.start();
        assertEquals(false, testServiceThread.isStopped()); // isStopped false
        return testServiceThread;
    }

    public void shutdown(boolean daemon, boolean interrupt) {
        ServiceThread testServiceThread = startTestServiceThread(daemon);
        shutdown0(interrupt, testServiceThread);
        // repeat 测试幂等关闭
        shutdown0(interrupt, testServiceThread);
    }

    private void shutdown0(boolean interrupt, ServiceThread testServiceThread) {
        if (interrupt) {
            testServiceThread.shutdown(true); // 带中断参数
        } else {
            testServiceThread.shutdown();
        }
        assertEquals(true, testServiceThread.isStopped());
        assertEquals(true, testServiceThread.hasNotified.get());
        assertEquals(0, testServiceThread.waitPoint.getCount());
    }

    public void stop(boolean interrupt) {
        ServiceThread testServiceThread = startTestServiceThread();
        stop0(interrupt, testServiceThread);
        // repeat
        stop0(interrupt, testServiceThread);
    }

    private void stop0(boolean interrupt, ServiceThread testServiceThread) {
        if (interrupt) {
            testServiceThread.stop(true);
        } else {
            testServiceThread.stop();
        }
        assertEquals(true, testServiceThread.isStopped());
        assertEquals(true, testServiceThread.hasNotified.get());
        assertEquals(0, testServiceThread.waitPoint.getCount());
    }

}
