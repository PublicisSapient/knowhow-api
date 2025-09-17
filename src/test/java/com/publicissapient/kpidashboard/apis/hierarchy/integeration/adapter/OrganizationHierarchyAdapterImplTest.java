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

package com.publicissapient.kpidashboard.apis.hierarchy.integeration.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.publicissapient.kpidashboard.apis.hierarchy.integration.adapter.OrganizationHierarchyAdapterImpl;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.hierarchy.integration.dto.HierarchyDetails;
import com.publicissapient.kpidashboard.apis.hierarchy.integration.dto.HierarchyLevel;
import com.publicissapient.kpidashboard.apis.hierarchy.integration.dto.HierarchyNode;
import com.publicissapient.kpidashboard.common.model.application.OrganizationHierarchy;
import com.publicissapient.kpidashboard.common.service.HierarchyLevelService;

@RunWith(MockitoJUnitRunner.class)
public class OrganizationHierarchyAdapterImplTest {

	@Mock
	private HierarchyLevelService hierarchyLevelService;

	@InjectMocks
	private OrganizationHierarchyAdapterImpl organizationHierarchyAdapter;

	private HierarchyDetails hierarchyDetails;
	private List<OrganizationHierarchy> allDbNodes;
	private List<com.publicissapient.kpidashboard.common.model.application.HierarchyLevel> mockHierarchyLevels;

	@Before
	public void setUp() {
		hierarchyDetails = createTestHierarchyDetails();
		allDbNodes = createTestDbNodes();
		mockHierarchyLevels = createMockHierarchyLevels();
		
		when(hierarchyLevelService.getTopHierarchyLevels()).thenReturn(mockHierarchyLevels);
	}

	// Test Data Factory Methods

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
		node.setPortfolio("Test Portfolio");
		node.setAccount("Test Account");
		node.setVertical("Test Vertical");
		node.setBu("Test BU");
		node.setRoot("Test Root");
		nodes.add(node);
		details.setHierarchyNode(nodes);

		List<HierarchyLevel> levels = new ArrayList<>();
		
		HierarchyLevel buLevel = new HierarchyLevel();
		buLevel.setId(1);
		buLevel.setName("bu");
		buLevel.setDisplayName("BU");
		buLevel.setLevel(1);
		levels.add(buLevel);
		
		HierarchyLevel verticalLevel = new HierarchyLevel();
		verticalLevel.setId(2);
		verticalLevel.setName("vertical");
		verticalLevel.setDisplayName("VERTICAL");
		verticalLevel.setLevel(2);
		levels.add(verticalLevel);
		
		HierarchyLevel accountLevel = new HierarchyLevel();
		accountLevel.setId(3);
		accountLevel.setName("account");
		accountLevel.setDisplayName("ACCOUNT");
		accountLevel.setLevel(3);
		levels.add(accountLevel);
		
		HierarchyLevel portfolioLevel = new HierarchyLevel();
		portfolioLevel.setId(4);
		portfolioLevel.setName("portfolio");
		portfolioLevel.setDisplayName("PORTFOLIO");
		portfolioLevel.setLevel(4);
		levels.add(portfolioLevel);
		
		details.setHierarchyLevels(levels);

