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

package com.publicissapient.kpidashboard.apis.kpiintegration.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.publicissapient.kpidashboard.apis.model.IterationKpiValue;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.bitbucket.service.BitBucketServiceKanbanR;
import com.publicissapient.kpidashboard.apis.bitbucket.service.BitBucketServiceR;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.EntityNotFoundException;
import com.publicissapient.kpidashboard.apis.jenkins.service.JenkinsServiceKanbanR;
import com.publicissapient.kpidashboard.apis.jenkins.service.JenkinsServiceR;
import com.publicissapient.kpidashboard.apis.jira.service.JiraServiceKanbanR;
import com.publicissapient.kpidashboard.apis.jira.service.JiraServiceR;
import com.publicissapient.kpidashboard.apis.jira.service.NonTrendServiceFactory;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.sonar.service.SonarServiceKanbanR;
import com.publicissapient.kpidashboard.apis.sonar.service.SonarServiceR;
import com.publicissapient.kpidashboard.apis.zephyr.service.ZephyrService;
import com.publicissapient.kpidashboard.apis.zephyr.service.ZephyrServiceKanban;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.application.KpiMaster;
import com.publicissapient.kpidashboard.common.model.application.OrganizationHierarchy;
import com.publicissapient.kpidashboard.common.repository.application.KpiMasterRepository;
import com.publicissapient.kpidashboard.common.repository.application.OrganizationHierarchyRepository;
import com.publicissapient.kpidashboard.common.service.HierarchyLevelService;

