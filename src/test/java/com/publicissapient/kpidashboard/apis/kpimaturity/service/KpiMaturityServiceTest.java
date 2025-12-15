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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceImpl;
import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceKanbanImpl;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.kpimaturity.dto.KpiMaturityRequest;
import com.publicissapient.kpidashboard.apis.kpimaturity.dto.KpiMaturityResponse;
import com.publicissapient.kpidashboard.apis.kpimaturity.dto.OrganizationEntityKpiMaturity;
import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.kpimaturity.organization.EfficiencyScore;
import com.publicissapient.kpidashboard.common.model.kpimaturity.organization.KpiMaturity;
import com.publicissapient.kpidashboard.common.model.kpimaturity.organization.MaturityScore;
import com.publicissapient.kpidashboard.common.repository.kpimaturity.organization.KpiMaturityCustomRepository;
import com.publicissapient.kpidashboard.common.shared.enums.ProjectDeliveryMethodology;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;

@ExtendWith(MockitoExtension.class)
class KpiMaturityServiceTest {

	@Mock
	private KpiMaturityCustomRepository kpiMaturityCustomRepository;

	@Mock
	private FilterHelperService filterHelperService;

	@Mock
	private AccountHierarchyServiceImpl accountHierarchyServiceImpl;

	@Mock
	private AccountHierarchyServiceKanbanImpl accountHierarchyServiceKanbanImpl;

	@InjectMocks
	private KpiMaturityService kpiMaturityService;

	private String testLevelName;

	@Test
	void when_KpiMaturityRequestIsNull_Expect_GetKpiMaturityThrowsBadRequestException() {
		assertThrows(BadRequestException.class, () -> kpiMaturityService.getKpiMaturity(null));
	}

	@Test
	@SuppressWarnings("java:S5778")
	void when_LevelNameIsNotProvided_Expect_BadRequestException() {
		assertThrows(BadRequestException.class,
				() -> kpiMaturityService.getKpiMaturity(KpiMaturityRequest.builder().levelName(null).build()));
		assertThrows(BadRequestException.class, () -> kpiMaturityService
				.getKpiMaturity(KpiMaturityRequest.builder().levelName(StringUtils.EMPTY).build()));
	}

	@Test
	@SuppressWarnings("java:S5778")
	void when_DeliveryMethodologyIsNotProvided_Expect_GetKpiMaturityThrowsBadRequestException() {
		assertThrows(BadRequestException.class, () -> kpiMaturityService
				.getKpiMaturity(KpiMaturityRequest.builder().levelName("bu").deliveryMethodology(null).build()));
	}

	@Test
	@SuppressWarnings("java:S5778")
	void when_MultipleLevelNamesAreCorrespondingToTheRequestedLevel_Expect_GetKpiMaturityThrowsInternalServerErrorException() {
		testLevelName = "duplicate-level-name";
		HierarchyLevel hierarchyLevel = new HierarchyLevel();
		hierarchyLevel.setHierarchyLevelName("duplicate-level-name");

		HierarchyLevel hierarchyLevel1 = new HierarchyLevel();
		hierarchyLevel1.setHierarchyLevelName("duplicate-level-name");

		Map<String, HierarchyLevel> hierarchyLevelMap = Map.of("testLevelId", hierarchyLevel, "testLevelId1",
				hierarchyLevel1);

		when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);

