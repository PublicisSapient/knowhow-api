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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.springframework.util.CollectionUtils;

import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;

import lombok.extern.slf4j.Slf4j;

/**
 * High-performance lookup utility for navigating organizational hierarchy data
 * structures.
 *
 * <p>
 * This class provides efficient access patterns for organizational hierarchy
 * data by maintaining multiple indexed views of the same dataset. It enables
 * fast lookups by node ID, parent-child relationships, and hierarchy levels,
 * making it ideal for dashboard and reporting applications that need to
 * traverse organizational structures frequently.
 * </p>
 *
 * <p>
 * The class builds three primary indexes during construction:
 * </p>
 * <ul>
 * <li><strong>Node ID Index:</strong> Direct access to entities by their unique
 * identifiers</li>
 * <li><strong>Parent ID Index:</strong> Fast retrieval of child entities for
 * any parent</li>
 * <li><strong>Level Index:</strong> Efficient access to all entities at a
 * specific hierarchy level</li>
 * </ul>
 */
@Slf4j
public class OrganizationLookup {

	/** Index for fast lookup of entities by their node ID */
	private final Map<String, List<AccountFilteredData>> byNodeId;

	/** Index for fast lookup of child entities by their parent's node ID */
	private final Map<String, List<AccountFilteredData>> byParentId;

	private final Map<Integer, List<AccountFilteredData>> byLevel;

	/**
	 * @param flatAccountFilteredData
	 *            flat collection of the organization entities at which the current
	 *            user has access. This is sent through the constructor because this
	 *            utility class should be used to perform lookups based on a
	 *            filtered data which is required for a specific use case.
	 */
	public OrganizationLookup(Set<AccountFilteredData> flatAccountFilteredData) {
		Objects.requireNonNull(flatAccountFilteredData, "The account filtered data cannot be null");

		this.byNodeId = new HashMap<>();
		this.byLevel = new HashMap<>();
		this.byParentId = new HashMap<>();

		for (AccountFilteredData accountFilteredData : flatAccountFilteredData) {
			if (Objects.nonNull(accountFilteredData) && StringUtils.isNotBlank(accountFilteredData.getNodeId())) {
				this.byNodeId.putIfAbsent(accountFilteredData.getNodeId(), new ArrayList<>());
				this.byNodeId.get(accountFilteredData.getNodeId()).add(accountFilteredData);

				this.byLevel.putIfAbsent(accountFilteredData.getLevel(), new ArrayList<>());
				this.byLevel.get(accountFilteredData.getLevel()).add(accountFilteredData);

				if (StringUtils.isNotBlank(accountFilteredData.getParentId())) {
					this.byParentId.putIfAbsent(accountFilteredData.getParentId(), new ArrayList<>());
					this.byParentId.get(accountFilteredData.getParentId()).add(accountFilteredData);
				}
			}
		}
	}

	/**
	 * Retrieves all organizational entities at the specified hierarchy level.
	 *
	 * <p>
	 * This method provides efficient access to entities grouped by their position
	 * in the organizational hierarchy. Common use cases include:
	 * </p>
	 * <ul>
	 * <li>Dashboard aggregations at specific organizational levels</li>
	 * <li>Level-based filtering and reporting</li>
	 * <li>Organizational structure analysis</li>
	 * </ul>
	 *
	 * <p>
	 * The method returns an empty list if no entities exist at the specified level,
	 * making it safe to use in iterative operations without null checks.
	 * </p>
	 *
	 * @param level
	 *            The hierarchy level to query (e.g., 1=BU, 2=Vertical, 3=Account,
	 *            5=Project)
	 * @return List of entities at the specified level, or empty list if none exist
	 *
	 * @see #getChildrenGroupedByParentNodeIds(int, int)
	 */
	public List<AccountFilteredData> getAccountDataByLevel(int level) {
		return this.byLevel.getOrDefault(level, List.of());
	}

	/**
	 * Retrieves organizational entities by their unique node identifier.
	 *
	 * <p>
	 * This method provides direct access to entities using their node ID, which is
	 * the primary key in the organizational hierarchy. The method may return
	 * multiple entities if the same node ID appears in different contexts (though
	 * this is rare in well-formed hierarchies).
	 * </p>
	 *
	 * @param nodeId
	 *            The unique identifier of the organizational entity
	 * @return List of entities with the specified node ID, or null if not found
	 */
	public List<AccountFilteredData> getAccountDataByNodeId(String nodeId) {
		return this.byNodeId.get(nodeId);
	}

	/**
	 * Finds all descendant entities at a specific level under the given parent
	 * node.
	 *
	 * <p>
	 * This method performs a hierarchical search to find all entities at the target
	 * level that are descendants of the specified parent. The search traverses the
	 * organizational tree recursively, making it suitable for finding entities that
	 * may be several levels below the parent.
	 * </p>
	 *
	 * <p>
	 * Usage sample: Getting all projects under a specific account
	 * </p>
	 *
	 * <p>
	 * If the parent node doesn't exist or has no descendants at the target level,
	 * an empty list is returned.
	 * </p>
	 *
	 * @param parentNodeId
	 *            The node ID of the parent entity to search under
	 * @param childLevel
	 *            The hierarchy level of the desired descendant entities
	 * @return List of descendant entities at the specified level, or empty list if
	 *         none found
	 */
	public List<AccountFilteredData> getChildrenByParentNodeId(String parentNodeId, int childLevel) {
		List<AccountFilteredData> parents = this.byNodeId.get(parentNodeId);
		if (CollectionUtils.isEmpty(parents)) {
			return Collections.emptyList();
		}
		List<AccountFilteredData> children = new ArrayList<>();
		for (AccountFilteredData parent : parents) {
			children.addAll(findDescendantsByLevel(parent, childLevel));
		}
		return children;
	}

