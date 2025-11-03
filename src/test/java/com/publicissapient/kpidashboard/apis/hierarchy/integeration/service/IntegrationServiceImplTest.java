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

package com.publicissapient.kpidashboard.apis.hierarchy.integeration.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.hierarchy.integration.adapter.OrganizationHierarchyAdapter;
import com.publicissapient.kpidashboard.apis.hierarchy.integration.dto.HierarchyDetails;
import com.publicissapient.kpidashboard.apis.hierarchy.integration.dto.HierarchyLevel;
import com.publicissapient.kpidashboard.apis.hierarchy.integration.dto.HierarchyNode;
import com.publicissapient.kpidashboard.apis.hierarchy.integration.service.IntegrationServiceImpl;
import com.publicissapient.kpidashboard.apis.hierarchy.service.OrganizationHierarchyService;
import com.publicissapient.kpidashboard.common.model.application.OrganizationHierarchy;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;

@RunWith(MockitoJUnitRunner.class)
public class IntegrationServiceImplTest {

	@Mock private OrganizationHierarchyAdapter organizationHierarchyAdapter;

	@Mock private ProjectBasicConfigRepository projectConfigRepository;

	@Mock private OrganizationHierarchyService organizationHierarchyService;

	@InjectMocks private IntegrationServiceImpl integrationService;

	private List<OrganizationHierarchy> allDbNodes;
	private Set<OrganizationHierarchy> externalList;
	private HierarchyDetails hierarchyDetails;

	@Before
	public void setUp() {
		allDbNodes = createTestDbNodes();
		externalList = createTestExternalNodes();
		hierarchyDetails = createTestHierarchyDetails();
	}

	// Test Data Factory Methods

	private List<OrganizationHierarchy> createTestDbNodes() {
		List<OrganizationHierarchy> nodes = new ArrayList<>();

		// Root node
		OrganizationHierarchy root = new OrganizationHierarchy();
		root.setId(new ObjectId("507f1f77bcf86cd799439011"));
		root.setNodeId("root_001");
		root.setNodeName("Root Organization");
		root.setNodeDisplayName("Root Organization");
		root.setHierarchyLevelId("root");
		root.setExternalId("EXT_ROOT_001");
		root.setCreatedDate(LocalDateTime.now().minusDays(10));
		root.setModifiedDate(LocalDateTime.now().minusDays(5));
		nodes.add(root);

		// Port node with external ID
		OrganizationHierarchy portWithExtId = new OrganizationHierarchy();
		portWithExtId.setId(new ObjectId("507f1f77bcf86cd799439012"));
		portWithExtId.setNodeId("port_001");
		portWithExtId.setNodeName("Port 1");
		portWithExtId.setNodeDisplayName("Port 1");
		portWithExtId.setHierarchyLevelId("port");
		portWithExtId.setParentId("root_001");
		portWithExtId.setExternalId("EXT_PORT_001");
		portWithExtId.setCreatedDate(LocalDateTime.now().minusDays(8));
		portWithExtId.setModifiedDate(LocalDateTime.now().minusDays(3));
		nodes.add(portWithExtId);

		// Port node without external ID
		OrganizationHierarchy portWithoutExtId = new OrganizationHierarchy();
		portWithoutExtId.setId(new ObjectId("507f1f77bcf86cd799439013"));
		portWithoutExtId.setNodeId("port_002");
		portWithoutExtId.setNodeName("Port 2");
		portWithoutExtId.setNodeDisplayName("Port 2");
		portWithoutExtId.setHierarchyLevelId("port");
		portWithoutExtId.setParentId("root_001");
		portWithoutExtId.setExternalId(null); // No external ID
		portWithoutExtId.setCreatedDate(LocalDateTime.now().minusDays(7));
		portWithoutExtId.setModifiedDate(LocalDateTime.now().minusDays(2));
		nodes.add(portWithoutExtId);

		// Project node under port with external ID
		OrganizationHierarchy project1 = new OrganizationHierarchy();
		project1.setId(new ObjectId("507f1f77bcf86cd799439014"));
		project1.setNodeId("project_001");
		project1.setNodeName("Project 1");
		project1.setNodeDisplayName("Project 1");
		project1.setHierarchyLevelId("project");
		project1.setParentId("port_001");
		project1.setExternalId("EXT_PROJECT_001");
		project1.setCreatedDate(LocalDateTime.now().minusDays(6));
		project1.setModifiedDate(LocalDateTime.now().minusDays(1));
		nodes.add(project1);

		// Project node under port without external ID
		OrganizationHierarchy project2 = new OrganizationHierarchy();
		project2.setId(new ObjectId("507f1f77bcf86cd799439015"));
		project2.setNodeId("project_002");
		project2.setNodeName("Project 2");
		project2.setNodeDisplayName("Project 2");
		project2.setHierarchyLevelId("project");
		project2.setParentId("port_002");
		project2.setExternalId("EXT_PROJECT_002");
		project2.setCreatedDate(LocalDateTime.now().minusDays(5));
		project2.setModifiedDate(LocalDateTime.now());
		nodes.add(project2);

		return nodes;
	}