import jakarta.ws.rs.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author kunkambl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpiIntegrationServiceImpl {
	private static final String KPI_SOURCE_JIRA = "Jira";
	private static final String KPI_SOURCE_SONAR = "Sonar";
	private static final String KPI_SOURCE_ZEPHYR = "Zypher";
	private static final String KPI_SOURCE_JENKINS = "Jenkins";
	private static final String KPI_SOURCE_DEVELOPER = "BitBucket";
	private static final String SPRINT_CLOSED = "CLOSED";

	private static final List<String> FILTER_LIST =
			Arrays.asList(
					"Final Scope (Story Points)", "Average Coverage", "Story Points", "Overall", "Lead Time");

	private final KpiMasterRepository kpiMasterRepository;
	private final OrganizationHierarchyRepository organizationHierarchyRepository;

	private final NonTrendServiceFactory serviceFactory;

	private final JiraServiceR jiraService;
	private final SonarServiceR sonarService;
	private final ZephyrService zephyrService;
	private final JenkinsServiceR jenkinsServiceR;
	private final BitBucketServiceR bitBucketServiceR;
	private final HierarchyLevelService hierarchyLevelService;

	private final JiraServiceKanbanR jiraServiceKanbanR;
	private final SonarServiceKanbanR sonarServiceKanbanR;
	private final ZephyrServiceKanban zephyrServiceKanban;
	private final JenkinsServiceKanbanR jenkinsServiceKanbanR;
	private final BitBucketServiceKanbanR bitBucketServiceKanbanR;

	/**
	 * Processes Kanban KPI requests by validating input, routing to appropriate services, and
	 * calculating overall maturity values.
	 *
	 * <p>This method handles the complete Kanban KPI processing workflow:
	 *
	 * <ol>
	 *   <li>Validates the incoming request parameters
	 *   <li>Retrieves KPI master data from repository
	 *   <li>Groups KPIs by their data source
	 *   <li>Delegates processing to source-specific Kanban services
	 *   <li>Aggregates results and calculates maturity values
	 * </ol>
	 *
	 * <p><strong>Validation Requirements:</strong>
	 *
	 * <ul>
	 *   <li>Non-null request object
	 *   <li>Non-empty KPI ID list
	 *   <li>Single positive integer in IDs array
	 *   <li>Non-blank label
	 *   <li>Selected map with date aggregation unit
	 * </ul>
	 *
	 * @param kpiRequest the KPI request containing KPI IDs, filters, and processing parameters
	 * @return list of processed KPI elements with calculated values and maturity scores
	 */
	public List<KpiElement> processKanbanKPIRequest(KpiRequest kpiRequest) {
		validateKanbanKPIRequest(kpiRequest);
		List<KpiMaster> kpiMasterList = kpiMasterRepository.findByKpiIdIn(kpiRequest.getKpiIdList());

		Map<KPISource, List<KpiMaster>> kpiElementsGroupedBySource =
				kpiMasterList.stream()
						.filter(kpiMaster -> StringUtils.isNotEmpty(kpiMaster.getKpiSource()))
						.collect(
								Collectors.groupingBy(
										kpiMaster -> KPISource.getKPISource(kpiMaster.getKpiSource().toUpperCase())));
		List<KpiElement> kpiElements = new ArrayList<>();
		kpiElementsGroupedBySource.forEach(
				(kpiSource, kpis) -> {
					kpiRequest.setKpiList(kpis.stream().map(this::mapKpiMasterToKpiElement).toList());
					try {
						switch (kpiSource) {
							case JIRA -> kpiElements.addAll(this.jiraServiceKanbanR.process(kpiRequest));
							case SONAR -> kpiElements.addAll(this.sonarServiceKanbanR.process(kpiRequest));
							case ZEPHYR -> kpiElements.addAll(this.zephyrServiceKanban.process(kpiRequest));
							case JENKINS -> kpiElements.addAll(this.jenkinsServiceKanbanR.process(kpiRequest));
							case BITBUCKET ->
									kpiElements.addAll(this.bitBucketServiceKanbanR.process(kpiRequest));
							default -> log.info("Unexpected kpi source received {}", kpiSource);
						}
					} catch (EntityNotFoundException exception) {
						log.error("Could not process the kpi request for kpi source {}", kpiSource, exception);
					}
				});
		calculateOverallMaturity(kpiElements);
		return kpiElements;
	}

	/**
	 * get kpi element list with maturity assuming req for hierarchy level 4
	 *
	 * @param kpiRequest kpiRequest to fetch kpi data
	 * @return list of KpiElement
	 */
	public List<KpiElement> processScrumKpiRequest(KpiRequest kpiRequest) {
		List<KpiMaster> kpiMasterList = kpiMasterRepository.findByKpiIdIn(kpiRequest.getKpiIdList());
		Map<String, List<KpiMaster>> sourceWiseKpiList =
				kpiMasterList.stream().collect(Collectors.groupingBy(KpiMaster::getKpiSource));
		setKpiRequest(kpiRequest);
		return getKpiElements(kpiRequest, sourceWiseKpiList, false);
	}

	/**
	 * Map KpiMaster object to KpiElement
	 *
	 * @param kpiMaster KpiMaster object fetched from db
	 * @return KpiElement
	 */
	public KpiElement mapKpiMasterToKpiElement(KpiMaster kpiMaster) {
		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId(kpiMaster.getKpiId());
		kpiElement.setKpiName(kpiMaster.getKpiName());
		kpiElement.setIsDeleted(kpiMaster.getIsDeleted());
		kpiElement.setKpiCategory(kpiMaster.getKpiCategory());
		kpiElement.setKpiInAggregatedFeed(kpiMaster.getKpiInAggregatedFeed());
		kpiElement.setKpiOnDashboard(kpiMaster.getKpiOnDashboard());
		kpiElement.setKpiBaseLine(kpiMaster.getKpiBaseLine());
		kpiElement.setKpiUnit(kpiMaster.getKpiUnit());
		kpiElement.setIsTrendUpOnValIncrease(kpiMaster.getIsTrendUpOnValIncrease());
		kpiElement.setKanban(kpiMaster.getKanban());
		kpiElement.setKpiSource(kpiMaster.getKpiSource());
		kpiElement.setThresholdValue(kpiMaster.getThresholdValue());
		kpiElement.setAggregationType(kpiMaster.getAggregationCriteria());
		kpiElement.setMaturityRange(kpiMaster.getMaturityRange());
		kpiElement.setGroupId(kpiMaster.getGroupId());
		return kpiElement;
	}

	@NotNull
	public List<KpiElement> getKpiElements(
			KpiRequest kpiRequest, Map<String, List<KpiMaster>> sourceWiseKpiList, boolean withCache) {
		List<KpiElement> kpiElements = new ArrayList<>();
		sourceWiseKpiList.forEach(
				(source, kpiList) -> {
					try {
						kpiRequest.setKpiList(
								sourceWiseKpiList.get(source).stream()
										.map(this::mapKpiMasterToKpiElement)
										.toList());
						switch (source) {
							case KPI_SOURCE_JIRA:
								kpiElements.addAll(getJiraKpiMaturity(kpiRequest, withCache));
								break;
							case KPI_SOURCE_SONAR:
								kpiElements.addAll(getSonarKpiMaturity(kpiRequest, withCache));
								break;
							case KPI_SOURCE_ZEPHYR:
								kpiElements.addAll(getZephyrKpiMaturity(kpiRequest, withCache));
								break;
							case KPI_SOURCE_JENKINS:
								kpiElements.addAll(getJenkinsKpiMaturity(kpiRequest, withCache));
								break;
							case KPI_SOURCE_DEVELOPER:
								kpiElements.addAll(getDeveloperKpiMaturity(kpiRequest, withCache));
								break;
							default:
								log.error("Invalid Kpi");
						}
					} catch (Exception ex) {
						log.error("Error while fetching kpi maturity data", ex);
					}
				});
		calculateOverallMaturity(kpiElements);
		return kpiElements;
	}

	public void calculateOverallMaturity(List<KpiElement> kpiElements) {
		kpiElements.forEach(
				kpiElement -> {
					List<?> trendValueList = (List<?>) kpiElement.getTrendValueList();
					if (CollectionUtils.isNotEmpty(trendValueList)) {
						if (trendValueList.get(0) instanceof DataCountGroup) {
							List<DataCountGroup> dataCountGroups = (List<DataCountGroup>) trendValueList;

							Optional<DataCount> firstMatchingDataCount =
									dataCountGroups.stream()
											.filter(
													trend ->
															FILTER_LIST.contains(trend.getFilter())
																	|| (FILTER_LIST.contains(trend.getFilter1())
																			&& FILTER_LIST.contains(trend.getFilter2())))
											.map(DataCountGroup::getValue)
											.flatMap(List::stream)
											.findFirst();

							firstMatchingDataCount.ifPresent(
									dataCount -> {
										Object maturityValue = dataCount.getMaturityValue();

										kpiElement.setOverAllMaturityValue(String.valueOf(maturityValue));

										kpiElement.setOverallMaturity(dataCount.getMaturity());
									});
						} else if (!(trendValueList.get(0) instanceof IterationKpiValue)) {
							List<DataCount> dataCounts = (List<DataCount>) trendValueList;
							DataCount firstDataCount = dataCounts.get(0);

							kpiElement.setOverAllMaturityValue((String) firstDataCount.getMaturityValue());
							kpiElement.setOverallMaturity(firstDataCount.getMaturity());
						}
					}
				});
	}

	/**
	 * set kpi request parameters as per the request
	 *
	 * @param kpiRequest received kpi request
	 */
	private void setKpiRequest(KpiRequest kpiRequest) {
		String[] hierarchyIdList = null;
		List<String> externalIDs = kpiRequest.getExternalIDs();
		if (CollectionUtils.isNotEmpty(externalIDs)) {
			List<OrganizationHierarchy> orgHierarchyList =
					organizationHierarchyRepository.findByExternalIdIn(externalIDs);
			if (CollectionUtils.isNotEmpty(orgHierarchyList)) {
				hierarchyIdList =
						orgHierarchyList.stream().map(OrganizationHierarchy::getNodeId).toArray(String[]::new);
			}
		}
		Optional<HierarchyLevel> optionalHierarchyLevel =
				hierarchyLevelService.getFullHierarchyLevels(false).stream()
						.filter(hierarchyLevel -> hierarchyLevel.getLevel() == kpiRequest.getLevel())
						.findFirst();
		if (optionalHierarchyLevel.isPresent()) {
			HierarchyLevel hierarchyLevel = optionalHierarchyLevel.get();
			if (hierarchyIdList == null) {
				hierarchyIdList = getHierarchyIdList(kpiRequest, hierarchyLevel);
			}
			if (MapUtils.isEmpty(kpiRequest.getSelectedMap())) {
				Map<String, List<String>> selectedMap = new HashMap<>();
				selectedMap.put(
						hierarchyLevel.getHierarchyLevelId(), Arrays.stream(hierarchyIdList).toList());
				kpiRequest.setSelectedMap(selectedMap);
			}

			kpiRequest.setIds(hierarchyIdList);
			kpiRequest.setLabel(hierarchyLevel.getHierarchyLevelId());
			kpiRequest.setSprintIncluded(List.of(SPRINT_CLOSED));
		}
	}

	private String[] getHierarchyIdList(KpiRequest kpiRequest, HierarchyLevel hierarchyLevel) {
		if (ArrayUtils.isNotEmpty(kpiRequest.getIds())) {
			log.debug(
					"Using hierarchy IDs directly from request: {}", Arrays.toString(kpiRequest.getIds()));
			return kpiRequest.getIds();
		}
		String[] hierarchyIdList;
		if (kpiRequest.getHierarchyName() != null) {
			OrganizationHierarchy byNodeNameAndHierarchyLevelId =
					organizationHierarchyRepository.findByNodeNameAndHierarchyLevelId(
							kpiRequest.getHierarchyName(), hierarchyLevel.getHierarchyLevelId());
			if (byNodeNameAndHierarchyLevelId != null
					&& byNodeNameAndHierarchyLevelId.getNodeId() != null) {
				String nodeId = byNodeNameAndHierarchyLevelId.getNodeId();
				hierarchyIdList = new String[] {nodeId};
			} else {
				throw new IllegalArgumentException("No hierarchy data found for given name/level");
			}
		} else {
			throw new IllegalArgumentException(
					"valid external Id or hierarchy name not found in payload."
							+ " Please maintain one of them");
		}

		return hierarchyIdList;
	}

	/**
	 * get kpi data for source jira
	 *
	 * @param kpiRequest kpiRequest to fetch kpi data
	 * @return list of jira KpiElement
	 * @throws EntityNotFoundException entity not found exception for jira service method
	 */
	private List<KpiElement> getJiraKpiMaturity(KpiRequest kpiRequest, boolean withCache)
			throws EntityNotFoundException {
		MDC.put("JiraScrumKpiRequest", kpiRequest.getRequestTrackerId());
		log.info("Received Jira KPI request {}", kpiRequest);
		long jiraRequestStartTime = System.currentTimeMillis();
		MDC.put("JiraRequestStartTime", String.valueOf(jiraRequestStartTime));
		HashSet<String> category = new HashSet<>();
		category.add(CommonConstant.ITERATION);
		category.add(CommonConstant.RELEASE);
		category.add(CommonConstant.BACKLOG);
		List<KpiElement> responseList;
		if (category.contains(kpiRequest.getKpiList().get(0).getKpiCategory())) {
			// when request coming from ITERATION/RELEASE/BACKLOG board
			responseList =
					serviceFactory
							.getService(kpiRequest.getKpiList().get(0).getKpiCategory())
							.processWithExposedApiToken(kpiRequest);
		} else {
			responseList = jiraService.processWithExposedApiToken(kpiRequest, withCache);
		}
		MDC.put(
				"TotalJiraRequestTime", String.valueOf(System.currentTimeMillis() - jiraRequestStartTime));
		MDC.clear();
		return responseList;
	}

	/**
	 * get kpi data for source sonar
	 *
	 * @param kpiRequest kpiRequest to fetch kpi data
	 * @param withCache
	 * @return list of sonar KpiElement
	 */
	private List<KpiElement> getSonarKpiMaturity(KpiRequest kpiRequest, boolean withCache) {
		MDC.put("SonarKpiRequest", kpiRequest.getRequestTrackerId());
		log.info("Received Sonar KPI request {}", kpiRequest);
		long sonarRequestStartTime = System.currentTimeMillis();
		MDC.put("SonarRequestStartTime", String.valueOf(sonarRequestStartTime));
		List<KpiElement> responseList = sonarService.processWithExposedApiToken(kpiRequest, withCache);
		MDC.put(
				"TotalSonarRequestTime",
				String.valueOf(System.currentTimeMillis() - sonarRequestStartTime));
		MDC.clear();
		return responseList;
	}

	/**
	 * get kpi data for source zephyr
	 *
	 * @param kpiRequest kpiRequest to fetch kpi data
	 * @param withCache
	 * @return list of sonar KpiElement
	 * @throws EntityNotFoundException entity not found exception for zephyr service method
	 */
	private List<KpiElement> getZephyrKpiMaturity(KpiRequest kpiRequest, boolean withCache)
			throws EntityNotFoundException {
		MDC.put("ZephyrKpiRequest", kpiRequest.getRequestTrackerId());
		log.info("Received Zephyr KPI request {}", kpiRequest);
		long zypherRequestStartTime = System.currentTimeMillis();
		MDC.put("ZephyrRequestStartTime", String.valueOf(zypherRequestStartTime));
		List<KpiElement> responseList = zephyrService.processWithExposedApiToken(kpiRequest, withCache);
		MDC.put(
				"TotalZephyrRequestTime",
				String.valueOf(System.currentTimeMillis() - zypherRequestStartTime));
		MDC.clear();
		return responseList;
	}

	/**
	 * get kpi data for source jenkins
	 *
	 * @param kpiRequest kpiRequest to fetch kpi data
	 * @param withCache
	 * @return list of sonar KpiElement
	 * @throws EntityNotFoundException entity not found exception for jenkins service method
	 */
	private List<KpiElement> getJenkinsKpiMaturity(KpiRequest kpiRequest, boolean withCache)
			throws EntityNotFoundException {
		MDC.put("JenkinsKpiRequest", kpiRequest.getRequestTrackerId());
		log.info("Received Jenkins KPI request {}", kpiRequest);
		long jenkinsRequestStartTime = System.currentTimeMillis();
		MDC.put("JenkinsRequestStartTime", String.valueOf(jenkinsRequestStartTime));
		List<KpiElement> responseList =
				jenkinsServiceR.processWithExposedApiToken(kpiRequest, withCache);
		MDC.put(
				"TotalJenkinsRequestTime",
				String.valueOf(System.currentTimeMillis() - jenkinsRequestStartTime));
		MDC.clear();
		return responseList;
	}

	/**
	 * get kpi data for source jenkins
	 *
	 * @param kpiRequest kpiRequest to fetch kpi data
	 * @param withCache
	 * @return list of sonar KpiElement
	 * @throws EntityNotFoundException entity not found exception for jenkins service method
	 */
	private List<KpiElement> getDeveloperKpiMaturity(KpiRequest kpiRequest, boolean withCache)
			throws EntityNotFoundException {
		MDC.put("DeveloperKpiRequest", kpiRequest.getRequestTrackerId());
		String sanitizedRequestTrackerId =
				kpiRequest.getRequestTrackerId().replaceAll("[^a-zA-Z0-9-_]", "_");
		log.info("Received Developer KPI request {}", sanitizedRequestTrackerId);
		long developerRequestStartTime = System.currentTimeMillis();
		MDC.put("JenkinsRequestStartTime", String.valueOf(developerRequestStartTime));
		List<KpiElement> responseList =
				bitBucketServiceR.processWithExposedApiToken(kpiRequest, withCache);
		MDC.put(
				"TotalJenkinsRequestTime",
				String.valueOf(System.currentTimeMillis() - developerRequestStartTime));
		MDC.clear();
		return responseList;
	}

	private static void validateKanbanKPIRequest(KpiRequest kpiRequest) {
		if (kpiRequest == null) {
			throw new BadRequestException("Received kpi request was null");
		}
		if (CollectionUtils.isEmpty(kpiRequest.getKpiIdList())) {
			throw new BadRequestException("'kpiIdList' must not be empty");
		}
		if (kpiRequest.getIds() == null || kpiRequest.getIds().length == 0) {
			throw new BadRequestException("'ids' must be provided and contain one positive integer");
		}
		if (kpiRequest.getIds().length > 1) {
			throw new BadRequestException("'ids' must contain only one positive integer");
		}
		if (!NumberUtils.isCreatable(kpiRequest.getIds()[0])) {
			throw new BadRequestException("'ids' must contain one valid positive integer");
		}
		if (StringUtils.isBlank(kpiRequest.getLabel())) {
			throw new BadRequestException("'label' must be provided");
		}
		Map<String, List<String>> selectedMap = kpiRequest.getSelectedMap();
		if (MapUtils.isEmpty(selectedMap)) {
			throw new BadRequestException("'selectedMap' must be provided");
		}
		if (!selectedMap.containsKey(Constant.DATE)) {
			throw new BadRequestException(
					"'selectedMap.date' must be provided with a valid temporal aggregation unit"
							+ " ex.: Weeks");
		}
	}
}
