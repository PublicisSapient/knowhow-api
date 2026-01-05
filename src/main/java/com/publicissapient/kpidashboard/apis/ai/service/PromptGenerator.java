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

package com.publicissapient.kpidashboard.apis.ai.service;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.errors.EntityNotFoundException;
import com.publicissapient.kpidashboard.common.constant.PromptKeys;
import com.publicissapient.kpidashboard.common.model.application.PromptDetails;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@Component
public class PromptGenerator {
	private final CacheService cacheService;

	public PromptDetails getPromptDetails(String key) throws EntityNotFoundException {
		return Optional.ofNullable(cacheService.getPromptDetails().get(key))
				.orElseThrow(() -> new EntityNotFoundException(PromptDetails.class, "promptKey", key));
	}

	public String getKpiRecommendationPrompt(Map<String, Object> kpiDataByProject, String userRole)
			throws EntityNotFoundException {
		PromptDetails analysisReport = getPromptDetails(PromptKeys.KPI_CORRELATION_ANALYSIS_REPORT);
		PromptDetails kpiRecommendationPrompt = getPromptDetails(PromptKeys.KPI_RECOMMENDATION_PROMPT);

		return kpiRecommendationPrompt
				.toString()
				.replace("ANALYSIS_REPORT_PLACEHOLDER", analysisReport.toString())
				.replace("KPI_DATA_BY_PROJECT_PLACEHOLDER", kpiDataByProject.toString())
				.replace("USER_ROLE_PLACEHOLDER", userRole);
	}

	public String getKpiSearchPrompt(String userQuery) throws EntityNotFoundException {
		PromptDetails kpiData = getPromptDetails(PromptKeys.KPI_DATA);
		PromptDetails searchBase = getPromptDetails(PromptKeys.KPI_SEARCH);
		return searchBase
				.toString()
				.replace("KPI_DATA", kpiData.toString())
				.replace("USER_QUERY", userQuery);
	}
}
