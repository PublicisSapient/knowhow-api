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

package com.publicissapient.kpidashboard.apis.analysis.analytics.aiusage.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.InternalServerErrorException;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.analysis.analytics.aiusage.dto.AiUsageAnalyticsDTO;
import com.publicissapient.kpidashboard.apis.analysis.analytics.aiusage.dto.AiUsageAnalyticsRequestDTO;
import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceImpl;
import com.publicissapient.kpidashboard.apis.model.AccountFilterRequest;
import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;

import io.micrometer.common.util.StringUtils;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiUsageAnalyticsServiceImpl implements AiUsageAnalyticsService {

	private static final int HIERARCHY_LEVEL_PROJECT = 5;

	private final AccountHierarchyServiceImpl accountHierarchyServiceImpl;

	@Override
	public ServiceResponse computeAiUsageAnalyticsData(AiUsageAnalyticsRequestDTO aiUsageAnalyticsRequestDTO) {
		List<AccountFilteredData> projectsDataCurrentUserHasAccessTo = getHierarchyDataCurrentUserHasAccessTo(
				HIERARCHY_LEVEL_PROJECT, CommonConstant.HIERARCHY_LEVEL_ID_PROJECT);
		validateComputeAiUsageAnalyticsRequest(aiUsageAnalyticsRequestDTO, projectsDataCurrentUserHasAccessTo);

		ServiceResponse serviceResponse = new ServiceResponse();
		serviceResponse.setData(computeAiUsageAnalytics(aiUsageAnalyticsRequestDTO, projectsDataCurrentUserHasAccessTo));

		return serviceResponse;
	}

	private AiUsageAnalyticsDTO computeAiUsageAnalytics(
			AiUsageAnalyticsRequestDTO aiUsageAnalyticsRequestDTO,
			List<AccountFilteredData> projectsDataCurrentUserHasAccessTo
	) {
		Set<String> projectBasicConfigIds = new HashSet<>();

		if (CollectionUtils.isEmpty(aiUsageAnalyticsRequestDTO.getProjectBasicConfigIds())) {
			projectBasicConfigIds = projectsDataCurrentUserHasAccessTo.stream()
					.map(accountFilteredData -> accountFilteredData.getBasicProjectConfigId().toString())
					.collect(Collectors.toSet());
		}



		return AiUsageAnalyticsDTO.builder()
				.build();
	}

	private void validateComputeAiUsageAnalyticsRequest(AiUsageAnalyticsRequestDTO aiUsageAnalyticsRequestDTO,
			List<AccountFilteredData> projectsDataCurrentUserHasAccessTo) {
		if (Objects.isNull(aiUsageAnalyticsRequestDTO)) {
			throw new BadRequestException("The AI usage request cannot be null");
		}

		if (CollectionUtils.isEmpty(projectsDataCurrentUserHasAccessTo)) {
			throw new ForbiddenException("Current user doesn't have access to any projects");
		}

		Set<String> projectBasicConfigIds = aiUsageAnalyticsRequestDTO.getProjectBasicConfigIds();
		if (CollectionUtils.isNotEmpty(projectBasicConfigIds)) {
			Set<String> invalidProjectBasicConfigIds = new HashSet<>();
			Set<String> validProjectBasicConfigIds = new HashSet<>();

			projectsDataCurrentUserHasAccessTo.forEach(accountFilteredData -> {
				if (Objects.nonNull(accountFilteredData.getBasicProjectConfigId())) {
					String basicProjectConfigIdAsString = accountFilteredData.getBasicProjectConfigId().toString();
					if (projectBasicConfigIds.contains(basicProjectConfigIdAsString)) {
						validProjectBasicConfigIds.add(basicProjectConfigIdAsString);
					} else {
						invalidProjectBasicConfigIds.add(basicProjectConfigIdAsString);
					}
				}
			});
			if (CollectionUtils.isNotEmpty(invalidProjectBasicConfigIds)) {
				throw new BadRequestException(String.format(
						"The current user doesn't have access to the project "
								+ "basic configs [%s] or they are not related to any project",
						String.join(",", invalidProjectBasicConfigIds)));
			}
			if (validProjectBasicConfigIds.size() != projectBasicConfigIds.size()) {
				throw new InternalServerErrorException("Could not process the AI usage analytics request. Projects "
						+ "data contains invalid entries");
			}
		}
	}

	private List<AccountFilteredData> getHierarchyDataCurrentUserHasAccessTo(int level, String label) {
		if (StringUtils.isEmpty(label)) {
			return Collections.emptyList();
		}
		AccountFilterRequest accountFilterRequest = new AccountFilterRequest();
		accountFilterRequest.setKanban(false);
		accountFilterRequest.setSprintIncluded(List.of("CLOSED"));

		return accountHierarchyServiceImpl.getFilteredList(accountFilterRequest).stream()
				.filter(accountFilteredData -> Objects.nonNull(accountFilteredData)
						&& level == accountFilteredData.getLevel()
						&& label.equalsIgnoreCase(accountFilteredData.getLabelName()))
				.toList();
	}
}