	private Set<OrganizationHierarchy> createTestExternalNodes() {
		Set<OrganizationHierarchy> nodes = new HashSet<>();

		OrganizationHierarchy externalNode = new OrganizationHierarchy();
		externalNode.setId(new ObjectId("507f1f77bcf86cd799439016"));
		externalNode.setNodeId("external_001");
		externalNode.setNodeName("External Node 1");
		externalNode.setNodeDisplayName("External Node 1");
		externalNode.setHierarchyLevelId("port");
		externalNode.setParentId("root_001");
		externalNode.setExternalId("EXT_EXTERNAL_001");
		externalNode.setCreatedDate(LocalDateTime.now());
		externalNode.setModifiedDate(LocalDateTime.now());
		nodes.add(externalNode);

		return nodes;
	}

	private HierarchyDetails createTestHierarchyDetails() {
		HierarchyDetails details = new HierarchyDetails();

		List<HierarchyNode> nodes = new ArrayList<>();
		HierarchyNode node = new HierarchyNode();
		node.setOpportunityUniqueId("opp_001");
		node.setPortfolioUniqueId("portfolio_001");
		node.setAccountUniqueId("account_001");
		node.setVerticalUniqueId("vertical_001");
		node.setBuUniqueId("bu_001");
		node.setRootUniqueId("root_001");
		node.setOpportunity("Test Opportunity");
		node.setOpportunityId("opportunity_001");
		nodes.add(node);
		details.setHierarchyNode(nodes);

		List<HierarchyLevel> levels = new ArrayList<>();
		HierarchyLevel level = new HierarchyLevel();
		level.setId(1);
		level.setName("port");
		level.setDisplayName("Port");
		level.setLevel(1);
		levels.add(level);
		details.setHierarchyLevels(levels);

		return details;
	}

	private List<ProjectBasicConfig> createTestProjectConfigs() {
		List<ProjectBasicConfig> configs = new ArrayList<>();

		ProjectBasicConfig config1 = new ProjectBasicConfig();
		config1.setId(new ObjectId("507f1f77bcf86cd799439018"));
		config1.setProjectNodeId("project_002");
		config1.setProjectName("Test Project 2");
		config1.setProjectOnHold(false);
		configs.add(config1);

		return configs;
	}

	// Test Methods for syncOrganizationHierarchy

	@Test
	public void testSyncOrganizationHierarchy_HappyPath() {
		// Arrange
		Set<OrganizationHierarchy> externalNodes = new HashSet<>();
		OrganizationHierarchy updatedNode = new OrganizationHierarchy();
		updatedNode.setId(new ObjectId("507f1f77bcf86cd799439017"));
		updatedNode.setNodeId("port_001");
		updatedNode.setNodeName("Updated Port 1");
		updatedNode.setNodeDisplayName("Updated Port 1");
		updatedNode.setHierarchyLevelId("port");
		updatedNode.setParentId("root_001");
		updatedNode.setExternalId("EXT_PORT_001");
		updatedNode.setCreatedDate(LocalDateTime.now());
		updatedNode.setModifiedDate(LocalDateTime.now());
		externalNodes.add(updatedNode);

		doNothing().when(organizationHierarchyService).saveAll(anyList());
		doNothing().when(organizationHierarchyService).clearCache();

		List<ProjectBasicConfig> projectConfigs = createTestProjectConfigs();
		/*when(projectConfigRepository.findByProjectNodeIdIn(anySet())).thenReturn(projectConfigs);
		when(projectConfigRepository.saveAll(anyList())).thenReturn(projectConfigs);*/

		// Act
		integrationService.syncOrganizationHierarchy(externalNodes, allDbNodes);

		// Assert
		verify(organizationHierarchyService, times(1)).saveAll(anyList());
		verify(organizationHierarchyService, times(1)).clearCache();
	}

