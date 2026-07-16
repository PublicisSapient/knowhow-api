/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.ai.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Shared thread pool used by AI-driven KPIs (Project Hygiene, KPI recommendations,
 * search-KPI, etc.) to fan out concurrent chat-completion calls to the AI gateway.
 *
 * <p>All AI calls are I/O-bound (each one blocks on the network for seconds to
 * minutes waiting for the LLM), so we size the pool generously but bound the
 * queue so bursts don't accumulate unbounded. The {@link ThreadPoolExecutor.CallerRunsPolicy
 * caller-runs} rejection policy provides natural back-pressure — when the queue
 * is full, the submitting thread runs the task inline, slowing down the producer.
 *
 * <p>Pool defaults are conservative (5/20/100). Tune via
 * {@code hygiene.ai.executor.*} properties in {@code application.properties}.
 */
@Configuration
public class HygieneAiExecutorConfig {

    public static final String HYGIENE_AI_EXECUTOR = "hygieneAiExecutor";

    @Bean(name = HYGIENE_AI_EXECUTOR)
    public Executor hygieneAiExecutor(
            @Value("${hygiene.ai.executor.core-pool-size:5}") int corePoolSize,
            @Value("${hygiene.ai.executor.max-pool-size:20}") int maxPoolSize,
            @Value("${hygiene.ai.executor.queue-capacity:100}") int queueCapacity,
            @Value("${hygiene.ai.executor.keep-alive-seconds:60}") int keepAliveSeconds) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix("hygiene-ai-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        // Back-pressure: when the queue is full, the calling thread runs the
        // task itself instead of throwing RejectedExecutionException.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}


