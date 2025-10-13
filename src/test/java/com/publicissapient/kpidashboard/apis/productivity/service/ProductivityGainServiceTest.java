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
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.assertj.core.api.Assertions;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.bitbucket.service.BitBucketServiceR;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.errors.EntityNotFoundException;
import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceImpl;
import com.publicissapient.kpidashboard.apis.jenkins.service.JenkinsServiceR;
import com.publicissapient.kpidashboard.apis.jira.service.JiraServiceR;
import com.publicissapient.kpidashboard.apis.jira.service.iterationdashboard.JiraIterationServiceR;
import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;
import com.publicissapient.kpidashboard.apis.model.IssueKpiModalValue;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.productivity.config.ProductivityGainConfig;
import com.publicissapient.kpidashboard.apis.productivity.dto.CalculateProductivityRequestDTO;
import com.publicissapient.kpidashboard.apis.productivity.dto.CategorizedProductivityGain;
import com.publicissapient.kpidashboard.apis.productivity.dto.KPITrendDTO;
import com.publicissapient.kpidashboard.apis.productivity.dto.KPITrendsDTO;
import com.publicissapient.kpidashboard.apis.productivity.dto.ProductivityGainDTO;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.application.KpiMaster;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.InternalServerErrorException;

@RunWith(MockitoJUnitRunner.class)
@ExtendWith(MockitoExtension.class) // used for enabling the parameterized test
class ProductivityGainServiceTest {
	private static final int DEFAULT_DATA_POINTS_NUMBER = 6;

	private static final String DATA_COUNT_GROUP_FILTER = "filter";
	private static final String DATA_COUNT_GROUP_FILTER1 = "filter1";
	private static final String DATA_COUNT_GROUP_FILTER2 = "filter2";

	private static final DateTimeFormatter ISO_FORMATTER =
			DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

	private static final List<Double> SAMPLE_DATA_POINT_VALUES =
			List.of(20.0, 100.0, 0.0, 50.0, 180.3, 2.9);

	private static final Map<Integer, Map<String, Object>>
			KPI_ELEMENT_GROUP_ID_KPI_ID_KPI_TREND_VALUE_LIST_MAP =
					constructKpiElementGroupIdKpiIdKpiTrendValueListMap();

	@Mock private ConfigHelperService configHelperService;

	@Mock private ProductivityGainConfig productivityGainConfig;

	@Mock private CacheService cacheService;

	@Mock private JiraServiceR jiraServiceR;

	@Mock private JenkinsServiceR jenkinsServiceR;

	@Mock private JiraIterationServiceR jiraIterationServiceR;

	@Mock private BitBucketServiceR bitBucketServiceR;

	@Mock private AccountHierarchyServiceImpl accountHierarchyServiceImpl;

	@InjectMocks private ProductivityGainServiceImpl productivityGainServiceImpl;

	@Test
	public void when_HierarchyLevelsDataCannotBeObtained_Expect_InternalServerErrorIsThrown() {
		when(cacheService.getFullHierarchyLevel()).thenReturn(Collections.emptyList());
		when(accountHierarchyServiceImpl.getFilteredList(any())).thenReturn(Collections.emptySet());

		assertThrows(
				InternalServerErrorException.class,
				() ->
						productivityGainServiceImpl.processCalculateProductivityRequest(
								createCalculateProductivityRequest(2, "test", null)));
	}

	@Test
	public void when_UserDoesntHaveAccessToAnyHierarchyData_Expect_ForbiddenExceptionIsThrown() {
		when(cacheService.getFullHierarchyLevel()).thenReturn(createHierarchyLevelList());
		when(accountHierarchyServiceImpl.getFilteredList(any())).thenReturn(Collections.emptySet());

		assertThrows(
				ForbiddenException.class,
				() ->
						productivityGainServiceImpl.processCalculateProductivityRequest(
								createCalculateProductivityRequest(2, "test", null)));
	}

	@Test
	public void when_RequestedLevelAndLabelDoNotExist_Expect_BadRequestExceptionIsThrown() {
		when(cacheService.getFullHierarchyLevel()).thenReturn(createHierarchyLevelList());
		when(accountHierarchyServiceImpl.getFilteredList(any()))
				.thenReturn(createAccountFilteredData());

		assertThrows(
				BadRequestException.class,
				() ->
						productivityGainServiceImpl.processCalculateProductivityRequest(
								createCalculateProductivityRequest(1, "test", null)));

		assertThrows(
				BadRequestException.class,
				() ->
						productivityGainServiceImpl.processCalculateProductivityRequest(
								createCalculateProductivityRequest(10, "acc", null)));
	}

	@Test
	public void
			when_RequestedLevelAndLabelAreNotCorrespondingToValuesBetweenAccountAndSprint_Expect_BadRequestExceptionIsThrown() {
		when(cacheService.getFullHierarchyLevel()).thenReturn(createHierarchyLevelList());
		when(accountHierarchyServiceImpl.getFilteredList(any()))
				.thenReturn(createAccountFilteredData());

		assertThrows(
				BadRequestException.class,
				() ->
						productivityGainServiceImpl.processCalculateProductivityRequest(
								createCalculateProductivityRequest(1, "bu", null)));

		assertThrows(
				BadRequestException.class,
				() ->
						productivityGainServiceImpl.processCalculateProductivityRequest(
								createCalculateProductivityRequest(7, "squad", null)));
	}

