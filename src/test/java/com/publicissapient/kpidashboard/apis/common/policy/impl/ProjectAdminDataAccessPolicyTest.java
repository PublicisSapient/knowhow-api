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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.common.model.rbac.AccessItem;
import com.publicissapient.kpidashboard.common.model.rbac.AccessNode;
import com.publicissapient.kpidashboard.common.model.rbac.ProjectsAccess;
import com.publicissapient.kpidashboard.common.model.rbac.UserInfo;
import com.publicissapient.kpidashboard.common.repository.rbac.UserInfoRepository;

class ProjectAdminDataAccessPolicyTest {
	@InjectMocks private ProjectAdminDataAccessPolicy policy;

	@Mock private UserInfoRepository userRepository;
	String userName;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		userName = "tempName";
	}

	@Test
	void shouldReturnEmptyListIfNoUsers() {
		// given
		when(userRepository.findByUsername(userName)).thenReturn(null);

		// when
		List<UserInfo> result = policy.getAccessibleMembers(userName);

		// then
		assertEquals(0, result.size());
		verify(userRepository, times(1)).findByUsername(userName);
	}

	@Test
	void shouldReturnEmptyListIfNoProjectIsAvailable() {

		UserInfo userInfo = new UserInfo();
		userInfo.setUsername(userName);
		userInfo.setProjectsAccess(new ArrayList<>());
		// given
		when(userRepository.findByUsername(userName)).thenReturn(userInfo);

		// when
		List<UserInfo> result = policy.getAccessibleMembers(userName);

		// then
		assertEquals(0, result.size());
		verify(userRepository, times(1)).findByUsername(userName);
	}

	@Test
	void shouldReturnEmptyListIfNoProjectAdminRoleIsAvailable() {

		ProjectsAccess access = new ProjectsAccess();
		access.setRole(Constant.ROLE_GUEST);
		access.setAccessNodes(new ArrayList<>());

		UserInfo userInfo = new UserInfo();
		userInfo.setUsername(userName);
		userInfo.setProjectsAccess(List.of(access));
		// given
		when(userRepository.findByUsername(userName)).thenReturn(userInfo);

		// when
		List<UserInfo> result = policy.getAccessibleMembers(userName);

		// then
		assertEquals(0, result.size());
		verify(userRepository, times(1)).findByUsername(userName);
	}

	@Test
	void shouldReturnUserList() {

		AccessItem accessItem = new AccessItem();
		accessItem.setItemId("tempItemId1");
		AccessItem accessItem2 = new AccessItem();
		accessItem2.setItemId("tempItemId2");
		AccessNode accessNode = new AccessNode();
		accessNode.setAccessLevel("project");
		accessNode.setAccessItems(List.of(accessItem, accessItem2));
		ProjectsAccess access = new ProjectsAccess();
		access.setRole(Constant.ROLE_PROJECT_ADMIN);
		access.setAccessNodes(List.of(accessNode));

		UserInfo userInfo = new UserInfo();
		userInfo.setUsername(userName);
		userInfo.setProjectsAccess(List.of(access));
		// given
		when(userRepository.findByUsername(userName)).thenReturn(userInfo);
		List<UserInfo> userInfoList = new ArrayList<>();
		userInfoList.add(userInfo);
		List<String> items = accessNode.getAccessItems().stream().map(AccessItem::getItemId).toList();
		when(userRepository.findUsersByItemIdsOrCreatedBy(items, "userName")).thenReturn(userInfoList);

		// when
		List<UserInfo> result = policy.getAccessibleMembers(userName);

		// then
		assertEquals(0, result.size());
		verify(userRepository, times(1)).findByUsername(userName);
	}
}
