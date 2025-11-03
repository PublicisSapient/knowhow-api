/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

package com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.factory;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.enums.SprintMetricType;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.strategy.SprintMetricStrategy;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/** Factory to get appropriate SprintMetricStrategy based on SprintMetricType */
@Component
@RequiredArgsConstructor
public class SprintMetricStrategyFactory {

	private final List<SprintMetricStrategy> strategies;
	private final Map<SprintMetricType, SprintMetricStrategy> strategyMap = new EnumMap<>(SprintMetricType.class);

	/** Initialize the strategy map */
	@PostConstruct
	public void init() {
		strategies.forEach(strategy -> strategyMap.put(strategy.getMetricType(), strategy));
	}

	/**
	 * Get strategy for a specific metric type
	 *
	 * @param metricType
	 *            Sprint metric type
	 * @return SprintMetricStrategy implementation
	 * @throws IllegalArgumentException
	 *             if no strategy found for metric type
	 */
	public SprintMetricStrategy getStrategy(SprintMetricType metricType) {
		SprintMetricStrategy strategy = strategyMap.get(metricType);
		if (strategy == null) {
			throw new IllegalArgumentException(String.format("No strategy found for metric type: %s", metricType));
		}
		return strategy;
	}

	/**
	 * Get all enabled metric types based on enum configuration
	 *
	 * @return List of enabled metric types
	 */
	public List<SprintMetricType> getEnabledMetricTypes() {
		return Arrays.stream(SprintMetricType.values()).filter(SprintMetricType::isEnabled).toList();
	}
}
