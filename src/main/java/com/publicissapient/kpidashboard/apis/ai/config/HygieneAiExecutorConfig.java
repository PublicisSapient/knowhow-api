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
 * Shared thread pool used by AI-driven KPIs (Story Hygiene, KPI recommendations, search-KPI, etc.)
 * to fan out concurrent chat-completion calls to the AI gateway.
 *
 * <p>All AI calls are I/O-bound — each thread blocks on the network for up to 150 s (OkHttp
 * callTimeout in AiGatewayConfig) waiting for the LLM. For I/O-bound work the pool must be sized to
 * expected concurrency, not CPU count. Default: 50 core threads handles ~10 concurrent users each
 * requesting 5 sprints simultaneously.
 *
 * <p>Queue capacity is intentionally small (10) so that new threads are created promptly once the
 * core pool is full, rather than tasks piling up behind a handful of blocked platform threads.
 *
 * <p>NOTE: when this codebase moves to Java 21, replace this entire bean with {@code
 * Executors.newVirtualThreadPerTaskExecutor()} — virtual threads park during I/O at zero cost and
 * make pool sizing irrelevant.
 *
 * <p>Tune via {@code hygiene.ai.executor.*} properties in {@code application.properties}.
 */
@Configuration
public class HygieneAiExecutorConfig {

	public static final String HYGIENE_AI_EXECUTOR = "hygieneAiExecutor";

	@Bean(name = HYGIENE_AI_EXECUTOR)
	public Executor hygieneAiExecutor(
			@Value("${hygiene.ai.executor.core-pool-size:50}") int corePoolSize,
			@Value("${hygiene.ai.executor.max-pool-size:100}") int maxPoolSize,
			@Value("${hygiene.ai.executor.queue-capacity:10}") int queueCapacity,
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