	/**
	 * Groups descendant entities by their parent nodes for multiple parent
	 * entities.
	 *
	 * <p>
	 * This method efficiently processes multiple parent nodes simultaneously,
	 * returning a map where each key is a parent node ID and the value is the list
	 * of its descendants at the specified level. This bulk operation is more
	 * efficient than calling {@link #getChildrenByParentNodeId(String, int)}
	 * multiple times.
	 * </p>
	 *
	 * <p>
	 * Parents without descendants at the target level will have empty lists as
	 * values.
	 * </p>
	 *
	 * @param parentNodeIds
	 *            Set of parent node IDs to process
	 * @param childLevel
	 *            The hierarchy level of the desired descendant entities
	 * @return Map where keys are parent node IDs and values are lists of their
	 *         descendants
	 */
	public Map<String, List<AccountFilteredData>> getChildrenGroupedByParentNodeIds(Set<String> parentNodeIds,
			int childLevel) {
		List<AccountFilteredData> parents = parentNodeIds.stream()
				.flatMap(parentNodeId -> this.byNodeId.get(parentNodeId).stream()).toList();
		Map<String, List<AccountFilteredData>> childrenGroupedByParents = new HashMap<>();
		for (AccountFilteredData parent : parents) {
			childrenGroupedByParents.put(parent.getNodeId(), findDescendantsByLevel(parent, childLevel));
		}
		return childrenGroupedByParents;
	}

	/**
	 * Groups descendant entities by their parents for all entities at a specific
	 * parent level.
	 *
	 * <p>
	 * This method finds all entities at the parent level and then groups their
	 * descendants at the child level. It's a convenience method that combines
	 * level-based lookup with parent-child grouping, eliminating the need to first
	 * retrieve parent entities manually.
	 * </p>
	 *
	 * <p>
	 * This is particularly useful for organizational reporting where you need to
	 * see the distribution of lower-level entities across all entities at a higher
	 * level. For example, grouping all projects by their parent accounts.
	 * </p>
	 *
	 * <p>
	 * <strong>Validation:</strong> The method validates that the child level is
	 * numerically higher than the parent level, as organizational hierarchies
	 * typically use ascending numbers for deeper levels.
	 * </p>
	 *
	 * @param parentLevel
	 *            The hierarchy level of the parent entities
	 * @param childLevel
	 *            The hierarchy level of the descendant entities
	 * @return Map where keys are parent node IDs and values are lists of their
	 *         descendants
	 */
	public Map<String, List<AccountFilteredData>> getChildrenGroupedByParentNodeIds(int parentLevel, int childLevel) {
		if (childLevel < parentLevel) {
			throw new IllegalArgumentException(
					String.format("The child level must be higher than the parent level. Received child "
							+ "level: %s, received parent level %s", childLevel, parentLevel));
		}
		List<AccountFilteredData> parents = getAccountDataByLevel(parentLevel);
		Map<String, List<AccountFilteredData>> childrenGroupedByParents = new HashMap<>();
		for (AccountFilteredData parent : parents) {
			childrenGroupedByParents.put(parent.getNodeId(), findDescendantsByLevel(parent, childLevel));
		}
		return childrenGroupedByParents;
	}

	/**
	 * Recursively finds all descendant entities at the specified target level.
	 *
	 * <p>
	 * This private method implements the core recursive algorithm for traversing
	 * the organizational hierarchy. It performs a depth-first search starting from
	 * the current node and continues until it finds all entities at the target
	 * level.
	 * </p>
	 *
	 * <p>
	 * The algorithm works as follows:
	 * </p>
	 * <ol>
	 * <li>If the current node is at the target level, add it to results</li>
	 * <li>Otherwise, recursively search all direct children</li>
	 * <li>Aggregate results from all recursive calls</li>
	 * </ol>
	 *
	 * <p>
	 * <strong>Performance Note:</strong> The time complexity is O(d) where d is the
	 * total number of descendants that need to be examined. In well-balanced
	 * hierarchies, this is typically much smaller than the total dataset size.
	 * </p>
	 *
	 * @param currentNode
	 *            The starting node for the recursive search
	 * @param targetLevel
	 *            The hierarchy level of entities to find
	 * @return List of all descendant entities at the target level
	 */
	private List<AccountFilteredData> findDescendantsByLevel(AccountFilteredData currentNode, int targetLevel) {
		List<AccountFilteredData> results = new ArrayList<>();
		if (currentNode.getLevel() == targetLevel) {
			results.add(currentNode);
			return results;
		}
		List<AccountFilteredData> children = this.byParentId.getOrDefault(currentNode.getNodeId(), List.of());

		children.forEach(child -> results.addAll(findDescendantsByLevel(child, targetLevel)));

		return results;
	}
}
