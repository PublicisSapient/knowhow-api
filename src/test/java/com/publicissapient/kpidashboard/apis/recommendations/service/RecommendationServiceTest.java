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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.apis.common.service.CacheService;
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
import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationLevel;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationsActionPlan;
import com.publicissapient.kpidashboard.common.repository.recommendation.RecommendationRepository;
import com.publicissapient.kpidashboard.common.shared.enums.ProjectDeliveryMethodology;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

	@Mock
	private RecommendationRepository recommendationRepository;

	@Mock
	private AccountHierarchyServiceImpl accountHierarchyService;

	@Mock
	private FilterHelperService filterHelperService;

	@Mock
	private CacheService cacheService;

	@InjectMocks
	private RecommendationService recommendationService;

	private RecommendationRequest testRequest;
	private HierarchyLevel projectLevel;
	private AccountFilteredData projectAccountData;
	private RecommendationsActionPlan testRecommendation;

	@BeforeEach
	void setUp() {
		testRequest = RecommendationRequest.builder()
				.levelName("project")
				.deliveryMethodology(ProjectDeliveryMethodology.SCRUM)
				.parentNodeId("test-parent-id")
				.build();

		projectLevel = new HierarchyLevel();
		projectLevel.setHierarchyLevelId(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT);
		projectLevel.setHierarchyLevelName("project");
		projectLevel.setLevel(5);

		HierarchyLevel childLevel = new HierarchyLevel();
		childLevel.setLevel(4);
		childLevel.setHierarchyLevelName("squad");

		projectAccountData = AccountFilteredData.builder()
				.nodeId("test-project-id")
				.nodeName("Test Project")
				.labelName(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT)
				.parentId("test-parent-id")
				.level(5)
				.build();

		testRecommendation = new RecommendationsActionPlan();
		testRecommendation.setId(new ObjectId());
		testRecommendation.setProjectId("test-project-id");
		testRecommendation.setProjectName("Test Project");
		testRecommendation.setPersona(Persona.ENGINEERING_LEAD);
		testRecommendation.setLevel(RecommendationLevel.PROJECT_LEVEL);
		testRecommendation.setCreatedAt(Instant.now());
	}

	@Test
	void getRecommendationsForLevel_returnsRecommendationsSuccessfully() {
		// Arrange
		HierarchyLevel childLevel = new HierarchyLevel();
		childLevel.setLevel(4);
		childLevel.setHierarchyLevelName("squad");

		when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(new HashMap<>());
		when(accountHierarchyService.getHierarchyLevelByLevelName("project")).thenReturn(Optional.of(projectLevel));
		when(accountHierarchyService.getHierarchyLevelByLevelId(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT))
				.thenReturn(Optional.of(projectLevel));
		when(accountHierarchyService.getHierarchyLevelByLevelNumber(5)).thenReturn(Optional.of(childLevel));
		when(accountHierarchyService.getFilteredList(any())).thenReturn(Set.of(projectAccountData));
		when(recommendationRepository.findByProjectIdInOrderByCreatedAtDesc(anyList()))
				.thenReturn(List.of(testRecommendation));

		// Act
		ServiceResponse response = recommendationService.getRecommendationsForLevel(testRequest);

		// Assert
		assertNotNull(response);
		assertTrue(response.getSuccess());
		assertEquals("Recommendations retrieved successfully", response.getMessage());
		
		RecommendationResponseDTO responseDTO = (RecommendationResponseDTO) response.getData();
		assertNotNull(responseDTO);
		assertNotNull(responseDTO.getRecommendations());
		assertEquals(1, responseDTO.getRecommendations().size());
		assertEquals(1, responseDTO.getTotalProjects());
		assertEquals(1, responseDTO.getTotalProjectsQueried());
		
		ProjectRecommendationDTO recommendation = responseDTO.getRecommendations().get(0);
		assertEquals("test-project-id", recommendation.getProjectId());
		assertEquals("Test Project", recommendation.getProjectName());
		assertEquals(Persona.ENGINEERING_LEAD, recommendation.getPersona());
		assertEquals(RecommendationLevel.PROJECT_LEVEL, recommendation.getLevel());
	}

	@Test
	void getRecommendationsForLevel_returnsEmptyResponseWhenNoProjectsFound() {
		// Arrange
		HierarchyLevel childLevel = new HierarchyLevel();
		childLevel.setLevel(4);

		when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(new HashMap<>());
		when(accountHierarchyService.getHierarchyLevelByLevelName("project")).thenReturn(Optional.of(projectLevel));
		when(accountHierarchyService.getHierarchyLevelByLevelId(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT))
				.thenReturn(Optional.of(projectLevel));
		when(accountHierarchyService.getHierarchyLevelByLevelNumber(5)).thenReturn(Optional.of(childLevel));
		
		// Return non-project hierarchy data
		AccountFilteredData nonProjectData = AccountFilteredData.builder()
				.nodeId("test-portfolio-id")
				.nodeName("Test Portfolio")
				.labelName("portfolio")
				.level(3)
				.build();
		when(accountHierarchyService.getFilteredList(any())).thenReturn(Set.of(nonProjectData));

		// Act
		ServiceResponse response = recommendationService.getRecommendationsForLevel(testRequest);

		// Assert
		assertNotNull(response);
		assertTrue(response.getSuccess());
		
		RecommendationResponseDTO responseDTO = (RecommendationResponseDTO) response.getData();
		assertNotNull(responseDTO);
		assertNotNull(responseDTO.getRecommendations());
		assertTrue(responseDTO.getRecommendations().isEmpty());
		assertEquals(0, responseDTO.getTotalProjects());
		assertEquals(0, responseDTO.getTotalProjectsQueried());
		assertEquals("No projects found for the specified criteria", responseDTO.getMessage());
	}
}