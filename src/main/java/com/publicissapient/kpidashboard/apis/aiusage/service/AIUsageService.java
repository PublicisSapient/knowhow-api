/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *    http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.aiusage.service;

import com.publicissapient.kpidashboard.apis.aiusage.dto.AIUsageStatisticsDTO;
import com.publicissapient.kpidashboard.apis.aiusage.dto.AIUsageStatisticsResponse;
import com.publicissapient.kpidashboard.apis.aiusage.dto.AIUsageSummary;
import com.publicissapient.kpidashboard.apis.aiusage.enums.HierarchyLevelType;
import com.publicissapient.kpidashboard.apis.aiusage.model.AIUsageStatistics;
import com.publicissapient.kpidashboard.apis.aiusage.repository.AIUsageStatisticsRepository;
import com.publicissapient.kpidashboard.apis.errors.EntityNotFoundException;
import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceImpl;
import com.publicissapient.kpidashboard.apis.model.AccountFilterRequest;
import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import org.apache.coyote.BadRequestException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@AllArgsConstructor
public class AIUsageService {

	private final AIUsageStatisticsRepository aiUsageStatisticsRepository;
	private final AccountHierarchyServiceImpl accountHierarchyServiceImpl;

	/**
	 * Retrieves AI usage statistics for the hierarchy level specified by {@code levelName},
	 * imited to the hierarchy elements the user has access to. For each matching item,
	 * this method builds an {@link AIUsageStatistics} object based on the hierarchy type:
	 * business unit ("bu"), vertical ("ver"), or account ("acc").
	 * <p>
	 * Entries that result in {@code null} statistics are skipped.
	 * </p>
	 *
	 * @param levelName     the hierarchy level name to filter by (e.g., "bu", "ver", "acc");
	 *                      comparison is case-insensitive.
	 * @param includeUsers  whether to include user-level statistics (currently not used in logic,
	 *                      but reserved for future functionality).
	 * @param startDate     the beginning of the date range filter (inclusive). May be null.
	 * @param endDate       the end of the date range filter (inclusive). May be null.
	 * @param pageable      pagination information for controlling result size (currently unused).
	 *
	 * @return a list of {@link AIUsageStatistics} for hierarchy levels the user is allowed to access.
	 *
	 * @throws IllegalArgumentException if an unknown hierarchy level is encountered.
	 */
	public AIUsageStatisticsResponse getAIUsageStats(String levelName, LocalDate startDate,
												   LocalDate endDate, Boolean includeUsers, Pageable pageable) throws EntityNotFoundException, BadRequestException {
		validateLevelName(levelName);
		validateDates(startDate, endDate);

		Set<AccountFilteredData> hierarchyDataUserHasAccessTo = fetchHierarchiesForCurrentLevel(levelName);

		List<AIUsageStatistics> responseList = new ArrayList<>();
		for (AccountFilteredData hierarchy : hierarchyDataUserHasAccessTo) {
			if (hierarchy.getLabelName() != null) {
				switch (hierarchy.getLabelName()) {
					case "bu" -> {
						AIUsageStatistics buStats = constructResponseForBusinessUnit(hierarchy, startDate, endDate);
						if (buStats != null) {
							responseList.add(buStats);
						}
					}
					case "ver" -> {
						AIUsageStatistics verStats = constructResponseForVertical(hierarchy, startDate, endDate);
						if (verStats != null) {
							responseList.add(verStats);
						}
					}
					case "acc" -> {
						AIUsageStatistics accStats = constructResponseForAccount(hierarchy, startDate, endDate);
						if (accStats != null) {
							responseList.add(accStats);
						}
					}
					default -> throw new IllegalArgumentException("Unknown hierarchy level: " + hierarchy.getLabelName());
				}
			}
		}
		if (responseList.isEmpty()) {
			throw new EntityNotFoundException(AIUsageStatistics.class);
		}

		List< AIUsageStatisticsDTO> responseDTOList = responseList.stream()
				.map(AIUsageStatisticsDTO::new)
				.toList();

		if (responseList.size() < hierarchyDataUserHasAccessTo.size()) {
			return new AIUsageStatisticsResponse(responseDTOList, true);
		}
		return new AIUsageStatisticsResponse(responseDTOList, false);
	}

