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

package com.publicissapient.kpidashboard.apis.productivity.service;

import static com.publicissapient.kpidashboard.apis.productivity.config.ProductivityGainConfig.CATEGORY_EFFICIENCY;
import static com.publicissapient.kpidashboard.apis.productivity.config.ProductivityGainConfig.CATEGORY_PRODUCTIVITY;
import static com.publicissapient.kpidashboard.apis.productivity.config.ProductivityGainConfig.CATEGORY_QUALITY;
import static com.publicissapient.kpidashboard.apis.productivity.config.ProductivityGainConfig.CATEGORY_SPEED;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.bitbucket.service.BitBucketServiceR;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.enums.FieldMappingEnum;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.errors.EntityNotFoundException;
import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceImpl;
import com.publicissapient.kpidashboard.apis.jenkins.service.JenkinsServiceR;
import com.publicissapient.kpidashboard.apis.jira.service.JiraServiceR;
import com.publicissapient.kpidashboard.apis.jira.service.iterationdashboard.JiraIterationServiceR;
import com.publicissapient.kpidashboard.apis.model.AccountFilterRequest;
import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;
import com.publicissapient.kpidashboard.apis.model.IssueKpiModalValue;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.productivity.config.ProductivityGainConfig;
import com.publicissapient.kpidashboard.apis.productivity.dto.CategorizedProductivityGain;
import com.publicissapient.kpidashboard.apis.productivity.dto.KPITrendDTO;
import com.publicissapient.kpidashboard.apis.productivity.dto.KPITrendsDTO;
import com.publicissapient.kpidashboard.apis.productivity.dto.ProductivityGainCalculationRequestDTO;
import com.publicissapient.kpidashboard.apis.productivity.dto.ProductivityGainDTO;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.application.KpiMaster;

