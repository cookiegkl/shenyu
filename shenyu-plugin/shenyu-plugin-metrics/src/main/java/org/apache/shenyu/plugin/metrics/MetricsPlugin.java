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

package org.apache.shenyu.plugin.metrics;

import org.apache.shenyu.common.constant.Constants;
import org.apache.shenyu.common.enums.PluginEnum;
import org.apache.shenyu.common.utils.DateUtils;
import org.apache.shenyu.plugin.api.ShenyuPlugin;
import org.apache.shenyu.plugin.api.ShenyuPluginChain;
import org.apache.shenyu.plugin.api.context.ShenyuContext;
import org.apache.shenyu.plugin.metrics.constant.LabelNames;
import org.apache.shenyu.plugin.metrics.reporter.MetricsReporter;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * the monitor plugin.
 *
 * @author XY
 */
public class MetricsPlugin implements ShenyuPlugin {

    @Override
    public Mono<Void> execute(final ServerWebExchange exchange, final ShenyuPluginChain chain) {
        MetricsReporter.counterIncrement(LabelNames.REQUEST_TOTAL);
        ShenyuContext shenyuContext = exchange.getAttribute(Constants.CONTEXT);
        Objects.requireNonNull(shenyuContext);
        setMetricsCallbacks(exchange);
        String path = exchange.getRequest().getURI().getRawPath();
        ServerHttpResponse response = exchange.getResponse();
        MetricsReporter.counterIncrement(LabelNames.REQUEST_TYPE_TOTAL, new String[]{path, shenyuContext.getRpcType()});
        LocalDateTime startDateTime = Optional.of(shenyuContext).map(ShenyuContext::getStartDateTime).orElseGet(LocalDateTime::now);
        return chain.execute(exchange).doOnSuccess(e -> responseCommitted(response, startDateTime, path)).doOnError(throwable -> {
            MetricsReporter.counterIncrement(LabelNames.REQUEST_THROW_TOTAL);
            responseCommitted(response, startDateTime, path);
        });
    }

    private void setMetricsCallbacks(final ServerWebExchange exchange) {
        exchange.getAttributes().put(Constants.METRICS_SENTINEL, (Consumer<HttpStatus>) status -> {
            if (Objects.equals(HttpStatus.TOO_MANY_REQUESTS, status)) {
                MetricsReporter.counterIncrement(LabelNames.SENTINEL_REQUEST_RESTRICT_TOTAL);
            } else if (Objects.equals(HttpStatus.INTERNAL_SERVER_ERROR, status)) {
                MetricsReporter.counterIncrement(LabelNames.SENTINEL_REQUEST_CIRCUITBREAKER_TOTAL);
            }
        });
        exchange.getAttributes().put(Constants.METRICS_RESILIENCE4J, (Consumer<HttpStatus>) status -> {
            if (Objects.equals(HttpStatus.TOO_MANY_REQUESTS, status)) {
                MetricsReporter.counterIncrement(LabelNames.RESILIENCE4J_REQUEST_RESTRICT_TOTAL);
            } else if (Objects.equals(HttpStatus.INTERNAL_SERVER_ERROR, status)) {
                MetricsReporter.counterIncrement(LabelNames.RESILIENCE4J_REQUEST_CIRCUITBREAKER_TOTAL);
            }
        });
        exchange.getAttributes().put(Constants.METRICS_HYSTRIX, (Consumer<HttpStatus>) status -> {
            if (Objects.equals(HttpStatus.INTERNAL_SERVER_ERROR, status)) {
                MetricsReporter.counterIncrement(LabelNames.HYSTRIX_REQUEST_CIRCUITBREAKER_TOTAL);
            }
        });
        exchange.getAttributes().put(Constants.METRICS_RATE_LIMITER, (Consumer<HttpStatus>) status -> {
            if (Objects.nonNull(status) && HttpStatus.TOO_MANY_REQUESTS.equals(status)) {
                MetricsReporter.counterIncrement(LabelNames.RATELIMITER_REQUEST_RESTRICT_TOTAL);
            }
        });
    }

    @Override
    public int getOrder() {
        return PluginEnum.METRICS.getCode();
    }

    @Override
    public String named() {
        return PluginEnum.METRICS.getName();
    }

    private void responseCommitted(ServerHttpResponse response, final LocalDateTime startDateTime, final String path) {
        if (response.isCommitted()) {
            recordTime(startDateTime, path);
        } else {
            response.beforeCommit(() -> {
                recordTime(startDateTime, path);
                return Mono.empty();
            });
        }
    }

    private void recordTime(final LocalDateTime startDateTime, final String path) {
        long millisBetween = DateUtils.acquireMillisBetween(startDateTime, LocalDateTime.now());
        MetricsReporter.recordTime(LabelNames.EXECUTE_LATENCY_NAME, millisBetween);
        MetricsReporter.recordTime(LabelNames.EXECUTE_LATENCY_NAME_PATH, new String[]{path}, millisBetween);
    }
}
