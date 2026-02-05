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

    /**
     * Initialize all Prometheus metrics. Must be called once before using metrics.
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }

        computingLogicDuration = Histogram.build()
            .name("user_service_computing_logic_duration_seconds")
            .help("Duration of computing logic execution in seconds")
            .buckets(0.001, 0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0)
            .register();

        verifyUserDuration = Histogram.build()
            .name("client_verify_user_duration_seconds")
            .help("Duration of verifyUser RPC call including local getUser in seconds")
            .buckets(0.001, 0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0)
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
