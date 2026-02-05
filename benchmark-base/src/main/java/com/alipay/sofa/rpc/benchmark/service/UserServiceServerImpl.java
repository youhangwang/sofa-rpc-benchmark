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

    // Prometheus histogram metric for computing logic execution time
    private static final Histogram computingLogicDuration = Histogram
                                                              .build()
                                                              .name("user_service_computing_logic_duration_seconds")
                                                              .help("Duration of computing logic execution in seconds")
                                                              .buckets(0.001, 0.005, 0.01, 0.025, 0.05, 0.075, 0.1,
                                                                  0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0)
                                                              .register();

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

    public User getUserById(long id, int resumeSize) {
        User user = new User();
        user.setId(id);
        user.setName("Doug Lea");
        user.setSex(1);
        user.setBirthday(LocalDate.of(1968, 12, 8));
        user.setEmail("dong.lea@gmail.com");
        user.setMobile("18612345678");
        user.setAddress("北京市 中关村 中关村大街1号 鼎好大厦 1605");
        user.setIcon("https://www.baidu.com/img/bd_logo1.png");
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(user.getCreateTime());
        List<Integer> permissions = new ArrayList<>(
                Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 19, 88, 86, 89, 90, 91, 92));
        user.setPermissions(permissions);

        // add computing logic - with Prometheus timing
        Histogram.Timer computingTimer = computingLogicDuration.startTimer();
        try {
            int size = 1024 * 1024;
            double[] doubleList = new double[size];
            for (int i = 0; i < size; i++) {
                doubleList[i] = i * 0.1;
            }
            CRC32 crc = new CRC32();
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
            doubleList = new double[1];
            doubleList[0] = result;
            user.setDoubleList(doubleList);
        } finally {
            computingTimer.observeDuration();
        }

        Map<String, Object> resume = new HashMap<>();
        StringBuilder notes = new StringBuilder();
        for (int i = 0; i < resumeSize; i++) {
            notes.append("a");
        }
        resume.put("mark", notes.toString());
        user.setResume(resume);
        return user;
    }
}
