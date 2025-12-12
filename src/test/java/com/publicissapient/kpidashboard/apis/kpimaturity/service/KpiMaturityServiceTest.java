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

package com.publicissapient.kpidashboard.apis.kpimaturity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceImpl;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.kpimaturity.dto.KpiMaturityRequest;
import com.publicissapient.kpidashboard.apis.kpimaturity.dto.KpiMaturityResponseDTO;
import com.publicissapient.kpidashboard.apis.model.AccountFilterRequest;
import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.kpimaturity.organization.EfficiencyScore;
import com.publicissapient.kpidashboard.common.model.kpimaturity.organization.KpiMaturity;
import com.publicissapient.kpidashboard.common.model.kpimaturity.organization.MaturityScore;
import com.publicissapient.kpidashboard.common.repository.kpimaturity.organization.KpiMaturityCustomRepository;
import com.publicissapient.kpidashboard.common.shared.enums.ProjectDeliveryMethodology;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;

@ExtendWith(MockitoExtension.class)
class KpiMaturityServiceTest {

	@Mock
	private KpiMaturityCustomRepository kpiMaturityCustomRepository;

	@Mock
	private FilterHelperService filterHelperService;

	@Mock
	private AccountHierarchyServiceImpl accountHierarchyServiceImpl;

	@InjectMocks
	private KpiMaturityService kpiMaturityService;

	private KpiMaturityRequest testKpiMaturityRequest;
	private HierarchyLevel testRequestedLevel;
	private HierarchyLevel testProjectLevel;
	private Set<AccountFilteredData> testAccountFilteredData;
	private List<KpiMaturity> testKpiMaturityList;

	@BeforeEach
	void setUp() {
		testKpiMaturityRequest = createTestKpiMaturityRequest();
		testRequestedLevel = createTestRequestedLevel();
		testProjectLevel = createTestProjectLevel();
		testAccountFilteredData = createTestAccountFilteredData();
		testKpiMaturityList = createTestKpiMaturityList();
	}

	@Test
	void when_GetKpiMaturityCalledWithScrumMethodology_Then_ReturnsScrumMaturityResponse() {
		// Arrange
		mockValidScrumScenario();

		// Act
		KpiMaturityResponseDTO result = kpiMaturityService.getKpiMaturity(testKpiMaturityRequest);

		// Assert
		assertNotNull(result);
		assertNotNull(result.getData());
		assertNotNull(result.getData().getMatrix());
		assertNotNull(result.getData().getMatrix().getRows());
		assertNotNull(result.getData().getMatrix().getColumns());
		assertFalse(result.getData().getMatrix().getRows().isEmpty());
		assertFalse(result.getData().getMatrix().getColumns().isEmpty());
	}

	@Test
	void when_GetKpiMaturityCalledWithNullRequest_Then_ThrowsBadRequestException() {
		// Act & Assert
		BadRequestException exception = assertThrows(BadRequestException.class,
				() -> kpiMaturityService.getKpiMaturity(null));

		assertEquals("Received null KPI maturity request", exception.getMessage());
	}

	@Test
	void when_GetKpiMaturityCalledWithEmptyLevelName_Then_ThrowsBadRequestException() {
		// Arrange
		KpiMaturityRequest invalidRequest = KpiMaturityRequest.builder().levelName("")
				.deliveryMethodology(ProjectDeliveryMethodology.SCRUM).build();

		// Act & Assert
		BadRequestException exception = assertThrows(BadRequestException.class,
				() -> kpiMaturityService.getKpiMaturity(invalidRequest));

		assertEquals("Level name must not be empty", exception.getMessage());
	}

	@Test
	void when_GetKpiMaturityCalledWithNullDeliveryMethodology_Then_ThrowsBadRequestException() {
		// Arrange
		KpiMaturityRequest invalidRequest = KpiMaturityRequest.builder().levelName("Account").deliveryMethodology(null)
				.build();

		// Act & Assert
		BadRequestException exception = assertThrows(BadRequestException.class,
				() -> kpiMaturityService.getKpiMaturity(invalidRequest));

		assertEquals("Delivery methodology must not be null", exception.getMessage());
	}

	@Test
	void when_GetKpiMaturityCalledWithNoUserAccess_Then_ThrowsForbiddenException() {
		// Arrange
		mockNoUserAccessScenario();

		// Act & Assert
		ForbiddenException exception = assertThrows(ForbiddenException.class,
				() -> kpiMaturityService.getKpiMaturity(testKpiMaturityRequest));

		assertEquals("Current user doesn't have access to any organization hierarchy data", exception.getMessage());
	}