	@Test
	public void
			when_ParentIdExistsButLabelAndLevelAreNotCorrespondingToTheNextChildLevel_Expect_BadRequestExceptionIsThrown() {
		when(cacheService.getFullHierarchyLevel()).thenReturn(createHierarchyLevelList());
		when(accountHierarchyServiceImpl.getFilteredList(any()))
				.thenReturn(createAccountFilteredData());

		assertThrows(
				BadRequestException.class,
				() ->
						productivityGainServiceImpl.processCalculateProductivityRequest(
								createCalculateProductivityRequest(5, "project", "acc-node-id")));
	}

	@ParameterizedTest
	@MethodSource("provideTestCalculateProductivityRequest")
	void when_ProductivityCalculationRequestIsValid_Expect_CategorizedProductivityResultIsReturned(
			CalculateProductivityRequestDTO calculateProductivityRequestDTO)
			throws EntityNotFoundException {

		initializeProductivityGainRequestMocks();

		/*
		 * The expected categorized productivity and kpi trends from below results from
		 * a manual calculation of the algorithm based on the provided test data
		 */
		List<KPITrendDTO> expectedPositiveTrends =
				List.of(
						KPITrendDTO.builder()
								.trendValue(194.33D)
								.kpiName("Sprint Capacity Utilization")
								.kpiCategory(CATEGORY_EFFICIENCY)
								.build(),
						KPITrendDTO.builder()
								.trendValue(194.33D)
								.kpiName("Commitment Reliability")
								.kpiCategory(CATEGORY_PRODUCTIVITY)
								.build(),
						KPITrendDTO.builder()
								.trendValue(194.33D)
								.kpiName("Check-Ins & Merge Requests (Developer) ")
								.kpiCategory(CATEGORY_PRODUCTIVITY)
								.build(),
						KPITrendDTO.builder()
								.trendValue(194.33D)
								.kpiName("PR Success Rate")
								.kpiCategory(CATEGORY_PRODUCTIVITY)
								.build(),
						KPITrendDTO.builder()
								.trendValue(194.33D)
								.kpiName("Sprint Velocity")
								.kpiCategory(CATEGORY_SPEED)
								.build());
		List<KPITrendDTO> expectedNegativeTrends =
				List.of(
						KPITrendDTO.builder()
								.trendValue(-194.33D)
								.kpiName("Revert Rate")
								.kpiCategory(CATEGORY_PRODUCTIVITY)
								.build(),
						KPITrendDTO.builder()
								.trendValue(-194.33D)
								.kpiName("Scope Churn")
								.kpiCategory(CATEGORY_SPEED)
								.build(),
						KPITrendDTO.builder()
								.trendValue(-194.33D)
								.kpiName("Pickup Time (Developer) ")
								.kpiCategory(CATEGORY_SPEED)
								.build(),
						KPITrendDTO.builder()
								.trendValue(-194.33D)
								.kpiName("Code Build Time")
								.kpiCategory(CATEGORY_SPEED)
								.build(),
						KPITrendDTO.builder()
								.trendValue(-194.33D)
								.kpiName("Defect Reopen Rate")
								.kpiCategory(CATEGORY_QUALITY)
								.build(),
						KPITrendDTO.builder()
								.trendValue(-194.33D)
								.kpiName("Defect Density")
								.kpiCategory(CATEGORY_QUALITY)
								.build(),
						KPITrendDTO.builder()
								.trendValue(-194.33D)
								.kpiName("Defect Seepage Rate")
								.kpiCategory(CATEGORY_QUALITY)
								.build(),
						KPITrendDTO.builder()
								.trendValue(-194.33D)
								.kpiName("Defect Severity Index (Scrum)")
								.kpiCategory(CATEGORY_QUALITY)
								.build());
		ProductivityGainDTO expectedProductivityGainDTO =
				ProductivityGainDTO.builder()
						.categorizedProductivityGain(
								CategorizedProductivityGain.builder()
										.overall(-11.66D)
										.efficiency(194.33)
										.quality(-194.33D)
										.speed(-38.87)
										.productivity(64.78D)
										.build())
						.kpiTrends(
								KPITrendsDTO.builder()
										.positive(expectedPositiveTrends)
										.negative(expectedNegativeTrends)
										.build())
						.build();

		ServiceResponse serviceResponse =
				productivityGainServiceImpl.processCalculateProductivityRequest(
						calculateProductivityRequestDTO);

		assertNotNull(serviceResponse);
		assertNotNull(serviceResponse.getData());
		assertInstanceOf(ProductivityGainDTO.class, serviceResponse.getData());
		ProductivityGainDTO resultedProductivityGainDTO =
				(ProductivityGainDTO) serviceResponse.getData();

		assertNotNull(resultedProductivityGainDTO.getCategorizedProductivityGain());

		assertEquals(
				expectedProductivityGainDTO.getCategorizedProductivityGain(),
				resultedProductivityGainDTO.getCategorizedProductivityGain());

		assertNotNull(((ProductivityGainDTO) serviceResponse.getData()).getKpiTrends());
		KPITrendsDTO resultedKPITrendsDTO =
				((ProductivityGainDTO) serviceResponse.getData()).getKpiTrends();

		assertTrue(CollectionUtils.isNotEmpty(resultedKPITrendsDTO.getPositive()));
		assertTrue(
				resultedKPITrendsDTO.getPositive().stream()
						.allMatch(kpiTrendDTO -> kpiTrendDTO.getTrendValue() > 0.0D));

		assertTrue(CollectionUtils.isNotEmpty(resultedKPITrendsDTO.getNegative()));
		assertTrue(
				resultedKPITrendsDTO.getNegative().stream()
						.allMatch(kpiTrendDTO -> kpiTrendDTO.getTrendValue() < 0.0D));

		Assertions.assertThat(resultedKPITrendsDTO.getPositive())
				.containsExactlyInAnyOrderElementsOf(expectedPositiveTrends);
		Assertions.assertThat(resultedKPITrendsDTO.getNegative())
				.containsExactlyInAnyOrderElementsOf(expectedNegativeTrends);
	}

