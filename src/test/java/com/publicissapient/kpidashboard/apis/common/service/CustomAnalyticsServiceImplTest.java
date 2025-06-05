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

package com.publicissapient.kpidashboard.apis.common.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.json.simple.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.google.common.collect.Lists;
import com.publicissapient.kpidashboard.apis.abac.ProjectAccessManager;
import com.publicissapient.kpidashboard.apis.abac.UserAuthorizedProjectsService;
import com.publicissapient.kpidashboard.apis.auth.model.Authentication;
import com.publicissapient.kpidashboard.apis.auth.repository.AuthenticationRepository;
import com.publicissapient.kpidashboard.apis.common.service.impl.CustomAnalyticsServiceImpl;
import com.publicissapient.kpidashboard.apis.common.service.impl.UserInfoServiceImpl;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.common.constant.AuthType;
import com.publicissapient.kpidashboard.common.model.rbac.CentralUserInfoDTO;
import com.publicissapient.kpidashboard.common.model.rbac.RoleWiseProjects;
import com.publicissapient.kpidashboard.common.model.rbac.UserInfo;
import com.publicissapient.kpidashboard.common.model.rbac.UserTokenData;
import com.publicissapient.kpidashboard.common.repository.rbac.UserInfoRepository;
import com.publicissapient.kpidashboard.common.repository.rbac.UserTokenReopository;

import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

@RunWith(MockitoJUnitRunner.class)
public class CustomAnalyticsServiceImplTest {

	@Mock
	UserAuthorizedProjectsService userAuthorizedProjectsService;
	Authentication authentication;
	UserInfo user;
	RoleWiseProjects roleWiseProjects;
	List<RoleWiseProjects> listRoleWiseProjects = new ArrayList<>();
	@InjectMocks
	private CustomAnalyticsServiceImpl customAnalyticsServiceImpl;
	@Mock
	private UserInfoRepository userInfoRepository;
	@Mock
	private AuthenticationRepository authenticationRepository;
	@Mock
	private CustomApiConfig customAPISettings;
	@Mock
	private ProjectAccessManager projectAccessManager;
	@Mock
	private UserInfoServiceImpl service;
	@Mock
	private UsersSessionService usersSessionService;
	@Mock
	private UserTokenReopository userTokenReopository;

	@Test
	public void testAddAnalyticsData() {
		HttpServletResponse resp = Mockito.mock(HttpServletResponse.class);
		user = new UserInfo();
		user.setUsername("user");
		user.setAuthType(AuthType.STANDARD);
		user.setAuthorities(Lists.newArrayList("ROLE_VIEWER"));
		user.setId(new ObjectId("6373796960277453212bc610"));
		authentication = new Authentication();
		authentication.setEmail("email");
		roleWiseProjects = new RoleWiseProjects();

		when(userInfoRepository.findByUsername(Mockito.anyString())).thenReturn(user);
		when(authenticationRepository.findByUsername(Mockito.anyString())).thenReturn(authentication);
		when(projectAccessManager.getProjectAccessesWithRole(Mockito.anyString())).thenReturn(listRoleWiseProjects);
		JSONObject json = customAnalyticsServiceImpl.addAnalyticsData(resp, "test");
		assertEquals("test", json.get("user_name"));
		assertEquals(json.get("authorities"), user.getAuthorities());
	}

	@Test
	public void testAddAnalyticsDataForCentralAuth_ExistingUser() {
		HttpServletResponse resp = Mockito.mock(HttpServletResponse.class);
		user = new UserInfo();
		user.setUsername("user");
		user.setAuthType(AuthType.STANDARD);
		user.setAuthorities(Lists.newArrayList("ROLE_VIEWER"));
		user.setId(new ObjectId("6373796960277453212bc610"));
		user.setFirstName("John");
		user.setLastName("Doe");
		user.setDisplayName("John Doe");
		user.setEmailAddress("john.doe@example.com");
		authentication = new Authentication();
		authentication.setEmail("email");
		roleWiseProjects = new RoleWiseProjects();

		when(userInfoRepository.findByUsername(Mockito.anyString())).thenReturn(user);
		when(authenticationRepository.findByUsername(Mockito.anyString())).thenReturn(authentication);
		when(projectAccessManager.getProjectAccessesWithRole(Mockito.anyString())).thenReturn(listRoleWiseProjects);
		
		Map<String, Object> json = customAnalyticsServiceImpl.addAnalyticsDataAndSaveCentralUser(resp, "test", "token");
		
		assertEquals("test", json.get("user_name"));
		assertEquals(json.get("authorities"), user.getAuthorities());
		// Verify that no central auth call was made since user info is complete
		Mockito.verify(service, Mockito.never()).getCentralAuthUserInfoDetails(Mockito.anyString(), Mockito.anyString());
	}
	
