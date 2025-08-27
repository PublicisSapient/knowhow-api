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

import com.publicissapient.kpidashboard.apis.hierarchy.integeration.adapter.OrganizationHierarchyAdapter;
import com.publicissapient.kpidashboard.apis.hierarchy.integeration.dto.HierarchyDetails;
import com.publicissapient.kpidashboard.apis.hierarchy.service.OrganizationHierarchyService;
import com.publicissapient.kpidashboard.apis.projectconfig.basic.service.ProjectBasicConfigService;
import com.publicissapient.kpidashboard.common.model.application.OrganizationHierarchy;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.repository.application.OrganizationHierarchyRepository;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class IntegrationServiceImpl implements IntegerationService {

    @Autowired
    private OrganizationHierarchyAdapter organizationHierarchyAdapter;
    @Autowired
    private OrganizationHierarchyRepository organizationHierarchyRepository;
    @Autowired
    private OrganizationHierarchyService organizationHierarchyService;
    @Autowired
    private ProjectBasicConfigService projectBasicConfigService;
    @Autowired
    private ProjectBasicConfigRepository projectBasicConfigRepository;


    @Override
    public void syncOrganizationHierarchy(Set<OrganizationHierarchy> externalList) {
        // Step 1: Fetch all existing records from the database
        List<OrganizationHierarchy> allDbNodes = organizationHierarchyRepository.findAll();

        // Step 2: Map database records by externalId for quick lookup
        Map<String, OrganizationHierarchy> databaseMapByExternalId = allDbNodes.stream()
                .filter(node -> node.getExternalId() != null)
                .collect(Collectors.toMap(OrganizationHierarchy::getExternalId, Function.identity()));

        // Create a map of existing nodes by ID for quick lookup
        Map<String, OrganizationHierarchy> databaseMapById = allDbNodes.stream()
                .collect(Collectors.toMap(node -> node.getId().toString(), Function.identity()));

        // Step 4: Prepare lists for inserts and updates
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

        // Handle deletions if enabled - now with updated maps
        if (false) {
            handleOrphanedNodes(databaseMapByExternalId, processedExternalIds, databaseMapById);
        }
    }

    @Override
    public Set<OrganizationHierarchy> convertHieracyResponseToOrganizationHierachy(HierarchyDetails hierarchyDetails) {
        return organizationHierarchyAdapter.convertToOrganizationHierarchy(hierarchyDetails);
    }

    /**
     * Handles deletion of orphaned nodes that exist in the database but not in the external list
     *
     * @param databaseMapByExternalId Map of database nodes by external ID
     * @param processedExternalIds    Set of external IDs from the external list
     * @param databaseMapById         Map of all database nodes by their ID
     */
    private void handleOrphanedNodes(Map<String, OrganizationHierarchy> databaseMapByExternalId,
                                     Set<String> processedExternalIds,
                                     Map<String, OrganizationHierarchy> databaseMapById) {

        // Find nodes that are in the database but not in the external list
        Set<OrganizationHierarchy> nodesToDelete = databaseMapByExternalId.values().stream()
                .filter(node -> !processedExternalIds.contains(node.getExternalId()))
                .filter(node -> !"project".equalsIgnoreCase(node.getHierarchyLevelId()))
                .collect(Collectors.toSet());

        if (CollectionUtils.isEmpty(nodesToDelete)) {
            return;
        }

        log.info("Found {} orphaned nodes to process for deletion", nodesToDelete.size());

        // Find all nodes that need to be deleted (including children)
        Set<ObjectId> nodeIdsToDelete = new HashSet<>();
        Set<String> projectNodeIdsToDelete = new HashSet<>();

        for (OrganizationHierarchy node : nodesToDelete) {
                collectNodesForDeletion(node, nodeIdsToDelete, projectNodeIdsToDelete, databaseMapById);

        }

        // Delete project nodes using the project service
        if (!projectNodeIdsToDelete.isEmpty()) {
            log.info("Deleting {} project nodes", projectNodeIdsToDelete.size());

            // Process each project node ID individually since we don't have a bulk findByProjectNodeIdIn method
            for (String projectNodeId : projectNodeIdsToDelete) {
                try {
                    ProjectBasicConfig project = projectBasicConfigRepository.findByProjectNodeId(projectNodeId);
                    if (project != null) {
                        log.info("Deleting project with ID: {}", project.getId());
                        projectBasicConfigService.deleteProject(project.getId().toString());
                    } else {
                        log.warn("No ProjectBasicConfig found for project node ID: {}", projectNodeId);
                    }
                } catch (Exception e) {
                    log.error("Error deleting project with node ID: " + projectNodeId, e);
                }
            }
        }

        // Delete non-project nodes
        if (!nodeIdsToDelete.isEmpty()) {
            log.info("Deleting {} non-project nodes", nodeIdsToDelete.size());
            if (!nodeIdsToDelete.isEmpty()) {
                organizationHierarchyRepository.deleteAllById(nodeIdsToDelete);
            }
        }

        organizationHierarchyService.clearCache();
    }

    /**
     * Recursively collects all child nodes for deletion
     */
    private void collectNodesForDeletion(OrganizationHierarchy node,
                                         Set<ObjectId> nodeIdsToDelete,
                                         Set<String> projectIdsToDelete,
                                         Map<String, OrganizationHierarchy> databaseMapById) {
        // Skip if already processed
        if (node == null || nodeIdsToDelete.contains(node.getId())) {
            return;
        }

        // Add current node to deletion set
        if ("project".equalsIgnoreCase(node.getHierarchyLevelId())) {
            projectIdsToDelete.add(node.getId().toString());
            nodeIdsToDelete.add(node.getId());
        } else {
            nodeIdsToDelete.add(node.getId());
        }

        // Recursively process children
        databaseMapById.values().stream()
                .filter(child -> node.getId().equals(child.getParentId()))
                .forEach(child -> collectNodesForDeletion(child, nodeIdsToDelete, projectIdsToDelete, databaseMapById));
    }
}