		assertThrows(InternalServerErrorException.class, () -> kpiMaturityService.getKpiMaturity(KpiMaturityRequest
				.builder().levelName(testLevelName).deliveryMethodology(ProjectDeliveryMethodology.KANBAN).build()));
	}

	@ParameterizedTest
	@EnumSource(value = ProjectDeliveryMethodology.class, names = { "SCRUM", "KANBAN" })
	@SuppressWarnings("java:S5778")
	void when_RequestedLevelNameDoesNotExist_Expect_GetKpiMaturityThrowsNotFoundException(
			ProjectDeliveryMethodology deliveryMethodology) {
		when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(constructTestHierarchyLevelMap());

		assertThrows(NotFoundException.class, () -> kpiMaturityService.getKpiMaturity(KpiMaturityRequest.builder()
				.levelName("not-existent").deliveryMethodology(deliveryMethodology).build()));
	}

	@ParameterizedTest
	@EnumSource(value = ProjectDeliveryMethodology.class, names = { "SCRUM", "KANBAN" })
	@SuppressWarnings("java:S5778")
	void when_ProjectLevelDoesNotExist_Expect_GetKpiMaturityThrowsNotFoundException(
			ProjectDeliveryMethodology deliveryMethodology) {
		when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(Map.of("sqd",
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

		if (deliveryMethodology == ProjectDeliveryMethodology.SCRUM) {
			when(accountHierarchyServiceImpl.getHierarchyLevelByLevelName(anyString()))
					.thenReturn(Optional.of(HierarchyLevel.builder().build()));
		} else if (deliveryMethodology == ProjectDeliveryMethodology.KANBAN) {
			when(accountHierarchyServiceKanbanImpl.getHierarchyLevelByLevelName(anyString()))
					.thenReturn(Optional.of(HierarchyLevel.builder().build()));
		}

		assertThrows(InternalServerErrorException.class, () -> kpiMaturityService.getKpiMaturity(
				KpiMaturityRequest.builder().levelName("engagement").deliveryMethodology(deliveryMethodology).build()));
	}

	@ParameterizedTest
	@EnumSource(value = ProjectDeliveryMethodology.class, names = { "SCRUM", "KANBAN" })
	@SuppressWarnings("java:S5778")
	void when_RequestedLevelIsNotSupported_Expect_GetMaturityThrowsBadRequestException(
			ProjectDeliveryMethodology deliveryMethodology) {
		Map<String, HierarchyLevel> hierarchyLevelMap = constructTestHierarchyLevelMap();
		when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);

		if (deliveryMethodology == ProjectDeliveryMethodology.SCRUM) {
			when(accountHierarchyServiceImpl.getHierarchyLevelByLevelId(anyString())).thenAnswer(
					invocationMock -> Optional.of(hierarchyLevelMap.get((String) invocationMock.getArgument(0))));
			when(accountHierarchyServiceImpl.getHierarchyLevelByLevelName(anyString())).thenAnswer(
					invocationOnMock -> hierarchyLevelMap.values().stream().filter(hierarchyLevel -> hierarchyLevel
							.getHierarchyLevelName().equalsIgnoreCase(invocationOnMock.getArgument(0))).findFirst());
		} else if (deliveryMethodology == ProjectDeliveryMethodology.KANBAN) {
			when(accountHierarchyServiceKanbanImpl.getHierarchyLevelByLevelId(anyString())).thenAnswer(
					invocationMock -> Optional.of(hierarchyLevelMap.get((String) invocationMock.getArgument(0))));
			when(accountHierarchyServiceKanbanImpl.getHierarchyLevelByLevelName(anyString())).thenAnswer(
					invocationOnMock -> hierarchyLevelMap.values().stream().filter(hierarchyLevel -> hierarchyLevel
							.getHierarchyLevelName().equalsIgnoreCase(invocationOnMock.getArgument(0))).findFirst());
		}

		assertThrows(BadRequestException.class, () -> kpiMaturityService.getKpiMaturity(
				KpiMaturityRequest.builder().levelName("squad").deliveryMethodology(deliveryMethodology).build()));
		assertThrows(BadRequestException.class, () -> kpiMaturityService.getKpiMaturity(
				KpiMaturityRequest.builder().levelName("sprint").deliveryMethodology(deliveryMethodology).build()));
	}

	@ParameterizedTest
	@EnumSource(value = ProjectDeliveryMethodology.class, names = { "SCRUM", "KANBAN" })
	@SuppressWarnings("java:S5778")
	void when_UserDoesNotHaveAccessToAnyData_Expect_GetKpiMaturityThrowsForbiddenException(
			ProjectDeliveryMethodology deliveryMethodology) {
		when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(constructTestHierarchyLevelMap());

		if (deliveryMethodology == ProjectDeliveryMethodology.SCRUM) {
			when(accountHierarchyServiceImpl.getFilteredList(any())).thenReturn(Collections.emptySet());
			when(accountHierarchyServiceImpl.getHierarchyLevelByLevelName(anyString()))
					.thenReturn(Optional.of(HierarchyLevel.builder().build()));
			when(accountHierarchyServiceImpl.getHierarchyLevelByLevelId(anyString()))
					.thenReturn(Optional.of(HierarchyLevel.builder().build()));
		} else if (deliveryMethodology == ProjectDeliveryMethodology.KANBAN) {
			when(accountHierarchyServiceKanbanImpl.getFilteredList(any())).thenReturn(Collections.emptySet());
			when(accountHierarchyServiceKanbanImpl.getHierarchyLevelByLevelName(anyString()))
					.thenReturn(Optional.of(HierarchyLevel.builder().build()));
			when(accountHierarchyServiceKanbanImpl.getHierarchyLevelByLevelId(anyString()))
					.thenReturn(Optional.of(HierarchyLevel.builder().build()));
		}

		assertThrows(ForbiddenException.class, () -> kpiMaturityService.getKpiMaturity(
				KpiMaturityRequest.builder().levelName("engagement").deliveryMethodology(deliveryMethodology).build()));
	}

	@ParameterizedTest
	@EnumSource(value = ProjectDeliveryMethodology.class, names = { "SCRUM", "KANBAN" })
	@SuppressWarnings("java:S5778")
	void when_OrganizationEntityCorrespondingToTheRequestedParentNodeIdIsNotFound_Expect_KpiMaturityCalculationThrowsBadRequestException(
			ProjectDeliveryMethodology deliveryMethodology) {
		Map<String, HierarchyLevel> hierarchyLevelMap = constructTestHierarchyLevelMap();

		when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);

		if (deliveryMethodology == ProjectDeliveryMethodology.SCRUM) {
			when(accountHierarchyServiceImpl.getFilteredList(any())).thenReturn(constructTestAccountFilteredData());

			when(accountHierarchyServiceImpl.getHierarchyLevelByLevelId(anyString())).thenAnswer(
					invocationMock -> Optional.of(hierarchyLevelMap.get((String) invocationMock.getArgument(0))));
			when(accountHierarchyServiceImpl.getHierarchyLevelByLevelName(anyString())).thenAnswer(
					invocationOnMock -> hierarchyLevelMap.values().stream().filter(hierarchyLevel -> hierarchyLevel
							.getHierarchyLevelName().equalsIgnoreCase(invocationOnMock.getArgument(0))).findFirst());
		} else if (deliveryMethodology == ProjectDeliveryMethodology.KANBAN) {
			when(accountHierarchyServiceKanbanImpl.getFilteredList(any()))
					.thenReturn(constructTestAccountFilteredData());
			when(accountHierarchyServiceKanbanImpl.getHierarchyLevelByLevelId(anyString())).thenAnswer(
					invocationMock -> Optional.of(hierarchyLevelMap.get((String) invocationMock.getArgument(0))));
			when(accountHierarchyServiceKanbanImpl.getHierarchyLevelByLevelName(anyString())).thenAnswer(
					invocationOnMock -> hierarchyLevelMap.values().stream().filter(hierarchyLevel -> hierarchyLevel
							.getHierarchyLevelName().equalsIgnoreCase(invocationOnMock.getArgument(0))).findFirst());
		}

		assertThrows(BadRequestException.class,
				() -> kpiMaturityService.getKpiMaturity(KpiMaturityRequest.builder().levelName("engagement")
						.parentNodeId("invalid-node-id").deliveryMethodology(deliveryMethodology).build()));
	}

	@ParameterizedTest
	@EnumSource(value = ProjectDeliveryMethodology.class, names = { "SCRUM", "KANBAN" })
	@SuppressWarnings("java:S5778")
	void when_RequestedLevelIsNotCorrespondingToAnyChildLevelOfTheOrganizationEntityWithTheRequestedParentNodeId_Expect_KpiMaturityCalculationThrowsBadRequestException(
			ProjectDeliveryMethodology deliveryMethodology) {
		Map<String, HierarchyLevel> hierarchyLevelMap = constructTestHierarchyLevelMap();

		when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);

		if (deliveryMethodology == ProjectDeliveryMethodology.SCRUM) {
			when(accountHierarchyServiceImpl.getFilteredList(any())).thenReturn(constructTestAccountFilteredData());

			when(accountHierarchyServiceImpl.getHierarchyLevelByLevelId(anyString())).thenAnswer(
					invocationMock -> Optional.of(hierarchyLevelMap.get((String) invocationMock.getArgument(0))));
			when(accountHierarchyServiceImpl.getHierarchyLevelByLevelName(anyString())).thenAnswer(
					invocationOnMock -> hierarchyLevelMap.values().stream().filter(hierarchyLevel -> hierarchyLevel
							.getHierarchyLevelName().equalsIgnoreCase(invocationOnMock.getArgument(0))).findFirst());
		} else if (deliveryMethodology == ProjectDeliveryMethodology.KANBAN) {
			when(accountHierarchyServiceKanbanImpl.getFilteredList(any()))
					.thenReturn(constructTestAccountFilteredData());
			when(accountHierarchyServiceKanbanImpl.getHierarchyLevelByLevelId(anyString())).thenAnswer(
					invocationMock -> Optional.of(hierarchyLevelMap.get((String) invocationMock.getArgument(0))));
			when(accountHierarchyServiceKanbanImpl.getHierarchyLevelByLevelName(anyString())).thenAnswer(
					invocationOnMock -> hierarchyLevelMap.values().stream().filter(hierarchyLevel -> hierarchyLevel
							.getHierarchyLevelName().equalsIgnoreCase(invocationOnMock.getArgument(0))).findFirst());
		}

		assertThrows(BadRequestException.class, () -> kpiMaturityService.getKpiMaturity(KpiMaturityRequest.builder()
				.levelName("bu").parentNodeId("acc-node-id-1").deliveryMethodology(deliveryMethodology).build()));
	}

	@ParameterizedTest
	@EnumSource(value = ProjectDeliveryMethodology.class, names = { "SCRUM", "KANBAN" })
	void when_NoKpiMaturityDataCanBeFound_Expect_KpiMaturityCalculationReturnsEmptyResponse(
			ProjectDeliveryMethodology deliveryMethodology) {
		testLevelName = "engagement";
		Map<String, HierarchyLevel> hierarchyLevelMap = constructTestHierarchyLevelMap();

		when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);
		when(kpiMaturityCustomRepository.getLatestKpiMaturityByCalculationDateForProjects(anySet()))
				.thenReturn(Collections.emptyList());

		if (deliveryMethodology == ProjectDeliveryMethodology.SCRUM) {
			when(accountHierarchyServiceImpl.getFilteredList(any())).thenReturn(constructTestAccountFilteredData());
			when(accountHierarchyServiceImpl.getHierarchyLevelByLevelId(anyString())).thenAnswer(
					invocationMock -> Optional.of(hierarchyLevelMap.get((String) invocationMock.getArgument(0))));
			when(accountHierarchyServiceImpl.getHierarchyLevelByLevelName(anyString())).thenAnswer(
					invocationOnMock -> hierarchyLevelMap.values().stream().filter(hierarchyLevel -> hierarchyLevel
							.getHierarchyLevelName().equalsIgnoreCase(invocationOnMock.getArgument(0))).findFirst());
		} else if (deliveryMethodology == ProjectDeliveryMethodology.KANBAN) {
			when(accountHierarchyServiceKanbanImpl.getFilteredList(any()))
					.thenReturn(constructTestAccountFilteredData());
			when(accountHierarchyServiceKanbanImpl.getHierarchyLevelByLevelId(anyString())).thenAnswer(
					invocationMock -> Optional.of(hierarchyLevelMap.get((String) invocationMock.getArgument(0))));
			when(accountHierarchyServiceKanbanImpl.getHierarchyLevelByLevelName(anyString())).thenAnswer(
					invocationOnMock -> hierarchyLevelMap.values().stream().filter(hierarchyLevel -> hierarchyLevel
							.getHierarchyLevelName().equalsIgnoreCase(invocationOnMock.getArgument(0))).findFirst());
		}

		ServiceResponse serviceResponse = kpiMaturityService.getKpiMaturity(
				KpiMaturityRequest.builder().levelName(testLevelName).deliveryMethodology(deliveryMethodology).build());
		assertNotNull(serviceResponse);
		assertNotNull(serviceResponse.getData());
		assertTrue(serviceResponse.getSuccess());
		assertInstanceOf(KpiMaturityResponse.class, serviceResponse.getData());

		KpiMaturityResponse kpiMaturityResponse = (KpiMaturityResponse) serviceResponse.getData();
		assertNull(kpiMaturityResponse.getSummary());
		assertNull(kpiMaturityResponse.getDetails());
	}

	@ParameterizedTest
	@EnumSource(value = ProjectDeliveryMethodology.class, names = { "SCRUM", "KANBAN" })
	void when_RequestIsValid_Expect_KpiMaturityResponseIsComputedAccordingly(
			ProjectDeliveryMethodology deliveryMethodology) {
		testLevelName = "engagement";
		Map<String, HierarchyLevel> hierarchyLevelMap = constructTestHierarchyLevelMap();

		when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);
		when(kpiMaturityCustomRepository.getLatestKpiMaturityByCalculationDateForProjects(anySet()))
				.thenReturn(constructProjectKpiMaturityList());

		if (deliveryMethodology == ProjectDeliveryMethodology.SCRUM) {
			when(accountHierarchyServiceImpl.getFilteredList(any())).thenReturn(constructTestAccountFilteredData());
			when(accountHierarchyServiceImpl.getHierarchyLevelByLevelId(anyString())).thenAnswer(
					invocationMock -> Optional.of(hierarchyLevelMap.get((String) invocationMock.getArgument(0))));
			when(accountHierarchyServiceImpl.getHierarchyLevelByLevelName(anyString())).thenAnswer(
					invocationOnMock -> hierarchyLevelMap.values().stream().filter(hierarchyLevel -> hierarchyLevel
							.getHierarchyLevelName().equalsIgnoreCase(invocationOnMock.getArgument(0))).findFirst());
		} else if (deliveryMethodology == ProjectDeliveryMethodology.KANBAN) {
			when(accountHierarchyServiceKanbanImpl.getFilteredList(any()))
					.thenReturn(constructTestAccountFilteredData());
			when(accountHierarchyServiceKanbanImpl.getHierarchyLevelByLevelId(anyString())).thenAnswer(
					invocationMock -> Optional.of(hierarchyLevelMap.get((String) invocationMock.getArgument(0))));
			when(accountHierarchyServiceKanbanImpl.getHierarchyLevelByLevelName(anyString())).thenAnswer(
					invocationOnMock -> hierarchyLevelMap.values().stream().filter(hierarchyLevel -> hierarchyLevel
							.getHierarchyLevelName().equalsIgnoreCase(invocationOnMock.getArgument(0))).findFirst());
		}

		ServiceResponse serviceResponse = kpiMaturityService.getKpiMaturity(
				KpiMaturityRequest.builder().levelName(testLevelName).deliveryMethodology(deliveryMethodology).build());
		assertNotNull(serviceResponse);
		assertNotNull(serviceResponse.getData());
		assertTrue(serviceResponse.getSuccess());
		assertInstanceOf(KpiMaturityResponse.class, serviceResponse.getData());

		KpiMaturityResponse kpiMaturityResponse = (KpiMaturityResponse) serviceResponse.getData();
		assertNotNull(kpiMaturityResponse.getSummary());
		OrganizationEntityKpiMaturity summary = kpiMaturityResponse.getSummary();
		assertTrue(summary.getLevelName().equalsIgnoreCase(testLevelName));

		assertTrue(CollectionUtils.isNotEmpty(summary.getMaturityScores()));
		List<com.publicissapient.kpidashboard.apis.kpimaturity.dto.MaturityScore> maturityScores = summary
				.getMaturityScores();

		maturityScores.forEach(maturityScore -> {
			assertNotNull(maturityScore);
			assertTrue(StringUtils.isNotEmpty(maturityScore.getKpiCategory()));
			switch (maturityScore.getKpiCategory()) {
			case "dora" -> assertEquals(2.5D, maturityScore.getScore());
			case "value" -> assertEquals(0.0D, maturityScore.getScore());
			case "quality" -> assertEquals(3.13D, maturityScore.getScore());
			case "speed" -> assertEquals(2.25D, maturityScore.getScore());
			default -> System.out.println("Unexpected kpi category found " + maturityScore.getKpiCategory());
			}
		});

		List<String> expectedCategories = List.of("dora", "speed", "quality", "value");

		assertThat(maturityScores.stream()
				.map(com.publicissapient.kpidashboard.apis.kpimaturity.dto.MaturityScore::getKpiCategory).toList())
				.containsExactlyInAnyOrderElementsOf(expectedCategories);

		assertTrue(CollectionUtils.isNotEmpty(kpiMaturityResponse.getDetails()));
		List<String> expectedOrganizationUnitNames = getOrganizationUnitNames(testLevelName);

		assertTrue(kpiMaturityResponse.getDetails().stream().allMatch(kpiMaturityDetail -> expectedOrganizationUnitNames
				.contains(kpiMaturityDetail.getOrganizationEntityName())));
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
		String result = ReflectionTestUtils.invokeMethod(KpiMaturityService.class,
				"determineHealthByEfficiencyPercentage", 30.0);

		// Assert
		assertEquals("Unhealthy", result);
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
						.labelName("project").parentId("port-node-id-1").build(),
				AccountFilteredData.builder().nodeId("sprint-node-id-1").level(6).nodeName("test-sprint-1")
						.labelName("sprint").parentId("project-node-id-1").build());
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
		case "project" -> expectedOrganizationUnitNames = List.of("test-project-1", "test-project-2");
		case "engagement" -> expectedOrganizationUnitNames = List.of("test-port-1", "test-port-2");
		case "account" -> expectedOrganizationUnitNames = List.of("test-acc-1", "test-acc-2");
		case "vertical" -> expectedOrganizationUnitNames = List.of("test-ver-1", "test-ver-2");
		case "bu" -> expectedOrganizationUnitNames = List.of("test-bu");
		default -> expectedOrganizationUnitNames = Collections.emptyList();
		}
		return expectedOrganizationUnitNames;
	}

	private static List<KpiMaturity> constructProjectKpiMaturityList() {
		return List.of(
				KpiMaturity.builder().hierarchyLevelId("project").hierarchyLevel(5)
						.hierarchyEntityNodeId("project-node-id-1")
						.maturityScores(List.of(MaturityScore.builder().kpiCategory("dora").build(),
								MaturityScore.builder().kpiCategory("value").build(),
								MaturityScore.builder().kpiCategory("speed").score(2.0D).level("M2").build(),
								MaturityScore.builder().kpiCategory("quality").score(3.25D).level("M4").build()))
						.efficiency(EfficiencyScore.builder().score(1.3).percentage(26.3).build()).build(),
				KpiMaturity.builder().hierarchyLevelId("project").hierarchyLevel(5)
						.hierarchyEntityNodeId("project-node-id-2")
						.maturityScores(
								List.of(MaturityScore.builder().kpiCategory("dora").level("M5").score(5.0D).build(),
										MaturityScore.builder().kpiCategory("value").build(),
										MaturityScore.builder().kpiCategory("speed").score(2.5D).level("M3").build(),
										MaturityScore.builder().kpiCategory("quality").score(3.0D).level("M3").build()))
						.efficiency(EfficiencyScore.builder().score(2.6).percentage(52.5).build()).build());
	}
}