	private void initializeProductivityGainRequestMocks() throws EntityNotFoundException {
		when(cacheService.getFullHierarchyLevel()).thenReturn(createHierarchyLevelList());
		when(accountHierarchyServiceImpl.getFilteredList(any()))
				.thenReturn(createAccountFilteredData());

		when(configHelperService.loadKpiMaster()).thenReturn(constructKpiMasterList());

		when(jiraServiceR.process(any(KpiRequest.class)))
				.thenAnswer(
						invocationOnMock -> {
							KpiRequest invocationArgument = invocationOnMock.getArgument(0);
							return invocationArgument.getKpiList().stream()
									.map(this::getKpiElementResponseBasedOnKpiElementFromKpiRequest)
									.toList();
						});

		when(jenkinsServiceR.process(any(KpiRequest.class)))
				.thenAnswer(
						invocationOnMock -> {
							KpiRequest invocationArgument = invocationOnMock.getArgument(0);
							return invocationArgument.getKpiList().stream()
									.map(this::getKpiElementResponseBasedOnKpiElementFromKpiRequest)
									.toList();
						});

		/*
		The implementation below will be temporarily commented
		*/
		//		when(jiraIterationServiceR.process(any(KpiRequest.class))).thenAnswer(invocationOnMock -> {
		//			KpiRequest invocationArgument = invocationOnMock.getArgument(0);
		//			return invocationArgument.getKpiList().stream()
		//					.map(this::getKpiElementResponseBasedOnKpiElementFromKpiRequest).toList();
		//		});

		when(bitBucketServiceR.process(any(KpiRequest.class)))
				.thenAnswer(
						invocationOnMock -> {
							KpiRequest invocationArgument = invocationOnMock.getArgument(0);
							return invocationArgument.getKpiList().stream()
									.map(this::getKpiElementResponseBasedOnKpiElementFromKpiRequest)
									.toList();
						});

		initializeProductivityConfig();
	}

	private KpiElement getKpiElementResponseBasedOnKpiElementFromKpiRequest(
			KpiElement kpiElementFromKpiRequest) {
		kpiElementFromKpiRequest.setTrendValueList(
				KPI_ELEMENT_GROUP_ID_KPI_ID_KPI_TREND_VALUE_LIST_MAP
						.get(kpiElementFromKpiRequest.getGroupId())
						.get(kpiElementFromKpiRequest.getKpiId()));

		kpiElementFromKpiRequest.setIssueData(constructTestIssueKpiModalValueSet());

		return kpiElementFromKpiRequest;
	}

	private void initializeProductivityConfig() {
		ProductivityGainConfig.DataPoints dataPoints = new ProductivityGainConfig.DataPoints();
		dataPoints.setCount(ProductivityGainServiceTest.DEFAULT_DATA_POINTS_NUMBER);

		when(productivityGainConfig.getWeightForCategory(CATEGORY_SPEED)).thenReturn(0.3D);
		when(productivityGainConfig.getWeightForCategory(CATEGORY_QUALITY)).thenReturn(0.3D);
		when(productivityGainConfig.getWeightForCategory(CATEGORY_EFFICIENCY)).thenReturn(0.25D);
		when(productivityGainConfig.getWeightForCategory(CATEGORY_PRODUCTIVITY)).thenReturn(0.15D);
		when(productivityGainConfig.getDataPoints()).thenReturn(dataPoints);
		when(productivityGainConfig.getAllCategories())
				.thenReturn(
						Set.of(CATEGORY_SPEED, CATEGORY_QUALITY, CATEGORY_EFFICIENCY, CATEGORY_PRODUCTIVITY));
	}

	private static List<HierarchyLevel> createHierarchyLevelList() {
		return List.of(
				HierarchyLevel.builder().hierarchyLevelId("bu").level(1).build(),
				HierarchyLevel.builder().hierarchyLevelId("ver").level(2).build(),
				HierarchyLevel.builder().hierarchyLevelId("acc").level(3).build(),
				HierarchyLevel.builder().hierarchyLevelId("port").level(4).build(),
				HierarchyLevel.builder().hierarchyLevelId("project").level(5).build(),
				HierarchyLevel.builder().hierarchyLevelId("sprint").level(6).build(),
				HierarchyLevel.builder().hierarchyLevelId("squad").level(7).build());
	}

	private static <T> Object constructTrendValueList(
			Class<T> trendValueListObjectType,
			List<Map<String, String>> dataCountGroupTrendValuesFilters) {
		if (trendValueListObjectType.equals(DataCountGroup.class)) {
			List<DataCountGroup> dataCountGroups = new ArrayList<>();
			for (Map<String, String> dataCountGroupFilterMap : dataCountGroupTrendValuesFilters) {
				if (MapUtils.isNotEmpty(dataCountGroupFilterMap)) {
					for (Map.Entry<String, String> dataCountGroupFilterEntry :
							dataCountGroupFilterMap.entrySet()) {
						DataCountGroup dataCountGroup = new DataCountGroup();
						if (DATA_COUNT_GROUP_FILTER.equalsIgnoreCase(dataCountGroupFilterEntry.getKey())) {
							dataCountGroup.setFilter(dataCountGroupFilterEntry.getValue());
						} else if (DATA_COUNT_GROUP_FILTER1.equalsIgnoreCase(
								dataCountGroupFilterEntry.getKey())) {
							dataCountGroup.setFilter1(dataCountGroupFilterEntry.getValue());
						} else if (DATA_COUNT_GROUP_FILTER2.equalsIgnoreCase(
								dataCountGroupFilterEntry.getKey())) {
							dataCountGroup.setFilter2(dataCountGroupFilterEntry.getValue());
						}
						List<DataCount> dataCountList = new ArrayList<>();
						DataCount dataCount =
								DataCount.builder().data("Test data").value(generateDataCountValues()).build();
						dataCountList.add(dataCount);
						dataCountGroup.setValue(dataCountList);
						dataCountGroups.add(dataCountGroup);
					}
				}
			}
			return dataCountGroups;
		}
		if (trendValueListObjectType.equals(DataCount.class)) {
			List<DataCount> dataCountList = new ArrayList<>();
			DataCount dataCount =
					DataCount.builder().data("Test data").value(generateDataCountValues()).build();
			dataCountList.add(dataCount);
			return dataCountList;
		}
		return Collections.emptyList();
	}

