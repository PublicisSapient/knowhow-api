/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.hierarchy.integration.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.hierarchy.integration.adapter.OrganizationHierarchyAdapter;
import com.publicissapient.kpidashboard.apis.hierarchy.integration.dto.HierarchyDetails;
import com.publicissapient.kpidashboard.apis.hierarchy.service.OrganizationHierarchyService;
import com.publicissapient.kpidashboard.common.model.application.OrganizationHierarchy;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*
 *	author@aksshriv1
 */

@Service
@Slf4j
@RequiredArgsConstructor
public class IntegrationServiceImpl implements IntegerationService {

	public static final String PORT = "port";
	public static final String PROJECT = "project";
	public static final String SYSTEM = "SYSTEM";

	private final OrganizationHierarchyAdapter organizationHierarchyAdapter;
	private final ProjectBasicConfigRepository projectConfigRepository;
	private final OrganizationHierarchyService organizationHierarchyService;

	@Override
	public void syncOrganizationHierarchy(Set<OrganizationHierarchy> externalList,
			List<OrganizationHierarchy> allDbNodes) {

		// Step 1: Pause projects and ports with missing parent external IDs
		pauseProjectsWithUnavailablePortExternalID(externalList, allDbNodes);

		// Step 2: Map database records by externalId for quick lookup
		Map<String, OrganizationHierarchy> databaseMapByExternalId = allDbNodes.stream()
				.filter(node -> node.getExternalId() != null)
				.collect(Collectors.toMap(OrganizationHierarchy::getExternalId, Function.identity()));

		// Create a map of existing nodes by ID for quick lookup
		Map<String, OrganizationHierarchy> databaseMapById = allDbNodes.stream()
				.collect(Collectors.toMap(node -> node.getId().toString(), Function.identity()));

		// Step 3: Prepare lists for inserts and updates
		List<OrganizationHierarchy> nodesToSave = new ArrayList<>();
		Set<String> processedExternalIds = new HashSet<>();

		for (OrganizationHierarchy externalNode : externalList) {
			if (externalNode.getExternalId() == null) {
				log.warn("Skipping node with null external ID: {}", externalNode);
				continue;
			}

			processedExternalIds.add(externalNode.getExternalId());
			OrganizationHierarchy dbNode = databaseMapByExternalId.get(externalNode.getExternalId());

			if (dbNode == null) {
				// New node: set createdDate and modifiedDate, and add to save list
				externalNode.setCreatedDate(LocalDateTime.now());
				externalNode.setModifiedDate(LocalDateTime.now());
				nodesToSave.add(externalNode);
			} else {
				// Existing node: update fields and add to save list
				dbNode.setNodeName(externalNode.getNodeName());
				dbNode.setNodeDisplayName(externalNode.getNodeName());
				dbNode.setHierarchyLevelId(externalNode.getHierarchyLevelId());
				dbNode.setParentId(externalNode.getParentId());
				dbNode.setModifiedDate(LocalDateTime.now());
				nodesToSave.add(dbNode);
			}
		}

		// Save all new and updated nodes and update the maps
		if (CollectionUtils.isNotEmpty(nodesToSave)) {
			// Save all nodes
			organizationHierarchyService.saveAll(nodesToSave);

			// Update our maps with the nodes that were just saved
			for (OrganizationHierarchy node : nodesToSave) {
				if (node.getExternalId() != null) {
					databaseMapByExternalId.put(node.getExternalId(), node);
				}
				databaseMapById.put(node.getId().toString(), node);
			}

			organizationHierarchyService.clearCache();
		}
	}

	@Override
	public Set<OrganizationHierarchy> convertHieracyResponseToOrganizationHierachy(HierarchyDetails hierarchyDetails,
			List<OrganizationHierarchy> allDbNodes) {
		return organizationHierarchyAdapter.convertToOrganizationHierarchy(hierarchyDetails, allDbNodes);
	}

	private void pauseProjectsWithUnavailablePortExternalID(Set<OrganizationHierarchy> externalList, List<OrganizationHierarchy> allDbNodes) {
		Set<String> externalIds = externalList.stream()
				.map(OrganizationHierarchy::getExternalId)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());

		// Find projects whose parent ports are not in external list
		Set<String> projectsToUpdate = allDbNodes.stream()
				.filter(node -> PROJECT.equalsIgnoreCase(node.getHierarchyLevelId()))
				.filter(node -> {
					OrganizationHierarchy parent = findParentNode(node.getParentId(), allDbNodes);
					return parent != null && PORT.equalsIgnoreCase(parent.getHierarchyLevelId())
							&& parent.getExternalId() != null && !externalIds.contains(parent.getExternalId());
				})
				.map(OrganizationHierarchy::getNodeId)
				.collect(Collectors.toSet());

		// Pause projects
		if (CollectionUtils.isNotEmpty(projectsToUpdate)) {
			List<ProjectBasicConfig> projectsToPause = projectConfigRepository.findByProjectNodeIdIn(projectsToUpdate);
			projectsToPause.forEach(project -> {
				project.setProjectOnHold(true);
				project.setUpdatedAt(DateUtil.dateTimeFormatter(LocalDateTime.now(), DateUtil.TIME_FORMAT));
				project.setUpdatedBy(SYSTEM);
			});
			projectConfigRepository.saveAll(projectsToPause);
			log.info("Paused {} projects with missing parent ports", projectsToPause.size());
		}

	}

	private OrganizationHierarchy findParentNode(String parentId, List<OrganizationHierarchy> allDbNodes) {
		return allDbNodes.stream()
				.filter(node -> node.getNodeId().equals(parentId))
				.findFirst().orElse(null);
	}

}