	@Test
	public void testAddAnalyticsDataForCentralAuth_NewUser() {
		HttpServletResponse resp = Mockito.mock(HttpServletResponse.class);
		
		// Return null for the initial user lookup to trigger new user creation
		when(userInfoRepository.findByUsername(Mockito.anyString())).thenReturn(null);
		
		// Setup central auth response
		CentralUserInfoDTO centralUserInfoDTO = new CentralUserInfoDTO();
		centralUserInfoDTO.setFirstName("John");
		centralUserInfoDTO.setLastName("Doe");
		centralUserInfoDTO.setDisplayName("John Doe");
		centralUserInfoDTO.setEmail("john.doe@example.com");
		centralUserInfoDTO.setAuthType(AuthType.STANDARD);
		
		when(service.getCentralAuthUserInfoDetails(Mockito.anyString(), Mockito.anyString()))
			.thenReturn(centralUserInfoDTO);
		
		// Setup new user to be saved - with ID already set
		UserInfo newUser = new UserInfo();
		newUser.setUsername("test");
		newUser.setFirstName("John");
		newUser.setLastName("Doe");
		newUser.setDisplayName("John Doe");
		newUser.setEmailAddress("john.doe@example.com");
		newUser.setAuthType(AuthType.STANDARD);
		newUser.setId(new ObjectId("6373796960277453212bc610"));
		
		// This is the key part - we need to make sure save() sets the ID
		// Use an Answer to set the ID on any UserInfo object that's passed to save()
		when(userInfoRepository.save(Mockito.any(UserInfo.class))).thenAnswer(invocation -> {
			UserInfo savedUser = invocation.getArgument(0);
			if (savedUser.getId() == null) {
				savedUser.setId(new ObjectId("6373796960277453212bc610"));
			}
			return savedUser;
		});
		
		// Setup authentication and roles
		authentication = new Authentication();
		authentication.setEmail("email");
		roleWiseProjects = new RoleWiseProjects();
		when(authenticationRepository.findByUsername(Mockito.anyString())).thenReturn(authentication);
		when(projectAccessManager.getProjectAccessesWithRole(Mockito.anyString())).thenReturn(listRoleWiseProjects);
		
		// Call the method under test
		Map<String, Object> json = customAnalyticsServiceImpl.addAnalyticsDataAndSaveCentralUser(resp, "test", "token");
		
		// Verify results
		assertNotNull(json);
		assertEquals("test", json.get("user_name"));
		
		// Verify central auth was called
		Mockito.verify(service, Mockito.times(1)).getCentralAuthUserInfoDetails(Mockito.anyString(), Mockito.anyString());
		
		// Verify user was saved
		Mockito.verify(userInfoRepository, Mockito.times(1)).save(Mockito.any(UserInfo.class));
	}
	
	@Test
	public void testAddAnalyticsDataForCentralAuth_IncompleteUser() {
		HttpServletResponse resp = Mockito.mock(HttpServletResponse.class);
		user = new UserInfo();
		user.setUsername("user");
		user.setAuthType(AuthType.STANDARD);
		user.setAuthorities(Lists.newArrayList("ROLE_VIEWER"));
		user.setId(new ObjectId("6373796960277453212bc610"));
		// Missing first name, last name, display name
		user.setEmailAddress("john.doe@example.com");
		
		CentralUserInfoDTO centralUserInfoDTO = new CentralUserInfoDTO();
		centralUserInfoDTO.setFirstName("John");
		centralUserInfoDTO.setLastName("Doe");
		centralUserInfoDTO.setDisplayName("John Doe");
		centralUserInfoDTO.setEmail("john.doe@example.com");
		centralUserInfoDTO.setAuthType(AuthType.STANDARD);
		
		authentication = new Authentication();
		authentication.setEmail("email");
		roleWiseProjects = new RoleWiseProjects();

		when(userInfoRepository.findByUsername(Mockito.anyString())).thenReturn(user);
		when(service.getCentralAuthUserInfoDetails(Mockito.anyString(), Mockito.anyString())).thenReturn(centralUserInfoDTO);
		when(authenticationRepository.findByUsername(Mockito.anyString())).thenReturn(authentication);
		when(projectAccessManager.getProjectAccessesWithRole(Mockito.anyString())).thenReturn(listRoleWiseProjects);
		
		Map<String, Object> json = customAnalyticsServiceImpl.addAnalyticsDataAndSaveCentralUser(resp, "test", "token");
		
		assertEquals("test", json.get("user_name"));
		// Verify central auth was called since user info was incomplete
		Mockito.verify(service, Mockito.times(1)).getCentralAuthUserInfoDetails(Mockito.anyString(), Mockito.anyString());
		// Verify user was updated
		Mockito.verify(userInfoRepository, Mockito.times(1)).save(Mockito.any(UserInfo.class));
	}