	@Test
	public void testSyncOrganizationHierarchy_NewNodes() {
		// Arrange
		Set<OrganizationHierarchy> externalNodes = new HashSet<>();
		OrganizationHierarchy newNode = new OrganizationHierarchy();
		newNode.setId(new ObjectId("507f1f77bcf86cd799439019"));
		newNode.setNodeId("new_node_001");
		newNode.setNodeName("New Node 1");
		newNode.setNodeDisplayName("New Node 1");
		newNode.setHierarchyLevelId("port");
		newNode.setParentId("root_001");
		newNode.setExternalId("EXT_NEW_001");
		newNode.setCreatedDate(LocalDateTime.now());
		newNode.setModifiedDate(LocalDateTime.now());
		externalNodes.add(newNode);

		doNothing().when(organizationHierarchyService).saveAll(anyList());
		doNothing().when(organizationHierarchyService).clearCache();

		List<ProjectBasicConfig> projectConfigs = createTestProjectConfigs();
		when(projectConfigRepository.findByProjectNodeIdIn(anySet())).thenReturn(projectConfigs);
		when(projectConfigRepository.saveAll(anyList())).thenReturn(projectConfigs);

		// Act
		integrationService.syncOrganizationHierarchy(externalNodes, allDbNodes);

		// Assert
		ArgumentCaptor<List<OrganizationHierarchy>> saveCaptor = ArgumentCaptor.forClass(List.class);
		verify(organizationHierarchyService, times(1)).saveAll(saveCaptor.capture());
		verify(organizationHierarchyService, times(1)).clearCache();

		List<OrganizationHierarchy> savedNodes = saveCaptor.getValue();
		assertEquals(1, savedNodes.size());
		assertEquals("EXT_NEW_001", savedNodes.get(0).getExternalId());
	}

	@Test
	public void testSyncOrganizationHierarchy_EmptyDbNodesList() {
		// Arrange
		List<OrganizationHierarchy> emptyDbNodes = new ArrayList<>();
		Set<OrganizationHierarchy> externalNodes = new HashSet<>();

		OrganizationHierarchy newNode = new OrganizationHierarchy();
		newNode.setId(new ObjectId("507f1f77bcf86cd799439020"));
		newNode.setNodeId("new_node_003");
		newNode.setNodeName("New Node 3");
		newNode.setNodeDisplayName("New Node 3");
		newNode.setHierarchyLevelId("port");
		newNode.setParentId("root_001");
		newNode.setExternalId("EXT_NEW_003");
		newNode.setCreatedDate(LocalDateTime.now());
		newNode.setModifiedDate(LocalDateTime.now());
		externalNodes.add(newNode);

		doNothing().when(organizationHierarchyService).saveAll(anyList());
		doNothing().when(organizationHierarchyService).clearCache();

		// Act
		integrationService.syncOrganizationHierarchy(externalNodes, emptyDbNodes);

		// Assert
		ArgumentCaptor<List<OrganizationHierarchy>> saveCaptor = ArgumentCaptor.forClass(List.class);
		verify(organizationHierarchyService, times(1)).saveAll(saveCaptor.capture());
		verify(organizationHierarchyService, times(1)).clearCache();

		List<OrganizationHierarchy> savedNodes = saveCaptor.getValue();
		assertEquals(1, savedNodes.size());
		assertEquals("EXT_NEW_003", savedNodes.get(0).getExternalId());

		verify(projectConfigRepository, never()).findByProjectNodeIdIn(anySet());
	}

