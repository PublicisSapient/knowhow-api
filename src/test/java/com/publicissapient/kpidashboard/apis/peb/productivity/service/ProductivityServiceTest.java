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

package com.publicissapient.kpidashboard.apis.peb.productivity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceImpl;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.peb.productivity.dto.CategoryScoresDTO;
import com.publicissapient.kpidashboard.apis.peb.productivity.dto.KPITrends;
import com.publicissapient.kpidashboard.apis.peb.productivity.dto.OrganizationEntityProductivity;
import com.publicissapient.kpidashboard.apis.peb.productivity.dto.ProductivityResponse;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.productivity.calculation.CategoryScores;
import com.publicissapient.kpidashboard.common.model.productivity.calculation.KPIData;
import com.publicissapient.kpidashboard.common.model.productivity.calculation.Productivity;
import com.publicissapient.kpidashboard.common.repository.productivity.ProductivityCustomRepository;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;

@ExtendWith(MockitoExtension.class)
class ProductivityServiceTest {

	@Mock
	private FilterHelperService filterHelperService;
	@Mock
	private AccountHierarchyServiceImpl accountHierarchyServiceImpl;

	@Mock
	private ProductivityCustomRepository productivityCustomRepository;

	@InjectMocks
	private ProductivityService productivityService;

	private String testLevelName;

	@Test
	void when_LevelNameIsNotProvided_Expect_GettingProductivityThrowsBadRequestException() {
		assertThrows(BadRequestException.class, () -> productivityService.getProductivityForLevel(null));
		assertThrows(BadRequestException.class, () -> productivityService.getProductivityForLevel(StringUtils.EMPTY));
	}

	@Test
	void when_MultipleLevelNamesAreCorrespondingToTheRequestedLevel_Expect_GettingProductivityThrowsInternalServerErrorException() {
		testLevelName = "duplicate-level-name";
		HierarchyLevel hierarchyLevel = new HierarchyLevel();
		hierarchyLevel.setHierarchyLevelName("duplicate-level-name");

		HierarchyLevel hierarchyLevel1 = new HierarchyLevel();
		hierarchyLevel1.setHierarchyLevelName("duplicate-level-name");

		Map<String, HierarchyLevel> hierarchyLevelMap = Map.of("testLevelId", hierarchyLevel, "testLevelId1",
				hierarchyLevel1);

		when(filterHelperService.getHierarchyLevelMap(false)).thenReturn(hierarchyLevelMap);

		assertThrows(InternalServerErrorException.class,
				() -> productivityService.getProductivityForLevel(testLevelName));
	}

	@Test
	void when_RequestedLevelNameDoesNotExist_Expect_GettingProductivityThrowsNotFoundException() {
		when(filterHelperService.getHierarchyLevelMap(false)).thenReturn(constructTestHierarchyLevelMap());

		assertThrows(NotFoundException.class, () -> productivityService.getProductivityForLevel("not-existent"));
	}

	@Test
	void when_ProjectLevelDoesNotExist_Expect_GettingProductivityThrowsNotFoundException() {
		when(filterHelperService.getHierarchyLevelMap(false)).thenReturn(Map.of("sqd",
				HierarchyLevel.builder().level(7).hierarchyLevelId("sqd").hierarchyLevelName("Squad").build(),
				"release",
				HierarchyLevel.builder().level(6).hierarchyLevelId("release").hierarchyLevelName("Release").build(),
				"sprint",
				HierarchyLevel.builder().level(6).hierarchyLevelId("sprint").hierarchyLevelName("Sprint").build(),
				"port",
				HierarchyLevel.builder().level(4).hierarchyLevelId("port").hierarchyLevelName("Engagement").build(),
				"acc", HierarchyLevel.builder().level(3).hierarchyLevelId("acc").hierarchyLevelName("Account").build(),
				"ver", HierarchyLevel.builder().level(2).hierarchyLevelId("ver").hierarchyLevelName("Vertical").build(),
				"bu", HierarchyLevel.builder().level(1).hierarchyLevelId("bu").hierarchyLevelName("BU").build()));

		assertThrows(InternalServerErrorException.class,
				() -> productivityService.getProductivityForLevel("engagement"));
	}

	@Test
	void when_RequestedLevelIsNotSupported_Expect_GettingProductivityThrowsBadRequestException() {
		when(filterHelperService.getHierarchyLevelMap(false)).thenReturn(constructTestHierarchyLevelMap());

		assertThrows(BadRequestException.class, () -> productivityService.getProductivityForLevel("squad"));
		assertThrows(BadRequestException.class, () -> productivityService.getProductivityForLevel("sprint"));
		assertThrows(BadRequestException.class, () -> productivityService.getProductivityForLevel("project"));
	}

	@Test
	void when_UserDoesNotHaveAccessToAnyData_Expect_GettingProductivityThrowsForbiddenException() {
		when(filterHelperService.getHierarchyLevelMap(false)).thenReturn(constructTestHierarchyLevelMap());
		when(accountHierarchyServiceImpl.getFilteredList(any())).thenReturn(Collections.emptySet());

		assertThrows(ForbiddenException.class, () -> productivityService.getProductivityForLevel("engagement"));
	}