	private static List<DataCount> generateDataCountValues() {
		return SAMPLE_DATA_POINT_VALUES.stream()
				.map(
						sampleData ->
								DataCount.builder()
										.data(String.valueOf(sampleData))
										.sSprintID("Test sprint id")
										.sSprintName("Test sprint name")
										.value(sampleData)
										.build())
				.toList();
	}

	private static Set<IssueKpiModalValue> constructTestIssueKpiModalValueSet() {
		Set<IssueKpiModalValue> issueKpiModalValueSet = new HashSet<>();
		issueKpiModalValueSet.add(
				IssueKpiModalValue.builder()
						.issueBlockedTime(100)
						.issueWaitTime(20)
						.categoryWiseDelay(Map.of("Planned", 0))
						.build());
		issueKpiModalValueSet.add(
				IssueKpiModalValue.builder()
						.issueBlockedTime(99)
						.issueWaitTime(19)
						.categoryWiseDelay(Map.of("Planned", 100))
						.build());
		issueKpiModalValueSet.add(
				IssueKpiModalValue.builder()
						.issueBlockedTime(40)
						.issueWaitTime(0)
						.categoryWiseDelay(Map.of("Planned", 84))
						.build());
		issueKpiModalValueSet.add(
				IssueKpiModalValue.builder()
						.issueBlockedTime(0)
						.issueWaitTime(0)
						.categoryWiseDelay(Map.of("Planned", 0))
						.build());
		issueKpiModalValueSet.add(
				IssueKpiModalValue.builder()
						.issueBlockedTime(20)
						.issueWaitTime(10)
						.categoryWiseDelay(Map.of("Planned", 400))
						.build());
		return issueKpiModalValueSet;
	}

	private static CalculateProductivityRequestDTO createCalculateProductivityRequest(
			int level, String label, String parentId) {
		CalculateProductivityRequestDTO calculateProductivityRequestDTO =
				new CalculateProductivityRequestDTO();
		calculateProductivityRequestDTO.setLevel(level);
		calculateProductivityRequestDTO.setLabel(label);
		calculateProductivityRequestDTO.setParentId(parentId);
		return calculateProductivityRequestDTO;
	}

	private static List<CalculateProductivityRequestDTO> provideTestCalculateProductivityRequest() {
		CalculateProductivityRequestDTO calculateProductivityRequestDTO =
				new CalculateProductivityRequestDTO();
		calculateProductivityRequestDTO.setLevel(5);
		calculateProductivityRequestDTO.setLabel("project");
		calculateProductivityRequestDTO.setParentId(null);

		CalculateProductivityRequestDTO calculateProductivityRequestDTO1 =
				new CalculateProductivityRequestDTO();
		calculateProductivityRequestDTO1.setLevel(5);
		calculateProductivityRequestDTO1.setLabel("project");
		calculateProductivityRequestDTO1.setParentId("port-node-id");

		CalculateProductivityRequestDTO calculateProductivityRequestDTO2 =
				new CalculateProductivityRequestDTO();
		calculateProductivityRequestDTO2.setLevel(6);
		calculateProductivityRequestDTO2.setLabel("sprint");
		calculateProductivityRequestDTO2.setParentId("project-node-id");

		return List.of(
				createCalculateProductivityRequest(5, "project", null),
				createCalculateProductivityRequest(5, "project", "port-node-id"),
				createCalculateProductivityRequest(6, "sprint", "project-node-id"));
	}