	@Test
	void when_GetKpiMaturityCalledWithNoKpiMaturityData_Then_ReturnsEmptyResponse() {
		// Arrange
		mockValidHierarchyData();
		mockValidAccountData();
		when(kpiMaturityCustomRepository.getLatestKpiMaturityByCalculationDateForProjects(anySet()))
				.thenReturn(Collections.emptyList());

		// Act
		KpiMaturityResponseDTO result = kpiMaturityService.getKpiMaturity(testKpiMaturityRequest);

		// Assert
		assertNotNull(result);
		assertNull(result.getData());
	}

	@Test
	void when_ComputeBoardMaturityMetricsCalledWithValidScores_Then_ReturnsCorrectMetrics() {
		// Arrange
		Map<String, List<Double>> maturityScoresGroupedByCategory = Map.of("speed", List.of(3.0, 4.0, 5.0), "quality",
				List.of(2.0, 3.0));
		int numberOfProjects = 2;

		// Act
		Map<String, String> result = ReflectionTestUtils.invokeMethod(KpiMaturityService.class,
				"computeBoardMaturityMetrics", maturityScoresGroupedByCategory, numberOfProjects);

		// Assert
		assertNotNull(result);
		assertEquals("M6", result.get("speed")); // ceil((3+4+5)/2) = ceil(6) = 6
		assertEquals("M3", result.get("quality")); // ceil((2+3)/2) = ceil(2.5) = 3
	}

	@Test
	void when_DetermineHealthByEfficiencyPercentageCalledWithHighPercentage_Then_ReturnsHealthy() {
		// Act
		String result = ReflectionTestUtils.invokeMethod(KpiMaturityService.class,
				"determineHealthByEfficiencyPercentage", 85.0);

		// Assert
		assertEquals("Healthy", result);
	}

	@Test
	void when_DetermineHealthByEfficiencyPercentageCalledWithModeratePercentage_Then_ReturnsModerate() {
		// Act
		String result = ReflectionTestUtils.invokeMethod(KpiMaturityService.class,
				"determineHealthByEfficiencyPercentage", 65.0);

		// Assert
		assertEquals("Moderate", result);
	}

	@Test
	void when_DetermineHealthByEfficiencyPercentageCalledWithLowPercentage_Then_ReturnsUnhealthy() {
		// Act
		String result = (String) ReflectionTestUtils.invokeMethod(KpiMaturityService.class,
				"determineHealthByEfficiencyPercentage", 30.0);

		// Assert
		assertEquals("Unhealthy", result);
	}

	@Test
	void when_RequestedLevelIsNotSupportedCalledWithUnsupportedLevel_Then_ReturnsTrue() {
		// Arrange
		HierarchyLevel unsupportedLevel = HierarchyLevel.builder().level(6).build();
		HierarchyLevel projectLevel = HierarchyLevel.builder().level(5).build();

		// Act
		boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(kpiMaturityService,
				"requestedLevelIsNotSupported", unsupportedLevel, projectLevel, null));

