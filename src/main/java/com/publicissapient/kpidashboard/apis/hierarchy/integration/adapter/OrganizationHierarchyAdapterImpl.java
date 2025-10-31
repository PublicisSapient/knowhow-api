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

package com.publicissapient.kpidashboard.apis.hierarchy.integration.adapter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.hierarchy.integration.dto.HierarchyDetails;
import com.publicissapient.kpidashboard.apis.hierarchy.integration.dto.HierarchyLevel;
import com.publicissapient.kpidashboard.apis.hierarchy.integration.dto.HierarchyNode;
import com.publicissapient.kpidashboard.common.model.application.OrganizationHierarchy;
import com.publicissapient.kpidashboard.common.service.HierarchyLevelService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*
 *	author@aksshriv1
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationHierarchyAdapterImpl implements OrganizationHierarchyAdapter {
	private final Map<String, OrganizationHierarchy> hierarchyMap = new HashMap<>();
	private final HierarchyLevelService hierarchyLevelService;

	/*
	 * add logic of converting input datalist to Organization Hierarchy
	 */
	@Override
	public Set<OrganizationHierarchy> convertToOrganizationHierarchy(
			HierarchyDetails hierarchyDetails, List<OrganizationHierarchy> allDbNodes) {
		Set<OrganizationHierarchy> transformedList = new HashSet<>();
		List<HierarchyNode> hierarchyNodes = hierarchyDetails.getHierarchyNode();
		List<String> centralHieracyLevels =
				hierarchyDetails.getHierarchyLevels().parallelStream()
						.filter(a -> a.getLevel() > 0)
						.sorted(Comparator.comparing(HierarchyLevel::getLevel))
						.map(k -> k.getDisplayName().toUpperCase())
						.toList();
		Map<String, String> localLevels =
				getHierachyLevelTillProject().stream()
						.limit(centralHieracyLevels.size())
						.collect(
								Collectors.toMap(
										(com.publicissapient.kpidashboard.common.model.application.HierarchyLevel h) ->
												h.getHierarchyLevelName().toUpperCase(),
										com.publicissapient.kpidashboard.common.model.application.HierarchyLevel
												::getHierarchyLevelId));
		List<String> levels = new ArrayList<>();
		for (String hierarchyNode : centralHieracyLevels) {
			levels.add(getMatchingValue(localLevels, hierarchyNode));
		}

		ensureHierarchyExists(hierarchyNodes, transformedList, levels, allDbNodes);
		return transformedList;
	}

	private List<com.publicissapient.kpidashboard.common.model.application.HierarchyLevel>
			getHierachyLevelTillProject() {
		return hierarchyLevelService.getTopHierarchyLevels();
	}

	public void ensureHierarchyExists(
			List<HierarchyNode> nodes,
			Set<OrganizationHierarchy> transformedList,
			List<String> centralHierarchyLevels,
			List<OrganizationHierarchy> allDbNodes) {
		for (HierarchyNode node : nodes) {
			try {
				List<OrganizationHierarchy> fullNode =
						processHierarchyNode(node, centralHierarchyLevels, allDbNodes);
				if (!fullNode.isEmpty()) {
					transformedList.addAll(fullNode);
				}
			} catch (Exception e) {
				log.error(
						"Error processing node: " + node.getOpportunityUniqueId() + " - " + e.getMessage(), e);
			}
		}
	}

	private List<OrganizationHierarchy> processHierarchyNode(
			HierarchyNode node,
			List<String> centralHierarchyLevels,
			List<OrganizationHierarchy> allDbNodes) {

		List<OrganizationHierarchy> fullNode = new ArrayList<>();
		Map<String, OrganizationHierarchy> createdNodes = new HashMap<>();
		Map<String, String> idMappings = getNodeIdMappings(node); // Extracts all externalIds

		for (String chsLevel : centralHierarchyLevels) {
			String parentLevel = getParentLevel(chsLevel);

			// Step 1: Determine parentId
			String parentId = null;
			parentId = getParentId(allDbNodes, parentLevel, createdNodes, parentId, idMappings);

			// Step 2: Skip if not BU and parent is still missing
			if (!"bu".equals(chsLevel) && parentId == null) {
				log.warn("Skipping " + chsLevel + " as parent is missing for node: " + node);
				return Collections.emptyList();
			}

			// Step 3: Check if node already exists in DB by externalId
			String externalId = idMappings.get(chsLevel);
			OrganizationHierarchy existingNode = null;
			existingNode = getExistingNode(allDbNodes, chsLevel, externalId, existingNode);

			OrganizationHierarchy newNode;
			if (existingNode != null) {
				// Reuse existing node, just update parentId if needed
				newNode = existingNode;
				if (parentId != null) {
					newNode.setParentId(parentId);
				}
			} else {
				// Create new node
				newNode = createOrUpdateNode(externalId, getNodeName(node, chsLevel), chsLevel, parentId);
			}

			createdNodes.put(chsLevel, newNode);
			fullNode.add(newNode);
		}

		return fullNode;
	}

	@Nullable
	private static OrganizationHierarchy getExistingNode(
			List<OrganizationHierarchy> allDbNodes,
			String chsLevel,
			String externalId,
			OrganizationHierarchy existingNode) {
		if (externalId != null) {
			existingNode =
					allDbNodes.stream()
							.filter(
									dbNode ->
											chsLevel.equalsIgnoreCase(dbNode.getHierarchyLevelId())
													&& externalId.equalsIgnoreCase(dbNode.getExternalId()))
							.findFirst()
							.orElse(null);
		}
		return existingNode;
	}

	private static String getParentId(
			List<OrganizationHierarchy> allDbNodes,
			String parentLevel,
			Map<String, OrganizationHierarchy> createdNodes,
			String parentId,
			Map<String, String> idMappings) {
		if (parentLevel != null) {
			if (createdNodes.containsKey(parentLevel)) {
				// Parent was created in current iteration
				parentId = createdNodes.get(parentLevel).getNodeId();
			} else {
				// Parent might already exist in DB
				String parentExternalId = idMappings.get(parentLevel);
				if (parentExternalId != null) {
					OrganizationHierarchy existingParent =
							allDbNodes.stream()
									.filter(dbNode -> parentExternalId.equalsIgnoreCase(dbNode.getExternalId()))
									.findFirst()
									.orElse(null);
					if (existingParent != null) {
						parentId = existingParent.getNodeId();
					}
				}
			}
		}
		return parentId;
	}

	private Map<String, String> getNodeIdMappings(HierarchyNode node) {
		Map<String, String> idMappings = new HashMap<>();
		idMappings.put("bu", node.getBuUniqueId());
		idMappings.put("ver", node.getVerticalUniqueId());
		idMappings.put("acc", node.getAccountUniqueId());
		idMappings.put("port", node.getPortfolioUniqueId());

		idMappings.replaceAll(
				(k, v) -> StringUtils.isEmpty(v) ? k + "_unique_" + UUID.randomUUID() : v);
		return idMappings;
	}

	private String getNodeName(HierarchyNode node, String chsLevel) {
		switch (chsLevel) {
			case "bu":
				return node.getBu();
			case "ver":
				return node.getVertical();
			case "acc":
				return node.getAccount();
			case "port":
				return node.getPortfolio();
			default:
				throw new IllegalArgumentException("Invalid hierarchy level: " + chsLevel);
		}
	}

	private String getParentLevel(String chsLevel) {
		switch (chsLevel) {
			case "bu":
				return null;
			case "ver":
				return "bu";
			case "acc":
				return "ver";
			case "port":
				return "acc";
			default:
				throw new IllegalArgumentException("Invalid hierarchy level: " + chsLevel);
		}
	}

	public OrganizationHierarchy createOrUpdateNode(
			String nodeId, String nodeName, String hierarchyLevelId, String parentId) {

		if (hierarchyMap.containsKey(nodeId)) {
			OrganizationHierarchy existingNode = hierarchyMap.get(nodeId);

			// Ensure child has only one parent
			if (!Objects.equals(existingNode.getParentId(), parentId)) {
				throw new IllegalStateException("Node " + nodeId + " cannot have multiple parents!");
			}
			return existingNode;
		}

		// Create a new node if not exists
		OrganizationHierarchy newNode = new OrganizationHierarchy();
		newNode.setNodeId(UUID.randomUUID().toString());
		newNode.setExternalId(nodeId);
		newNode.setNodeName(nodeName);
		newNode.setNodeDisplayName(nodeName);
		newNode.setHierarchyLevelId(hierarchyLevelId);
		newNode.setParentId(parentId);
		newNode.setCreatedDate(LocalDateTime.now());
		newNode.setModifiedDate(LocalDateTime.now());

		hierarchyMap.put(nodeId, newNode);
		return newNode;
	}

	public static String getMatchingValue(Map<String, String> dataMap, String inputKey) {
		String[] possibleKeys = inputKey.split("[/\\s]+");
		for (String key : possibleKeys) {
			if (dataMap.containsKey(key)) {
				return dataMap.get(key); // Return value of "Project"
			}
		}
		throw new IllegalStateException("Hierarchy Missing");
	}
}
