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

package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.strategy;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.enums.KPICode;

@Component
public class KpiStrategyRegistry {

	private final Map<String, KpiCalculationStrategy<?>> strategies;

	@Autowired
	public KpiStrategyRegistry(List<KpiCalculationStrategy<?>> strategyList) {
		this.strategies =
				strategyList.stream()
						.collect(
								Collectors.toMap(KpiCalculationStrategy::getStrategyType, Function.identity()));
	}

	@SuppressWarnings("unchecked")
	public <T> KpiCalculationStrategy<T> getStrategy(KPICode kpiCode, String chartType) {
		String strategyKey = buildStrategyKey(kpiCode, chartType);
		KpiCalculationStrategy<?> strategy = strategies.get(strategyKey);

		if (strategy == null) {
			throw new IllegalArgumentException(
					"No strategy found for KPI: " + kpiCode + " with chart type: " + chartType);
		}

		return (KpiCalculationStrategy<T>) strategy;
	}

	private String buildStrategyKey(KPICode kpiCode, String chartType) {
		String trendType = "line".equalsIgnoreCase(chartType) ? "TREND" : "NON_TREND";
		return kpiCode.name() + "_" + trendType;
	}
}