	@Test
	public void testSyncOrganizationHierarchy_NodesWithNullExternalIds() {
		// Arrange
		Set<OrganizationHierarchy> externalNodes = new HashSet<>();

		OrganizationHierarchy nodeWithNullExtId = new OrganizationHierarchy();
		nodeWithNullExtId.setId(new ObjectId("507f1f77bcf86cd799439021"));
		nodeWithNullExtId.setNodeId("invalid_node_001");
		nodeWithNullExtId.setNodeName("Invalid Node");
		nodeWithNullExtId.setNodeDisplayName("Invalid Node");
		nodeWithNullExtId.setHierarchyLevelId("port");
		nodeWithNullExtId.setParentId("root_001");
		nodeWithNullExtId.setExternalId(null);
		nodeWithNullExtId.setCreatedDate(LocalDateTime.now());
		nodeWithNullExtId.setModifiedDate(LocalDateTime.now());
		externalNodes.add(nodeWithNullExtId);

		OrganizationHierarchy nodeWithEmptyExtId = new OrganizationHierarchy();
		nodeWithEmptyExtId.setId(new ObjectId("507f1f77bcf86cd799439022"));
		nodeWithEmptyExtId.setNodeId("invalid_node_002");
		nodeWithEmptyExtId.setNodeName("Invalid Node 2");
		nodeWithEmptyExtId.setNodeDisplayName("Invalid Node 2");
		nodeWithEmptyExtId.setHierarchyLevelId("port");
		nodeWithEmptyExtId.setParentId("root_001");
		nodeWithEmptyExtId.setExternalId("");
		nodeWithEmptyExtId.setCreatedDate(LocalDateTime.now());
		nodeWithEmptyExtId.setModifiedDate(LocalDateTime.now());
		externalNodes.add(nodeWithEmptyExtId);

		OrganizationHierarchy validNode = new OrganizationHierarchy();
		validNode.setId(new ObjectId("507f1f77bcf86cd799439023"));
		validNode.setNodeId("valid_node_001");
		validNode.setNodeName("Valid Node");
		validNode.setNodeDisplayName("Valid Node");
		validNode.setHierarchyLevelId("port");
		validNode.setParentId("root_001");
		validNode.setExternalId("EXT_VALID_001");
		validNode.setCreatedDate(LocalDateTime.now());
		validNode.setModifiedDate(LocalDateTime.now());
		externalNodes.add(validNode);

		doNothing().when(organizationHierarchyService).saveAll(anyList());
		doNothing().when(organizationHierarchyService).clearCache();

		// Act
		integrationService.syncOrganizationHierarchy(externalNodes, allDbNodes);

		// Assert
		ArgumentCaptor<List<OrganizationHierarchy>> saveCaptor = ArgumentCaptor.forClass(List.class);
		verify(organizationHierarchyService, times(1)).saveAll(saveCaptor.capture());

		List<OrganizationHierarchy> savedNodes = saveCaptor.getValue();
		assertEquals(2, savedNodes.size());
		assertEquals("EXT_VALID_001", savedNodes.get(0).getExternalId());
	}

	@Test
	public void testSyncOrganizationHierarchy_NoProjectsUnderPortsWithoutExternalIds() {
		// Arrange
		List<OrganizationHierarchy> dbNodesWithPortsButNoProjects = new ArrayList<>();

		OrganizationHierarchy portWithoutExtId = new OrganizationHierarchy();
		portWithoutExtId.setId(new ObjectId("507f1f77bcf86cd799439024"));
		portWithoutExtId.setNodeId("port_004");
		portWithoutExtId.setNodeName("Port 4");
		portWithoutExtId.setNodeDisplayName("Port 4");
		portWithoutExtId.setHierarchyLevelId("port");
		portWithoutExtId.setParentId("root_001");
		portWithoutExtId.setExternalId(null);
		portWithoutExtId.setCreatedDate(LocalDateTime.now());
		portWithoutExtId.setModifiedDate(LocalDateTime.now());
		dbNodesWithPortsButNoProjects.add(portWithoutExtId);

		// Act
		integrationService.syncOrganizationHierarchy(new HashSet<>(), dbNodesWithPortsButNoProjects);

		// Assert
		verify(projectConfigRepository, never()).saveAll(anyList());
	}

	// Test Methods for convertHieracyResponseToOrganizationHierachy