	private Set<AccountFilteredData> fetchHierarchiesForCurrentLevel(String levelName) {
		AccountFilterRequest accountFilterRequest = new AccountFilterRequest();
		accountFilterRequest.setKanban(false);
		accountFilterRequest.setSprintIncluded(List.of(CommonConstant.CLOSED.toUpperCase()));

		return accountHierarchyServiceImpl.getFilteredList(accountFilterRequest).stream()
				.filter(data -> data.getLabelName().equalsIgnoreCase(levelName))
				.collect(Collectors.toSet());
	}

	private void validateLevelName(String levelName) throws BadRequestException {
		if (!List.of("bu", "ver", "acc").contains(levelName.toLowerCase())) {
			throw new BadRequestException("Invalid levelName: " + levelName + ". Expected values are 'bu', 'ver', or 'acc'.");
		}
	}

	private void validateDates(LocalDate startDate, LocalDate endDate) throws BadRequestException {
		if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
			throw new BadRequestException("startDate cannot be after endDate.");
		}
	}

	private AIUsageStatistics constructResponseForAccount(AccountFilteredData hierarchy,  LocalDate startDate,
														  LocalDate endDate) {
		String nodeName = hierarchy.getNodeName();
		if (nodeName == null) {
			return null;
		}
		if (startDate != null && endDate != null) {
			return aiUsageStatisticsRepository.findTop1ByLevelNameAndStatsDateBetweenOrderByIngestTimestampDesc(
					nodeName, startDate, endDate);
		}
		log.info("Fetching AI usage statistics for account level: {}", nodeName);
		return aiUsageStatisticsRepository.findTop1ByLevelNameAndLevelTypeOrderByIngestTimestampDesc(
				nodeName, HierarchyLevelType.ACCOUNT.getDisplayName());
	}

	private Set<AccountFilteredData> fetchHierarchyDataUserHasAccessToForParentId(String nodeId) {
		AccountFilterRequest accountFilterRequest = new AccountFilterRequest();
		accountFilterRequest.setKanban(false);
		accountFilterRequest.setSprintIncluded(List.of(CommonConstant.CLOSED.toUpperCase()));

		return accountHierarchyServiceImpl.getFilteredList(accountFilterRequest).stream()
						.filter(data -> data.getParentId() != null 
								&& data.getParentId().equalsIgnoreCase(nodeId))
				.collect(Collectors.toSet());
	}

	private AIUsageStatistics aggregateForLevel(
			AccountFilteredData node,
			String levelType,
			Function<AccountFilteredData, AIUsageStatistics> childResolver
	) {
		Set<AccountFilteredData> children = fetchHierarchyDataUserHasAccessToForParentId(node.getNodeId());

		List<AIUsageStatistics> statsList = children.stream()
				.map(childResolver)
				.filter(Objects::nonNull)
				.toList();

		AIUsageSummary summary = aggregateSummaries(statsList);
		if (summary.getUserCount() == 0) {
			return null;
		}

		AIUsageStatistics aggregated = new AIUsageStatistics();
		aggregated.setLevelType(levelType);
		aggregated.setLevelName(node.getNodeName());
		aggregated.setUsageSummary(summary);
		return aggregated;
	}

	private AIUsageStatistics constructResponseForVertical(AccountFilteredData vertical, LocalDate startDate,
														   LocalDate endDate) {
		return aggregateForLevel(
				vertical,
				HierarchyLevelType.VERTICAL.name(),
				child -> constructResponseForAccount(child, startDate, endDate)
		);
	}

	private AIUsageStatistics constructResponseForBusinessUnit(AccountFilteredData businessUnit, LocalDate startDate,
															   LocalDate endDate) {
		return aggregateForLevel(
				businessUnit,
				HierarchyLevelType.BUSINESS_UNIT.name(),
				child -> constructResponseForVertical(child, startDate, endDate)
		);
	}

	private AIUsageSummary aggregateSummaries(List<AIUsageStatistics> statsList) {
		long totalLoc = 0L;
		long totalPrompts = 0L;
		long totalUsers = 0L;
		long totalOther = 0L;

		for (AIUsageStatistics stats : statsList) {
			if (stats == null) {
				continue;
			}
			AIUsageSummary summary = stats.getUsageSummary();
			if (summary != null) {
				totalLoc += valueOrZero(summary.getTotalLocGenerated());
				totalPrompts += valueOrZero(summary.getTotalPrompts());
				totalUsers += valueOrZero(summary.getUserCount());
				totalOther += valueOrZero(summary.getOtherMetrics());
			}
		}
		return new AIUsageSummary(totalLoc, totalPrompts, totalUsers, totalOther, null);
	}

	private Long valueOrZero(Long value) {
		return value == null ? 0L : value;
	}
}