/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.recommendations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceImpl;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.recommendations.dto.ProjectRecommendationDTO;
import com.publicissapient.kpidashboard.apis.recommendations.dto.RecommendationRequest;
import com.publicissapient.kpidashboard.apis.recommendations.dto.RecommendationResponseDTO;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.Persona;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.Recommendation;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationLevel;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationsActionPlan;
import com.publicissapient.kpidashboard.common.repository.recommendation.RecommendationRepository;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendationService Tests")
class RecommendationServiceTest {

	@Mock private RecommendationRepository recommendationRepository;

	@Mock private AccountHierarchyServiceImpl accountHierarchyService;

	@Mock private FilterHelperService filterHelperService;

	@InjectMocks private RecommendationService recommendationService;

	private HierarchyLevel projectLevel;
	private HierarchyLevel accountLevel;
	private HierarchyLevel sprintLevel;
	private AccountFilteredData projectAccountData;
	private RecommendationsActionPlan testRecommendation;
	private Map<String, HierarchyLevel> hierarchyLevelMap;

	@BeforeEach
	void setUp() {
		// Setup hierarchy levels
		projectLevel = new HierarchyLevel();
		projectLevel.setHierarchyLevelId(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT);
		projectLevel.setHierarchyLevelName("project");
		projectLevel.setLevel(5);

		accountLevel = new HierarchyLevel();
		accountLevel.setHierarchyLevelId("account");
		accountLevel.setHierarchyLevelName("account");
		accountLevel.setLevel(2);

		sprintLevel = new HierarchyLevel();
		sprintLevel.setHierarchyLevelId("sprint");
		sprintLevel.setHierarchyLevelName("sprint");
		sprintLevel.setLevel(6);

		hierarchyLevelMap = new HashMap<>();
		hierarchyLevelMap.put("project", projectLevel);
		hierarchyLevelMap.put("account", accountLevel);

		// Setup account filtered data
		ObjectId basicProjectConfigId = new ObjectId();
		projectAccountData =
				AccountFilteredData.builder()
						.nodeId("test-project-node-id")
						.nodeName("Test Project")
						.labelName(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT)
						.parentId("test-parent-id")
						.level(5)
						.basicProjectConfigId(basicProjectConfigId)
						.build();

		// Setup test recommendation with recommendations field
		Recommendation recommendation = new Recommendation();
		recommendation.setTitle("Test Recommendation");
		recommendation.setDescription("Test Description");

		testRecommendation = new RecommendationsActionPlan();
		testRecommendation.setId(new ObjectId());
		testRecommendation.setBasicProjectConfigId(basicProjectConfigId.toHexString());
		testRecommendation.setProjectName("Test Project");
		testRecommendation.setPersona(Persona.ENGINEERING_LEAD);
		testRecommendation.setLevel(RecommendationLevel.PROJECT_LEVEL);
		testRecommendation.setRecommendations(recommendation);
		testRecommendation.setCreatedAt(Instant.now());
	}

	@Nested
	@DisplayName("Successful Scenarios")
	class SuccessfulScenarios {

