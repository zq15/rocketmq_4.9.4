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

package org.apache.rocketmq.client.common;

import java.util.Random;

// 线程级别的索引计数器,主要应用于消息队列的轮询选择,实现负载均衡
public class ThreadLocalIndex {
    private final ThreadLocal<Integer> threadLocalIndex = new ThreadLocal<Integer>();
    private final Random random = new Random();
    private final static int POSITIVE_MASK = 0x7FFFFFFF;

    public int incrementAndGet() {
        Integer index = this.threadLocalIndex.get();
        if (null == index) {
            index = Math.abs(random.nextInt()); // 实现随机初始化
            this.threadLocalIndex.set(index);
        }

        this.threadLocalIndex.set(++index);
        return Math.abs(index & POSITIVE_MASK); // 避免了 Math.abs() 对 Integer.MAX_VALUE + 1 处理不当的陷阱
//        return Math.abs(index);
    }

    @Override
    public String toString() {
        return "ThreadLocalIndex{" +
            "threadLocalIndex=" + threadLocalIndex.get() +
            '}';
    }
}
