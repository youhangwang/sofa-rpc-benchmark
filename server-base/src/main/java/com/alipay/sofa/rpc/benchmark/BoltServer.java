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

import com.alipay.sofa.rpc.benchmark.metrics.PrometheusMetrics;
import com.alipay.sofa.rpc.benchmark.service.UserService;
import com.alipay.sofa.rpc.benchmark.service.UserServiceServerImpl;
import com.alipay.sofa.rpc.config.ProviderConfig;
import com.alipay.sofa.rpc.config.ServerConfig;
import io.prometheus.client.exporter.HTTPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class BoltServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(BoltServer.class);

    public static void main(String[] args) {
        PrometheusMetrics.init();

        String port = System.getProperty("server.port", "12200");
        ServerConfig serverConfig;
        ProviderConfig<UserService> providerConfig;
        serverConfig = new ServerConfig()
            .setProtocol("bolt")
            .setHost("0.0.0.0")
            .setPort(Integer.parseInt(port));

        providerConfig = new ProviderConfig<UserService>()
            .setInterfaceId(UserService.class.getName())
            .setRef(new UserServiceServerImpl())
            .setServer(serverConfig);

        providerConfig.export();

        HTTPServer prometheusServer = null;
        String prometheusPort = System.getProperty("prometheus.port", "9092");
        try {
            prometheusServer = new HTTPServer(Integer.parseInt(prometheusPort));
            LOGGER.info("Prometheus metrics server started on port " + prometheusPort);
            LOGGER.info("Access metrics at: http://localhost:" + prometheusPort + "/metrics");
        } catch (IOException e) {
            LOGGER.error("Failed to start Prometheus HTTP server: " + e.getMessage(), e);
        }
    }
}