	private static Set<AccountFilteredData> createAccountFilteredData() {
		List<AccountFilteredData> accountFilteredData = new ArrayList<>();
		accountFilteredData.add(
				AccountFilteredData.builder()
						.nodeId("bu-node-id")
						.nodeName("Test bu")
						.labelName("bu")
						.level(1)
						.build());
		accountFilteredData.add(
				AccountFilteredData.builder()
						.nodeId("ver-node-id")
						.nodeName("Test ver")
						.labelName("ver")
						.level(2)
						.parentId("bu-node-id")
						.build());
		accountFilteredData.add(
				AccountFilteredData.builder()
						.nodeId("acc-node-id")
						.nodeName("Test acc")
						.labelName("acc")
						.level(3)
						.parentId("ver-node-id")
						.build());
		accountFilteredData.add(
				AccountFilteredData.builder()
						.nodeId("port-node-id")
						.nodeName("Test port")
						.labelName("port")
						.level(4)
						.parentId("acc-node-id")
						.build());
		accountFilteredData.add(
				AccountFilteredData.builder()
						.nodeId("project-node-id")
						.nodeName("Test project")
						.labelName("project")
						.level(5)
						.parentId("port-node-id")
						.build());

		Instant baseSprintEnd = Instant.parse("2025-08-31T22:00:00.000Z");
		for (int i = 0; i < 10; i++) {
			// Calculate sprintEndDate for sprint i counting backwards by 12 days per sprint
			Instant sprintEndDate = baseSprintEnd.minusSeconds(12L * 24 * 3600 * i);
			Instant sprintStartDate = sprintEndDate.minusSeconds(11L * 24 * 3600).plusSeconds(1);

			String sprintStartDateStr = ISO_FORMATTER.format(sprintStartDate);
			String sprintEndDateStr = ISO_FORMATTER.format(sprintEndDate);

			AccountFilteredData sprintsData =
					AccountFilteredData.builder()
							.nodeId(String.format("sprint-node-id-%s", i))
							.nodeName(String.format("Test sprint %s", i))
							.parentId("project-node-id")
							.labelName("sprint")
							.level(6)
							.sprintStartDate(sprintStartDateStr)
							.sprintEndDate(sprintEndDateStr)
							.build();

			accountFilteredData.add(sprintsData);
		}

		// Shuffle the list to avoid specific order
		Collections.shuffle(accountFilteredData, new Random(42));
		return new HashSet<>(accountFilteredData);
	}

	private static Map<Integer, Map<String, Object>>
			constructKpiElementGroupIdKpiIdKpiTrendValueListMap() {
		Map<Integer, Map<String, Object>> kpiElementGroupIdKpiIdKpiTrendValueListMap = new HashMap<>();

		kpiElementGroupIdKpiIdKpiTrendValueListMap.put(1, new HashMap<>());
		kpiElementGroupIdKpiIdKpiTrendValueListMap
				.get(1)
				.put(
						"kpi164",
						constructTrendValueList(
								DataCountGroup.class,
								List.of(
										Map.of(DATA_COUNT_GROUP_FILTER, "Story Points"),
										Map.of(DATA_COUNT_GROUP_FILTER, "Issue Count"))));
		kpiElementGroupIdKpiIdKpiTrendValueListMap
				.get(1)
				.put("kpi46", constructTrendValueList(DataCount.class, null));

		kpiElementGroupIdKpiIdKpiTrendValueListMap.put(2, new HashMap<>());
		kpiElementGroupIdKpiIdKpiTrendValueListMap
				.get(2)
				.put(
						"kpi72",
						constructTrendValueList(
								DataCountGroup.class,
								List.of(
										Map.of(
												DATA_COUNT_GROUP_FILTER1,
												"Final Scope (Count)",
												DATA_COUNT_GROUP_FILTER2,
												"Overall"))));
		kpiElementGroupIdKpiIdKpiTrendValueListMap
				.get(2)
				.put("kpi39", constructTrendValueList(DataCount.class, null));

		kpiElementGroupIdKpiIdKpiTrendValueListMap.put(24, new HashMap<>());
		kpiElementGroupIdKpiIdKpiTrendValueListMap
				.get(24)
				.put(
						"kpi194",
						constructTrendValueList(
								DataCountGroup.class, List.of(Map.of(DATA_COUNT_GROUP_FILTER, "Overall"))));
		kpiElementGroupIdKpiIdKpiTrendValueListMap
				.get(24)
				.put("kpi111", constructTrendValueList(DataCount.class, null));
		kpiElementGroupIdKpiIdKpiTrendValueListMap
				.get(24)
				.put(
						"kpi35",
						constructTrendValueList(
								DataCountGroup.class, List.of(Map.of(DATA_COUNT_GROUP_FILTER, "Overall"))));
		kpiElementGroupIdKpiIdKpiTrendValueListMap
				.get(24)
				.put(
						"kpi190",
						constructTrendValueList(
								DataCountGroup.class, List.of(Map.of(DATA_COUNT_GROUP_FILTER, "Overall"))));

		kpiElementGroupIdKpiIdKpiTrendValueListMap.put(8, new HashMap<>());
		kpiElementGroupIdKpiIdKpiTrendValueListMap.get(8).put("kpi128", null);

		kpiElementGroupIdKpiIdKpiTrendValueListMap.put(30, new HashMap<>());
		kpiElementGroupIdKpiIdKpiTrendValueListMap
				.get(30)
				.put(
						"kpi8",
						constructTrendValueList(
								DataCountGroup.class, List.of(Map.of(DATA_COUNT_GROUP_FILTER, "Overall"))));

		kpiElementGroupIdKpiIdKpiTrendValueListMap.put(18, new HashMap<>());
		kpiElementGroupIdKpiIdKpiTrendValueListMap.get(18).put("kpi131", null);

		kpiElementGroupIdKpiIdKpiTrendValueListMap.put(6, new HashMap<>());
		kpiElementGroupIdKpiIdKpiTrendValueListMap
				.get(6)
				.put(
						"kpi160",
						constructTrendValueList(
								DataCountGroup.class,
								List.of(
										Map.of(
												DATA_COUNT_GROUP_FILTER1,
												"test-master-branch",
												DATA_COUNT_GROUP_FILTER2,
												"Overall"))));
		kpiElementGroupIdKpiIdKpiTrendValueListMap
				.get(6)
				.put(
						"kpi157",
						constructTrendValueList(
								DataCountGroup.class,
								List.of(
										Map.of(
												DATA_COUNT_GROUP_FILTER1,
												"test-master-branch",
												DATA_COUNT_GROUP_FILTER2,
												"Overall"))));
		kpiElementGroupIdKpiIdKpiTrendValueListMap
				.get(6)
				.put(
						"kpi158",
						constructTrendValueList(
								DataCountGroup.class,
								List.of(
										Map.of(
												DATA_COUNT_GROUP_FILTER1,
												"test-master-branch",
												DATA_COUNT_GROUP_FILTER2,
												"Overall"))));

		kpiElementGroupIdKpiIdKpiTrendValueListMap.put(7, new HashMap<>());
		kpiElementGroupIdKpiIdKpiTrendValueListMap
				.get(7)
				.put(
						"kpi180",
						constructTrendValueList(
								DataCountGroup.class,
								List.of(
										Map.of(
												DATA_COUNT_GROUP_FILTER1,
												"test-master-branch",
												DATA_COUNT_GROUP_FILTER2,
												"Overall"))));
		kpiElementGroupIdKpiIdKpiTrendValueListMap
				.get(7)
				.put(
						"kpi182",
						constructTrendValueList(
								DataCountGroup.class,
								List.of(
										Map.of(
												DATA_COUNT_GROUP_FILTER1,
												"test-master-branch",
												DATA_COUNT_GROUP_FILTER2,
												"Overall"))));

		return kpiElementGroupIdKpiIdKpiTrendValueListMap;
	}

