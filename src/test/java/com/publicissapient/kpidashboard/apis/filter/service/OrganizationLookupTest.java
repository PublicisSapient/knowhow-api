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

package com.publicissapient.kpidashboard.apis.filter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;

@ExtendWith(MockitoExtension.class)
class OrganizationLookupTest {

	private OrganizationLookup organizationLookup;

	@BeforeEach
	void setUp() {
		organizationLookup = new OrganizationLookup(createTestData());
	}

	@Test
	void when_ConstructorCalledWithNullData_Then_ThrowsNullPointerException() {
		// Act & Assert
		assertThrows(NullPointerException.class, () -> new OrganizationLookup(null));
	}

	@Test
	void when_ConstructorCalledWithEmptySet_Then_CreatesEmptyMaps() {
		// Arrange
		Set<AccountFilteredData> emptyData = new HashSet<>();

		// Act
		OrganizationLookup lookup = new OrganizationLookup(emptyData);

		// Assert
		assertTrue(lookup.getByNodeId().isEmpty());
		assertTrue(lookup.getByParentId().isEmpty());
		assertTrue(lookup.getByLevel().isEmpty());
	}

	@Test
	void when_ConstructorCalledWithNullAccountFilteredData_Then_SkipsNullEntries() {
		// Arrange
		Set<AccountFilteredData> dataWithNull = new HashSet<>();
		dataWithNull.add(null);
		dataWithNull.add(AccountFilteredData.builder().nodeId("node1").parentId("parent1").level(1).build());

		// Act
		OrganizationLookup lookup = new OrganizationLookup(dataWithNull);

		// Assert
		assertEquals(1, lookup.getByNodeId().size());
		assertTrue(lookup.getByNodeId().containsKey("node1"));
	}

	@Test
	void when_ConstructorCalledWithBlankNodeId_Then_SkipsEntry() {
		// Arrange
		Set<AccountFilteredData> dataWithBlankNodeId = new HashSet<>();
		dataWithBlankNodeId.add(AccountFilteredData.builder().nodeId("").parentId("parent1").level(1).build());
		dataWithBlankNodeId.add(AccountFilteredData.builder().nodeId("   ").parentId("parent2").level(1).build());
		dataWithBlankNodeId.add(AccountFilteredData.builder().nodeId("validNode").parentId("parent3").level(1).build());

		// Act
		OrganizationLookup lookup = new OrganizationLookup(dataWithBlankNodeId);

		// Assert
		assertEquals(1, lookup.getByNodeId().size());
		assertTrue(lookup.getByNodeId().containsKey("validNode"));
	}

	@Test
	void when_ConstructorCalledWithBlankParentId_Then_AddsToNodeIdAndLevelButNotParentId() {
		// Arrange
		Set<AccountFilteredData> dataWithBlankParentId = new HashSet<>();
		AccountFilteredData data = AccountFilteredData.builder().nodeId("node1").parentId("").level(1).build();
		dataWithBlankParentId.add(data);

		// Act
		OrganizationLookup lookup = new OrganizationLookup(dataWithBlankParentId);

		// Assert
		assertTrue(lookup.getByNodeId().containsKey("node1"));
		assertTrue(lookup.getByLevel().containsKey(1));
		assertTrue(lookup.getByParentId().isEmpty());
	}

	@Test
	void when_ConstructorCalledWithValidData_Then_PopulatesAllMapsCorrectly() {
		// Assert
		// Verify byNodeId map
		assertEquals(6, organizationLookup.getByNodeId().size());
		assertTrue(organizationLookup.getByNodeId().containsKey("bu1"));
		assertTrue(organizationLookup.getByNodeId().containsKey("vertical1"));
		assertTrue(organizationLookup.getByNodeId().containsKey("account1"));
		assertTrue(organizationLookup.getByNodeId().containsKey("project1"));
		assertTrue(organizationLookup.getByNodeId().containsKey("project2"));
		assertTrue(organizationLookup.getByNodeId().containsKey("project3"));

		// Verify byLevel map
		assertEquals(4, organizationLookup.getByLevel().size());
		assertEquals(1, organizationLookup.getByLevel().get(1).size());
		assertEquals(1, organizationLookup.getByLevel().get(2).size());
		assertEquals(1, organizationLookup.getByLevel().get(3).size());
		assertEquals(3, organizationLookup.getByLevel().get(5).size());

		// Verify byParentId map
		assertEquals(3, organizationLookup.getByParentId().size());
		assertTrue(organizationLookup.getByParentId().containsKey("bu1"));
		assertTrue(organizationLookup.getByParentId().containsKey("vertical1"));
		assertTrue(organizationLookup.getByParentId().containsKey("account1"));
	}

