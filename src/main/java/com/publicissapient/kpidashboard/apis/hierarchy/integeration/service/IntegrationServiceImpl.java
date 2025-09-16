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

package com.publicissapient.kpidashboard.apis.hierarchy.integeration.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.hierarchy.integeration.adapter.OrganizationHierarchyAdapter;
import com.publicissapient.kpidashboard.apis.hierarchy.integeration.dto.HierarchyDetails;
import com.publicissapient.kpidashboard.apis.hierarchy.service.OrganizationHierarchyService;
import com.publicissapient.kpidashboard.common.model.application.OrganizationHierarchy;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

/*
 *	author@aksshriv1
 */

@Service
@Slf4j
public class IntegrationServiceImpl implements IntegerationService {

	public static final String PORT = "port";
	public static final String PROJECT = "project";
	public static final String SYSTEM = "SYSTEM";
	@Autowired
	private OrganizationHierarchyAdapter organizationHierarchyAdapter;
	@Autowired
	private ProjectBasicConfigRepository projectConfigRepository;
	@Autowired
	private OrganizationHierarchyService organizationHierarchyService;

	@Override
	public void syncOrganizationHierarchy(Set<OrganizationHierarchy> externalList,
			List<OrganizationHierarchy> allDbNodes) {

		// Step 1: Find all ports without external IDs
		List<OrganizationHierarchy> portsWithoutExternalIds = allDbNodes.stream()
				.filter(node -> PORT.equalsIgnoreCase(node.getHierarchyLevelId()))
				.filter(node -> node.getExternalId() == null || node.getExternalId().trim().isEmpty()).toList();

		// Pause projects under ports without external IDs
		if (CollectionUtils.isNotEmpty(portsWithoutExternalIds)) {
			pauseProjectsUnderPorts(portsWithoutExternalIds, allDbNodes);
		}

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

	/**
	 * Pauses all projects that are children of the given ports in the organization
	 * hierarchy
	 *
	 * @param ports
	 *            List of port nodes whose projects should be paused
	 * @param allNodes
	 *            All nodes in the organization hierarchy
	 */
	private void pauseProjectsUnderPorts(List<OrganizationHierarchy> ports, List<OrganizationHierarchy> allNodes) {
		log.info("Found {} ports without external IDs. Pausing their projects...", ports.size());

		// Get all project nodes that are children of these ports
		Set<String> portNodeIds = ports.stream().map(node -> node.getNodeId()).collect(Collectors.toSet());

		// Find all project nodes that are direct children of these ports
		List<OrganizationHierarchy> projectNodes = allNodes.stream()
				.filter(node -> PROJECT.equalsIgnoreCase(node.getHierarchyLevelId()))
				.filter(node -> portNodeIds.contains(node.getParentId())).toList();

		// Get all project configs for these project nodes
		Set<String> projectNodeIds = projectNodes.stream().map(node -> node.getNodeId())
				.collect(Collectors.toSet());

		if (CollectionUtils.isNotEmpty(projectNodeIds)) {
			List<ProjectBasicConfig> projectsToPause = projectConfigRepository.findByProjectNodeIdIn(projectNodeIds);

			if (CollectionUtils.isNotEmpty(projectsToPause)) {
				log.info("Pausing {} projects under ports without external IDs", projectsToPause.size());
				// Mark all in-memory objects as paused
				projectsToPause.forEach(projectBasicConfig -> {
					projectBasicConfig.setProjectOnHold(true);
					projectBasicConfig
							.setUpdatedAt(DateUtil.dateTimeFormatter(LocalDateTime.now(), DateUtil.TIME_FORMAT));
					projectBasicConfig.setUpdatedBy(SYSTEM);
				});

				try {
					projectConfigRepository.saveAll(projectsToPause);
					log.info("Paused {} projects successfully", projectsToPause.size());
				} catch (Exception e) {
					log.error("Error pausing projects", e);
				}

			} else {
				log.info("No projects found under ports without external IDs");
			}
		} else {
			log.info("No project nodes found under ports without external IDs");
		}
	}

}