	private static List<KpiMaster> constructKpiMasterList() {
		List<KpiMaster> kpiMasters = new ArrayList<>();

		kpiMasters.add(
				KpiMaster.builder()
						.aggregationCriteria("average")
						.calculateMaturity(false)
						.chartType("line")
						.combinedKpiSource("Jira/Azure/Rally")
						.defaultOrder(6)
						.groupId(24)
						.hideOverallFilter(false)
						.isAdditionalFilterSupport(true)
						.isDeleted("False")
						.isPositiveTrend(false)
						.isTrendCalculative(false)
						.kanban(false)
						.kpiFilter("dropDown")
						.kpiId("kpi190")
						.kpiName("Defect Reopen Rate")
						.kpiSource("Jira")
						.kpiUnit("%")
						.lowerThresholdBG("white")
						.maxValue("90")
						.showTrend(true)
						.thresholdValue(55D)
						.upperThresholdBG("red")
						.xAxisLabel("Sprints")
						.yAxisLabel("Percentage")
						.build());

		kpiMasters.add(
				KpiMaster.builder()
						.aggregationCriteria("average")
						.calculateMaturity(true)
						.chartType("line")
						.combinedKpiSource("Jenkins/Bamboo/GitHubAction/AzurePipeline/Teamcity/ArgoCD")
						.defaultOrder(24)
						.groupId(30)
						.hideOverallFilter(true)
						.isAdditionalFilterSupport(false)
						.isDeleted("False")
						.isPositiveTrend(false)
						.kanban(false)
						.kpiFilter("dropDown")
						.kpiId("kpi8")
						.kpiName("Code Build Time")
						.kpiSource("Jenkins")
						.kpiUnit("min")
						.lowerThresholdBG("white")
						.maturityRange(List.of("-45", "45-30", "30-15", "15-5", "5-"))
						.maxValue("100")
						.showTrend(true)
						.thresholdValue(6D)
						.upperThresholdBG("red")
						.xAxisLabel("Weeks")
						.yAxisLabel("Count(Mins)")
						.build());

		kpiMasters.add(
				KpiMaster.builder()
						.aggregationCriteria("average")
						.calculateMaturity(true)
						.chartType("line")
						.combinedKpiSource("Bitbucket/AzureRepository/GitHub/GitLab")
						.defaultOrder(3)
						.groupId(6)
						.hideOverallFilter(true)
						.isAdditionalFilterSupport(false)
						.isDeleted("false")
						.isPositiveTrend(false)
						.isRepoToolKpi(true)
						.kanban(false)
						.kpiCategory("Developer")
						.kpiFilter("dropDown")
						.kpiId("kpi160")
						.kpiName("Pickup Time")
						.kpiSource("BitBucket")
						.kpiUnit("Hours")
						.lowerThresholdBG("white")
						.maturityRange(List.of("-16", "16-8", "8-4", "4-2", "2-"))
						.maxValue("10")
						.showTrend(true)
						.thresholdValue(20D)
						.upperThresholdBG("red")
						.xAxisLabel("Weeks")
						.yAxisLabel("Count (Hours)")
						.build());

		kpiMasters.add(
				KpiMaster.builder()
						.aggregationCriteria("average")
						.barLegend("Commits")
						.calculateMaturity(true)
						.chartType("grouped_column_plus_line")
						.combinedKpiSource("Bitbucket/AzureRepository/GitHub/GitLab")
						.defaultOrder(1)
						.groupId(6)
						.hideOverallFilter(true)
						.isAdditionalFilterSupport(false)
						.isDeleted("False")
						.isPositiveTrend(true)
						.isRepoToolKpi(true)
						.kanban(false)
						.kpiCategory("Developer")
						.kpiFilter("dropDown")
						.kpiId("kpi157")
						.kpiName("Check-Ins & Merge Requests")
						.kpiSource("BitBucket")
						.kpiUnit("MRs")
						.lineLegend("Merge Requests")
						.maturityRange(List.of("-2", "2-4", "4-8", "8-16", "16-"))
						.maxValue("10")
						.showTrend(true)
						.thresholdValue(55D)
						.xAxisLabel("Days")
						.yAxisLabel("Count")
						.build());

		kpiMasters.add(
				KpiMaster.builder()
						.aggregationCriteria("average")
						.calculateMaturity(true)
						.chartType("line")
						.combinedKpiSource("Jira/Azure/Rally")
						.defaultOrder(30)
						.groupId(1)
						.hideOverallFilter(false)
						.isAdditionalFilterSupport(true)
						.isDeleted("False")
						.isPositiveTrend(false)
						.kanban(false)
						.kpiFilter("radioButton")
						.kpiId("kpi164")
						.kpiName("Scope Churn")
						.kpiSource("Jira")
						.kpiUnit("%")
						.lowerThresholdBG("white")
						.maturityRange(List.of("-50", "50-30", "30-20", "20-10", "10-"))
						.maxValue("200")
						.showTrend(true)
						.thresholdValue(20D)
						.upperThresholdBG("red")
						.xAxisLabel("Sprints")
						.yAxisLabel("Percentage")
						.build());

		kpiMasters.add(
				KpiMaster.builder()
						.aggregationCriteria("average")
						.calculateMaturity(false)
						.chartType("line")
						.combinedKpiSource("Bitbucket/AzureRepository/GitHub/GitLab")
						.defaultOrder(5)
						.groupId(7)
						.hideOverallFilter(true)
						.isAdditionalFilterSupport(false)
						.isDeleted("False")
						.isPositiveTrend(false)
						.isRepoToolKpi(true)
						.kanban(false)
						.kpiCategory("Developer")
						.kpiFilter("dropDown")
						.kpiId("kpi180")
						.kpiName("Revert Rate")
						.kpiSource("BitBucket")
						.kpiUnit("%")
						.lowerThresholdBG("white")
						.maturityRange(List.of("-80", "80-50", "50-20", "20-5", "5-"))
						.showTrend(true)
						.thresholdValue(50D)
						.upperThresholdBG("red")
						.xAxisLabel("Weeks")
						.yAxisLabel("Percentage")
						.build());

		kpiMasters.add(
				KpiMaster.builder()
						.aggregationCriteria("average")
						.calculateMaturity(false)
						.chartType("line")
						.combinedKpiSource("Bitbucket/AzureRepository/GitHub/GitLab")
						.defaultOrder(5)
						.groupId(7)
						.hideOverallFilter(true)
						.isAdditionalFilterSupport(false)
						.isDeleted("False")
						.isPositiveTrend(true)
						.isRepoToolKpi(true)
						.kanban(false)
						.kpiCategory("Developer")
						.kpiFilter("dropDown")
						.kpiId("kpi182")
						.kpiName("PR Success Rate")
						.kpiSource("BitBucket")
						.kpiUnit("%")
						.lowerThresholdBG("red")
						.maturityRange(List.of("-5", "5-20", "20-50", "50-80", "80-"))
						.showTrend(true)
						.thresholdValue(50D)
						.upperThresholdBG("white")
						.xAxisLabel("Weeks")
						.yAxisLabel("Percentage")
						.build());

		kpiMasters.add(
				KpiMaster.builder()
						.aggregationCriteria("sum")
						.calculateMaturity(false)
						.chartType("line")
						.combinedKpiSource("Jira/Azure/Rally")
						.defaultOrder(7)
						.groupId(24)
						.hideOverallFilter(false)
						.isAdditionalFilterSupport(true)
						.isDeleted("False")
						.isPositiveTrend(false)
						.isTrendCalculative(false)
						.kanban(false)
						.kpiFilter("multiSelectDropDown")
						.kpiId("kpi194")
						.kpiName("Defect Severity Index")
						.kpiSource("Jira")
						.kpiUnit("Number")
						.lowerThresholdBG("white")
						.maxValue("90")
						.showTrend(true)
						.thresholdValue(55D)
						.upperThresholdBG("red")
						.xAxisLabel("Sprints")
						.yAxisLabel("Count")
						.build());

		kpiMasters.add(
				KpiMaster.builder()
						.aggregationCriteria("average")
						.calculateMaturity(true)
						.chartType("line")
						.combinedKpiSource("Jira/Azure/Rally")
						.defaultOrder(3)
						.groupId(24)
						.hideOverallFilter(false)
						.isAdditionalFilterSupport(true)
						.isDeleted("False")
						.isPositiveTrend(false)
						.kanban(false)
						.kpiId("kpi111")
						.kpiName("Defect Density")
						.kpiSource("Jira")
						.kpiUnit("%")
						.lowerThresholdBG("white")
						.maturityRange(List.of("-50", "50-40", "40-20", "20-10", "10-"))
						.maxValue("500")
						.showTrend(true)
						.thresholdValue(25D)
						.upperThresholdBG("red")
						.xAxisLabel("Sprints")
						.yAxisLabel("Percentage")
						.build());

		kpiMasters.add(
				KpiMaster.builder()
						.aggregationCriteria("average")
						.calculateMaturity(true)
						.chartType("line")
						.combinedKpiSource("Jira/Azure/Rally")
						.defaultOrder(4)
						.groupId(24)
						.hideOverallFilter(false)
						.isAdditionalFilterSupport(true)
						.isDeleted("False")
						.isPositiveTrend(false)
						.kanban(false)
						.kpiFilter("dropDown")
						.kpiId("kpi35")
						.kpiName("Defect Seepage Rate")
						.kpiSource("Jira")
						.kpiUnit("%")
						.lowerThresholdBG("white")
						.maturityRange(List.of("-90", "90-75", "75-50", "50-25", "25-"))
						.maxValue("100")
						.showTrend(true)
						.thresholdValue(10D)
						.upperThresholdBG("red")
						.xAxisLabel("Sprints")
						.yAxisLabel("Percentage")
						.build());

		kpiMasters.add(
				KpiMaster.builder()
						.aggregationCriteria("average")
						.calculateMaturity(true)
						.chartType("line")
						.combinedKpiSource("Jira/Azure/Rally")
						.defaultOrder(18)
						.groupId(2)
						.hideOverallFilter(false)
						.isAdditionalFilterSupport(true)
						.isDeleted("False")
						.isPositiveTrend(true)
						.kanban(false)
						.kpiFilter("dropDown")
						.kpiId("kpi72")
						.kpiName("Commitment Reliability")
						.kpiSource("Jira")
						.kpiUnit("%")
						.lowerThresholdBG("red")
						.maturityRange(List.of("-60", "60-79", "80-94", "95-105", "105-"))
						.maxValue("200")
						.showTrend(true)
						.thresholdValue(85D)
						.upperThresholdBG("white")
						.xAxisLabel("Sprints")
						.yAxisLabel("Percentage")
						.build());

		kpiMasters.add(
				KpiMaster.builder()
						.aggregationCriteria("sum")
						.barLegend("Last 5 Sprints Average")
						.calculateMaturity(true)
						.chartType("grouped_column_plus_line")
						.combinedKpiSource("Jira/Azure/Rally")
						.defaultOrder(20)
						.groupId(2)
						.hideOverallFilter(false)
						.isAdditionalFilterSupport(true)
						.isDeleted("False")
						.isPositiveTrend(true)
						.kanban(false)
						.kpiId("kpi39")
						.kpiName("Sprint Velocity")
						.kpiSource("Jira")
						.kpiUnit("SP")
						.lineLegend("Sprint Velocity")
						.lowerThresholdBG("red")
						.maturityRange(List.of("1-2", "2-3", "3-4", "4-5", "5-6"))
						.maxValue("300")
						.showTrend(false)
						.thresholdValue(40D)
						.upperThresholdBG("white")
						.xAxisLabel("Sprints")
						.yAxisLabel("Count")
						.build());

		kpiMasters.add(
				KpiMaster.builder()
						.aggregationCriteria("sum")
						.barLegend("Estimated")
						.calculateMaturity(false)
						.chartType("grouped_column_plus_line")
						.combinedKpiSource("Jira/Azure/Rally")
						.defaultOrder(21)
						.groupId(1)
						.hideOverallFilter(false)
						.isAdditionalFilterSupport(true)
						.isDeleted("False")
						.isPositiveTrend(true)
						.kanban(false)
						.kpiId("kpi46")
						.kpiName("Sprint Capacity Utilization")
						.kpiSource("Jira")
						.kpiUnit("Hours")
						.lineLegend("Logged")
						.lowerThresholdBG("red")
						.maxValue("500")
						.showTrend(false)
						.thresholdValue(0D)
						.upperThresholdBG("white")
						.xAxisLabel("Sprints")
						.yAxisLabel("Hours")
						.build());

		kpiMasters.add(
				KpiMaster.builder()
						.aggregationCriteria("average")
						.calculateMaturity(true)
						.chartType("line")
						.combinedKpiSource("Bitbucket/AzureRepository/GitHub/GitLab")
						.defaultOrder(22)
						.groupId(7)
						.hideOverallFilter(true)
						.isAdditionalFilterSupport(false)
						.isDeleted("False")
						.isPositiveTrend(false)
						.isRepoToolKpi(false)
						.kanban(false)
						.kpiCategory("Developer")
						.kpiFilter("dropDown")
						.kpiId("kpi84")
						.kpiName("Mean Time To Merge")
						.kpiSource("BitBucket")
						.kpiUnit("Hours")
						.lowerThresholdBG("white")
						.maturityRange(List.of("-48", "48-16", "16-8", "8-4", "4-"))
						.maxValue("10")
						.showTrend(true)
						.thresholdValue(55D)
						.upperThresholdBG("red")
						.xAxisLabel("Weeks")
						.yAxisLabel("Count(Hours)")
						.build());

		kpiMasters.add(
				KpiMaster.builder()
						.boxType("3_column")
						.calculateMaturity(false)
						.chartType("grouped-bar-chart")
						.combinedKpiSource("Jira/Azure/Rally")
						.defaultOrder(7)
						.groupId(8)
						.hideOverallFilter(false)
						.isAdditionalFilterSupport(false)
						.isDeleted("False")
						.isPositiveTrend(true)
						.kanban(false)
						.kpiCategory("Iteration")
						.kpiFilter("multiSelectDropDown")
						.kpiId("kpi128")
						.kpiName("Work Status")
						.kpiSource("Jira")
						.kpiSubCategory("Iteration Review")
						.kpiUnit("Count")
						.kpiWidth(66)
						.showTrend(false)
						.isRawData(true)
						.kpiHeight(100)
						.build());

		kpiMasters.add(
				KpiMaster.builder()
						.boxType("3_column")
						.calculateMaturity(false)
						.chartType("stacked-bar")
						.combinedKpiSource("Jira/Azure/Rally")
						.defaultOrder(4)
						.groupId(18)
						.hideOverallFilter(false)
						.isAdditionalFilterSupport(false)
						.isDeleted("False")
						.isPositiveTrend(true)
						.kanban(false)
						.kpiCategory("Iteration")
						.kpiFilter("multiSelectDropDown")
						.kpiId("kpi131")
						.kpiName("Wastage")
						.kpiSource("Jira")
						.kpiSubCategory("Iteration Review")
						.kpiUnit("Hours")
						.kpiWidth(33)
						.showTrend(false)
						.isRawData(true)
						.kpiHeight(100)
						.build());

		for (KpiMaster kpiMaster : kpiMasters) {
			kpiMaster.setId(new ObjectId("507f1f77bcf86cd799439011"));
		}

		return kpiMasters;
	}
}