	@Test
	void when_GetAccountDataByLevelCalledWithExistingLevel_Then_ReturnsCorrectData() {
		// Act
		List<AccountFilteredData> level1Data = organizationLookup.getAccountDataByLevel(1);
		List<AccountFilteredData> level5Data = organizationLookup.getAccountDataByLevel(5);

		// Assert
		assertEquals(1, level1Data.size());
		assertEquals("bu1", level1Data.get(0).getNodeId());
		assertEquals(3, level5Data.size());
	}

	@Test
	void when_GetAccountDataByLevelCalledWithNonExistingLevel_Then_ReturnsEmptyList() {
		// Act
		List<AccountFilteredData> result = organizationLookup.getAccountDataByLevel(99);

		// Assert
		assertTrue(result.isEmpty());
	}

	@Test
	void when_GetAccountDataByNodeIdCalledWithExistingNodeId_Then_ReturnsCorrectData() {
		// Act
		List<AccountFilteredData> result = organizationLookup.getAccountDataByNodeId("bu1");

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("bu1", result.get(0).getNodeId());
	}

	@Test
	void when_GetAccountDataByNodeIdCalledWithNonExistingNodeId_Then_ReturnsNull() {
		// Act
		List<AccountFilteredData> result = organizationLookup.getAccountDataByNodeId("nonexistent");

		// Assert
		assertNull(result);
	}

	@Test
	void when_GetChildrenByParentNodeIdCalledWithExistingParent_Then_ReturnsCorrectChildren() {
		// Act
		List<AccountFilteredData> children = organizationLookup.getChildrenByParentNodeId("account1", 5);

		// Assert
		assertEquals(3, children.size());
		assertTrue(children.stream().allMatch(child -> child.getLevel() == 5));
		assertTrue(children.stream().anyMatch(child -> "project1".equals(child.getNodeId())));
		assertTrue(children.stream().anyMatch(child -> "project2".equals(child.getNodeId())));
		assertTrue(children.stream().anyMatch(child -> "project3".equals(child.getNodeId())));
	}

	@Test
	void when_GetChildrenByParentNodeIdCalledWithNonExistingParent_Then_ReturnsEmptyList() {
		// Act
		List<AccountFilteredData> children = organizationLookup.getChildrenByParentNodeId("nonexistent", 5);

		// Assert
		assertTrue(children.isEmpty());
	}

	@Test
	void when_GetChildrenByParentNodeIdCalledWithSameLevel_Then_ReturnsParentItself() {
		// Act
		List<AccountFilteredData> children = organizationLookup.getChildrenByParentNodeId("account1", 3);

		// Assert
		assertEquals(1, children.size());
		assertEquals("account1", children.get(0).getNodeId());
	}

	@Test
	void when_GetChildrenGroupedByParentNodeIdsCalledWithValidParents_Then_ReturnsCorrectGrouping() {
		// Arrange
		Set<String> parentNodeIds = Set.of("account1", "vertical1");

		// Act
		Map<String, List<AccountFilteredData>> result = organizationLookup
				.getChildrenGroupedByParentNodeIds(parentNodeIds, 5);

		// Assert
		assertEquals(2, result.size());
		assertTrue(result.containsKey("account1"));
		assertTrue(result.containsKey("vertical1"));
		assertEquals(3, result.get("account1").size());
	}

	@Test
	void when_GetChildrenGroupedByParentNodeIdsCalledWithEmptySet_Then_ReturnsEmptyMap() {
		// Arrange
		Set<String> emptyParentNodeIds = new HashSet<>();

		// Act
		Map<String, List<AccountFilteredData>> result = organizationLookup
				.getChildrenGroupedByParentNodeIds(emptyParentNodeIds, 5);

		// Assert
		assertTrue(result.isEmpty());
	}

	@Test
	void when_GetChildrenGroupedByParentNodeIdsCalledWithLevels_Then_ReturnsCorrectGrouping() {
		// Act
		Map<String, List<AccountFilteredData>> result = organizationLookup.getChildrenGroupedByParentNodeIds(2, 5);

		// Assert
		assertEquals(1, result.size());
		assertTrue(result.containsKey("vertical1"));
		assertEquals(3, result.get("vertical1").size());
	}

	@Test
	void when_GetChildrenGroupedByParentNodeIdsCalledWithChildLevelLowerThanParent_Then_ThrowsIllegalArgumentException() {
		// Act & Assert
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> organizationLookup.getChildrenGroupedByParentNodeIds(5, 3));

