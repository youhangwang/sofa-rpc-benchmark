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
package com.alipay.sofa.rpc.benchmark.service;

import com.alipay.sofa.rpc.benchmark.bean.Page;
import com.alipay.sofa.rpc.benchmark.bean.User;
import com.alipay.sofa.rpc.benchmark.metrics.PrometheusMetrics;
import com.alipay.sofa.common.utils.StringUtil;

import io.prometheus.client.Histogram;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.CRC32;

public class UserServiceServerImpl implements UserService {

    // 可重用的对象，避免每次调用都创建
    private static final int DOUBLE_LIST_SIZE = 1024 * 1024;
    private static final ThreadLocal<double[]> DOUBLE_LIST_HOLDER = ThreadLocal.withInitial(
            () -> new double[DOUBLE_LIST_SIZE]);
    private static final ThreadLocal<CRC32> CRC32_HOLDER = ThreadLocal.withInitial(CRC32::new);
    private static final List<Integer> PERMISSIONS = Arrays.asList(
            1, 2, 3, 4, 5, 6, 7, 8, 19, 88, 86, 89, 90, 91, 92);

    @Override
    public boolean existUser(String email) {
        Random random = new Random();
        int rand = 0;
        int count = 100000;

        for (int i = 0; i < count; i++) {
            int randomNumber = random.nextInt(5);
            if (randomNumber % 2 == 1) {
                rand += 1;
            } else {
                rand -= 1;
            }
        }
        if (rand % 2 == 0) {
            return true;
        }
        if (email == null || email.isEmpty()) {
            return true;
        }

        if (email.charAt(email.length() - 1) < '5') {
            return false;
        }

        return true;
    }

    @Override
    public User getUser(long id) {
        String requestSize = System.getProperty("request.size");
        String resumeSize = StringUtil.isNotBlank(requestSize) ? requestSize : "1";
        return getUserById(id, Integer.parseInt(resumeSize));
    }

    @Override
    public Page<User> listUser(int pageNo) {
        List<User> userList = new ArrayList<>(15);

        for (int i = 0; i < 15; i++) {
            User user = getUserById(i, 1);
            userList.add(user);
        }

        Page<User> page = new Page<>();
        page.setPageNo(pageNo);
        page.setTotal(1000);
        page.setResult(userList);
        return page;
    }

    @Override
    public boolean createUser(User user) {
        return user != null;
    }

    @Override
    public User verifyUser(User user) {
        return user;
    }

    // 静态常量，避免每次调用都创建
    private static final LocalDate USER_BIRTHDAY = LocalDate.of(1968, 12, 8);
    private static final String USER_NAME = "Doug Lea";
    private static final String USER_EMAIL = "dong.lea@gmail.com";
    private static final String USER_MOBILE = "18612345678";
    private static final String USER_ADDRESS = "北京市 中关村 中关村大街1号 鼎好大厦 1605";
    private static final String USER_ICON = "https://www.baidu.com/img/bd_logo1.png";

    public User getUserById(long id, int resumeSize) {
        User user = new User();
        user.setId(id);
        user.setName(USER_NAME);
        user.setSex(1);
        user.setBirthday(USER_BIRTHDAY);
        user.setEmail(USER_EMAIL);
        user.setMobile(USER_MOBILE);
        user.setAddress(USER_ADDRESS);
        user.setIcon(USER_ICON);
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(user.getCreateTime());
        // 复用静态权限列表，避免每次创建新的ArrayList
        user.setPermissions(PERMISSIONS);

        // add computing logic - with Prometheus timing
        Histogram.Timer computingTimer = PrometheusMetrics.computingLogicDuration.startTimer();
        try {
            // 使用ThreadLocal重用double数组，避免每次创建8MB数组
            double[] doubleList = DOUBLE_LIST_HOLDER.get();
            int size = DOUBLE_LIST_SIZE;
            for (int i = 0; i < size; i++) {
                doubleList[i] = i * 0.1;
            }
            // 使用ThreadLocal重用CRC32对象
            CRC32 crc = CRC32_HOLDER.get();
            crc.reset();
            int iterations = 850;
            double result = 0;
            int start = 0;

                for (int i = 0; i < iterations; i++) {
                    for (int j = start; j < start + 1024; j++) {
                        int index = start % size;
                        long doubleAsLong = Double.doubleToLongBits(doubleList[index]);
                        crc.update((int) (doubleAsLong & 0xFF));
                        crc.update((int) ((doubleAsLong >> 8) & 0xFF));
                        crc.update((int) ((doubleAsLong >> 16) & 0xFF));
                        crc.update((int) ((doubleAsLong >> 24) & 0xFF));
                        crc.update((int) ((doubleAsLong >> 32) & 0xFF));
                        crc.update((int) ((doubleAsLong >> 40) & 0xFF));
                        crc.update((int) ((doubleAsLong >> 48) & 0xFF));
                        crc.update((int) ((doubleAsLong >> 56) & 0xFF));

                        // 算术运算
                        double value = doubleList[index];
                        double value2 = doubleList[(index + 1) % size];
                        value = (value + 2.0) * 1.5 / 2.0;
                        value2 = value2 * 1.1 + 0.5 / value2;
                        doubleList[index] = (value + value2) / 2;
                    }
                    start = (start + 1024) % size;
                }

            // 重用数组，只设置第一个元素，避免创建新数组
            doubleList[0] = result;
            // 创建一个新的1元素数组来返回给User对象
            double[] resultArray = new double[1];
            resultArray[0] = result;
            user.setDoubleList(resultArray);
        } finally {
            computingTimer.observeDuration();
        }

        // 对于resume，当resumeSize相同时可以重用，但由于resumeSize是参数，每次都不同
        // 所以仍然需要创建新的Map和StringBuilder，但可以通过capacity优化
        Map<String, Object> resume = new HashMap<>(2);
        StringBuilder notes = new StringBuilder(resumeSize);
        for (int i = 0; i < resumeSize; i++) {
            notes.append('a');
        }
        resume.put("mark", notes.toString());
        user.setResume(resume);
        return user;
    }
}