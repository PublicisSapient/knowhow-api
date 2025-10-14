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

package com.publicissapient.kpidashboard.apis.analysis.analytics.shared.utils;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;

import com.publicissapient.kpidashboard.apis.analysis.analytics.shared.dto.BaseAnalyticsRequestDTO;
import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AnalyticsValidationUtils {

	public static void validateBaseAnalyticsComputationRequest(BaseAnalyticsRequestDTO baseAnalyticsRequestDTO,
			List<AccountFilteredData> projectsDataCurrentUserHasAccessTo) {
		if (Objects.isNull(baseAnalyticsRequestDTO)) {
			throw new BadRequestException("The AI usage request cannot be null");
		}

		if (CollectionUtils.isEmpty(projectsDataCurrentUserHasAccessTo)) {
			throw new ForbiddenException("Current user doesn't have access to any projects");
		}

		Set<String> projectBasicConfigIds = baseAnalyticsRequestDTO.getProjectBasicConfigIds();
		if (CollectionUtils.isNotEmpty(projectBasicConfigIds)) {
			Set<String> basicProjectConfigIdsUserHasAccessTo = projectsDataCurrentUserHasAccessTo.stream()
					.filter(accountFilteredData -> Objects.nonNull(accountFilteredData.getBasicProjectConfigId())
							&& projectBasicConfigIds.contains(accountFilteredData.getBasicProjectConfigId().toString()))
					.map(accountFilteredData -> accountFilteredData.getBasicProjectConfigId().toString())
					.collect(Collectors.toSet());

			Set<String> invalidProjectBasicConfigIds = projectBasicConfigIds.stream().filter(
					projectBasicConfigId -> !basicProjectConfigIdsUserHasAccessTo.contains(projectBasicConfigId))
					.collect(Collectors.toSet());

			if (CollectionUtils.isNotEmpty(invalidProjectBasicConfigIds)) {
				throw new BadRequestException(String.format(
						"The current user doesn't have access to the project "
								+ "basic configs [%s] or they are not related to any project",
						String.join(",", invalidProjectBasicConfigIds)));
			}
		}
	}
}