		assertTrue(exception.getMessage().contains("The child level must be higher than the parent level"));
	}

	@Test
	void when_GetChildrenGroupedByParentNodeIdsCalledWithEqualLevels_Then_ReturnsParentsThemselves() {
		// Act
		Map<String, List<AccountFilteredData>> result = organizationLookup.getChildrenGroupedByParentNodeIds(3, 3);

		// Assert
		assertEquals(1, result.size());
		assertTrue(result.containsKey("account1"));
		assertEquals(1, result.get("account1").size());
		assertEquals("account1", result.get("account1").get(0).getNodeId());
	}

	@Test
	void when_FindDescendantsByLevelCalledWithTargetLevelEqualToCurrentLevel_Then_ReturnsCurrentNode() {
		// Arrange
		AccountFilteredData currentNode = AccountFilteredData.builder().nodeId("test").level(3).build();

		// Act
		List<AccountFilteredData> result = ReflectionTestUtils.invokeMethod(organizationLookup,
				"findDescendantsByLevel", currentNode, 3);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("test", result.get(0).getNodeId());
	}

	@Test
	void when_FindDescendantsByLevelCalledWithNoChildren_Then_ReturnsEmptyList() {
		// Arrange
		AccountFilteredData leafNode = AccountFilteredData.builder().nodeId("project1").level(5).build();

		// Act
		List<AccountFilteredData> result = ReflectionTestUtils.invokeMethod(organizationLookup,
				"findDescendantsByLevel", leafNode, 6);

		// Assert
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void when_FindDescendantsByLevelCalledWithMultipleLevelsDown_Then_ReturnsCorrectDescendants() {
		// Arrange
		AccountFilteredData buNode = organizationLookup.getByNodeId().get("bu1").get(0);

		// Act
		List<AccountFilteredData> result = ReflectionTestUtils.invokeMethod(organizationLookup,
				"findDescendantsByLevel", buNode, 5);

		// Assert
		assertNotNull(result);
		assertEquals(3, result.size());
		assertTrue(result.stream().allMatch(node -> node.getLevel() == 5));
	}

	@Test
	void when_MultipleNodesWithSameNodeIdExist_Then_HandlesCorrectly() {
		// Arrange
		Set<AccountFilteredData> duplicateNodeIdData = new HashSet<>();
		duplicateNodeIdData.add(AccountFilteredData.builder().nodeId("duplicate").parentId("parent1").level(1).build());
		duplicateNodeIdData.add(AccountFilteredData.builder().nodeId("duplicate").parentId("parent2").level(2).build());

		// Act
		OrganizationLookup lookup = new OrganizationLookup(duplicateNodeIdData);

		// Assert
		assertEquals(2, lookup.getByNodeId().get("duplicate").size());
		assertEquals(1, lookup.getByLevel().get(1).size());
		assertEquals(1, lookup.getByLevel().get(2).size());
	}

	@Test
	void when_GetChildrenGroupedByParentNodeIdsCalledWithNonExistentParentLevel_Then_ReturnsEmptyMap() {
		// Act
		Map<String, List<AccountFilteredData>> result = organizationLookup.getChildrenGroupedByParentNodeIds(99, 100);

		// Assert
		assertTrue(result.isEmpty());
	}

	@Test
	void when_OrganizationLookupCreatedWithComplexHierarchy_Then_AllMapsArePopulatedCorrectly() {
		// This test verifies the overall integrity of the data structure
		// Assert byNodeId contains all nodes
		Set<String> expectedNodeIds = Set.of("bu1", "vertical1", "account1", "project1", "project2", "project3");
		assertEquals(expectedNodeIds, organizationLookup.getByNodeId().keySet());

		// Assert byLevel contains all levels
		Set<Integer> expectedLevels = Set.of(1, 2, 3, 5);
		assertEquals(expectedLevels, organizationLookup.getByLevel().keySet());

		// Assert byParentId contains all parent relationships
		Set<String> expectedParentIds = Set.of("bu1", "vertical1", "account1");
		assertEquals(expectedParentIds, organizationLookup.getByParentId().keySet());

		// Verify hierarchy integrity
		assertEquals(1, organizationLookup.getByParentId().get("bu1").size());
		assertEquals(1, organizationLookup.getByParentId().get("vertical1").size());
		assertEquals(3, organizationLookup.getByParentId().get("account1").size());
	}

	private Set<AccountFilteredData> createTestData() {
		Set<AccountFilteredData> data = new HashSet<>();

		// Level 1 - BU (root level, no parent)
		data.add(AccountFilteredData.builder().nodeId("bu1").level(1).nodeName("Business Unit 1").build());

		// Level 2 - Vertical
		data.add(AccountFilteredData.builder().nodeId("vertical1").parentId("bu1").level(2).nodeName("Vertical 1")
				.build());

		// Level 3 - Account
		data.add(AccountFilteredData.builder().nodeId("account1").parentId("vertical1").level(3).nodeName("Account 1")
				.build());

		// Level 5 - Projects
		data.add(AccountFilteredData.builder().nodeId("project1").parentId("account1").level(5).nodeName("Project 1")
				.build());

		data.add(AccountFilteredData.builder().nodeId("project2").parentId("account1").level(5).nodeName("Project 2")
				.build());

		data.add(AccountFilteredData.builder().nodeId("project3").parentId("account1").level(5).nodeName("Project 3")
				.build());

		return data;
	}
}
