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
	void when_GetChildrenGroupedByParentNodeIdsCalledWithNonExistentParentLevel_Then_ReturnsEmptyMap() {
		// Act
		Map<String, List<AccountFilteredData>> result = organizationLookup.getChildrenGroupedByParentNodeIds(99, 100);

		// Assert
		assertTrue(result.isEmpty());
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
