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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.common.model.rbac.UserInfo;
import com.publicissapient.kpidashboard.common.repository.rbac.UserInfoRepository;

class SuperAdminDataAccessPolicyTest {

	@InjectMocks private SuperAdminDataAccessPolicy policy;

	@Mock private UserInfoRepository userRepository;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void shouldReturnAllUsers() {
		// given
		UserInfo userInfo = new UserInfo();
		userInfo.setUsername("user1");
		List<UserInfo> allUsers = List.of(userInfo, new UserInfo());
		when(userRepository.findAll()).thenReturn(allUsers);

		// when
		List<UserInfo> result = policy.getAccessibleMembers(Constant.ROLE_SUPERADMIN);

		// then
		assertEquals(2, result.size());
		assertEquals("user1", result.get(0).getUsername());
		verify(userRepository, times(1)).findAll();
	}

	@Test
	void shouldReturnEmptyListIfNoUsers() {
		// given
		when(userRepository.findAll()).thenReturn(List.of());

		// when
		List<UserInfo> result = policy.getAccessibleMembers(Constant.ROLE_SUPERADMIN);

		// then
		assertEquals(0, result.size());
		verify(userRepository, times(1)).findAll();
	}
}
