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

import java.lang.reflect.Field;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ThreadLocalIndexTest {
    @Test
    public void testIncrementAndGet() throws Exception {
        ThreadLocalIndex localIndex = new ThreadLocalIndex();
        int initialVal = localIndex.incrementAndGet();

        assertThat(localIndex.incrementAndGet()).isEqualTo(initialVal + 1); // 调用两次递增验证，第二次  = 第一次 + 1
    }

    @Test
    public void testIncrementAndGet2() throws Exception {
        ThreadLocalIndex localIndex = new ThreadLocalIndex();
        int initialVal = localIndex.incrementAndGet();
        assertThat(initialVal >= 0).isTrue(); // 验证 incrementAndGet 获取的值大于0
    }

    @Test
    public void testIncrementAndGet3() throws Exception {
        ThreadLocalIndex localIndex = new ThreadLocalIndex();
        Field threadLocalIndexField = ThreadLocalIndex.class.getDeclaredField("threadLocalIndex");
        ThreadLocal<Integer> mockThreadLocal = new ThreadLocal<Integer>();
        mockThreadLocal.set(Integer.MAX_VALUE);

        threadLocalIndexField.setAccessible(true);
        threadLocalIndexField.set(localIndex, mockThreadLocal);

        int initialVal = localIndex.incrementAndGet();
        System.out.println(initialVal);
//        assertThat(initialVal >= 0).isTrue();

        /**
         *   1. 整数溢出是环形的：MAX + 1 = MIN
         *   2. Math.abs() 对 MIN_VALUE 失效：这是 Java 的已知陷阱
         *   3. 位掩码是可靠的解决方案：& 0x7FFFFFFF 强制清除符号位，确保非负
         */
        System.out.println(Integer.MAX_VALUE); // 2147483647
        System.out.println(Integer.MAX_VALUE+1); // -2147483648 = Integer.MIN_VALUE
        System.out.println(Math.abs(Integer.MAX_VALUE+1)); // -2147483648 存在  Math.abs(Integer.MIN_VALUE) 还是  Integer.MIN_VALUE 的陷阱
        System.out.println(Integer.MAX_VALUE+1 & 0x7FFFFFFF); // 0

        System.out.println(Integer.MAX_VALUE + 2); // 整数溢出变成负数 -2147483647
    }

}