import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.InternalServerErrorException;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductivityGainServiceImpl implements ProductivityGainService {

	private static final int HIERARCHY_LEVEL_ACCOUNT = 3;
	private static final int HIERARCHY_LEVEL_PROJECT = 5;
	private static final int HIERARCHY_LEVEL_SPRINT = 6;

	private static final double WEEK_WEIGHT = 1.0D;
	private static final double SPRINT_WEIGHT = 2.0D;
	private static final double TWO_DECIMAL_ROUNDING_COEFFICIENT = 100.0D;
	private static final double PERCENTAGE_MULTIPLIER = 100.0D;

	private static final String SPRINT_STATUS_CLOSED = "CLOSED";
	private static final String SPRINT_STATUS_ACTIVE = "ACTIVE";

	@Getter
	@Builder
	@SuppressWarnings("java:S2972")
	private static final class KPIConfiguration {
		private double weightInProductivityScoreCalculation;

		private String kpiName;
		private String dataCountGroupFilterUsedForCalculation;
		private String dataCountGroupFilter1UsedForCalculation;
		private String dataCountGroupFilter2UsedForCalculation;

		private KPICode kpiCode;

		private ProcessorType processorType;

		private PositiveGainTrend positiveGainTrend;

		private XAxisMeasurement xAxisMeasurement;

		private SupportedKPIHierarchyLevelAggregation supportedKPIHierarchyLevelAggregation;

		private enum XAxisMeasurement {
			SPRINTS,
			WEEKS,
			ITERATION
		}

		private enum PositiveGainTrend {
			ASCENDING,
			DESCENDING
		}

		private enum ProcessorType {
			JIRA,
			JENKINS,
			JIRA_ITERATION,
			BITBUCKET
		}

		private enum SupportedKPIHierarchyLevelAggregation {
			ALL,
			PROJECT,
			SPRINT
		}
	}

	@Getter
	@Builder
	private static final class KPIGainTrendCalculationData {
		private double dataPointGainWeightSumProduct;
		private double weightParts;

		private String kpiName;
	}

	private final ProductivityGainConfig productivityGainConfig;

	private final JiraServiceR jiraServiceR;
	private final JenkinsServiceR jenkinsServiceR;
	private final JiraIterationServiceR jiraIterationServiceR;
	private final BitBucketServiceR bitBucketServiceR;

	private final CacheService cacheService;
	private final ConfigHelperService configHelperService;

	private final AccountHierarchyServiceImpl accountHierarchyServiceImpl;

	private Map<String, Map<String, KPIConfiguration>> categoryKpiIdConfigurationMap;

	@PostConstruct
	private void initializeConfiguration() {
		categoryKpiIdConfigurationMap = constructCategoryKpiIdConfigurationMap();
	}

	@Override
	public ServiceResponse processProductivityCalculationRequest(
			ProductivityGainCalculationRequestDTO productivityGainCalculationRequestDTO) {
		if (CollectionUtils.isNotEmpty(productivityGainConfig.getConfigValidationIssues())) {
			log.error(
					"The following config validations errors occurred: {}",
					String.join(CommonConstant.COMMA, productivityGainConfig.getConfigValidationIssues()));
			throw new InternalServerErrorException(
					"Could not process the productivity calculation request due to "
							+ "incorrect service configuration");
		}
		Set<String> hierarchyNodeIds =
				getNodeIdsCurrentUserHasAccessToByHierarchyLevel(productivityGainCalculationRequestDTO);

		Map<String, Set<String>> projectNodeIdSprintNodeIdsMap =
				constructProjectAndSprintNodeIdsMapForIterationKPIProductivityCalculation(
						productivityGainCalculationRequestDTO.getParentId());

		Map<KPIConfiguration.ProcessorType, List<KpiRequest>> processorTypeKpiRequestsMap =
				constructProcessorTypeKpiRequestsMapForAllProductivityCalculationMetrics(
						productivityGainCalculationRequestDTO.getLevel(),
						productivityGainCalculationRequestDTO.getLabel(),
						hierarchyNodeIds,
						projectNodeIdSprintNodeIdsMap);

		List<KpiElement> kpiElementList = processAllKpiRequests(processorTypeKpiRequestsMap);
		return new ServiceResponse(
				true,
				"Productivity gain was successfully calculated",
				calculateProductivityGain(projectNodeIdSprintNodeIdsMap, kpiElementList));
	}

	@SuppressWarnings("java:S3776")
	private List<KpiElement> processAllKpiRequests(
			Map<KPIConfiguration.ProcessorType, List<KpiRequest>> processorTypeKpiRequestsMap) {
		List<KpiElement> kpiElementList = new ArrayList<>();
		for (Map.Entry<KPIConfiguration.ProcessorType, List<KpiRequest>> processorTypeKpiRequestsEntry :
				processorTypeKpiRequestsMap.entrySet()) {
			switch (processorTypeKpiRequestsEntry.getKey()) {
				case JIRA ->
						processorTypeKpiRequestsEntry
								.getValue()
								.forEach(
										kpiRequest -> {
											try {
												kpiElementList.addAll(
														cloneKpiElementsFromProcessorResponse(
																jiraServiceR.process(kpiRequest)));
											} catch (EntityNotFoundException e) {
												log.error("Processing Jira KPI Requests failed {}", e.getMessage());
											}
										});
				case JENKINS ->
						processorTypeKpiRequestsEntry
								.getValue()
								.forEach(
										kpiRequest -> {
											try {
												kpiElementList.addAll(
														cloneKpiElementsFromProcessorResponse(
																jenkinsServiceR.process(kpiRequest)));
											} catch (EntityNotFoundException e) {
												log.error("Processing Jenkins KPI Requests failed {}", e.getMessage());
											}
										});
				case BITBUCKET ->
						processorTypeKpiRequestsEntry
								.getValue()
								.forEach(
										kpiRequest -> {
											try {
												kpiElementList.addAll(
														cloneKpiElementsFromProcessorResponse(
																bitBucketServiceR.process(kpiRequest)));
											} catch (EntityNotFoundException e) {
												log.error("Processing Bitbucket KPI Requests failed {}", e.getMessage());
											}
										});
				case JIRA_ITERATION ->
						processorTypeKpiRequestsEntry
								.getValue()
								.forEach(
										kpiRequest -> {
											try {
												kpiElementList.addAll(
														cloneKpiElementsFromProcessorResponse(
																jiraIterationServiceR.process(kpiRequest)));
											} catch (EntityNotFoundException e) {
												log.error(
														"Processing Jira iteration KPI Requests failed {}", e.getMessage());
											}
										});
			}
		}
		return kpiElementList;
	}

	private Map<String, List<KPIGainTrendCalculationData>>
			constructCategoryBasedKPIGainTrendCalculationDataMap(
					Map<String, List<KpiElement>> kpiIdKpiElementsMap,
					Map<String, Set<String>> projectNodeIdSprintNodeIdsMap) {
		Map<String, List<KPIGainTrendCalculationData>> categoryBasedKPIGainTrendCalculationDataMap =
				new HashMap<>();
		for (String kpiCategory : productivityGainConfig.getAllConfiguredCategories()) {
			categoryBasedKPIGainTrendCalculationDataMap.put(
					kpiCategory,
					constructGainTrendCalculationDataForAllKPIsInCategory(
							kpiIdKpiElementsMap, projectNodeIdSprintNodeIdsMap, kpiCategory));
		}
		return categoryBasedKPIGainTrendCalculationDataMap;
	}

	private ProductivityGainDTO calculateProductivityGain(
			Map<String, Set<String>> projectNodeIdSprintNodeIdsMap,
			List<KpiElement> kpisFromAllCategories) {
		Map<String, List<KpiElement>> kpiIdKpiElementsMap =
				kpisFromAllCategories.stream().collect(Collectors.groupingBy(KpiElement::getKpiId));

		Map<String, List<KPIGainTrendCalculationData>> categoryBasedKPIGainTrendCalculationData =
				constructCategoryBasedKPIGainTrendCalculationDataMap(
						kpiIdKpiElementsMap, projectNodeIdSprintNodeIdsMap);

		KPITrendsDTO kpiTrendsDTO =
				constructKPITrendsByCategoryBasedKPIGainTrendCalculationDataMap(
						categoryBasedKPIGainTrendCalculationData);

		ProductivityGainDTO productivityGainDTO = new ProductivityGainDTO();

		productivityGainDTO.setKpiTrends(kpiTrendsDTO);

		double speedGain =
				calculateCategorizedGain(categoryBasedKPIGainTrendCalculationData.get(CATEGORY_SPEED));
		double qualityGain =
				calculateCategorizedGain(categoryBasedKPIGainTrendCalculationData.get(CATEGORY_QUALITY));
		double productivityGain =
				calculateCategorizedGain(
						categoryBasedKPIGainTrendCalculationData.get(CATEGORY_PRODUCTIVITY));
		double efficiencyGain =
				calculateCategorizedGain(categoryBasedKPIGainTrendCalculationData.get(CATEGORY_EFFICIENCY));

		double overallGain =
				(speedGain * productivityGainConfig.getWeightForCategory(CATEGORY_SPEED))
						+ (qualityGain * productivityGainConfig.getWeightForCategory(CATEGORY_QUALITY))
						+ (productivityGain
								* productivityGainConfig.getWeightForCategory(CATEGORY_PRODUCTIVITY))
						+ (efficiencyGain * productivityGainConfig.getWeightForCategory(CATEGORY_EFFICIENCY));

		double overallGainRounded =
				Math.round((overallGain) * TWO_DECIMAL_ROUNDING_COEFFICIENT)
						/ TWO_DECIMAL_ROUNDING_COEFFICIENT;

		CategorizedProductivityGain categorizedProductivityGain = new CategorizedProductivityGain();
		categorizedProductivityGain.setSpeed(speedGain);
		categorizedProductivityGain.setQuality(qualityGain);
		categorizedProductivityGain.setProductivity(productivityGain);
		categorizedProductivityGain.setEfficiency(efficiencyGain);
		categorizedProductivityGain.setOverall(overallGainRounded);

		productivityGainDTO.setCategorizedProductivityGain(categorizedProductivityGain);

		return productivityGainDTO;
	}

	private Map<String, Set<String>>
			constructProjectAndSprintNodeIdsMapForIterationKPIProductivityCalculation(String parentId) {
		AccountFilterRequest accountFilterRequest = new AccountFilterRequest();
		accountFilterRequest.setKanban(false);
		accountFilterRequest.setSprintIncluded(List.of(SPRINT_STATUS_CLOSED));
		Set<AccountFilteredData> hierarchyDataUserHasAccessTo =
				accountHierarchyServiceImpl.getFilteredList(accountFilterRequest);

		Set<AccountFilteredData> sprintDataSortedBySprintStartDateDescending;

		Map<String, Set<String>> projectNodeIdSprintNodeIdsMap = new HashMap<>();

		if (StringUtils.isEmpty(parentId)) {
			sprintDataSortedBySprintStartDateDescending =
					hierarchyDataUserHasAccessTo.stream()
							.filter(
									accountFilteredData ->
											accountFilteredData.getLevel() == HIERARCHY_LEVEL_SPRINT
													&& CommonConstant.HIERARCHY_LEVEL_ID_SPRINT.equalsIgnoreCase(
															accountFilteredData.getLabelName())
													&& StringUtils.isNotEmpty(accountFilteredData.getSprintEndDate())
													&& StringUtils.isNotEmpty(accountFilteredData.getSprintStartDate()))
							.filter(
									sprintFilteredData ->
											Instant.parse(sprintFilteredData.getSprintEndDate()).isBefore(Instant.now()))
							.collect(
									Collectors.toCollection(
											() ->
													new TreeSet<>(
															Comparator.comparing(
																			(AccountFilteredData sprintFilteredData) ->
																					Instant.parse(sprintFilteredData.getSprintStartDate()))
																	.reversed())));
		} else {
			sprintDataSortedBySprintStartDateDescending =
					computeSprintDataSortedBySprintStartDateDescendingBasedOnParentNodeId(
							parentId, hierarchyDataUserHasAccessTo);
		}
		sprintDataSortedBySprintStartDateDescending.forEach(
				sprintNodeData -> {
					projectNodeIdSprintNodeIdsMap.computeIfAbsent(
							sprintNodeData.getParentId(), v -> new LinkedHashSet<>());

					if (projectNodeIdSprintNodeIdsMap.get(sprintNodeData.getParentId()).size()
							< productivityGainConfig.getDataPoints().getCount()) {
						projectNodeIdSprintNodeIdsMap
								.get(sprintNodeData.getParentId())
								.add(sprintNodeData.getNodeId());
					}
				});

		projectNodeIdSprintNodeIdsMap.replaceAll(
				(projectNodeId, sprintNodeIdsSortedBySprintStartDateDescending) -> {
					List<String> sprintNodeIds =
							new ArrayList<>(sprintNodeIdsSortedBySprintStartDateDescending);
					Collections.reverse(sprintNodeIds);
					return new LinkedHashSet<>(sprintNodeIds);
				});

		return projectNodeIdSprintNodeIdsMap;
	}

	@SuppressWarnings("java:S1067")
	private Set<String> getNodeIdsCurrentUserHasAccessToByHierarchyLevel(
			ProductivityGainCalculationRequestDTO productivityGainCalculationRequestDTO) {
		AccountFilterRequest accountFilterRequest = new AccountFilterRequest();
		accountFilterRequest.setKanban(false);
		accountFilterRequest.setSprintIncluded(List.of(SPRINT_STATUS_CLOSED));

		Set<AccountFilteredData> hierarchyDataUserHasAccessTo =
				accountHierarchyServiceImpl.getFilteredList(accountFilterRequest);

		int requestedLevel = productivityGainCalculationRequestDTO.getLevel();
		String requestedLabel = productivityGainCalculationRequestDTO.getLabel();
		String requestedParentId = productivityGainCalculationRequestDTO.getParentId();

		validateCalculateProductivityRequest(
				productivityGainCalculationRequestDTO, hierarchyDataUserHasAccessTo);

		if (requestedParentNodeIdCorrespondsToProjectLevel(
				requestedParentId, hierarchyDataUserHasAccessTo)) {
			Set<AccountFilteredData> projectSprintsOrderedBySprintStartDateDescending =
					hierarchyDataUserHasAccessTo.stream()
							.filter(
									accountFilteredData ->
											requestedLevel == accountFilteredData.getLevel()
													&& requestedLabel.equalsIgnoreCase(accountFilteredData.getLabelName())
													&& (StringUtils.isEmpty(requestedParentId)
															|| requestedParentId.equalsIgnoreCase(
																	accountFilteredData.getParentId()))
													&& StringUtils.isNotEmpty(accountFilteredData.getSprintStartDate())
													&& StringUtils.isNotEmpty(accountFilteredData.getSprintEndDate()))
							.filter(
									sprintFilteredData ->
											Instant.parse(sprintFilteredData.getSprintEndDate()).isBefore(Instant.now()))
							.collect(
									Collectors.toCollection(
											() ->
													new TreeSet<>(
															Comparator.comparing(
																			(AccountFilteredData sprintFilteredData) ->
																					Instant.parse(sprintFilteredData.getSprintStartDate()))
																	.reversed())));

			Set<String> sprintNodeIds = new HashSet<>();
			for (AccountFilteredData sprintData : projectSprintsOrderedBySprintStartDateDescending) {
				if (sprintNodeIds.size() == productivityGainConfig.getDataPoints().getCount()) {
					return sprintNodeIds;
				} else {
					sprintNodeIds.add(sprintData.getNodeId());
				}
			}
		}

		return hierarchyDataUserHasAccessTo.stream()
				.filter(
						accountFilteredData ->
								requestedLevel == accountFilteredData.getLevel()
										&& requestedLabel.equalsIgnoreCase(accountFilteredData.getLabelName())
										&& (StringUtils.isEmpty(requestedParentId)
												|| requestedParentId.equalsIgnoreCase(accountFilteredData.getParentId())))
				.map(AccountFilteredData::getNodeId)
				.collect(Collectors.toUnmodifiableSet());
	}

	private void validateCalculateProductivityRequest(
			ProductivityGainCalculationRequestDTO productivityGainCalculationRequestDTO,
			Set<AccountFilteredData> hierarchyDataCurrentUserHasAccessTo) {
		List<HierarchyLevel> hierarchyLevels = cacheService.getFullHierarchyLevel();
		if (CollectionUtils.isEmpty(hierarchyLevels)) {
			throw new InternalServerErrorException("No hierarchy levels could be found");
		}
		if (CollectionUtils.isEmpty(hierarchyDataCurrentUserHasAccessTo)) {
			throw new ForbiddenException("Current user doesn't have access to any hierarchy data");
		}

		int level = productivityGainCalculationRequestDTO.getLevel();
		String label = productivityGainCalculationRequestDTO.getLabel();

		Optional<HierarchyLevel> requestedHierarchyLevelOptional =
				hierarchyLevels.stream()
						.filter(
								hierarchyLevel ->
										hierarchyLevel.getLevel() == level
												&& label.equalsIgnoreCase(hierarchyLevel.getHierarchyLevelId()))
						.findFirst();
		if (requestedHierarchyLevelOptional.isEmpty()) {
			throw new BadRequestException(
					String.format("No hierarchy entity was found for level %s and label %s", level, label));
		}
		HierarchyLevel requestedHierarchyLevel = requestedHierarchyLevelOptional.get();
		if (requestedHierarchyLevel.getLevel() < HIERARCHY_LEVEL_ACCOUNT
				|| requestedHierarchyLevel.getLevel() > HIERARCHY_LEVEL_SPRINT) {
			throw new BadRequestException(
					"The 'productivity' calculation supports only hierarchy entries between account and sprint");
		}
		String parentId = productivityGainCalculationRequestDTO.getParentId();
		if (StringUtils.isNotEmpty(parentId)) {
			boolean requestedLevelAndLabelCorrespondToTheFirstChildLevelOfTheParentId =
					hierarchyDataCurrentUserHasAccessTo.stream()
							.anyMatch(
									accountFilteredData ->
											parentId.equalsIgnoreCase(accountFilteredData.getParentId())
													&& accountFilteredData.getLevel() == level
													&& label.equalsIgnoreCase(accountFilteredData.getLabelName()));
			if (Boolean.FALSE.equals(requestedLevelAndLabelCorrespondToTheFirstChildLevelOfTheParentId)) {
				throw new BadRequestException(
						"""
								When providing a 'parentId' then the 'level' and 'label' from request body must correspond to the next child hierarchy level and label
								""");
			}
		}
	}

	private Map<KPIConfiguration.ProcessorType, List<KpiRequest>>
			constructProcessorTypeKpiRequestsMapForAllProductivityCalculationMetrics(
					int hierarchyLevel,
					String hierarchyLabel,
					Set<String> hierarchyNodeIds,
					Map<String, Set<String>> projectNodeIdSprintIdsMap) {

		Set<String> kpiIds =
				categoryKpiIdConfigurationMap.values().stream()
						.flatMap(value -> value.keySet().stream())
						.collect(Collectors.toSet());

		List<KpiMaster> kpiMasterList = new ArrayList<>();

		for (KpiMaster kpiMaster : configHelperService.loadKpiMaster()) {
			if (kpiIds.contains(kpiMaster.getKpiId())) {
				kpiMasterList.add(kpiMaster);
			}
		}

		Map<Integer, List<KpiMaster>> groupIdKpiMasterMap =
				kpiMasterList.stream().collect(Collectors.groupingBy(KpiMaster::getGroupId));

		return createProcessorTypeKpiRequestsMap(
				hierarchyLevel,
				hierarchyLabel,
				hierarchyNodeIds,
				groupIdKpiMasterMap,
				projectNodeIdSprintIdsMap);
	}

	private Map<KPIConfiguration.ProcessorType, List<KpiRequest>> createProcessorTypeKpiRequestsMap(
			int hierarchyLevel,
			String hierarchyLabel,
			Set<String> hierarchyNodeIds,
			Map<Integer, List<KpiMaster>> groupIdKpiMasterMap,
			Map<String, Set<String>> projectNodeIdSprintIdsMap) {
		Map<KPIConfiguration.ProcessorType, List<KpiRequest>> processorTypeKpiRequestsMap =
				new EnumMap<>(KPIConfiguration.ProcessorType.class);
		for (Map.Entry<Integer, List<KpiMaster>> entry : groupIdKpiMasterMap.entrySet()) {
			List<KpiElement> kpiElementList =
					entry.getValue().stream()
							.map(ProductivityGainServiceImpl::constructKpiElementFromKpiMaster)
							.toList();
			Optional<KPIConfiguration> kpiConfigurationOptional =
					getKpiConfigurationByKpiId(entry.getValue().get(0).getKpiId());
			if (kpiConfigurationOptional.isPresent()) {
				KPIConfiguration.ProcessorType processorType =
						kpiConfigurationOptional.get().getProcessorType();
				processorTypeKpiRequestsMap.computeIfAbsent(processorType, v -> new ArrayList<>());
				KPIConfiguration.XAxisMeasurement kpiGroupXAxisMeasurement =
						kpiConfigurationOptional.get().xAxisMeasurement;
				switch (kpiGroupXAxisMeasurement) {
					case WEEKS ->
							populateProcessorTypeKpiRequestsMapForWeekBasedKPIs(
									hierarchyLevel,
									hierarchyLabel,
									kpiConfigurationOptional.get(),
									hierarchyNodeIds,
									kpiElementList,
									projectNodeIdSprintIdsMap,
									processorTypeKpiRequestsMap);
					case SPRINTS -> {
						KpiRequest kpiRequest = new KpiRequest();
						kpiRequest.setSelectedMap(Map.of(hierarchyLabel, hierarchyNodeIds.stream().toList()));
						kpiRequest.setLevel(hierarchyLevel);
						kpiRequest.setLabel(hierarchyLabel);
						kpiRequest.setKpiList(kpiElementList);
						kpiRequest.setSprintIncluded(List.of(SPRINT_STATUS_CLOSED));
						kpiRequest.setIds(hierarchyNodeIds.toArray(new String[] {}));
						processorTypeKpiRequestsMap.get(processorType).add(kpiRequest);
					}
					case ITERATION ->
							populateProcessorTypeKpiRequestsMapForIterationKPIs(
									processorType,
									kpiElementList,
									projectNodeIdSprintIdsMap,
									processorTypeKpiRequestsMap);
				}
			}
		}
		return processorTypeKpiRequestsMap;
	}

	private void populateProcessorTypeKpiRequestsMapForWeekBasedKPIs(
			int hierarchyLevel,
			String hierarchyLabel,
			KPIConfiguration kpiConfiguration,
			Set<String> hierarchyNodeIds,
			List<KpiElement> kpiElementList,
			Map<String, Set<String>> projectNodeIdSprintIdsMap,
			Map<KPIConfiguration.ProcessorType, List<KpiRequest>> processorTypeKpiRequestsMap) {
		if (kpiConfiguration.getSupportedKPIHierarchyLevelAggregation()
				== KPIConfiguration.SupportedKPIHierarchyLevelAggregation.PROJECT) {
			projectNodeIdSprintIdsMap
					.keySet()
					.forEach(
							projectNodeId -> {
								KpiRequest kpiRequest = new KpiRequest();
								kpiRequest.setSelectedMap(
										Map.of(
												CommonConstant.HIERARCHY_LEVEL_ID_PROJECT,
												List.of(projectNodeId),
												CommonConstant.DATE,
												List.of("Weeks")));
								kpiRequest.setLevel(HIERARCHY_LEVEL_PROJECT);
								kpiRequest.setLabel(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT);
								kpiRequest.setKpiList(kpiElementList);
								kpiRequest.setSprintIncluded(List.of(SPRINT_STATUS_CLOSED));
								kpiRequest.setIds(
										new String[] {
											String.valueOf(productivityGainConfig.getDataPoints().getCount())
										});
								processorTypeKpiRequestsMap
										.get(kpiConfiguration.getProcessorType())
										.add(kpiRequest);
							});
		} else {
			KpiRequest kpiRequest = new KpiRequest();
			kpiRequest.setSelectedMap(
					Map.of(
							hierarchyLabel,
							hierarchyNodeIds.stream().toList(),
							CommonConstant.DATE,
							List.of("Weeks")));
			kpiRequest.setLevel(hierarchyLevel);
			kpiRequest.setLabel(hierarchyLabel);
			kpiRequest.setKpiList(kpiElementList);
			kpiRequest.setSprintIncluded(List.of(SPRINT_STATUS_CLOSED));
			kpiRequest.setIds(
					new String[] {String.valueOf(productivityGainConfig.getDataPoints().getCount())});
			processorTypeKpiRequestsMap.get(kpiConfiguration.getProcessorType()).add(kpiRequest);
		}
	}

	private static KPITrendsDTO constructKPITrendsByCategoryBasedKPIGainTrendCalculationDataMap(
			Map<String, List<KPIGainTrendCalculationData>> categoryBasedKPIGainTrendCalculationData) {
		KPITrendsDTO.KPITrendsDTOBuilder kpiTrendsDTOBuilder = KPITrendsDTO.builder();
		List<KPITrendDTO> positiveTrends = new ArrayList<>();
		List<KPITrendDTO> negativeTrends = new ArrayList<>();
		double trendValue;
		for (Map.Entry<String, List<KPIGainTrendCalculationData>>
				categoryBasedKpiGainTrendCalculationDataEntry :
						categoryBasedKPIGainTrendCalculationData.entrySet()) {
			for (KPIGainTrendCalculationData kpiGainTrendCalculationData :
					categoryBasedKpiGainTrendCalculationDataEntry.getValue()) {
				trendValue =
						Math.round(
										(kpiGainTrendCalculationData.getDataPointGainWeightSumProduct()
														/ kpiGainTrendCalculationData.getWeightParts())
												* TWO_DECIMAL_ROUNDING_COEFFICIENT)
								/ TWO_DECIMAL_ROUNDING_COEFFICIENT;
				KPITrendDTO kpiTrendDTO =
						KPITrendDTO.builder()
								.kpiCategory(categoryBasedKpiGainTrendCalculationDataEntry.getKey())
								.kpiName(kpiGainTrendCalculationData.getKpiName())
								.trendValue(trendValue)
								.build();
				if (kpiGainTrendCalculationData.getDataPointGainWeightSumProduct() > 0.0) {
					positiveTrends.add(kpiTrendDTO);
				} else {
					negativeTrends.add(kpiTrendDTO);
				}
			}
		}
		return kpiTrendsDTOBuilder.positive(positiveTrends).negative(negativeTrends).build();
	}

	@SuppressWarnings({"java:S3776", "java:S134"})
	private List<KPIGainTrendCalculationData> constructGainTrendCalculationDataForAllKPIsInCategory(
			Map<String, List<KpiElement>> kpiIdKpiElementsMap,
			Map<String, Set<String>> projectNodeIdSprintNodeIdsMap,
			String categoryName) {
		List<KPIGainTrendCalculationData> kpiGainTrendCalculationDataList = new ArrayList<>();
		int kpiWeightParts;
		for (Map.Entry<String, KPIConfiguration> kpiIdKpiConfigurationMapEntry :
				categoryKpiIdConfigurationMap.get(categoryName).entrySet()) {
			KPIConfiguration kpiConfiguration = kpiIdKpiConfigurationMapEntry.getValue();
			List<KpiElement> kpiData = kpiIdKpiElementsMap.get(kpiIdKpiConfigurationMapEntry.getKey());

			Map<Integer, List<Double>> kpiValuesByDataPointMap =
					constructKpiValuesByDataPointMap(
							kpiConfiguration, kpiData, projectNodeIdSprintNodeIdsMap);

			if (MapUtils.isNotEmpty(kpiValuesByDataPointMap)) {
				Optional<Map.Entry<Integer, List<Double>>> entryContainingTheBaseLineValue =
						kpiValuesByDataPointMap.entrySet().stream()
								.filter(
										entrySet ->
												Double.compare(
																entrySet.getValue().stream()
																		.mapToDouble(Double::doubleValue)
																		.average()
																		.orElse(0.0D),
																0.0D)
														!= 0)
								.findFirst();
				if (entryContainingTheBaseLineValue.isPresent()) {
					double kpiDataPointGainWeightSumProduct = 0.0D;
					double baseLineValue =
							entryContainingTheBaseLineValue.get().getValue().stream()
									.mapToDouble(Double::doubleValue)
									.average()
									.orElse(0.0D);

					kpiWeightParts =
							(int)
									(kpiValuesByDataPointMap.keySet().size()
											* kpiConfiguration.weightInProductivityScoreCalculation);

					for (Map.Entry<Integer, List<Double>> entry : kpiValuesByDataPointMap.entrySet()) {
						double average =
								entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0D);
						if (KPIConfiguration.PositiveGainTrend.ASCENDING
								== kpiConfiguration.positiveGainTrend) {
							kpiDataPointGainWeightSumProduct +=
									((average - baseLineValue) / baseLineValue)
											* PERCENTAGE_MULTIPLIER
											* kpiConfiguration.weightInProductivityScoreCalculation;
						} else {
							kpiDataPointGainWeightSumProduct +=
									((baseLineValue - average) / baseLineValue)
											* PERCENTAGE_MULTIPLIER
											* kpiConfiguration.weightInProductivityScoreCalculation;
						}
					}
					kpiGainTrendCalculationDataList.add(
							KPIGainTrendCalculationData.builder()
									.dataPointGainWeightSumProduct(kpiDataPointGainWeightSumProduct)
									.weightParts(kpiWeightParts)
									.kpiName(getKpiNameByKpiId(kpiIdKpiConfigurationMapEntry.getKey()))
									.build());
				}
			}
		}
		return kpiGainTrendCalculationDataList;
	}

	private static double calculateCategorizedGain(
			List<KPIGainTrendCalculationData> kpiGainTrendCalculationDataListForCategory) {
		if (CollectionUtils.isEmpty(kpiGainTrendCalculationDataListForCategory)) {
			return 0.0D;
		}
		int totalNumberOfParts = 0;
		double totalWeightSum = 0.0D;

		for (KPIGainTrendCalculationData kpiGainTrendCalculationData :
				kpiGainTrendCalculationDataListForCategory) {
			totalWeightSum += kpiGainTrendCalculationData.getDataPointGainWeightSumProduct();
			totalNumberOfParts += (int) kpiGainTrendCalculationData.getWeightParts();
		}

		if (totalNumberOfParts != 0) {
			return Math.round((totalWeightSum / totalNumberOfParts) * TWO_DECIMAL_ROUNDING_COEFFICIENT)
					/ TWO_DECIMAL_ROUNDING_COEFFICIENT;
		}
		return 0.0D;
	}

	@SuppressWarnings({"java:S3776", "java:S134"})
	private static Map<Integer, List<Double>> constructKpiValuesByDataPointMap(
			KPIConfiguration kpiConfiguration,
			List<KpiElement> kpiElementsFromProcessorResponse,
			Map<String, Set<String>> projectNodeIdSprintNodeIdsMap) {
		if (CollectionUtils.isNotEmpty(kpiElementsFromProcessorResponse)) {
			Map<Integer, List<Double>> kpiValuesByDataPointMap = new HashMap<>();

			if (kpiConfiguration.getXAxisMeasurement() == KPIConfiguration.XAxisMeasurement.ITERATION) {
				populateKpiValuesByDataPointMapForIterationBasedKpi(
						kpiValuesByDataPointMap,
						projectNodeIdSprintNodeIdsMap,
						kpiElementsFromProcessorResponse,
						kpiConfiguration.getKpiCode().getKpiId());
			} else {
				kpiElementsFromProcessorResponse.forEach(
						kpiElement -> {
							Object trendValuesObj = kpiElement.getTrendValueList();
							if (trendValuesObj instanceof List<?>) {
								List<?> trendValuesList = (List<?>) kpiElement.getTrendValueList();
								if (CollectionUtils.isNotEmpty(trendValuesList)) {
									if (trendValuesList.get(0) instanceof DataCount) {
										trendValuesList.forEach(
												trendValue -> {
													DataCount dataCount = (DataCount) trendValue;
													List<DataCount> dataValuesOfHierarchyEntity =
															(List<DataCount>) dataCount.getValue();
													for (int dataPoint = 0;
															dataPoint < dataValuesOfHierarchyEntity.size();
															dataPoint++) {
														kpiValuesByDataPointMap.computeIfAbsent(
																dataPoint, v -> new ArrayList<>());
														kpiValuesByDataPointMap
																.get(dataPoint)
																.add(
																		((Number) dataValuesOfHierarchyEntity.get(dataPoint).getValue())
																				.doubleValue());
													}
												});
									} else if (trendValuesList.get(0) instanceof DataCountGroup) {
										List<DataCountGroup> overallDataCountGroups =
												trendValuesList.stream()
														.filter(
																trendValue ->
																		dataCountGroupMatchesFiltersSetForOverallProductivityGainCalculation(
																				trendValue, kpiConfiguration))
														.map(DataCountGroup.class::cast)
														.toList();
										if (CollectionUtils.isNotEmpty(overallDataCountGroups)) {
											overallDataCountGroups.forEach(
													overallDataCountGroup ->
															overallDataCountGroup
																	.getValue()
																	.forEach(
																			entityLevelDataCount -> {
																				List<DataCount> dataValuesOfHierarchyEntity =
																						(List<DataCount>) entityLevelDataCount.getValue();
																				for (int dataPoint = 0;
																						dataPoint < dataValuesOfHierarchyEntity.size();
																						dataPoint++) {
																					kpiValuesByDataPointMap.computeIfAbsent(
																							dataPoint, v -> new ArrayList<>());
																					kpiValuesByDataPointMap
																							.get(dataPoint)
																							.add(
																									((Number)
																													dataValuesOfHierarchyEntity
																															.get(dataPoint)
																															.getValue())
																											.doubleValue());
																				}
																			}));
										}
									} else {
										log.info(
												"KPI {} did not have any data ", kpiConfiguration.getKpiCode().getKpiId());
									}
								}
							}
						});
			}
			return kpiValuesByDataPointMap;
		}
		return Collections.emptyMap();
	}

	@SuppressWarnings("java:S3776")
	private static void populateKpiValuesByDataPointMapForIterationBasedKpi(
			Map<Integer, List<Double>> dataPointAggregatedKpiSumMap,
			Map<String, Set<String>> projectNodeIdSprintNodeIdsMap,
			List<KpiElement> kpiData,
			String kpiId) {
		Map<String, Double> sprintIdKpiValueMap = new HashMap<>();
		kpiData.forEach(
				kpiElement -> {
					double kpiSprintValue = 0.0D;
					if (CollectionUtils.isNotEmpty(kpiElement.getIssueData())) {
						for (IssueKpiModalValue issueKpiModalValue : kpiElement.getIssueData()) {
							if (KPICode.WASTAGE.getKpiId().equalsIgnoreCase(kpiId)) {
								kpiSprintValue +=
										(issueKpiModalValue.getIssueBlockedTime()
												+ issueKpiModalValue.getIssueWaitTime());
							}
							if (KPICode.WORK_STATUS.getKpiId().equalsIgnoreCase(kpiId)
									&& Objects.nonNull(issueKpiModalValue.getCategoryWiseDelay().get("Planned"))) {
								kpiSprintValue += (issueKpiModalValue.getCategoryWiseDelay().get("Planned"));
							}
						}
					}
					sprintIdKpiValueMap.put(kpiElement.getSprintId(), kpiSprintValue);
				});

		for (Map.Entry<String, Set<String>> projectNodeIdSprintNodeIdsEntry :
				projectNodeIdSprintNodeIdsMap.entrySet()) {
			List<String> sprintNodeIdsList = new ArrayList<>(projectNodeIdSprintNodeIdsEntry.getValue());

			for (int dataPoint = 0; dataPoint < sprintNodeIdsList.size(); dataPoint++) {
				dataPointAggregatedKpiSumMap.computeIfAbsent(dataPoint, v -> new ArrayList<>());
				dataPointAggregatedKpiSumMap
						.get(dataPoint)
						.add(sprintIdKpiValueMap.getOrDefault(sprintNodeIdsList.get(dataPoint), 0.0D));
			}
		}
	}

	private static void populateProcessorTypeKpiRequestsMapForIterationKPIs(
			KPIConfiguration.ProcessorType processorType,
			List<KpiElement> kpiElementList,
			Map<String, Set<String>> projectNodeIdSprintIdsMap,
			Map<KPIConfiguration.ProcessorType, List<KpiRequest>> processorTypeKpiRequestsMap) {
		for (Map.Entry<String, Set<String>> projectNodeIdSprintIdsEntry :
				projectNodeIdSprintIdsMap.entrySet()) {
			projectNodeIdSprintIdsEntry
					.getValue()
					.forEach(
							sprintNodeId -> {
								KpiRequest kpiRequest = new KpiRequest();
								kpiRequest.setSelectedMap(
										Map.of(
												CommonConstant.HIERARCHY_LEVEL_ID_PROJECT,
												List.of(projectNodeIdSprintIdsEntry.getKey()),
												CommonConstant.HIERARCHY_LEVEL_ID_SPRINT,
												List.of(sprintNodeId)));
								kpiRequest.setLevel(HIERARCHY_LEVEL_SPRINT);
								kpiRequest.setLabel(CommonConstant.HIERARCHY_LEVEL_ID_SPRINT);
								kpiRequest.setKpiList(kpiElementList);
								kpiRequest.setSprintIncluded(List.of(SPRINT_STATUS_CLOSED, SPRINT_STATUS_ACTIVE));
								kpiRequest.setIds(new String[] {sprintNodeId});
								processorTypeKpiRequestsMap.get(processorType).add(kpiRequest);
							});
		}
	}

	private static KpiElement constructKpiElementFromKpiMaster(KpiMaster kpiMaster) {
		KpiElement kpiElement = new KpiElement();
		kpiElement.setChartType(kpiMaster.getChartType());
		kpiElement.setGroupId(kpiMaster.getGroupId());
		kpiElement.setId(kpiMaster.getId().toString());
		kpiElement.setIsDeleted(kpiMaster.getIsDeleted());
		kpiElement.setKanban(kpiMaster.getKanban());
		kpiElement.setKpiCategory(kpiMaster.getKpiCategory());
		kpiElement.setKpiId(kpiMaster.getKpiId());
		kpiElement.setKpiName(kpiMaster.getKpiName());
		kpiElement.setKpiSource(kpiMaster.getKpiSource());
		kpiElement.setKpiUnit(kpiMaster.getKpiUnit());
		kpiElement.setMaxValue(kpiMaster.getMaxValue());
		kpiElement.setThresholdValue(kpiMaster.getThresholdValue());
		return kpiElement;
	}

	private Optional<KPIConfiguration> getKpiConfigurationByKpiId(String kpiId) {
		return categoryKpiIdConfigurationMap.values().stream()
				.filter(value -> value.containsKey(kpiId))
				.map(value -> value.get(kpiId))
				.findFirst();
	}

	private static boolean requestedParentNodeIdCorrespondsToProjectLevel(
			String parentNodeId, Set<AccountFilteredData> accountFilteredData) {
		return StringUtils.isNotEmpty(parentNodeId)
				&& accountFilteredData.stream()
						.anyMatch(
								accountFilteredData1 ->
										parentNodeId.equalsIgnoreCase(accountFilteredData1.getNodeId())
												&& accountFilteredData1.getLevel() == HIERARCHY_LEVEL_PROJECT);
	}

	private static boolean dataCountGroupMatchesFiltersSetForOverallProductivityGainCalculation(
			Object trendValue, KPIConfiguration kpiConfiguration) {
		DataCountGroup dataCountGroup = (DataCountGroup) trendValue;
		String dataCountGroupFilter = kpiConfiguration.getDataCountGroupFilterUsedForCalculation();
		String dataCountGroupFilter1 = kpiConfiguration.getDataCountGroupFilter1UsedForCalculation();
		String dataCountGroupFilter2 = kpiConfiguration.getDataCountGroupFilter2UsedForCalculation();

		boolean matchesAllKpiConfigurationFilters = false;

		if (StringUtils.isNotEmpty(dataCountGroupFilter)) {
			matchesAllKpiConfigurationFilters =
					dataCountGroupFilter.equalsIgnoreCase(dataCountGroup.getFilter());
		}

		if (StringUtils.isNotEmpty(dataCountGroupFilter1)) {
			matchesAllKpiConfigurationFilters =
					dataCountGroupFilter1.equalsIgnoreCase(dataCountGroup.getFilter1());
		}

		if (StringUtils.isNotEmpty(dataCountGroupFilter2)) {
			matchesAllKpiConfigurationFilters =
					dataCountGroupFilter2.equalsIgnoreCase(dataCountGroup.getFilter2());
		}

		return matchesAllKpiConfigurationFilters;
	}

	private static List<KpiElement> cloneKpiElementsFromProcessorResponse(
			List<KpiElement> kpiElementsFromProcessorResponse) {
		return kpiElementsFromProcessorResponse.stream()
				.map(
						kpiElementFromProcessorResponse -> {
							KpiElement kpiElement = new KpiElement(kpiElementFromProcessorResponse);
							kpiElement.setSprintId(kpiElementFromProcessorResponse.getSprintId());
							kpiElement.setIssueData(kpiElementFromProcessorResponse.getIssueData());
							kpiElement.setTrendValueList(kpiElementFromProcessorResponse.getTrendValueList());
							return kpiElement;
						})
				.toList();
	}

	@SuppressWarnings({"java:S3776", "java:S134"})
	private static Set<AccountFilteredData>
			computeSprintDataSortedBySprintStartDateDescendingBasedOnParentNodeId(
					String parentNodeId, Set<AccountFilteredData> hierarchyDataUserHasAccessTo) {
		Set<AccountFilteredData> sprintDataSortedBySprintStartDateDescending = new LinkedHashSet<>();
		Optional<AccountFilteredData> parentAccountFilteredDataOptional =
				hierarchyDataUserHasAccessTo.stream()
						.filter(
								accountFilteredData ->
										accountFilteredData.getNodeId().equalsIgnoreCase(parentNodeId))
						.findFirst();

		if (parentAccountFilteredDataOptional.isPresent()) {
			AccountFilteredData parentAccountFilteredData = parentAccountFilteredDataOptional.get();
			if (parentAccountFilteredData.getLevel() == HIERARCHY_LEVEL_PROJECT) {
				sprintDataSortedBySprintStartDateDescending =
						hierarchyDataUserHasAccessTo.stream()
								.filter(
										accountFilteredData ->
												accountFilteredData.getLevel() == HIERARCHY_LEVEL_SPRINT
														&& CommonConstant.HIERARCHY_LEVEL_ID_SPRINT.equalsIgnoreCase(
																accountFilteredData.getLabelName())
														&& (StringUtils.isEmpty(parentNodeId)
																|| accountFilteredData
																		.getParentId()
																		.equalsIgnoreCase(parentNodeId)))
								.filter(
										sprintFilteredData ->
												Instant.parse(sprintFilteredData.getSprintEndDate())
														.isBefore(Instant.now()))
								.collect(
										Collectors.toCollection(
												() ->
														new TreeSet<>(
																Comparator.comparing(
																				(AccountFilteredData sprintFilteredData) ->
																						Instant.parse(sprintFilteredData.getSprintStartDate()))
																		.reversed())));
			} else {
				Map<Integer, Set<String>> hierarchyLevelNodeIdsMap = new HashMap<>();
				for (int level = parentAccountFilteredData.getLevel();
						level <= HIERARCHY_LEVEL_SPRINT;
						level++) {
					if (level == HIERARCHY_LEVEL_SPRINT) {
						int currentLevel = level;
						sprintDataSortedBySprintStartDateDescending =
								hierarchyDataUserHasAccessTo.stream()
										.filter(
												accountFilteredData ->
														accountFilteredData.getLevel() == currentLevel
																&& CommonConstant.HIERARCHY_LEVEL_ID_SPRINT.equalsIgnoreCase(
																		accountFilteredData.getLabelName())
																&& hierarchyLevelNodeIdsMap
																		.get(currentLevel - 1)
																		.contains(accountFilteredData.getParentId()))
										.filter(
												sprintFilteredData ->
														Instant.parse(sprintFilteredData.getSprintEndDate())
																.isBefore(Instant.now()))
										.collect(
												Collectors.toCollection(
														() ->
																new TreeSet<>(
																		Comparator.comparing(
																						(AccountFilteredData sprintFilteredData) ->
																								Instant.parse(
																										sprintFilteredData.getSprintStartDate()))
																				.reversed())));
					} else {
						if (MapUtils.isEmpty(hierarchyLevelNodeIdsMap)) {
							hierarchyLevelNodeIdsMap.put(
									parentAccountFilteredData.getLevel(),
									hierarchyDataUserHasAccessTo.stream()
											.map(AccountFilteredData::getNodeId)
											.filter(parentNodeId::equalsIgnoreCase)
											.collect(Collectors.toSet()));
						} else {
							int currentLevel = level;
							hierarchyLevelNodeIdsMap.put(
									level,
									hierarchyDataUserHasAccessTo.stream()
											.filter(
													accountFilteredData ->
															accountFilteredData.getLevel() == currentLevel
																	&& hierarchyLevelNodeIdsMap
																			.get((accountFilteredData.getLevel() - 1))
																			.contains(accountFilteredData.getParentId()))
											.map(AccountFilteredData::getNodeId)
											.collect(Collectors.toSet()));
						}
					}
				}
			}
		}
		return sprintDataSortedBySprintStartDateDescending;
	}

	private static String getKpiNameByKpiId(String kpiId) {
		return FieldMappingEnum.valueOf(kpiId.toUpperCase()).getKpiName();
	}

	private Map<String, Map<String, KPIConfiguration>> constructCategoryKpiIdConfigurationMap() {
		Map<String, Map<String, KPIConfiguration>> configuredCategoryKpiIdConfigurationMap =
				new HashMap<>();
		if (CollectionUtils.isNotEmpty(productivityGainConfig.getAllConfiguredCategories())) {
			productivityGainConfig
					.getAllConfiguredCategories()
					.forEach(
							configuredCategory -> {
								switch (configuredCategory) {
									case CATEGORY_EFFICIENCY ->
											configuredCategoryKpiIdConfigurationMap.put(
													CATEGORY_EFFICIENCY,
													constructKpiIdKpiConfigurationMapForEfficiencyKpis());
									case CATEGORY_SPEED ->
											configuredCategoryKpiIdConfigurationMap.put(
													CATEGORY_SPEED, constructKpiIdKpiConfigurationMapForSpeedKpis());
									case CATEGORY_PRODUCTIVITY ->
											configuredCategoryKpiIdConfigurationMap.put(
													CATEGORY_PRODUCTIVITY,
													constructKpiIdKpiConfigurationMapForProductivityKpis());
									case CATEGORY_QUALITY ->
											configuredCategoryKpiIdConfigurationMap.put(
													CATEGORY_QUALITY, constructKpiIdKpiConfigurationMapForQualityKpis());
								}
							});
		}
		return Collections.unmodifiableMap(configuredCategoryKpiIdConfigurationMap);
	}

	private static Map<String, KPIConfiguration> constructKpiIdKpiConfigurationMapForSpeedKpis() {
		return Map.of(
				KPICode.SPRINT_VELOCITY.getKpiId(),
				KPIConfiguration.builder()
						.kpiCode(KPICode.SPRINT_VELOCITY)
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.processorType(KPIConfiguration.ProcessorType.JIRA)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.ASCENDING)
						.xAxisMeasurement(KPIConfiguration.XAxisMeasurement.SPRINTS)
						.supportedKPIHierarchyLevelAggregation(
								KPIConfiguration.SupportedKPIHierarchyLevelAggregation.ALL)
						.build(),
				KPICode.REPO_TOOL_MEAN_TIME_TO_MERGE.getKpiId(),
				KPIConfiguration.builder()
						.kpiCode(KPICode.MEAN_TIME_TO_MERGE)
						.dataCountGroupFilter2UsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.processorType(KPIConfiguration.ProcessorType.BITBUCKET)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.DESCENDING)
						.xAxisMeasurement(KPIConfiguration.XAxisMeasurement.WEEKS)
						.supportedKPIHierarchyLevelAggregation(
								KPIConfiguration.SupportedKPIHierarchyLevelAggregation.PROJECT)
						.build(),
				KPICode.CODE_BUILD_TIME.getKpiId(),
				KPIConfiguration.builder()
						.kpiCode(KPICode.CODE_BUILD_TIME)
						.dataCountGroupFilterUsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.processorType(KPIConfiguration.ProcessorType.JENKINS)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.DESCENDING)
						.xAxisMeasurement(KPIConfiguration.XAxisMeasurement.SPRINTS)
						.supportedKPIHierarchyLevelAggregation(
								KPIConfiguration.SupportedKPIHierarchyLevelAggregation.ALL)
						.build(),
				KPICode.PICKUP_TIME.getKpiId(),
				KPIConfiguration.builder()
						.kpiCode(KPICode.PICKUP_TIME)
						.dataCountGroupFilter2UsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.processorType(KPIConfiguration.ProcessorType.BITBUCKET)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.DESCENDING)
						.xAxisMeasurement(KPIConfiguration.XAxisMeasurement.WEEKS)
						.supportedKPIHierarchyLevelAggregation(
								KPIConfiguration.SupportedKPIHierarchyLevelAggregation.PROJECT)
						.build(),
				KPICode.SCOPE_CHURN.getKpiId(),
				KPIConfiguration.builder()
						.kpiCode(KPICode.SCOPE_CHURN)
						.dataCountGroupFilterUsedForCalculation("Story Points")
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.processorType(KPIConfiguration.ProcessorType.JIRA)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.DESCENDING)
						.xAxisMeasurement(KPIConfiguration.XAxisMeasurement.SPRINTS)
						.supportedKPIHierarchyLevelAggregation(
								KPIConfiguration.SupportedKPIHierarchyLevelAggregation.ALL)
						.build());
	}

	private static Map<String, KPIConfiguration> constructKpiIdKpiConfigurationMapForQualityKpis() {
		return Map.of(
				KPICode.DEFECT_DENSITY.getKpiId(),
				KPIConfiguration.builder()
						.kpiCode(KPICode.DEFECT_DENSITY)
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.processorType(KPIConfiguration.ProcessorType.JIRA)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.DESCENDING)
						.xAxisMeasurement(KPIConfiguration.XAxisMeasurement.SPRINTS)
						.supportedKPIHierarchyLevelAggregation(
								KPIConfiguration.SupportedKPIHierarchyLevelAggregation.ALL)
						.build(),
				KPICode.DEFECT_SEEPAGE_RATE.getKpiId(),
				KPIConfiguration.builder()
						.kpiCode(KPICode.DEFECT_SEEPAGE_RATE)
						.dataCountGroupFilterUsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.processorType(KPIConfiguration.ProcessorType.JIRA)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.DESCENDING)
						.xAxisMeasurement(KPIConfiguration.XAxisMeasurement.SPRINTS)
						.supportedKPIHierarchyLevelAggregation(
								KPIConfiguration.SupportedKPIHierarchyLevelAggregation.ALL)
						.build(),
				KPICode.DEFECT_SEVERITY_INDEX.getKpiId(),
				KPIConfiguration.builder()
						.kpiCode(KPICode.DEFECT_SEVERITY_INDEX)
						.dataCountGroupFilterUsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.processorType(KPIConfiguration.ProcessorType.JIRA)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.DESCENDING)
						.xAxisMeasurement(KPIConfiguration.XAxisMeasurement.SPRINTS)
						.supportedKPIHierarchyLevelAggregation(
								KPIConfiguration.SupportedKPIHierarchyLevelAggregation.ALL)
						.build(),
				KPICode.DEFECT_REOPEN_RATE_QUALITY.getKpiId(),
				KPIConfiguration.builder()
						.kpiCode(KPICode.DEFECT_REOPEN_RATE)
						.dataCountGroupFilterUsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.processorType(KPIConfiguration.ProcessorType.JIRA)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.DESCENDING)
						.xAxisMeasurement(KPIConfiguration.XAxisMeasurement.SPRINTS)
						.supportedKPIHierarchyLevelAggregation(
								KPIConfiguration.SupportedKPIHierarchyLevelAggregation.ALL)
						.build());
	}

	private static Map<String, KPIConfiguration>
			constructKpiIdKpiConfigurationMapForEfficiencyKpis() {
		return Map.of(
				KPICode.SPRINT_CAPACITY_UTILIZATION.getKpiId(),
				KPIConfiguration.builder()
						.kpiCode(KPICode.SPRINT_CAPACITY_UTILIZATION)
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.processorType(KPIConfiguration.ProcessorType.JIRA)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.ASCENDING)
						.xAxisMeasurement(KPIConfiguration.XAxisMeasurement.SPRINTS)
						.supportedKPIHierarchyLevelAggregation(
								KPIConfiguration.SupportedKPIHierarchyLevelAggregation.ALL)
						.build(),
				KPICode.WASTAGE.getKpiId(),
				KPIConfiguration.builder()
						.kpiCode(KPICode.WASTAGE)
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.processorType(KPIConfiguration.ProcessorType.JIRA_ITERATION)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.DESCENDING)
						.xAxisMeasurement(KPIConfiguration.XAxisMeasurement.ITERATION)
						.supportedKPIHierarchyLevelAggregation(
								KPIConfiguration.SupportedKPIHierarchyLevelAggregation.SPRINT)
						.build(),
				KPICode.WORK_STATUS.getKpiId(),
				KPIConfiguration.builder()
						.kpiCode(KPICode.WORK_STATUS)
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.processorType(KPIConfiguration.ProcessorType.JIRA_ITERATION)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.DESCENDING)
						.xAxisMeasurement(KPIConfiguration.XAxisMeasurement.ITERATION)
						.supportedKPIHierarchyLevelAggregation(
								KPIConfiguration.SupportedKPIHierarchyLevelAggregation.SPRINT)
						.build());
	}

	private static Map<String, KPIConfiguration>
			constructKpiIdKpiConfigurationMapForProductivityKpis() {
		return Map.of(
				KPICode.COMMITMENT_RELIABILITY.getKpiId(),
				KPIConfiguration.builder()
						.kpiCode(KPICode.COMMITMENT_RELIABILITY)
						.dataCountGroupFilter1UsedForCalculation("Final Scope (Count)")
						.dataCountGroupFilter2UsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.processorType(KPIConfiguration.ProcessorType.JIRA)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.ASCENDING)
						.xAxisMeasurement(KPIConfiguration.XAxisMeasurement.SPRINTS)
						.supportedKPIHierarchyLevelAggregation(
								KPIConfiguration.SupportedKPIHierarchyLevelAggregation.ALL)
						.build(),
				KPICode.REPO_TOOL_CODE_COMMIT.getKpiId(),
				KPIConfiguration.builder()
						.kpiCode(KPICode.REPO_TOOL_CODE_COMMIT)
						.dataCountGroupFilter2UsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.processorType(KPIConfiguration.ProcessorType.BITBUCKET)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.ASCENDING)
						.xAxisMeasurement(KPIConfiguration.XAxisMeasurement.WEEKS)
						.supportedKPIHierarchyLevelAggregation(
								KPIConfiguration.SupportedKPIHierarchyLevelAggregation.PROJECT)
						.build(),
				KPICode.PR_SUCCESS_RATE.getKpiId(),
				KPIConfiguration.builder()
						.kpiCode(KPICode.PR_SUCCESS_RATE)
						.dataCountGroupFilter2UsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.processorType(KPIConfiguration.ProcessorType.BITBUCKET)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.ASCENDING)
						.xAxisMeasurement(KPIConfiguration.XAxisMeasurement.WEEKS)
						.supportedKPIHierarchyLevelAggregation(
								KPIConfiguration.SupportedKPIHierarchyLevelAggregation.PROJECT)
						.build(),
				KPICode.REVERT_RATE.getKpiId(),
				KPIConfiguration.builder()
						.kpiCode(KPICode.REVERT_RATE)
						.dataCountGroupFilter2UsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.processorType(KPIConfiguration.ProcessorType.BITBUCKET)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.DESCENDING)
						.xAxisMeasurement(KPIConfiguration.XAxisMeasurement.WEEKS)
						.supportedKPIHierarchyLevelAggregation(
								KPIConfiguration.SupportedKPIHierarchyLevelAggregation.PROJECT)
						.build());
	}
}
