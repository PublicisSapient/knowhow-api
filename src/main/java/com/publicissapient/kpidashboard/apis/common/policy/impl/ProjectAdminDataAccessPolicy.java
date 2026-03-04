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
package com.publicissapient.kpidashboard.apis.common.policy.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.common.policy.DataAccessPolicy;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.common.model.application.OrganizationHierarchy;
import com.publicissapient.kpidashboard.common.model.rbac.AccessItem;
import com.publicissapient.kpidashboard.common.model.rbac.UserInfo;
import com.publicissapient.kpidashboard.common.repository.application.OrganizationHierarchyRepository;
import com.publicissapient.kpidashboard.common.repository.rbac.UserInfoRepository;

/**
 * Bean for Project Admin.
 *
 * @author gursinh49
 */
@Component(Constant.ROLE_PROJECT_ADMIN)
public class ProjectAdminDataAccessPolicy implements DataAccessPolicy {
	@Autowired private UserInfoRepository userInfoRepository;
	@Autowired private OrganizationHierarchyRepository organizationHierarchyRepository;

	@Override
	public List<UserInfo> getAccessibleMembers(String userName) {
		UserInfo fullUserDoc = userInfoRepository.findByUsername(userName);
		if (fullUserDoc == null || fullUserDoc.getProjectsAccess().isEmpty()) {
			return Collections.emptyList();
		}

		List<String> accessibleItemIds =
				fullUserDoc.getProjectsAccess().stream()
						.filter(p -> p.getRole().equals(Constant.ROLE_PROJECT_ADMIN))
						.flatMap(p -> p.getAccessNodes().stream())
						.flatMap(n -> n.getAccessItems().stream())
						.map(AccessItem::getItemId)
						.toList();

		if (accessibleItemIds.isEmpty()) {
			return Collections.emptyList();
		}

		List<String> allAccessibleItemIds = new ArrayList<>(accessibleItemIds);
		List<OrganizationHierarchy> allHierarchies = organizationHierarchyRepository.findAll();
		for (String itemId : accessibleItemIds) {
			allAccessibleItemIds.addAll(getChildNodeIds(itemId, allHierarchies));
		}

		return userInfoRepository.findUsersByItemIdsOrCreatedBy(allAccessibleItemIds, userName);
	}

	private List<String> getChildNodeIds(String parentId, List<OrganizationHierarchy> allHierarchies) {
		List<String> childIds = new ArrayList<>();
		for (OrganizationHierarchy hierarchy : allHierarchies) {
			if (parentId.equals(hierarchy.getParentId())) {
				childIds.add(hierarchy.getNodeId());
				childIds.addAll(getChildNodeIds(hierarchy.getNodeId(), allHierarchies));
			}
		}
		return childIds;
	}
}
