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
package com.alipay.sofa.rpc.benchmark;

import com.alipay.sofa.rpc.benchmark.bean.Page;
import com.alipay.sofa.rpc.benchmark.bean.User;
import com.alipay.sofa.rpc.benchmark.client.AbstractClient;
import com.alipay.sofa.rpc.benchmark.metrics.PrometheusMetrics;
import com.alipay.sofa.rpc.benchmark.service.UserService;
import com.alipay.sofa.rpc.benchmark.utils.JMHHelper;
import com.alipay.sofa.common.utils.StringUtil;
import com.alipay.sofa.rpc.config.ConsumerConfig;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.prometheus.client.Histogram;
import io.prometheus.client.exporter.HTTPServer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;

import java.time.Duration;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
public class BoltClient extends AbstractClient {

    private static final Logger               LOGGER      = LoggerFactory.getLogger(BoltClient.class);

    private static int                        CONCURRENCY = 32;

    private final UserService                 userService;

    private final ConsumerConfig<UserService> consumerConfig;

    private HTTPServer                        prometheusServer;

    private static PrometheusMeterRegistry    micrometerRegistry;

    public BoltClient() {
        // Ensure Prometheus metrics are initialized (needed in forked JMH processes)
        PrometheusMetrics.init();

        String port = System.getProperty("server.port", "12200");
        String threadNum = System.getProperty("thread.num");
        if (StringUtil.isNotBlank(threadNum)) {
            CONCURRENCY = Integer.parseInt(threadNum);
        }
        String server = System.getProperty("server.host");
        consumerConfig = new ConsumerConfig<UserService>()
            .setRepeatedReferLimit(10)
            .setInterfaceId(UserService.class.getName()) // 指定接口
            .setProtocol("bolt") // 指定协议
            .setDirectUrl("bolt://" + server + ":" + port) // 指定直连地址
            .setTimeout(4000);
        // 生成代理类
        userService = consumerConfig.refer();
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected UserService getUserService() {
        return userService;
    }

    @Setup
    public void setup() {
        // Create Micrometer registry with histogram percentiles configuration
        PrometheusConfig prometheusConfig = new PrometheusConfig() {
            @Override
            public Duration step() {
                return Duration.ofMinutes(1);
            }

            @Override
            public String get(String key) {
                return null;
            }
        };

        // Configure distribution statistics for GC pause metrics BEFORE creating registry
        // Use custom SLA buckets optimized for GC pause times
        // Based on observed P50(1ms), P90/P95(2ms), P99(4ms)
        // IMPORTANT: This filter must be registered BEFORE JvmGcMetrics to take effect
        MeterFilter gcPauseMeterFilter = new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                if (id.getName().startsWith("jvm.gc.pause")) {
                    // SLA buckets in seconds for GC pause histogram
                    // 0.5ms, 1ms, 2ms, 3ms, 4ms, 5ms, 7ms, 10ms, 15ms, 20ms, 30ms, 50ms, 100ms
                    // Note: explicitly specify as double seconds values
                    double[] buckets = new double[] {
                        0.0005,0.0007,0.0009, 0.00095,0.001, 0.002, 0.003, 0.004, 0.005,
                        0.007, 0.01
                    };
                    long[] slaNanos = new long[buckets.length];
                    for (int i = 0; i < buckets.length; i++) {
                        slaNanos[i] = (long) (buckets[i] * 1_000_000_000L);
                    }
                    return DistributionStatisticConfig.builder()
                        .sla(slaNanos)
                        .build();
                }
                return config;
            }
        };

        micrometerRegistry = new PrometheusMeterRegistry(prometheusConfig,
            io.prometheus.client.CollectorRegistry.defaultRegistry,
            io.micrometer.core.instrument.Clock.SYSTEM);

        // Register the meter filter BEFORE registering JvmGcMetrics
        micrometerRegistry.config().meterFilter(gcPauseMeterFilter);

        // Register JVM metrics including GC pause metrics with histogram enabled
        new JvmGcMetrics().bindTo(micrometerRegistry);
        new JvmMemoryMetrics().bindTo(micrometerRegistry);
        new JvmThreadMetrics().bindTo(micrometerRegistry);
        LOGGER.info("Micrometer JVM metrics registered including GC pause with histogram enabled");

        // Start Prometheus server that serves metrics from default registry
        String prometheusPort = System.getProperty("prometheus.port", "9090");
        try {
            prometheusServer = new HTTPServer(Integer.parseInt(prometheusPort));
            LOGGER.info("Prometheus metrics server started on port " + prometheusPort);
            LOGGER.info("Access metrics at: http://localhost:" + prometheusPort + "/metrics");
        } catch (IOException e) {
            LOGGER.error("Failed to start Prometheus HTTP server: " + e.getMessage(), e);
        }
    }

    @TearDown
    public void close() {
        if (prometheusServer != null) {
            prometheusServer.stop();
            LOGGER.info("Prometheus metrics server stopped");
        }
        consumerConfig.unRefer();
    }

    // @Benchmark
    @BenchmarkMode({ Mode.Throughput, Mode.AverageTime, Mode.SampleTime })
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Override
    public boolean existUser() throws Exception {
        return super.existUser();
    }

    // @Benchmark
    @BenchmarkMode({ Mode.Throughput, Mode.AverageTime, Mode.SampleTime })
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Override
    public boolean createUser() throws Exception {
        return super.createUser();
    }

    // @Benchmark
    @BenchmarkMode({ Mode.Throughput, Mode.AverageTime, Mode.SampleTime })
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Override
    public User getUser() throws Exception {
        return super.getUser();
    }

    // @Benchmark
    @BenchmarkMode({ Mode.Throughput, Mode.AverageTime, Mode.SampleTime })
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Override
    public Page<User> listUser() throws Exception {
        return super.listUser();
    }

    @Benchmark
    @BenchmarkMode({ Mode.Throughput, Mode.AverageTime, Mode.SampleTime })
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Override
    public User verifyUser() throws Exception {
        Histogram.Timer timer = PrometheusMetrics.TotalDuration.startTimer();
        try {
            return super.verifyUser();
        } finally {
            timer.observeDuration();
        }
    }

    public static void main(String[] args) throws Exception {
        // Initialize Prometheus metrics (must be called once before use)
        PrometheusMetrics.init();

        LOGGER.info(Arrays.toString(args));
        int concurrency = CONCURRENCY;
        String threadNum = System.getProperty("thread.num");
        if (StringUtil.isNotBlank(threadNum)) {
            concurrency = Integer.parseInt(threadNum);
        }
        ChainedOptionsBuilder optBuilder = JMHHelper.newBaseChainedOptionsBuilder(args)
            .include(BoltClient.class.getSimpleName())
            .threads(concurrency)
            .forks(1);

        Options opt = optBuilder.build();
        new Runner(opt).run();
    }
}