	private static List<String> provideTestLevelNames() {
		return List.of("engagement", "account", "vertical", "bu");
	}

	@ParameterizedTest
	@MethodSource("provideTestLevelNames")
	void when_RequestIsValid_Expect_ProductivityResponseIsComputedAccordingly(String levelName) {
		when(filterHelperService.getHierarchyLevelMap(false)).thenReturn(constructTestHierarchyLevelMap());
		when(accountHierarchyServiceImpl.getFilteredList(any())).thenReturn(constructTestAccountFilteredData());
		when(productivityCustomRepository.getLatestProductivityByCalculationDateForProjects(anySet()))
				.thenReturn(constructProjectProductivityList());

		ServiceResponse serviceResponse = productivityService.getProductivityForLevel(levelName);
		assertNotNull(serviceResponse);
		assertNotNull(serviceResponse.getData());
		assertTrue(serviceResponse.getSuccess());
		assertInstanceOf(ProductivityResponse.class, serviceResponse.getData());

		ProductivityResponse productivityResponse = (ProductivityResponse) serviceResponse.getData();
		assertNotNull(productivityResponse.getSummary());
		OrganizationEntityProductivity summary = productivityResponse.getSummary();
		assertTrue(summary.getLevelName().equalsIgnoreCase(levelName));
		assertNotNull(summary.getCategoryScores());

		CategoryScoresDTO summaryCategoryScoresDTO = summary.getCategoryScores();
		assertEquals(0, Double.compare(summaryCategoryScoresDTO.getOverall(), 4.14D));
		assertEquals(0, Double.compare(summaryCategoryScoresDTO.getSpeed(), -32.86D));
		assertEquals(0, Double.compare(summaryCategoryScoresDTO.getQuality(), -18.75D));
		assertEquals(0, Double.compare(summaryCategoryScoresDTO.getEfficiency(), 79.05));
		assertEquals(0, Double.compare(summaryCategoryScoresDTO.getProductivity(), -21.04));

		assertNotNull(summary.getTrends());
		KPITrends kpiTrends = summary.getTrends();
		assertTrue(CollectionUtils.isNotEmpty(kpiTrends.getPositive()));
		assertTrue(kpiTrends.getPositive().stream().allMatch(kpiTrend -> kpiTrend.getTrendValue() > 0.0D));
		assertEquals(1, kpiTrends.getPositive().size());
		assertEquals("Work Status", kpiTrends.getPositive().get(0).getKpiName());

		assertTrue(CollectionUtils.isNotEmpty(kpiTrends.getNegative()));
		assertTrue(kpiTrends.getNegative().stream().allMatch(kpiTrend -> kpiTrend.getTrendValue() < 0.0D));
		assertEquals(3, kpiTrends.getNegative().size());
		List<String> expectedNegativeTrendKpiNames = List.of("Sprint Velocity", "Commitment Reliability", "Wastage");
		assertTrue(kpiTrends.getNegative().stream()
				.allMatch(kpiTrend -> expectedNegativeTrendKpiNames.contains(kpiTrend.getKpiName())));

		assertTrue(CollectionUtils.isNotEmpty(productivityResponse.getDetails()));
		List<String> expectedOrganizationUnitNames = getOrganizationUnitNames(levelName);

		assertTrue(
				productivityResponse.getDetails().stream().allMatch(productivityDetail -> expectedOrganizationUnitNames
						.contains(productivityDetail.getOrganizationEntityName())));
	}