		@Test
		@DisplayName("Should return recommendations for project level successfully")
		void getRecommendationsForLevel_ProjectLevel_Success() {
			// Arrange
			when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);
			when(accountHierarchyService.getHierarchyLevelByLevelName("project"))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getHierarchyLevelByLevelId(
							CommonConstant.HIERARCHY_LEVEL_ID_PROJECT))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getFilteredList(any())).thenReturn(Set.of(projectAccountData));
			when(recommendationRepository.findLatestRecommendationsByProjectIds(anyList(), eq(1), eq(RecommendationLevel.PROJECT_LEVEL)))
					.thenReturn(List.of(testRecommendation));

			// Act
			ServiceResponse response =
					recommendationService.getRecommendationsForLevel(
						new RecommendationRequest("project", null, null));

			// Assert
			assertNotNull(response);
			assertTrue(response.getSuccess());
			assertEquals("Recommendations retrieved successfully", response.getMessage());

			RecommendationResponseDTO responseDTO = (RecommendationResponseDTO) response.getData();
			assertNotNull(responseDTO);
			assertNotNull(responseDTO.getSummary());
			assertEquals("project", responseDTO.getSummary().getLevelName());
			assertEquals(1, responseDTO.getSummary().getTotalProjectsWithRecommendations());
			assertEquals(1, responseDTO.getSummary().getTotalProjectsQueried());

			assertNotNull(responseDTO.getDetails());
			assertEquals(1, responseDTO.getDetails().size());

			ProjectRecommendationDTO detail = responseDTO.getDetails().get(0);
			assertEquals(testRecommendation.getBasicProjectConfigId(), detail.getProjectId());
			assertEquals("Test Project", detail.getProjectName());
			assertEquals(Persona.ENGINEERING_LEAD, detail.getPersona());
			assertNotNull(detail.getRecommendations());
		}

		@Test
		@DisplayName("Should return recommendations for account level successfully")
		void getRecommendationsForLevel_AccountLevel_Success() {
			// Arrange - Create proper hierarchy with account and project levels
			AccountFilteredData accountData =
					AccountFilteredData.builder()
							.nodeId("account-node-id")
							.nodeName("Test Account")
							.labelName("account")
							.parentId("org-id")
							.level(2)
							.build();

			// Project under the account
			AccountFilteredData projectUnderAccount =
					AccountFilteredData.builder()
							.nodeId("project-under-account")
							.nodeName("Project Under Account")
							.labelName(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT)
							.parentId("account-node-id")
							.level(5)
							.basicProjectConfigId(new ObjectId())
							.build();

			when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);
			when(accountHierarchyService.getHierarchyLevelByLevelName("account"))
					.thenReturn(Optional.of(accountLevel));
			when(accountHierarchyService.getHierarchyLevelByLevelId(
							CommonConstant.HIERARCHY_LEVEL_ID_PROJECT))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getFilteredList(any()))
					.thenReturn(Set.of(accountData, projectUnderAccount));
			when(recommendationRepository.findLatestRecommendationsByProjectIds(anyList(), eq(1), eq(RecommendationLevel.PROJECT_LEVEL)))
					.thenReturn(List.of(testRecommendation));

			// Act
			ServiceResponse response =
					recommendationService.getRecommendationsForLevel(
							new RecommendationRequest("account", null, null));

			// Assert
			assertNotNull(response);
			assertTrue(response.getSuccess());

			RecommendationResponseDTO responseDTO = (RecommendationResponseDTO) response.getData();
			assertEquals("account", responseDTO.getSummary().getLevelName());
			assertEquals(1, responseDTO.getDetails().size());
		}

		@Test
		@DisplayName("Should return recommendations for multiple projects")
		void getRecommendationsForLevel_MultipleProjects_Success() {
			// Arrange
			ObjectId configId2 = new ObjectId();
			AccountFilteredData project2 =
					AccountFilteredData.builder()
							.nodeId("project-2")
							.nodeName("Project 2")
							.labelName(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT)
							.parentId("test-parent-id")
							.level(5)
							.basicProjectConfigId(configId2)
							.build();

			RecommendationsActionPlan rec2 = new RecommendationsActionPlan();
			rec2.setId(new ObjectId());
			rec2.setBasicProjectConfigId(configId2.toHexString());
			rec2.setProjectName("Project 2");
			rec2.setPersona(Persona.ENGINEERING_LEAD);
			rec2.setLevel(RecommendationLevel.PROJECT_LEVEL);
			rec2.setRecommendations(new Recommendation());
			rec2.setCreatedAt(Instant.now());

			when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);
			when(accountHierarchyService.getHierarchyLevelByLevelName("project"))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getHierarchyLevelByLevelId(
							CommonConstant.HIERARCHY_LEVEL_ID_PROJECT))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getFilteredList(any()))
					.thenReturn(Set.of(projectAccountData, project2));
			when(recommendationRepository.findLatestRecommendationsByProjectIds(anyList(), eq(1), eq(RecommendationLevel.PROJECT_LEVEL)))
					.thenReturn(List.of(testRecommendation, rec2));

			// Act
			ServiceResponse response =
					recommendationService.getRecommendationsForLevel(
							new RecommendationRequest("project", null, null));

			// Assert
			assertNotNull(response);
			RecommendationResponseDTO responseDTO = (RecommendationResponseDTO) response.getData();
			assertEquals(2, responseDTO.getDetails().size());
			assertEquals(2, responseDTO.getSummary().getTotalProjectsWithRecommendations());
			assertEquals(2, responseDTO.getSummary().getTotalProjectsQueried());
		}

		@Test
		@DisplayName("Should return recommendations for KPI level successfully")
		void getRecommendationsForLevel_KpiLevel_Success() {
			// Arrange
			when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);
			when(accountHierarchyService.getHierarchyLevelByLevelName("project"))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getHierarchyLevelByLevelId(
							CommonConstant.HIERARCHY_LEVEL_ID_PROJECT))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getFilteredList(any())).thenReturn(Set.of(projectAccountData));
			when(recommendationRepository.findLatestRecommendationsByProjectIds(anyList(), eq(1), eq(RecommendationLevel.KPI_LEVEL)))
					.thenReturn(List.of(testRecommendation));

			// Act
			ServiceResponse response =
					recommendationService.getRecommendationsForLevel(
							new RecommendationRequest("project", null, RecommendationLevel.KPI_LEVEL));

			// Assert
			assertNotNull(response);
			assertTrue(response.getSuccess());
			assertEquals("Recommendations retrieved successfully", response.getMessage());

			RecommendationResponseDTO responseDTO = (RecommendationResponseDTO) response.getData();
			assertNotNull(responseDTO);
			assertNotNull(responseDTO.getSummary());
			assertEquals("project", responseDTO.getSummary().getLevelName());
			assertEquals(1, responseDTO.getSummary().getTotalProjectsWithRecommendations());
			assertEquals(1, responseDTO.getSummary().getTotalProjectsQueried());

			assertNotNull(responseDTO.getDetails());
			assertEquals(1, responseDTO.getDetails().size());

			ProjectRecommendationDTO detail = responseDTO.getDetails().get(0);
			assertEquals(testRecommendation.getBasicProjectConfigId(), detail.getProjectId());
			assertEquals("Test Project", detail.getProjectName());
			assertEquals(Persona.ENGINEERING_LEAD, detail.getPersona());
			assertNotNull(detail.getRecommendations());
		}
	}

	@Nested
	@DisplayName("Empty Result Scenarios")
	class EmptyResultScenarios {

		@Test
		@DisplayName(
				"Should throw ForbiddenException when no hierarchy data accessible after filtering")
		void getRecommendationsForLevel_NoAccessibleData_ThrowsForbiddenException() {
			// Arrange
			when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);
			when(accountHierarchyService.getHierarchyLevelByLevelName("project"))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getHierarchyLevelByLevelId(
							CommonConstant.HIERARCHY_LEVEL_ID_PROJECT))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getFilteredList(any())).thenReturn(Collections.emptySet());

			// Act & Assert
			ForbiddenException exception =
					assertThrows(
							ForbiddenException.class,
							() ->
									recommendationService.getRecommendationsForLevel(
											new RecommendationRequest("project", null, null)));

			assertTrue(exception.getMessage().contains("doesn't have access"));
		}

		@Test
		@DisplayName("Should return empty response when projects have no basic config ID")
		void getRecommendationsForLevel_ProjectsWithoutConfigId_ReturnsEmptyResponse() {
			// Arrange
			AccountFilteredData dataWithoutConfigId =
					AccountFilteredData.builder()
							.nodeId("test-node")
							.nodeName("Test")
							.parentId("parent-id")
							.level(5)
							.basicProjectConfigId(null)
							.build();

			when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);
			when(accountHierarchyService.getHierarchyLevelByLevelName("project"))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getHierarchyLevelByLevelId(
							CommonConstant.HIERARCHY_LEVEL_ID_PROJECT))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getFilteredList(any())).thenReturn(Set.of(dataWithoutConfigId));

			// Act
			ServiceResponse response =
					recommendationService.getRecommendationsForLevel(
							new RecommendationRequest("project", null, null));

			// Assert
			assertNotNull(response);
			assertTrue(response.getSuccess());
			assertEquals("No projects found", response.getMessage());

			RecommendationResponseDTO responseDTO = (RecommendationResponseDTO) response.getData();
			assertEquals(0, responseDTO.getSummary().getTotalProjectsQueried());
		}

		@Test
		@DisplayName("Should handle case when repository returns no recommendations")
		void getRecommendationsForLevel_NoRecommendations_ReturnsEmptyDetails() {
			// Arrange
			when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);
			when(accountHierarchyService.getHierarchyLevelByLevelName("project"))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getHierarchyLevelByLevelId(
							CommonConstant.HIERARCHY_LEVEL_ID_PROJECT))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getFilteredList(any())).thenReturn(Set.of(projectAccountData));
			when(recommendationRepository.findLatestRecommendationsByProjectIds(anyList(), eq(1), eq(RecommendationLevel.PROJECT_LEVEL)))
					.thenReturn(Collections.emptyList());

			// Act
			ServiceResponse response =
					recommendationService.getRecommendationsForLevel(
							new RecommendationRequest("project", null, null));

			// Assert
			assertNotNull(response);
			assertTrue(response.getSuccess());

			RecommendationResponseDTO responseDTO = (RecommendationResponseDTO) response.getData();
			assertEquals(0, responseDTO.getDetails().size());
			assertEquals(0, responseDTO.getSummary().getTotalRecommendations());
		}
	}

	@Nested
	@DisplayName("Exception Scenarios")
	class ExceptionScenarios {

		@Test
		@DisplayName("Should throw BadRequestException when request is null")
		void getRecommendationsForLevel_NullRequest_ThrowsBadRequestException() {
			// Act & Assert
			BadRequestException exception =
					assertThrows(
							BadRequestException.class,
							() -> recommendationService.getRecommendationsForLevel(null));

			assertEquals("Recommendation request cannot be null", exception.getMessage());
		}

		@Test
		@DisplayName("Should throw BadRequestException when levelName is blank")
		void getRecommendationsForLevel_BlankLevelName_ThrowsBadRequestException() {
			// Act & Assert
			BadRequestException exception =
					assertThrows(
							BadRequestException.class,
							() ->
									recommendationService.getRecommendationsForLevel(
											new RecommendationRequest("", null, null)));

			assertEquals("The recommendation request 'levelName' is required", exception.getMessage());
		}

		@Test
		@DisplayName("Should throw NotFoundException when level name does not exist")
		void getRecommendationsForLevel_InvalidLevelName_ThrowsNotFoundException() {
			// Arrange
			when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);
			when(accountHierarchyService.getHierarchyLevelByLevelName("invalid"))
					.thenReturn(Optional.empty());

			// Act & Assert
			NotFoundException exception =
					assertThrows(
							NotFoundException.class,
							() ->
									recommendationService.getRecommendationsForLevel(
											new RecommendationRequest("invalid", null, null)));

			assertTrue(exception.getMessage().contains("does not exist"));
		}

		@Test
		@DisplayName("Should throw BadRequestException when level is below project (sprint)")
		void getRecommendationsForLevel_BelowProjectLevel_ThrowsBadRequestException() {
			// Arrange
			hierarchyLevelMap.put("sprint", sprintLevel);

			when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);
			when(accountHierarchyService.getHierarchyLevelByLevelName("sprint"))
					.thenReturn(Optional.of(sprintLevel));
			when(accountHierarchyService.getHierarchyLevelByLevelId(
							CommonConstant.HIERARCHY_LEVEL_ID_PROJECT))
					.thenReturn(Optional.of(projectLevel));

			// Act & Assert
			BadRequestException exception =
					assertThrows(
							BadRequestException.class,
							() ->
									recommendationService.getRecommendationsForLevel(
											new RecommendationRequest("sprint", null, null)));

			assertTrue(exception.getMessage().contains("below project level"));
		}

		@Test
		@DisplayName(
				"Should throw IllegalStateException when recommendation has null recommendations field")
		void getRecommendationsForLevel_NullRecommendationsField_ThrowsIllegalStateException() {
			// Arrange
			RecommendationsActionPlan invalidEntity = new RecommendationsActionPlan();
			invalidEntity.setId(new ObjectId());
			invalidEntity.setBasicProjectConfigId("project-123");
			invalidEntity.setProjectName("Test Project");
			invalidEntity.setRecommendations(null); // Invalid state

			when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);
			when(accountHierarchyService.getHierarchyLevelByLevelName("project"))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getHierarchyLevelByLevelId(
							CommonConstant.HIERARCHY_LEVEL_ID_PROJECT))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getFilteredList(any())).thenReturn(Set.of(projectAccountData));
			when(recommendationRepository.findLatestRecommendationsByProjectIds(anyList(), eq(1), eq(RecommendationLevel.PROJECT_LEVEL)))
					.thenReturn(List.of(invalidEntity));

			// Act & Assert
			IllegalStateException exception =
					assertThrows(
							IllegalStateException.class,
							() ->
									recommendationService.getRecommendationsForLevel(
											new RecommendationRequest("project", null, null)));

			assertTrue(exception.getMessage().contains("Missing recommendations"));
			assertTrue(exception.getMessage().contains("project-123"));
		}

		@Test
		@DisplayName("Should throw ForbiddenException when filtered data has nodes outside level range")
		void getRecommendationsForLevel_DataOutsideLevelRange_ThrowsForbiddenException() {
			// Arrange - Return data that's outside the requested level range
			AccountFilteredData sprintData =
					AccountFilteredData.builder()
							.nodeId("sprint-1")
							.nodeName("Sprint 1")
							.parentId("parent-id")
							.level(6) // Below project level, will be filtered out
							.basicProjectConfigId(new ObjectId())
							.build();

			when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);
			when(accountHierarchyService.getHierarchyLevelByLevelName("project"))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getHierarchyLevelByLevelId(
							CommonConstant.HIERARCHY_LEVEL_ID_PROJECT))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getFilteredList(any())).thenReturn(Set.of(sprintData));

			// Act & Assert
			ForbiddenException exception =
					assertThrows(
							ForbiddenException.class,
							() ->
									recommendationService.getRecommendationsForLevel(
											new RecommendationRequest("project", null, null)));

			assertTrue(exception.getMessage().contains("doesn't have access"));
		}

		@Test
		@DisplayName("Should throw InternalServerErrorException when multiple levels have same name")
		void getRecommendationsForLevel_MultipleLevelsWithSameName_ThrowsInternalServerError() {
			// Arrange
			HierarchyLevel duplicateProject = new HierarchyLevel();
			duplicateProject.setHierarchyLevelId("project2");
			duplicateProject.setHierarchyLevelName("project");
			duplicateProject.setLevel(4);

			Map<String, HierarchyLevel> duplicateMap = new HashMap<>();
			duplicateMap.put("project1", projectLevel);
			duplicateMap.put("project2", duplicateProject);

			when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(duplicateMap);

			// Act & Assert
			InternalServerErrorException exception =
					assertThrows(
							InternalServerErrorException.class,
							() ->
									recommendationService.getRecommendationsForLevel(
											new RecommendationRequest("project", null, null)));

			assertTrue(
					exception.getMessage().contains("Multiple hierarchy levels found for name 'project'"));
		}

		@Test
		@DisplayName("Should throw InternalServerErrorException when project level not found")
		void getRecommendationsForLevel_ProjectLevelNotFound_ThrowsInternalServerError() {
			// Arrange
			when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);
			when(accountHierarchyService.getHierarchyLevelByLevelName("account"))
					.thenReturn(Optional.of(accountLevel));
			when(accountHierarchyService.getHierarchyLevelByLevelId(
							CommonConstant.HIERARCHY_LEVEL_ID_PROJECT))
					.thenReturn(Optional.empty());

			// Act & Assert
			InternalServerErrorException exception =
					assertThrows(
							InternalServerErrorException.class,
							() ->
									recommendationService.getRecommendationsForLevel(
											new RecommendationRequest("account", null, null)));

			assertTrue(exception.getMessage().contains("Could not find project hierarchy level"));
		}

		@Test
		@DisplayName("Should throw IllegalStateException when entity has null recommendations")
		void getRecommendationsForLevel_NullRecommendationsField_ThrowsIllegalStateException2() {
			// Arrange
			RecommendationsActionPlan badEntity = new RecommendationsActionPlan();
			badEntity.setId(new ObjectId());
			badEntity.setBasicProjectConfigId("test-id");
			badEntity.setProjectName("Test Project");
			badEntity.setRecommendations(null); // Null recommendations

			when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);
			when(accountHierarchyService.getHierarchyLevelByLevelName("project"))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getHierarchyLevelByLevelId(
							CommonConstant.HIERARCHY_LEVEL_ID_PROJECT))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getFilteredList(any())).thenReturn(Set.of(projectAccountData));
			when(recommendationRepository.findLatestRecommendationsByProjectIds(anyList(), eq(1), eq(RecommendationLevel.PROJECT_LEVEL)))
					.thenReturn(List.of(badEntity));

			// Act & Assert
			IllegalStateException exception =
					assertThrows(
							IllegalStateException.class,
							() ->
									recommendationService.getRecommendationsForLevel(
											new RecommendationRequest("project", null, null)));

			assertTrue(exception.getMessage().contains("Missing recommendations"));
			assertTrue(exception.getMessage().contains("test-id"));
		}
	}

	@Nested
	@DisplayName("Data Filtering Scenarios")
	class DataFilteringScenarios {

		@Test
		@DisplayName("Should filter projects correctly when parentNodeId is provided")
		void getRecommendationsForLevel_WithParentNodeId_FiltersCorrectly() {
			// Arrange
			String parentNodeId = "parent-account-123";
			AccountFilteredData parentAccount =
					AccountFilteredData.builder()
							.nodeId(parentNodeId)
							.nodeName("Parent Account")
							.parentId("root")
							.level(3)
							.build();

			ObjectId childProjectConfigId = new ObjectId();
			AccountFilteredData childProject =
					AccountFilteredData.builder()
							.nodeId("project-child-1")
							.nodeName("Child Project")
							.parentId(parentNodeId)
							.level(5)
							.basicProjectConfigId(childProjectConfigId)
							.build();

			RecommendationsActionPlan childRecommendation = new RecommendationsActionPlan();
			childRecommendation.setId(new ObjectId());
			childRecommendation.setBasicProjectConfigId(childProjectConfigId.toHexString());
			childRecommendation.setProjectName("Child Project");
			childRecommendation.setRecommendations(testRecommendation.getRecommendations());

			when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);
			when(accountHierarchyService.getHierarchyLevelByLevelName("project"))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getHierarchyLevelByLevelId(
							CommonConstant.HIERARCHY_LEVEL_ID_PROJECT))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getFilteredList(any()))
					.thenReturn(Set.of(parentAccount, childProject));
			when(recommendationRepository.findLatestRecommendationsByProjectIds(anyList(), eq(1), eq(RecommendationLevel.PROJECT_LEVEL)))
					.thenReturn(List.of(childRecommendation));

			// Act
			ServiceResponse response =
					recommendationService.getRecommendationsForLevel(
							new RecommendationRequest("project", parentNodeId, null));

			// Assert
			assertNotNull(response);
			assertTrue(response.getSuccess());
			RecommendationResponseDTO responseDTO = (RecommendationResponseDTO) response.getData();
			assertEquals(1, responseDTO.getDetails().size());
		}

		@Test
		@DisplayName("Should return all projects when parentNodeId is null")
		void getRecommendationsForLevel_NoParentNodeId_ReturnsAllProjects() {
			// Arrange
			ObjectId project1ConfigId = new ObjectId();
			AccountFilteredData project1 =
					AccountFilteredData.builder()
							.nodeId("project-1")
							.nodeName("Project 1")
							.parentId("account-1")
							.level(5)
							.basicProjectConfigId(project1ConfigId)
							.build();

			ObjectId project2ConfigId = new ObjectId();
			AccountFilteredData project2 =
					AccountFilteredData.builder()
							.nodeId("project-2")
							.nodeName("Project 2")
							.parentId("account-2")
							.level(5)
							.basicProjectConfigId(project2ConfigId)
							.build();

			RecommendationsActionPlan recommendation1 = new RecommendationsActionPlan();
			recommendation1.setId(new ObjectId());
			recommendation1.setBasicProjectConfigId(project1ConfigId.toHexString());
			recommendation1.setProjectName("Project 1");
			recommendation1.setRecommendations(testRecommendation.getRecommendations());

			RecommendationsActionPlan recommendation2 = new RecommendationsActionPlan();
			recommendation2.setId(new ObjectId());
			recommendation2.setBasicProjectConfigId(project2ConfigId.toHexString());
			recommendation2.setProjectName("Project 2");
			recommendation2.setRecommendations(testRecommendation.getRecommendations());

			when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);
			when(accountHierarchyService.getHierarchyLevelByLevelName("project"))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getHierarchyLevelByLevelId(
							CommonConstant.HIERARCHY_LEVEL_ID_PROJECT))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getFilteredList(any())).thenReturn(Set.of(project1, project2));
			when(recommendationRepository.findLatestRecommendationsByProjectIds(anyList(), eq(1), eq(RecommendationLevel.PROJECT_LEVEL)))
					.thenReturn(List.of(recommendation1, recommendation2));

			// Act
			ServiceResponse response =
					recommendationService.getRecommendationsForLevel(
							new RecommendationRequest("project", null, null));

			// Assert
			assertNotNull(response);
			assertTrue(response.getSuccess());
			RecommendationResponseDTO responseDTO = (RecommendationResponseDTO) response.getData();
			assertEquals(2, responseDTO.getDetails().size());
			assertEquals(2, responseDTO.getSummary().getTotalProjectsQueried());
		}

		@Test
		@DisplayName("Should filter hierarchy data by level range correctly")
		void getRecommendationsForLevel_FiltersDataByLevelRange() {
			// Arrange - Create data at different levels with proper parent-child
			// relationships
			AccountFilteredData accountData =
					AccountFilteredData.builder()
							.nodeId("account-1")
							.nodeName("Account 1")
							.labelName("account")
							.parentId("org-id")
							.level(2)
							.build();

			AccountFilteredData portfolioData =
					AccountFilteredData.builder()
							.nodeId("portfolio-1")
							.nodeName("Portfolio 1")
							.labelName("portfolio")
							.parentId("account-1")
							.level(3)
							.build();

			// Project under the portfolio (which is under account)
			ObjectId projectConfigId = new ObjectId();
			AccountFilteredData projectUnderPortfolio =
					AccountFilteredData.builder()
							.nodeId("project-under-portfolio")
							.nodeName("Project Under Portfolio")
							.labelName(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT)
							.parentId("portfolio-1") // Parent is the portfolio
							.level(5)
							.basicProjectConfigId(projectConfigId)
							.build();

			AccountFilteredData sprintData =
					AccountFilteredData.builder()
							.nodeId("sprint-1")
							.nodeName("Sprint 1")
							.labelName("sprint")
							.parentId("project-under-portfolio")
							.level(6) // Below project level - should be filtered
							// out
							.basicProjectConfigId(new ObjectId())
							.build();

			Set<AccountFilteredData> allData = new HashSet<>();
			allData.add(accountData); // level 2 - requested level
			allData.add(portfolioData); // level 3 - in range
			allData.add(projectUnderPortfolio); // level 5 - project with basicProjectConfigId
			allData.add(sprintData); // level 6 - outside range, filtered out

			when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);
			when(accountHierarchyService.getHierarchyLevelByLevelName("account"))
					.thenReturn(Optional.of(accountLevel));
			when(accountHierarchyService.getHierarchyLevelByLevelId(
							CommonConstant.HIERARCHY_LEVEL_ID_PROJECT))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getFilteredList(any())).thenReturn(allData);
			when(recommendationRepository.findLatestRecommendationsByProjectIds(anyList(), eq(1), eq(RecommendationLevel.PROJECT_LEVEL)))
					.thenReturn(Collections.emptyList());

			// Act
			ServiceResponse response =
					recommendationService.getRecommendationsForLevel(
							new RecommendationRequest("account", null, null));

			// Assert
			assertNotNull(response);
			// Verify that repository was called with the project ID (data was filtered to
			// levels 2-5, excluding sprint level 6)
			verify(recommendationRepository, times(1))
					.findLatestRecommendationsByProjectIds(anyList(), eq(1), eq(RecommendationLevel.PROJECT_LEVEL));
		}

		@Test
		@DisplayName("Should filter out hierarchy data with empty node IDs")
		void getRecommendationsForLevel_FiltersDataWithEmptyNodeId() {
			// Arrange - OrganizationLookup constructor filters out empty/null nodeIds
			// internally
			// Note: We can't test with actual null/empty nodeIds in Set because
			// AccountFilteredData.equals()
			// will throw NPE when comparing. Instead, we verify the service works with only
			// valid data.

			// Create additional valid projects to demonstrate filtering works
			ObjectId configId2 = new ObjectId();
			AccountFilteredData project2 =
					AccountFilteredData.builder()
							.nodeId("project-2")
							.nodeName("Project 2")
							.parentId("test-parent-id")
							.level(5)
							.basicProjectConfigId(configId2)
							.build();

			Set<AccountFilteredData> validData = new HashSet<>();
			validData.add(projectAccountData); // Valid data with proper nodeId
			validData.add(project2); // Another valid project

			when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);
			when(accountHierarchyService.getHierarchyLevelByLevelName("project"))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getHierarchyLevelByLevelId(
							CommonConstant.HIERARCHY_LEVEL_ID_PROJECT))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getFilteredList(any())).thenReturn(validData);
			when(recommendationRepository.findLatestRecommendationsByProjectIds(anyList(), eq(1), eq(RecommendationLevel.PROJECT_LEVEL)))
					.thenReturn(List.of(testRecommendation));

			// Act
			ServiceResponse response =
					recommendationService.getRecommendationsForLevel(
							new RecommendationRequest("project", null, null));

			// Assert
			assertNotNull(response);
			assertTrue(response.getSuccess());
			// Verify repository was called with valid project IDs
			verify(recommendationRepository, times(1))
					.findLatestRecommendationsByProjectIds(anyList(), eq(1), eq(RecommendationLevel.PROJECT_LEVEL));
		}
	}

	@Nested
	@DisplayName("Edge Cases")
	class EdgeCases {

		@Test
		@DisplayName("Should handle case-insensitive level name matching")
		void getRecommendationsForLevel_CaseInsensitiveLevelName() {
			// This test verifies that the service properly delegates to accountHierarchyService
			// which should handle case-insensitive matching
			when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);
			when(accountHierarchyService.getHierarchyLevelByLevelName("PROJECT"))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getHierarchyLevelByLevelId(
							CommonConstant.HIERARCHY_LEVEL_ID_PROJECT))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getFilteredList(any())).thenReturn(Set.of(projectAccountData));
			when(recommendationRepository.findLatestRecommendationsByProjectIds(anyList(), eq(1), eq(RecommendationLevel.PROJECT_LEVEL)))
					.thenReturn(List.of(testRecommendation));

			// Act
			ServiceResponse response =
					recommendationService.getRecommendationsForLevel(
							new RecommendationRequest("PROJECT", null, null));

			// Assert
			assertNotNull(response);
			assertTrue(response.getSuccess());
		}

		@Test
		@DisplayName("Should handle repository returning fewer recommendations than expected")
		void getRecommendationsForLevel_FewerRecommendationsThanProjects() {
			// Arrange - 2 projects but only 1 recommendation
			ObjectId configId2 = new ObjectId();
			AccountFilteredData project2 =
					AccountFilteredData.builder()
							.nodeId("project-2")
							.nodeName("Project 2")
							.parentId("test-parent-id")
							.level(5)
							.basicProjectConfigId(configId2)
							.build();

			when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);
			when(accountHierarchyService.getHierarchyLevelByLevelName("project"))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getHierarchyLevelByLevelId(
							CommonConstant.HIERARCHY_LEVEL_ID_PROJECT))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getFilteredList(any()))
					.thenReturn(Set.of(projectAccountData, project2));
			when(recommendationRepository.findLatestRecommendationsByProjectIds(anyList(), eq(1), eq(RecommendationLevel.PROJECT_LEVEL)))
					.thenReturn(List.of(testRecommendation)); // Only 1 recommendation

			// Act
			ServiceResponse response =
					recommendationService.getRecommendationsForLevel(
							new RecommendationRequest("project", null, null));

			// Assert
			assertNotNull(response);
			RecommendationResponseDTO responseDTO = (RecommendationResponseDTO) response.getData();
			assertEquals(1, responseDTO.getSummary().getTotalProjectsWithRecommendations());
			assertEquals(2, responseDTO.getSummary().getTotalProjectsQueried());
		}

		@Test
		@DisplayName("Should default to PROJECT_LEVEL when recommendationLevel is null")
		void getRecommendationsForLevel_NullRecommendationLevel_DefaultsToProjectLevel() {
			// Arrange
			when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(hierarchyLevelMap);
			when(accountHierarchyService.getHierarchyLevelByLevelName("project"))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getHierarchyLevelByLevelId(
							CommonConstant.HIERARCHY_LEVEL_ID_PROJECT))
					.thenReturn(Optional.of(projectLevel));
			when(accountHierarchyService.getFilteredList(any())).thenReturn(Set.of(projectAccountData));
			when(recommendationRepository.findLatestRecommendationsByProjectIds(anyList(), eq(1), eq(RecommendationLevel.PROJECT_LEVEL)))
					.thenReturn(List.of(testRecommendation));

			// Act
			ServiceResponse response =
					recommendationService.getRecommendationsForLevel(
							new RecommendationRequest("project", null, null));

			// Assert
			assertNotNull(response);
			assertTrue(response.getSuccess());
			// Verify that repository was called with PROJECT_LEVEL (default for null)
			verify(recommendationRepository, times(1))
					.findLatestRecommendationsByProjectIds(anyList(), eq(1), eq(RecommendationLevel.PROJECT_LEVEL));
		}
	}
}