		// Assert
		assertTrue(result);
	}

	@Test
	void when_RequestedLevelIsNotSupportedCalledWithSupportedLevel_Then_ReturnsFalse() {
		// Arrange
		HierarchyLevel supportedLevel = HierarchyLevel.builder().level(3).build();
		HierarchyLevel projectLevel = HierarchyLevel.builder().level(5).build();
		when(accountHierarchyServiceImpl.getHierarchyLevelByLevelNumber(4))
				.thenReturn(Optional.of(HierarchyLevel.builder().level(4).build()));

		// Act
		boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(kpiMaturityService,
				"requestedLevelIsNotSupported", supportedLevel, projectLevel, null));

		// Assert
		assertFalse(result);
	}

	@Test
	void when_MultipleLevelsAreCorrespondingToLevelNameCalledWithDuplicates_Then_ReturnsTrue() {
		// Arrange
		Map<String, HierarchyLevel> hierarchyLevels = Map.of("level1",
				HierarchyLevel.builder().hierarchyLevelName("Account").build(), "level2",
				HierarchyLevel.builder().hierarchyLevelName("Account").build());

		// Act
		boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(KpiMaturityService.class,
				"multipleLevelsAreCorrespondingToLevelName", "Account", hierarchyLevels));

		// Assert
		assertTrue(result);
	}

	@Test
	void when_MultipleLevelsAreCorrespondingToLevelNameCalledWithUniqueLevels_Then_ReturnsFalse() {
		// Arrange
		Map<String, HierarchyLevel> hierarchyLevels = Map.of("level1",
				HierarchyLevel.builder().hierarchyLevelName("Account").build(), "level2",
				HierarchyLevel.builder().hierarchyLevelName("Project").build());

		// Act
		boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(KpiMaturityService.class,
				"multipleLevelsAreCorrespondingToLevelName", "Account", hierarchyLevels));

		// Assert
		assertFalse(result);
	}

	// Helper methods for creating test data
	private KpiMaturityRequest createTestKpiMaturityRequest() {
		return KpiMaturityRequest.builder().levelName("Account").deliveryMethodology(ProjectDeliveryMethodology.SCRUM)
				.build();
	}

	private HierarchyLevel createTestRequestedLevel() {
		return HierarchyLevel.builder().hierarchyLevelId("acc").hierarchyLevelName("Account").level(3).build();
	}

	private HierarchyLevel createTestProjectLevel() {
		return HierarchyLevel.builder().hierarchyLevelId("project").hierarchyLevelName("Project").level(5).build();
	}

	private Set<AccountFilteredData> createTestAccountFilteredData() {
		return Set.of(
				AccountFilteredData.builder().nodeId("acc1").nodeName("Account 1").level(3).parentId("ver1").build(),
				AccountFilteredData.builder().nodeId("proj1").nodeName("Project 1").level(5).parentId("acc1").build(),
				AccountFilteredData.builder().nodeId("proj2").nodeName("Project 2").level(5).parentId("acc1").build());
	}

	private List<KpiMaturity> createTestKpiMaturityList() {
		KpiMaturity kpiMaturity1 = KpiMaturity.builder().build();
		kpiMaturity1.setHierarchyEntityNodeId("proj1");
		kpiMaturity1.setEfficiency(EfficiencyScore.builder().percentage(85.0).build());
		kpiMaturity1.setMaturityScores(List.of(MaturityScore.builder().kpiCategory("speed").score(4.0).build(),
				MaturityScore.builder().kpiCategory("quality").score(3.0).build()));

		KpiMaturity kpiMaturity2 = KpiMaturity.builder().build();
		kpiMaturity2.setHierarchyEntityNodeId("proj2");
		kpiMaturity2.setEfficiency(EfficiencyScore.builder().percentage(75.0).build());
		kpiMaturity2.setMaturityScores(List.of(MaturityScore.builder().kpiCategory("speed").score(3.0).build(),
				MaturityScore.builder().kpiCategory("quality").score(4.0).build()));

		return List.of(kpiMaturity1, kpiMaturity2);
	}

	private void mockValidScrumScenario() {
		mockValidHierarchyData();
		mockValidAccountData();
		when(kpiMaturityCustomRepository.getLatestKpiMaturityByCalculationDateForProjects(anySet()))
				.thenReturn(testKpiMaturityList);
	}

	private void mockValidHierarchyData() {
		Map<String, HierarchyLevel> hierarchyLevels = Map.of("acc", testRequestedLevel, "project", testProjectLevel);
		when(filterHelperService.getHierarchyLevelMap(false)).thenReturn(hierarchyLevels);
		when(accountHierarchyServiceImpl.getHierarchyLevelByLevelName("Account"))
				.thenReturn(Optional.of(testRequestedLevel));
		when(accountHierarchyServiceImpl.getHierarchyLevelByLevelId("project"))
				.thenReturn(Optional.of(testProjectLevel));
		when(accountHierarchyServiceImpl.getHierarchyLevelByLevelNumber(4))
				.thenReturn(Optional.of(HierarchyLevel.builder().level(4).build()));
	}

	private void mockValidAccountData() {
		when(accountHierarchyServiceImpl.getFilteredList(any(AccountFilterRequest.class)))
				.thenReturn(testAccountFilteredData);
	}

	private void mockNoUserAccessScenario() {
		mockValidHierarchyData();
		when(accountHierarchyServiceImpl.getFilteredList(any(AccountFilterRequest.class)))
				.thenReturn(Collections.emptySet());
	}
}