	private List<Productivity> constructProjectProductivityList() {
		Productivity testProductivity1 = new Productivity();
		testProductivity1.setHierarchyLevel(5);
		testProductivity1.setHierarchyEntityNodeId("project-node-id-1");
		testProductivity1.setHierarchyEntityName("test-project-1");
		testProductivity1.setHierarchyLevelId("project");

		CategoryScores testCategoryScores1 = new CategoryScores();
		testCategoryScores1.setOverall(-10.91);
		testCategoryScores1.setProductivity(-45.45);
		testCategoryScores1.setSpeed(-65.73);
		testCategoryScores1.setEfficiency(83.33);
		testCategoryScores1.setQuality(-37.5);

		List<KPIData> kpis1 = List.of(
				KPIData.builder().kpiId("kpi131").name("Wastage").category("efficiency").variationPercentage(-33.33)
						.calculationValue(-400.0).build(),
				KPIData.builder().kpiId("kpi128").name("Work Status").category("efficiency").variationPercentage(-41.67)
						.calculationValue(-500.0).build(),
				KPIData.builder().kpiId("kpi72").name("Commitment Reliability").category("productivity")
						.variationPercentage(-45.45).calculationValue(-545.4545454545455).build(),
				KPIData.builder().kpiId("kpi39").name("Sprint Velocity").category("speed")
						.variationPercentage(-216.48148148148147).calculationValue(-18.04).build());

		testProductivity1.setCategoryScores(testCategoryScores1);
		testProductivity1.setKpis(kpis1);

		Productivity testProductivity2 = new Productivity();
		testProductivity2.setHierarchyLevel(5);
		testProductivity2.setHierarchyEntityNodeId("project-node-id-2");
		testProductivity2.setHierarchyEntityName("test-project-1");
		testProductivity2.setHierarchyLevelId("project");

		CategoryScores testCategoryScores2 = new CategoryScores();
		testCategoryScores2.setOverall(19.2);
		testCategoryScores2.setProductivity(3.37);
		testCategoryScores2.setSpeed(0);
		testCategoryScores2.setEfficiency(74.77);
		testCategoryScores2.setQuality(0);

		List<KPIData> kpis2 = List.of(
				KPIData.builder().kpiId("kpi128").name("Work Status").category("efficiency").variationPercentage(74.77)
						.calculationValue(897.2789115646258).build(),
				KPIData.builder().kpiId("kpi72").name("Commitment Reliability").category("productivity")
						.variationPercentage(3.37).calculationValue(40.4040404040404).build());

		testProductivity2.setCategoryScores(testCategoryScores2);
		testProductivity2.setKpis(kpis2);

		return List.of(testProductivity1, testProductivity2);
	}

	private Set<AccountFilteredData> constructTestAccountFilteredData() {
		return Set.of(
				AccountFilteredData.builder().nodeId("bu-node-id-1").level(1).nodeName("test-bu").labelName("bu")
						.build(),
				AccountFilteredData.builder().nodeId("ver-node-id-1").level(2).nodeName("test-ver-1").labelName("ver")
						.parentId("bu-node-id-1").build(),
				AccountFilteredData.builder().nodeId("acc-node-id-1").level(3).nodeName("test-acc-1").labelName("acc")
						.parentId("ver-node-id-1").build(),
				AccountFilteredData.builder().nodeId("port-node-id-1").level(4).nodeName("test-port-1")
						.labelName("port").parentId("acc-node-id-1").build(),
				AccountFilteredData.builder().nodeId("project-node-id-1").level(5).nodeName("test-project-1")
						.labelName("project").parentId("port-node-id-1").build(),
				AccountFilteredData.builder().nodeId("project-node-id-2").level(5).nodeName("test-project-2")
						.labelName("project").parentId("port-node-id-1").build(),
				AccountFilteredData.builder().nodeId("project-node-id-3").level(5).nodeName("test-project-3")
						.labelName("project").parentId("port-node-id-1").build(),
				AccountFilteredData.builder().nodeId("project-node-id-4").level(5).nodeName("test-project-4")
						.labelName("project").parentId("port-node-id-1").build(),
				AccountFilteredData.builder().nodeId("project-node-id-5").level(5).nodeName("test-project-5")
						.labelName("project").parentId("port-node-id-1").build(),
				AccountFilteredData.builder().nodeId("project-node-id-6").level(5).nodeName("test-project-6")
						.labelName("project").parentId("port-node-id-1").build());
	}

	private Map<String, HierarchyLevel> constructTestHierarchyLevelMap() {
		return Map.of("sqd",
				HierarchyLevel.builder().level(7).hierarchyLevelId("sqd").hierarchyLevelName("Squad").build(),
				"release",
				HierarchyLevel.builder().level(6).hierarchyLevelId("release").hierarchyLevelName("Release").build(),
				"sprint",
				HierarchyLevel.builder().level(6).hierarchyLevelId("sprint").hierarchyLevelName("Sprint").build(),
				"project",
				HierarchyLevel.builder().level(5).hierarchyLevelId("project").hierarchyLevelName("Project").build(),
				"port",
				HierarchyLevel.builder().level(4).hierarchyLevelId("port").hierarchyLevelName("Engagement").build(),
				"acc", HierarchyLevel.builder().level(3).hierarchyLevelId("acc").hierarchyLevelName("Account").build(),
				"ver", HierarchyLevel.builder().level(2).hierarchyLevelId("ver").hierarchyLevelName("Vertical").build(),
				"bu", HierarchyLevel.builder().level(1).hierarchyLevelId("bu").hierarchyLevelName("BU").build());
	}

	private static List<String> getOrganizationUnitNames(String levelName) {
		List<String> expectedOrganizationUnitNames;

		switch (levelName) {
			case "engagement" -> expectedOrganizationUnitNames = List.of("test-project-1", "test-project-2");
			case "account" -> expectedOrganizationUnitNames = List.of("test-port-1", "test-port-2");
			case "vertical" -> expectedOrganizationUnitNames = List.of("test-acc-1", "test-acc-2");
			case "bu" -> expectedOrganizationUnitNames = List.of("test-ver-1", "test-ver-2");
			default -> expectedOrganizationUnitNames = Collections.emptyList();
		}
		return expectedOrganizationUnitNames;
	}
}