	@Test
	public void getAnalyticsSwitch() {
		when(customAPISettings.isAnalyticsSwitch()).thenReturn(true);
		JSONObject json = customAnalyticsServiceImpl.getAnalyticsCheck();
		assertEquals(true, json.get("analyticsSwitch"));
	}
	
	@Test
	public void testIsUserInfoIncomplete_Complete() throws Exception {
		// Setup a complete user info
		UserInfo userInfo = new UserInfo();
		userInfo.setFirstName("John");
		userInfo.setLastName("Doe");
		userInfo.setDisplayName("John Doe");
		
		// Use reflection to access the private method
		Method isUserInfoIncompleteMethod = CustomAnalyticsServiceImpl.class.getDeclaredMethod("isUserInfoIncomplete", UserInfo.class);
		isUserInfoIncompleteMethod.setAccessible(true);
		
		// Call the method and verify result
		boolean result = (boolean) isUserInfoIncompleteMethod.invoke(customAnalyticsServiceImpl, userInfo);
		assertEquals(false, result); // Should return false for complete user info
	}
	
	@Test
	public void testIsUserInfoIncomplete_MissingFirstName() throws Exception {
		// Setup an incomplete user info (missing first name)
		UserInfo userInfo = new UserInfo();
		userInfo.setLastName("Doe");
		userInfo.setDisplayName("John Doe");
		
		// Use reflection to access the private method
		Method isUserInfoIncompleteMethod = CustomAnalyticsServiceImpl.class.getDeclaredMethod("isUserInfoIncomplete", UserInfo.class);
		isUserInfoIncompleteMethod.setAccessible(true);
		
		// Call the method and verify result
		boolean result = (boolean) isUserInfoIncompleteMethod.invoke(customAnalyticsServiceImpl, userInfo);
		assertEquals(true, result); // Should return true for incomplete user info
	}
	
	@Test
	public void testUpdateExistingUserInfo() throws Exception {
		// Setup test data
		UserInfo userInfo = new UserInfo();
		userInfo.setUsername("test");
		
		CentralUserInfoDTO centralUserInfoDTO = new CentralUserInfoDTO();
		centralUserInfoDTO.setFirstName("John");
		centralUserInfoDTO.setLastName("Doe");
		centralUserInfoDTO.setDisplayName("John Doe");
		
		// Setup mocks
		when(service.getCentralAuthUserInfoDetails(Mockito.anyString(), Mockito.anyString()))
			.thenReturn(centralUserInfoDTO);
		when(userInfoRepository.save(Mockito.any(UserInfo.class))).thenReturn(userInfo);
		
		// Use reflection to access the private method
		Method updateExistingUserInfoMethod = CustomAnalyticsServiceImpl.class.getDeclaredMethod(
			"updateExistingUserInfo", UserInfo.class, String.class, String.class);
		updateExistingUserInfoMethod.setAccessible(true);
		
		// Call the method
		UserInfo result = (UserInfo) updateExistingUserInfoMethod.invoke(customAnalyticsServiceImpl, userInfo, "test", "token");
		
		// Verify the user info was updated with central auth data
		assertEquals("John", result.getFirstName());
		assertEquals("Doe", result.getLastName());
		assertEquals("John Doe", result.getDisplayName());
		
		// Verify the save method was called
		Mockito.verify(userInfoRepository, Mockito.times(1)).save(userInfo);
	}
	
	@Test
	public void testCreateNewUserInfo() throws Exception {
		// Setup test data
		CentralUserInfoDTO centralUserInfoDTO = new CentralUserInfoDTO();
		centralUserInfoDTO.setFirstName("John");
		centralUserInfoDTO.setLastName("Doe");
		centralUserInfoDTO.setDisplayName("John Doe");
		centralUserInfoDTO.setEmail("john.doe@example.com");
		
		// Setup mocks
		when(service.getCentralAuthUserInfoDetails(Mockito.anyString(), Mockito.anyString()))
			.thenReturn(centralUserInfoDTO);
		
		// Use reflection to access the private method
		Method createNewUserInfoMethod = CustomAnalyticsServiceImpl.class.getDeclaredMethod(
			"createNewUserInfo", String.class, String.class);
		createNewUserInfoMethod.setAccessible(true);
		
		// Call the method
		UserInfo result = (UserInfo) createNewUserInfoMethod.invoke(customAnalyticsServiceImpl, "test", "token");
		
		// Verify the user info was created with central auth data
		assertEquals("John", result.getFirstName());
		assertEquals("Doe", result.getLastName());
		assertEquals("John Doe", result.getDisplayName());
		assertEquals("john.doe@example.com", result.getEmailAddress());
		
		// Verify the token was saved
		Mockito.verify(userTokenReopository, Mockito.times(1)).save(Mockito.any(UserTokenData.class));
	}
	
	// We'll skip the processUserInfo test since it's difficult to test private methods that call other private methods
	// The individual helper methods are already tested, which provides good coverage
}
