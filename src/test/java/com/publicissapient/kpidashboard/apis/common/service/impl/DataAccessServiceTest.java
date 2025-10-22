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
package com.publicissapient.kpidashboard.apis.common.service.impl;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.publicissapient.kpidashboard.apis.common.policy.DataAccessPolicy;
import com.publicissapient.kpidashboard.common.model.rbac.UserInfo;

class DataAccessServiceTest {

	private DataAccessService dataAccessService;

	private DataAccessPolicy superAdminPolicy;
	private DataAccessPolicy projectAdminPolicy;
	private DataAccessPolicy readOnlyPolicy;

	@BeforeEach
	void setUp() {
		// Mock policies
		superAdminPolicy = mock(DataAccessPolicy.class);
		projectAdminPolicy = mock(DataAccessPolicy.class);
		readOnlyPolicy = mock(DataAccessPolicy.class);

		// Map of role → policy
		Map<String, DataAccessPolicy> policies = new HashMap<>();
		policies.put("SUPER_ADMIN", superAdminPolicy);
		policies.put("PROJECT_ADMIN", projectAdminPolicy);

		// Initialize service with policies
		dataAccessService = new DataAccessService(policies);
	}

	@Test
	void shouldReturnMembersForSuperAdmin() {
		// given
		List<String> providedRoles = List.of("SUPER_ADMIN");
		String user = "superAdminUser";
		UserInfo userInfo = new UserInfo();
		userInfo.setUsername("user1");
		List<UserInfo> expectedMembers = List.of(userInfo, new UserInfo());
		when(superAdminPolicy.getAccessibleMembers(user)).thenReturn(expectedMembers);

		// when
		List<UserInfo> result = dataAccessService.getMembersForUser(providedRoles, user);

		// then
		assertEquals(2, result.size());
		assertEquals("user1", result.get(0).getUsername());
		verify(superAdminPolicy, times(1)).getAccessibleMembers(user);

		// other policies should not be called
		verifyNoInteractions(projectAdminPolicy);
		verifyNoInteractions(readOnlyPolicy);
	}

	@Test
	void shouldReturnMembersForProjectAdmin() {
		// given
		List<String> providedRoles = List.of("PROJECT_ADMIN", "READ_ONLY_USER");
		String user = "projectAdminUser";
		UserInfo userInfo = new UserInfo();
		userInfo.setUsername("member1");
		List<UserInfo> expectedMembers = List.of(userInfo);
		when(projectAdminPolicy.getAccessibleMembers(user)).thenReturn(expectedMembers);

		// when
		List<UserInfo> result = dataAccessService.getMembersForUser(providedRoles, user);

		// then
		assertEquals(1, result.size());
		assertEquals("member1", result.get(0).getUsername());
		verify(projectAdminPolicy, times(1)).getAccessibleMembers(user);
		verifyNoInteractions(superAdminPolicy);
		verifyNoInteractions(readOnlyPolicy);
	}

	@Test
	void shouldReturnMembersForReadOnlyUser() {
		// given
		List<String> providedRoles = List.of("READ_ONLY_USER");
		String user = "readOnlyUser";
		List<UserInfo> expectedMembers = List.of(); // empty list
		when(readOnlyPolicy.getAccessibleMembers(user)).thenReturn(expectedMembers);

		// when
		IllegalArgumentException exception =
				assertThrows(
						IllegalArgumentException.class,
						() -> dataAccessService.getMembersForUser(providedRoles, user));

		// then
		assertTrue(exception.getMessage().contains("No policy defined for role"));
		verifyNoInteractions(superAdminPolicy);
		verifyNoInteractions(projectAdminPolicy);
	}

	@Test
	void shouldThrowExceptionIfNoPolicyDefined() {
		List<String> providedRoles = List.of("UNKNOWN_ROLE");
		String user = "someUser";

		IllegalArgumentException exception =
				assertThrows(
						IllegalArgumentException.class,
						() -> dataAccessService.getMembersForUser(providedRoles, user));

		assertTrue(exception.getMessage().contains("No policy defined for role"));
		verifyNoInteractions(superAdminPolicy, projectAdminPolicy, readOnlyPolicy);
	}
}