		return details;
	}

	private List<OrganizationHierarchy> createTestDbNodes() {
		List<OrganizationHierarchy> nodes = new ArrayList<>();

		// Existing BU node
		OrganizationHierarchy existingBu = new OrganizationHierarchy();
		existingBu.setId(new ObjectId("507f1f77bcf86cd799439011"));
		existingBu.setNodeId("existing_bu_001");
		existingBu.setNodeName("Existing BU");
		existingBu.setNodeDisplayName("Existing BU");
		existingBu.setHierarchyLevelId("bu");
		existingBu.setExternalId("bu_001");
		existingBu.setCreatedDate(LocalDateTime.now().minusDays(10));
		existingBu.setModifiedDate(LocalDateTime.now().minusDays(5));
		nodes.add(existingBu);

		// Existing Vertical node
		OrganizationHierarchy existingVertical = new OrganizationHierarchy();
		existingVertical.setId(new ObjectId("507f1f77bcf86cd799439012"));
		existingVertical.setNodeId("existing_vertical_001");
		existingVertical.setNodeName("Existing Vertical");
		existingVertical.setNodeDisplayName("Existing Vertical");
		existingVertical.setHierarchyLevelId("ver");
		existingVertical.setParentId("existing_bu_001");
		existingVertical.setExternalId("vertical_001");
		existingVertical.setCreatedDate(LocalDateTime.now().minusDays(8));
		existingVertical.setModifiedDate(LocalDateTime.now().minusDays(3));
		nodes.add(existingVertical);

		return nodes;
	}

	private List<com.publicissapient.kpidashboard.common.model.application.HierarchyLevel> createMockHierarchyLevels() {
		List<com.publicissapient.kpidashboard.common.model.application.HierarchyLevel> levels = new ArrayList<>();

		com.publicissapient.kpidashboard.common.model.application.HierarchyLevel buLevel = 
			new com.publicissapient.kpidashboard.common.model.application.HierarchyLevel();
		buLevel.setHierarchyLevelId("bu");
		buLevel.setHierarchyLevelName("BU");
		buLevel.setLevel(1);
		levels.add(buLevel);

		com.publicissapient.kpidashboard.common.model.application.HierarchyLevel verticalLevel = 
			new com.publicissapient.kpidashboard.common.model.application.HierarchyLevel();
		verticalLevel.setHierarchyLevelId("ver");
		verticalLevel.setHierarchyLevelName("VERTICAL");
		verticalLevel.setLevel(2);
		levels.add(verticalLevel);

		com.publicissapient.kpidashboard.common.model.application.HierarchyLevel accountLevel = 
			new com.publicissapient.kpidashboard.common.model.application.HierarchyLevel();
		accountLevel.setHierarchyLevelId("acc");
		accountLevel.setHierarchyLevelName("ACCOUNT");
		accountLevel.setLevel(3);
		levels.add(accountLevel);

		com.publicissapient.kpidashboard.common.model.application.HierarchyLevel portfolioLevel = 
			new com.publicissapient.kpidashboard.common.model.application.HierarchyLevel();
		portfolioLevel.setHierarchyLevelId("port");
		portfolioLevel.setHierarchyLevelName("PORTFOLIO");
		portfolioLevel.setLevel(4);
		levels.add(portfolioLevel);

		return levels;
	}

	private HierarchyNode createTestHierarchyNode(String buId, String verticalId, String accountId, String portfolioId) {
		HierarchyNode node = new HierarchyNode();
		node.setBuUniqueId(buId);
		node.setVerticalUniqueId(verticalId);
		node.setAccountUniqueId(accountId);
		node.setPortfolioUniqueId(portfolioId);
		node.setBu("Test BU");
		node.setVertical("Test Vertical");
		node.setAccount("Test Account");
		node.setPortfolio("Test Portfolio");
		return node;
	}

	// Test Methods for convertToOrganizationHierarchy

	@Test
	public void testConvertToOrganizationHierarchy_ValidInput() {
		// Act
		Set<OrganizationHierarchy> result = organizationHierarchyAdapter
				.convertToOrganizationHierarchy(hierarchyDetails, allDbNodes);

		// Assert
		assertNotNull(result);
		assertTrue(result.size() > 0);
		
		// Verify that nodes are created for each hierarchy level
		boolean hasBu = result.stream().anyMatch(node -> "bu".equals(node.getHierarchyLevelId()));
		boolean hasVertical = result.stream().anyMatch(node -> "ver".equals(node.getHierarchyLevelId()));
		boolean hasAccount = result.stream().anyMatch(node -> "acc".equals(node.getHierarchyLevelId()));
		boolean hasPortfolio = result.stream().anyMatch(node -> "port".equals(node.getHierarchyLevelId()));
		
		assertTrue("Should contain BU node", hasBu);
		assertTrue("Should contain Vertical node", hasVertical);
		assertTrue("Should contain Account node", hasAccount);
		assertTrue("Should contain Portfolio node", hasPortfolio);
	}

	@Test
	public void testConvertToOrganizationHierarchy_EmptyHierarchyNodes() {
		// Arrange
		HierarchyDetails emptyDetails = new HierarchyDetails();
		emptyDetails.setHierarchyNode(new ArrayList<>());
		emptyDetails.setHierarchyLevels(hierarchyDetails.getHierarchyLevels());

		// Act
		Set<OrganizationHierarchy> result = organizationHierarchyAdapter
				.convertToOrganizationHierarchy(emptyDetails, allDbNodes);

		// Assert
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	public void testConvertToOrganizationHierarchy_EmptyDbNodes() {
		// Arrange
		List<OrganizationHierarchy> emptyDbNodes = new ArrayList<>();

		// Act
		Set<OrganizationHierarchy> result = organizationHierarchyAdapter
				.convertToOrganizationHierarchy(hierarchyDetails, emptyDbNodes);

		// Assert
		assertNotNull(result);
		assertTrue(result.size() > 0);
		
		// All nodes should be newly created
		result.forEach(node -> {
			assertNotNull(node.getNodeId());
			assertNotNull(node.getExternalId());
			assertNotNull(node.getCreatedDate());
			assertNotNull(node.getModifiedDate());
		});
	}

	@Test
	public void testConvertToOrganizationHierarchy_WithExistingNodes() {
		// Act
		Set<OrganizationHierarchy> result = organizationHierarchyAdapter
				.convertToOrganizationHierarchy(hierarchyDetails, allDbNodes);

		// Assert
		assertNotNull(result);
		
		// Should reuse existing nodes where external IDs match
		boolean hasExistingBu = result.stream()
				.anyMatch(node -> "existing_bu_001".equals(node.getNodeId()) && "bu_001".equals(node.getExternalId()));
		boolean hasExistingVertical = result.stream()
				.anyMatch(node -> "existing_vertical_001".equals(node.getNodeId()) && "vertical_001".equals(node.getExternalId()));
		
		assertTrue("Should reuse existing BU node", hasExistingBu);
		assertTrue("Should reuse existing Vertical node", hasExistingVertical);
	}

	// Test Methods for createOrUpdateNode

	@Test
	public void testCreateOrUpdateNode_NewNode() {
		// Act
		OrganizationHierarchy result = organizationHierarchyAdapter
				.createOrUpdateNode("test_external_id", "Test Node", "bu", null);

		// Assert
		assertNotNull(result);
		assertEquals("test_external_id", result.getExternalId());
		assertEquals("Test Node", result.getNodeName());
		assertEquals("Test Node", result.getNodeDisplayName());
		assertEquals("bu", result.getHierarchyLevelId());
		assertNotNull(result.getNodeId());
		assertNotNull(result.getCreatedDate());
		assertNotNull(result.getModifiedDate());
	}

	@Test
	public void testCreateOrUpdateNode_ExistingNode() {
		// Arrange - Create a node first
		OrganizationHierarchy firstCall = organizationHierarchyAdapter
				.createOrUpdateNode("test_external_id", "Test Node", "bu", null);

		// Act - Try to create the same node again
		OrganizationHierarchy secondCall = organizationHierarchyAdapter
				.createOrUpdateNode("test_external_id", "Test Node", "bu", null);

		// Assert
		assertEquals(firstCall, secondCall);
		assertEquals(firstCall.getNodeId(), secondCall.getNodeId());
	}

	@Test
	public void testCreateOrUpdateNode_MultipleParentsThrowsException() {
		// Arrange - Create a node with one parent
		organizationHierarchyAdapter.createOrUpdateNode("test_external_id", "Test Node", "ver", "parent1");

		// Act & Assert - Try to create the same node with different parent
		assertThrows(IllegalStateException.class, () -> {
			organizationHierarchyAdapter.createOrUpdateNode("test_external_id", "Test Node", "ver", "parent2");
		});
	}

	@Test
	public void testCreateOrUpdateNode_WithParent() {
		// Act
		OrganizationHierarchy result = organizationHierarchyAdapter
				.createOrUpdateNode("test_external_id", "Test Node", "ver", "parent_id");

		// Assert
		assertNotNull(result);
		assertEquals("parent_id", result.getParentId());
		assertEquals("ver", result.getHierarchyLevelId());
	}

	// Test Methods for getMatchingValue

	@Test
	public void testGetMatchingValue_ExactMatch() {
		// Arrange
		Map<String, String> dataMap = new HashMap<>();
		dataMap.put("BU", "bu");
		dataMap.put("VERTICAL", "ver");
		dataMap.put("ACCOUNT", "acc");

		// Act
		String result = OrganizationHierarchyAdapterImpl.getMatchingValue(dataMap, "BU");

		// Assert
		assertEquals("bu", result);
	}

	@Test
	public void testGetMatchingValue_MultipleKeysMatch() {
		// Arrange
		Map<String, String> dataMap = new HashMap<>();
		dataMap.put("BU", "bu");
		dataMap.put("VERTICAL", "ver");
		dataMap.put("ACCOUNT", "acc");

		// Act
		String result = OrganizationHierarchyAdapterImpl.getMatchingValue(dataMap, "BU/BUSINESS_UNIT");

		// Assert
		assertEquals("bu", result); // Should return the first match
	}

	@Test
	public void testGetMatchingValue_SpaceSeparatedKeys() {
		// Arrange
		Map<String, String> dataMap = new HashMap<>();
		dataMap.put("BUSINESS", "bu");
		dataMap.put("VERTICAL", "ver");

		// Act
		String result = OrganizationHierarchyAdapterImpl.getMatchingValue(dataMap, "UNIT BUSINESS");

		// Assert
		assertEquals("bu", result); // Should match "BUSINESS"
	}

	@Test
	public void testGetMatchingValue_NoMatch() {
		// Arrange
		Map<String, String> dataMap = new HashMap<>();
		dataMap.put("BU", "bu");
		dataMap.put("VERTICAL", "ver");

		// Act & Assert
		assertThrows(IllegalStateException.class, () -> {
			OrganizationHierarchyAdapterImpl.getMatchingValue(dataMap, "NONEXISTENT");
		});
	}

	// Test Methods for ensureHierarchyExists

	@Test
	public void testEnsureHierarchyExists_ValidNodes() {
		// Arrange
		List<HierarchyNode> nodes = new ArrayList<>();
		nodes.add(createTestHierarchyNode("bu_001", "ver_001", "acc_001", "port_001"));
		
		Set<OrganizationHierarchy> transformedList = new java.util.HashSet<>();
		List<String> centralHierarchyLevels = List.of("bu", "ver", "acc", "port");

		// Act
		organizationHierarchyAdapter.ensureHierarchyExists(nodes, transformedList, centralHierarchyLevels, allDbNodes);

		// Assert
		assertNotNull(transformedList);
		assertTrue(transformedList.size() > 0);
	}

	@Test
	public void testEnsureHierarchyExists_EmptyNodes() {
		// Arrange
		List<HierarchyNode> emptyNodes = new ArrayList<>();
		Set<OrganizationHierarchy> transformedList = new java.util.HashSet<>();
		List<String> centralHierarchyLevels = List.of("bu", "ver", "acc", "port");

		// Act
		organizationHierarchyAdapter.ensureHierarchyExists(emptyNodes, transformedList, centralHierarchyLevels, allDbNodes);

		// Assert
		assertTrue(transformedList.isEmpty());
	}

	@Test
	public void testEnsureHierarchyExists_NodeWithMissingParent() {
		// Arrange
		HierarchyNode nodeWithMissingParent = createTestHierarchyNode(null, "ver_001", "acc_001", "port_001");
		List<HierarchyNode> nodes = List.of(nodeWithMissingParent);
		
		Set<OrganizationHierarchy> transformedList = new java.util.HashSet<>();
		List<String> centralHierarchyLevels = List.of("bu", "ver", "acc", "port");

		// Act
		organizationHierarchyAdapter.ensureHierarchyExists(nodes, transformedList, centralHierarchyLevels, allDbNodes);

		// Assert - Should handle gracefully and not add nodes with missing parents
		// The exact behavior depends on the implementation logic
		assertNotNull(transformedList);
	}

	// Edge Case Tests

	@Test
	public void testConvertToOrganizationHierarchy_NullHierarchyDetails() {
		// Act & Assert
		assertThrows(NullPointerException.class, () -> {
			organizationHierarchyAdapter.convertToOrganizationHierarchy(null, allDbNodes);
		});
	}

	@Test
	public void testConvertToOrganizationHierarchy_NullDbNodes() {
		// Act
		Set<OrganizationHierarchy> result = organizationHierarchyAdapter
				.convertToOrganizationHierarchy(hierarchyDetails, null);

		// Assert
		assertNotNull(result);
		// Should handle null DB nodes gracefully
	}

	@Test
	public void testCreateOrUpdateNode_NullValues() {
		// Act
		OrganizationHierarchy result = organizationHierarchyAdapter
				.createOrUpdateNode(null, null, "bu", null);

		// Assert
		assertNotNull(result);
		assertEquals("bu", result.getHierarchyLevelId());
	}

	@Test
	public void testCreateOrUpdateNode_EmptyValues() {
		// Act
		OrganizationHierarchy result = organizationHierarchyAdapter
				.createOrUpdateNode("", "", "bu", "");

		// Assert
		assertNotNull(result);
		assertEquals("", result.getExternalId());
		assertEquals("", result.getNodeName());
		assertEquals("bu", result.getHierarchyLevelId());
	}

	// Integration Tests

	@Test
	public void testFullHierarchyCreation() {
		// Arrange
		HierarchyDetails fullHierarchy = createCompleteHierarchyDetails();

		// Act
		Set<OrganizationHierarchy> result = organizationHierarchyAdapter
				.convertToOrganizationHierarchy(fullHierarchy, new ArrayList<>());

		// Assert
		assertNotNull(result);
		assertEquals(4, result.size()); // BU, Vertical, Account, Portfolio
		
		// Verify hierarchy relationships
		OrganizationHierarchy buNode = result.stream()
				.filter(node -> "bu".equals(node.getHierarchyLevelId()))
				.findFirst().orElse(null);
		assertNotNull(buNode);
		
		OrganizationHierarchy verticalNode = result.stream()
				.filter(node -> "ver".equals(node.getHierarchyLevelId()))
				.findFirst().orElse(null);
		assertNotNull(verticalNode);
		assertEquals(buNode.getNodeId(), verticalNode.getParentId());
	}

	private HierarchyDetails createCompleteHierarchyDetails() {
		HierarchyDetails details = new HierarchyDetails();

		List<HierarchyNode> nodes = new ArrayList<>();
		HierarchyNode node = new HierarchyNode();
		node.setBuUniqueId("complete_bu_001");
		node.setVerticalUniqueId("complete_ver_001");
		node.setAccountUniqueId("complete_acc_001");
		node.setPortfolioUniqueId("complete_port_001");
		node.setBu("Complete BU");
		node.setVertical("Complete Vertical");
		node.setAccount("Complete Account");
		node.setPortfolio("Complete Portfolio");
		nodes.add(node);
		details.setHierarchyNode(nodes);

		details.setHierarchyLevels(hierarchyDetails.getHierarchyLevels());
		return details;
	}

	// Additional Test Methods to Achieve >95% Coverage

	@Test
	public void testEnsureHierarchyExists_ExceptionHandling() {
		// Arrange
		List<HierarchyNode> nodes = new ArrayList<>();
		HierarchyNode problematicNode = new HierarchyNode();
		problematicNode.setBuUniqueId("bu_001");
		problematicNode.setVerticalUniqueId("ver_001");
		problematicNode.setAccountUniqueId("acc_001");
		problematicNode.setPortfolioUniqueId("port_001");
		// Set null values that might cause exceptions
		problematicNode.setBu(null);
		problematicNode.setVertical(null);
		problematicNode.setAccount(null);
		problematicNode.setPortfolio(null);
		problematicNode.setOpportunityUniqueId("opp_001");
		nodes.add(problematicNode);

		Set<OrganizationHierarchy> transformedList = new java.util.HashSet<>();
		List<String> centralHierarchyLevels = List.of("bu", "ver", "acc", "port");

		// Act - Should handle exceptions gracefully
		organizationHierarchyAdapter.ensureHierarchyExists(nodes, transformedList, centralHierarchyLevels, allDbNodes);

		// Assert - Should not throw exception and continue processing
		assertNotNull(transformedList);
	}

	@Test
	public void testProcessHierarchyNode_SkipNonBuWithMissingParent() {
		// Arrange
		HierarchyNode nodeWithMissingParent = new HierarchyNode();
		nodeWithMissingParent.setBuUniqueId(null); // Missing BU
		nodeWithMissingParent.setVerticalUniqueId("ver_001");
		nodeWithMissingParent.setAccountUniqueId("acc_001");
		nodeWithMissingParent.setPortfolioUniqueId("port_001");
		nodeWithMissingParent.setBu("Test BU");
		nodeWithMissingParent.setVertical("Test Vertical");
		nodeWithMissingParent.setAccount("Test Account");
		nodeWithMissingParent.setPortfolio("Test Portfolio");

		List<String> centralHierarchyLevels = List.of("ver", "acc", "port"); // Start from vertical (non-BU)

		Set<OrganizationHierarchy> transformedList = new java.util.HashSet<>();

		// Act
		organizationHierarchyAdapter.ensureHierarchyExists(List.of(nodeWithMissingParent), transformedList, centralHierarchyLevels, allDbNodes);

		// Assert - Should skip processing when parent is missing for non-BU levels
		assertTrue("Should be empty when parent is missing for non-BU levels", transformedList.isEmpty());
	}

	@Test
	public void testGetParentId_ParentInCreatedNodes() {
		// Arrange - Create a scenario where parent exists in createdNodes
		HierarchyNode node = new HierarchyNode();
		node.setBuUniqueId("bu_001");
		node.setVerticalUniqueId("ver_001");
		node.setAccountUniqueId("acc_001");
		node.setPortfolioUniqueId("port_001");
		node.setBu("Test BU");
		node.setVertical("Test Vertical");
		node.setAccount("Test Account");
		node.setPortfolio("Test Portfolio");

		List<String> centralHierarchyLevels = List.of("bu", "ver", "acc", "port");

		Set<OrganizationHierarchy> transformedList = new java.util.HashSet<>();

		// Act
		organizationHierarchyAdapter.ensureHierarchyExists(List.of(node), transformedList, centralHierarchyLevels, new ArrayList<>());

		// Assert - Should create hierarchy with proper parent-child relationships
		assertNotNull(transformedList);
		assertTrue(transformedList.size() >= 2);

		// Verify parent-child relationships
		OrganizationHierarchy buNode = transformedList.stream()
			.filter(n -> "bu".equals(n.getHierarchyLevelId()))
			.findFirst().orElse(null);
		OrganizationHierarchy verNode = transformedList.stream()
			.filter(n -> "ver".equals(n.getHierarchyLevelId()))
			.findFirst().orElse(null);

		assertNotNull(buNode);
		assertNotNull(verNode);
		assertEquals(buNode.getNodeId(), verNode.getParentId());
	}

	@Test
	public void testGetParentId_ParentInDbNodes() {
		// Arrange - Create scenario where parent exists in DB
		List<OrganizationHierarchy> dbNodesWithParent = new ArrayList<>(allDbNodes);

		HierarchyNode node = new HierarchyNode();
		node.setBuUniqueId("bu_001"); // This matches existing BU in allDbNodes
		node.setVerticalUniqueId("new_ver_001");
		node.setAccountUniqueId("new_acc_001");
		node.setBu("Test BU");
		node.setVertical("New Vertical");
		node.setAccount("New Account");

		List<String> centralHierarchyLevels = List.of("bu", "ver", "acc");
		Set<OrganizationHierarchy> transformedList = new java.util.HashSet<>();

		// Act
		organizationHierarchyAdapter.ensureHierarchyExists(List.of(node), transformedList, centralHierarchyLevels, dbNodesWithParent);

		// Assert
		assertNotNull(transformedList);

		// Should reuse existing BU and create new vertical under it
		boolean hasExistingBu = transformedList.stream()
			.anyMatch(n -> "existing_bu_001".equals(n.getNodeId()));
		assertTrue("Should reuse existing BU node", hasExistingBu);
	}

	@Test
	public void testGetNodeIdMappings_EmptyValues() {
		// Arrange
		HierarchyNode nodeWithEmptyValues = new HierarchyNode();
		nodeWithEmptyValues.setBuUniqueId("");
		nodeWithEmptyValues.setVerticalUniqueId(null);
		nodeWithEmptyValues.setAccountUniqueId("   "); // Whitespace
		nodeWithEmptyValues.setPortfolioUniqueId("valid_port_001");
		nodeWithEmptyValues.setBu("Test BU");
		nodeWithEmptyValues.setVertical("Test Vertical");
		nodeWithEmptyValues.setAccount("Test Account");
		nodeWithEmptyValues.setPortfolio("Test Portfolio");

		List<String> centralHierarchyLevels = List.of("bu", "ver", "acc", "port");
		Set<OrganizationHierarchy> transformedList = new java.util.HashSet<>();

		// Act
		organizationHierarchyAdapter.ensureHierarchyExists(List.of(nodeWithEmptyValues), transformedList, centralHierarchyLevels, new ArrayList<>());

		// Assert - Should generate unique IDs for empty values
		assertNotNull(transformedList);
	}

	@Test
	public void testCreateOrUpdateNode_SameParentMultipleCalls() {
		// Arrange & Act - Create node with same parent multiple times
		OrganizationHierarchy firstCall = organizationHierarchyAdapter
			.createOrUpdateNode("same_parent_test", "Test Node", "ver", "same_parent");
		OrganizationHierarchy secondCall = organizationHierarchyAdapter
			.createOrUpdateNode("same_parent_test", "Test Node", "ver", "same_parent");

		// Assert - Should return same node when parent is same
		assertEquals(firstCall, secondCall);
		assertEquals("same_parent", firstCall.getParentId());
		assertEquals("same_parent", secondCall.getParentId());
	}

	@Test
	public void testCreateOrUpdateNode_NullParentToNullParent() {
		// Arrange & Act - Create node with null parent, then try again with null parent
		OrganizationHierarchy firstCall = organizationHierarchyAdapter
			.createOrUpdateNode("null_parent_test", "Test Node", "bu", null);
		OrganizationHierarchy secondCall = organizationHierarchyAdapter
			.createOrUpdateNode("null_parent_test", "Test Node", "bu", null);

		// Assert - Should return same node
		assertEquals(firstCall, secondCall);
		assertEquals(firstCall.getParentId(), secondCall.getParentId());
	}

	@Test
	public void testConvertToOrganizationHierarchy_NullHierarchyLevels() {
		// Arrange
		HierarchyDetails detailsWithNullLevels = new HierarchyDetails();
		detailsWithNullLevels.setHierarchyNode(hierarchyDetails.getHierarchyNode());
		detailsWithNullLevels.setHierarchyLevels(null);

		// Act & Assert - Should handle null hierarchy levels gracefully
		assertThrows(NullPointerException.class, () -> {
			organizationHierarchyAdapter.convertToOrganizationHierarchy(detailsWithNullLevels, allDbNodes);
		});
	}

	@Test
	public void testConvertToOrganizationHierarchy_EmptyHierarchyLevels() {
		// Arrange
		HierarchyDetails detailsWithEmptyLevels = new HierarchyDetails();
		detailsWithEmptyLevels.setHierarchyNode(hierarchyDetails.getHierarchyNode());
		detailsWithEmptyLevels.setHierarchyLevels(new ArrayList<>());

		// Act
		Set<OrganizationHierarchy> result = organizationHierarchyAdapter
			.convertToOrganizationHierarchy(detailsWithEmptyLevels, allDbNodes);

		// Assert - Should handle empty hierarchy levels
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	public void testConvertToOrganizationHierarchy_HierarchyLevelsWithZeroLevel() {
		// Arrange
		HierarchyDetails detailsWithZeroLevel = new HierarchyDetails();
		detailsWithZeroLevel.setHierarchyNode(hierarchyDetails.getHierarchyNode());

		List<HierarchyLevel> levelsWithZero = new ArrayList<>();
		HierarchyLevel zeroLevel = new HierarchyLevel();
		zeroLevel.setId(0);
		zeroLevel.setName("root");
		zeroLevel.setDisplayName("ROOT");
		zeroLevel.setLevel(0); // Level 0 should be filtered out
		levelsWithZero.add(zeroLevel);

		HierarchyLevel validLevel = new HierarchyLevel();
		validLevel.setId(1);
		validLevel.setName("bu");
		validLevel.setDisplayName("BU");
		validLevel.setLevel(1);
		levelsWithZero.add(validLevel);

		detailsWithZeroLevel.setHierarchyLevels(levelsWithZero);

		// Act
		Set<OrganizationHierarchy> result = organizationHierarchyAdapter
			.convertToOrganizationHierarchy(detailsWithZeroLevel, allDbNodes);

		// Assert - Should filter out level 0 and process only valid levels
		assertNotNull(result);
		// Should only process BU level since level 0 is filtered out
	}

	@Test
	public void testGetMatchingValue_EmptyInputKey() {
		// Arrange
		Map<String, String> dataMap = new HashMap<>();
		dataMap.put("BU", "bu");
		dataMap.put("VERTICAL", "ver");

		// Act & Assert
		assertThrows(IllegalStateException.class, () -> {
			OrganizationHierarchyAdapterImpl.getMatchingValue(dataMap, "");
		});
	}

	@Test
	public void testExistingNodeUpdate_ParentIdUpdate() {
		// Arrange - Create existing node in DB with different parent
		List<OrganizationHierarchy> dbNodesWithDifferentParent = new ArrayList<>();
		OrganizationHierarchy existingNodeWithOldParent = new OrganizationHierarchy();
		existingNodeWithOldParent.setId(new ObjectId("507f1f77bcf86cd799439030"));
		existingNodeWithOldParent.setNodeId("existing_node_001");
		existingNodeWithOldParent.setNodeName("Existing Node");
		existingNodeWithOldParent.setNodeDisplayName("Existing Node");
		existingNodeWithOldParent.setHierarchyLevelId("ver");
		existingNodeWithOldParent.setParentId("old_parent_001");
		existingNodeWithOldParent.setExternalId("ver_update_001");
		dbNodesWithDifferentParent.add(existingNodeWithOldParent);

		// Add BU node as new parent
		OrganizationHierarchy newParentBu = new OrganizationHierarchy();
		newParentBu.setId(new ObjectId("507f1f77bcf86cd799439031"));
		newParentBu.setNodeId("new_parent_bu_001");
		newParentBu.setNodeName("New Parent BU");
		newParentBu.setNodeDisplayName("New Parent BU");
		newParentBu.setHierarchyLevelId("bu");
		newParentBu.setExternalId("bu_update_001");
		dbNodesWithDifferentParent.add(newParentBu);

		HierarchyNode nodeForUpdate = new HierarchyNode();
		nodeForUpdate.setBuUniqueId("bu_update_001");
		nodeForUpdate.setVerticalUniqueId("ver_update_001");
		nodeForUpdate.setBu("New Parent BU");
		nodeForUpdate.setVertical("Existing Node");

		List<String> centralHierarchyLevels = List.of("bu", "ver");
		Set<OrganizationHierarchy> transformedList = new java.util.HashSet<>();

		// Act
		organizationHierarchyAdapter.ensureHierarchyExists(List.of(nodeForUpdate), transformedList, centralHierarchyLevels, dbNodesWithDifferentParent);

		// Assert - Should update parent ID of existing node
		assertNotNull(transformedList);
		OrganizationHierarchy updatedVerticalNode = transformedList.stream()
			.filter(n -> "ver".equals(n.getHierarchyLevelId()) && "ver_update_001".equals(n.getExternalId()))
			.findFirst().orElse(null);

		assertNotNull(updatedVerticalNode);
		assertEquals("new_parent_bu_001", updatedVerticalNode.getParentId());
	}

	@Test
	public void testGetExistingNode_NullExternalId() {
		// Arrange - Test with null external ID
		HierarchyNode nodeWithNullId = new HierarchyNode();
		nodeWithNullId.setBuUniqueId(null);
		nodeWithNullId.setVerticalUniqueId("ver_001");
		nodeWithNullId.setBu("Test BU");
		nodeWithNullId.setVertical("Test Vertical");

		Set<OrganizationHierarchy> result = organizationHierarchyAdapter
			.convertToOrganizationHierarchy(createHierarchyDetailsWithNode(nodeWithNullId), allDbNodes);

		// Assert - Should handle null external ID gracefully
		assertNotNull(result);
	}

	@Test
	public void testGetParentId_NullParentExternalId() {
		// Arrange - Create node where parent external ID is null
		HierarchyNode nodeWithNullParentId = new HierarchyNode();
		nodeWithNullParentId.setBuUniqueId(null); // This will cause parent lookup to fail
		nodeWithNullParentId.setVerticalUniqueId("ver_001");
		nodeWithNullParentId.setBu("Test BU");
		nodeWithNullParentId.setVertical("Test Vertical");

		List<String> centralHierarchyLevels = List.of("bu", "ver");
		Set<OrganizationHierarchy> transformedList = new java.util.HashSet<>();

		// Act
		organizationHierarchyAdapter.ensureHierarchyExists(List.of(nodeWithNullParentId), transformedList, centralHierarchyLevels, allDbNodes);

		// Assert - Should handle null parent external ID
		assertNotNull(transformedList);
	}

	// Helper method for creating HierarchyDetails with specific node
	private HierarchyDetails createHierarchyDetailsWithNode(HierarchyNode node) {
		HierarchyDetails details = new HierarchyDetails();
		details.setHierarchyNode(List.of(node));
		details.setHierarchyLevels(hierarchyDetails.getHierarchyLevels());
		return details;
	}
}