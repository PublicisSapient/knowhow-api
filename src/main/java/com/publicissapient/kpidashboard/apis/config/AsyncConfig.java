/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/
package com.publicissapient.kpidashboard.apis.config;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class AsyncConfig {
	private ThreadPoolTaskExecutor scrumExecutor;
	private ThreadPoolTaskExecutor kanbanExecutor;

	@Bean(name = "scrumExecutiveTaskExecutor")
	public Executor scrumExecutiveTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		int processors = Runtime.getRuntime().availableProcessors();
		executor.setCorePoolSize(processors * 2); // was processors
		executor.setMaxPoolSize(processors * 4); // was processors * 2
		executor.setQueueCapacity(1000);
		executor.setThreadNamePrefix("ScrumExecutive-");
		executor.initialize();
		this.scrumExecutor = executor;
		return executor;
	}

	@Bean(name = "kanbanExecutiveTaskExecutor")
	public Executor kanbanExecutiveTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		int processors = Runtime.getRuntime().availableProcessors();
		executor.setCorePoolSize(processors);
		executor.setMaxPoolSize(processors * 2);
		executor.setQueueCapacity(200);
		executor.setThreadNamePrefix("KanbanExecutive-");
		executor.initialize();
		this.kanbanExecutor = executor;
		return executor;
	}

	@PreDestroy
	public void onDestroy() {
		shutdownExecutor(scrumExecutor, "ScrumExecutive");
		shutdownExecutor(kanbanExecutor, "KanbanExecutive");
	}

	private void shutdownExecutor(ThreadPoolTaskExecutor executor, String name) {
		if (executor != null) {
			executor.shutdown();
			try {
				executor.getThreadPoolExecutor().awaitTermination(30, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.info(name + " executor did not terminate gracefully");
			}
		}
	}

}
