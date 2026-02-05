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
package com.alipay.sofa.rpc.benchmark.metrics;

import io.prometheus.client.Histogram;

/**
 * Centralized Prometheus metrics registry.
 * Call {@link #init()} once from main() before using any metrics.
 */
public class PrometheusMetrics {

    private static volatile boolean initialized = false;

    // Histogram for computing logic execution time in UserServiceServerImpl
    public static Histogram         computingLogicDuration;

    // Histogram for client-side verifyUser RPC call duration
    public static Histogram         verifyUserDuration;

    // Histogram for total duration
    public static Histogram         TotalDuration;

    /**
     * Initialize all Prometheus metrics. Must be called once before using metrics.
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }

        TotalDuration = Histogram.build()
            .name("total_duration_seconds")
            .help("Duration of total in seconds")
            .buckets(0.01, 0.015, 0.02, 0.025, 0.03, 0.035, 0.04, 0.045, 0.05, 0.06, 0.07, 0.08, 0.09, 0.1, 0.2, 0.3)
            .register();

        computingLogicDuration = Histogram.build()
            .name("user_service_computing_logic_duration_seconds")
            .help("Duration of computing logic execution in seconds")
            .buckets(0.01, 0.015, 0.02, 0.025, 0.03, 0.035, 0.04, 0.045, 0.05, 0.06, 0.07, 0.08, 0.09, 0.1, 0.2, 0.3)
            .register();

        verifyUserDuration = Histogram
            .build()
            .name("client_verify_user_duration_seconds")
            .help("Duration of verifyUser RPC call including local getUser in seconds")
            .buckets(0.001, 0.002, 0.003, 0.004, 0.005, 0.006, 0.007, 0.008, 0.009, 0.010, 0.015, 0.025, 0.035, 0.040,
                0.060, 0.075, 0.100, 0.150, 0.200, 0.500)
            .register();

        initialized = true;
    }

    /**
     * Check if metrics have been initialized.
     */
    public static boolean isInitialized() {
        return initialized;
    }
}