	@Test
	public void testConvertHieracyResponseToOrganizationHierachy_ValidInput() {
		// Arrange
		Set<OrganizationHierarchy> expectedResult = new HashSet<>();
		OrganizationHierarchy convertedNode = new OrganizationHierarchy();
		convertedNode.setId(new ObjectId("507f1f77bcf86cd799439025"));
		convertedNode.setNodeId("converted_001");
		convertedNode.setNodeName("Converted Node");
		convertedNode.setNodeDisplayName("Converted Node");
		convertedNode.setHierarchyLevelId("port");
		convertedNode.setParentId("root_001");
		convertedNode.setExternalId("EXT_CONVERTED_001");
		convertedNode.setCreatedDate(LocalDateTime.now());
		convertedNode.setModifiedDate(LocalDateTime.now());
		expectedResult.add(convertedNode);

		when(organizationHierarchyAdapter.convertToOrganizationHierarchy(
						any(HierarchyDetails.class), anyList()))
				.thenReturn(expectedResult);

		// Act
		Set<OrganizationHierarchy> result =
				integrationService.convertHieracyResponseToOrganizationHierachy(
						hierarchyDetails, allDbNodes);

		// Assert
		assertNotNull(result);
		assertEquals(expectedResult, result);
		verify(organizationHierarchyAdapter, times(1))
				.convertToOrganizationHierarchy(any(HierarchyDetails.class), anyList());
	}

	@Test
	public void testConvertHieracyResponseToOrganizationHierachy_NullHierarchyDetails() {
		// Arrange
		Set<OrganizationHierarchy> expectedResult = new HashSet<>();
		when(organizationHierarchyAdapter.convertToOrganizationHierarchy(any(), anyList()))
				.thenReturn(expectedResult);

		// Act
		Set<OrganizationHierarchy> result =
				integrationService.convertHieracyResponseToOrganizationHierachy(null, allDbNodes);

		// Assert
		assertNotNull(result);
		assertEquals(expectedResult, result);
		verify(organizationHierarchyAdapter, times(1)).convertToOrganizationHierarchy(any(), anyList());
	}

	@Test
	public void testConvertHieracyResponseToOrganizationHierachy_EmptyDbNodes() {
		// Arrange
		List<OrganizationHierarchy> emptyDbNodes = new ArrayList<>();
		Set<OrganizationHierarchy> expectedResult = new HashSet<>();

		when(organizationHierarchyAdapter.convertToOrganizationHierarchy(
						any(HierarchyDetails.class), anyList()))
				.thenReturn(expectedResult);

		// Act
		Set<OrganizationHierarchy> result =
				integrationService.convertHieracyResponseToOrganizationHierachy(
						hierarchyDetails, emptyDbNodes);

		// Assert
		assertNotNull(result);
		assertEquals(expectedResult, result);
		verify(organizationHierarchyAdapter, times(1))
				.convertToOrganizationHierarchy(any(HierarchyDetails.class), anyList());
	}

	@Test
	public void testConvertHieracyResponseToOrganizationHierachy_EmptyResult() {
		// Arrange
		Set<OrganizationHierarchy> emptyResult = new HashSet<>();
		when(organizationHierarchyAdapter.convertToOrganizationHierarchy(
						any(HierarchyDetails.class), anyList()))
				.thenReturn(emptyResult);

		// Act
		Set<OrganizationHierarchy> result =
				integrationService.convertHieracyResponseToOrganizationHierachy(
						hierarchyDetails, allDbNodes);

		// Assert
		assertNotNull(result);
		assertTrue(result.isEmpty());
		verify(organizationHierarchyAdapter, times(1))
				.convertToOrganizationHierarchy(any(HierarchyDetails.class), anyList());
	}

	@Test
	public void testSyncOrganizationHierarchy_EmptyExternalNodes() {
		// Arrange
		Set<OrganizationHierarchy> emptyExternalNodes = new HashSet<>();

		List<ProjectBasicConfig> projectConfigs = createTestProjectConfigs();
		when(projectConfigRepository.findByProjectNodeIdIn(anySet())).thenReturn(projectConfigs);
		when(projectConfigRepository.saveAll(anyList())).thenReturn(projectConfigs);

		// Act
		integrationService.syncOrganizationHierarchy(emptyExternalNodes, allDbNodes);

		// Assert
		verify(organizationHierarchyService, never()).saveAll(anyList());
		verify(organizationHierarchyService, never()).clearCache();
		verify(projectConfigRepository, times(1)).findByProjectNodeIdIn(anySet());
	}
}
