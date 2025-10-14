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

import com.google.common.collect.Lists;
import com.publicissapient.kpidashboard.apis.abac.ProjectAccessManager;
import com.publicissapient.kpidashboard.apis.auth.AuthProperties;
import com.publicissapient.kpidashboard.apis.auth.AuthenticationFixture;
import com.publicissapient.kpidashboard.apis.auth.exceptions.DeleteLastAdminException;
import com.publicissapient.kpidashboard.apis.auth.exceptions.UserNotFoundException;
import com.publicissapient.kpidashboard.apis.auth.model.Authentication;
import com.publicissapient.kpidashboard.apis.auth.repository.AuthenticationRepository;
import com.publicissapient.kpidashboard.apis.auth.service.AuthenticationService;
import com.publicissapient.kpidashboard.apis.auth.service.UserTokenDeletionService;
import com.publicissapient.kpidashboard.apis.auth.token.CookieUtil;
import com.publicissapient.kpidashboard.apis.auth.token.TokenAuthenticationService;
import com.publicissapient.kpidashboard.apis.common.service.impl.DataAccessService;
import com.publicissapient.kpidashboard.apis.common.service.impl.UserInfoServiceImpl;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.projectconfig.basic.service.ProjectBasicConfigService;
import com.publicissapient.kpidashboard.apis.userboardconfig.service.UserBoardConfigService;
import com.publicissapient.kpidashboard.common.constant.AuthType;
import com.publicissapient.kpidashboard.common.model.rbac.*;
import com.publicissapient.kpidashboard.common.repository.rbac.UserInfoCustomRepository;
import com.publicissapient.kpidashboard.common.repository.rbac.UserInfoRepository;
import com.publicissapient.kpidashboard.common.repository.rbac.UserTokenReopository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
class UserInfoServiceImplTest {

	private static final String ROLE_VIEWER = "ROLE_VIEWER";
	private static final String ROLE_SUPERADMIN = "ROLE_SUPERADMIN";
	@Mock
	AuthenticationService authenticationService;
	@Mock
	UserTokenDeletionService userTokenDeletionService;
	@Mock
	UserBoardConfigService userBoardConfigService;
	@Mock
	CacheService cacheService;
	@Mock
	TokenAuthenticationService tokenAuthenticationService;
	@Mock
	private UserInfoRepository userInfoRepository;
	@InjectMocks
	private UserInfoServiceImpl service;

	@Mock
	private DataAccessService dataAccessService;

	@Mock
	private AuthProperties authProperties;
	@Mock
	private AuthenticationRepository authenticationRepository;
	@Mock
	private UserInfoCustomRepository userInfoCustomRepository;
	@Mock
	private ProjectBasicConfigService projectBasicConfigService;
	@Mock
	private ProjectAccessManager projectAccessManager;
	@Mock
	private HttpServletRequest httpServletRequest;
	@Mock
	private UserTokenReopository userTokenReopository;
	@Mock
	private CookieUtil cookieUtil;
	@Mock
	private Cookie cookie;

	@Mock
	private SecurityContext securityContext;

	@Mock
	private org.springframework.security.core.Authentication authentication;

	@BeforeEach
	void setUp(){
		authentication = Mockito.mock(org.springframework.security.core.Authentication.class);
		securityContext = Mockito.mock(SecurityContext.class);
	}

	@Test
	public void shouldGetAuthorities() {
		UserInfo user = new UserInfo();
		user.setUsername("user");
		user.setAuthType(AuthType.STANDARD);
		user.setAuthorities(Lists.newArrayList(Constant.ROLE_VIEWER));
		SimpleGrantedAuthority authority = new SimpleGrantedAuthority(Constant.ROLE_VIEWER);
		when(userInfoRepository.findByUsername("user")).thenReturn(user);
		Collection<GrantedAuthority> authorities = service.getAuthorities("user");
		assertTrue(authorities.contains(authority));
	}

	@Test(expected = DeleteLastAdminException.class)
	public void shouldNotDeleteLastAdmin() {
		String username = "user";
		AuthType authType = AuthType.STANDARD;
		service.demoteFromAdmin(username, authType);

		fail("Should have thrown an exception");
	}

	@Test(expected = UserNotFoundException.class)
	public void shouldNotRemoveAdminFromNonExistingUSer() {
		String username = "user";
		AuthType authType = AuthType.STANDARD;
		List<UserInfo> users = Lists.newArrayList(new UserInfo(), new UserInfo());
		when(userInfoRepository.findByAuthoritiesIn(List.of(Constant.ROLE_SUPERADMIN))).thenReturn(users);
		when(userInfoRepository.findByUsernameAndAuthType(username, authType)).thenReturn(null);

		service.demoteFromAdmin(username, authType);

		fail("Exception should have been thrown.");
	}

	@Test
	public void shouldRemoveAdminFromExistingUser() {
		String username = "user";
		AuthType authType = AuthType.STANDARD;
		UserInfo user = new UserInfo();
		user.setUsername(username);
		user.setAuthType(authType);
		List<String> auth = Lists.newArrayList();
		auth.add("ROLE_VIEWER");
		user.setAuthorities(auth);
		List<UserInfo> users = Lists.newArrayList(new UserInfo(), new UserInfo());
		when(userInfoRepository.findByAuthoritiesIn(List.of(Constant.ROLE_SUPERADMIN))).thenReturn(users);
		when(userInfoRepository.findByUsernameAndAuthType(username, authType)).thenReturn(user);
		when(userInfoRepository.save(isA(UserInfo.class))).thenReturn(user);

		UserInfo result = service.demoteFromAdmin(username, authType);

		assertNotNull(result);
		assertFalse(result.getAuthorities().contains("ROLE_SUPERADMIN"));
		verify(userInfoRepository).save(user);
	}

	/**
	 * 1. if username present in the db then update it with new one else return null
	 */
	@Test
	public void updateUserTest() {

		UserInfo updatedUser = new UserInfo();
		updatedUser.setUsername("standarduser");
		updatedUser.setAuthType(AuthType.STANDARD);
		updatedUser.setAuthorities(Lists.newArrayList("ROLE_PROJECT_VIEWER", "ROLE_PROJECT_ADMIN"));
		when(userInfoRepository.save(updatedUser)).thenReturn(updatedUser);

		UserInfo savedUser = service.updateUserInfo(updatedUser);
		assertEquals(savedUser, updatedUser);
	}

	@Test
	public void getUserInfoWithEmailTest() {
		UserInfo user = new UserInfo();
		user.setUsername("standarduser");
		user.setAuthType(AuthType.STANDARD);

		Authentication auth = new Authentication();
		auth.setUsername("username");
		auth.setEmail("mail@mail.com");

		when(userInfoRepository.findByUsernameAndAuthType(anyString(), any())).thenReturn(user);
		when(authenticationRepository.findByUsername(anyString())).thenReturn(auth);

		UserInfo userInfo = service.getUserInfoWithEmail(anyString(), any());
		assertNotNull(userInfo);
		assertNotNull(userInfo.getEmailAddress());
	}

	@Test
	public void getUserInfoWithEmailTest_userNotFound() {
		when(userInfoRepository.findByUsernameAndAuthType(anyString(), any())).thenReturn(null);

		UserInfo userInfo = service.getUserInfoWithEmail(anyString(), any());
		assertNull(userInfo);
	}

	@Test
	public void getUserInfoWithEmailTest_authNotFound() {
		UserInfo user = new UserInfo();
		user.setUsername("standarduser");
		user.setAuthType(AuthType.STANDARD);

		when(userInfoRepository.findByUsernameAndAuthType(anyString(), any())).thenReturn(user);
		when(authenticationRepository.findByUsername(anyString())).thenReturn(null);

		UserInfo userInfo = service.getUserInfoWithEmail(anyString(), any());
		assertNotNull(userInfo);
		assertNull(userInfo.getEmailAddress());
	}

	@Test
	public void getUserInfoFromUserNameAndAuthType() {
		String username = "user";
		AuthType authType = AuthType.STANDARD;
		UserInfo user = new UserInfo();
		user.setUsername(username);
		user.setAuthType(authType);
		when(userInfoRepository.findByUsernameAndAuthType(username, authType)).thenReturn(user);
		UserInfo result = service.getUserInfo(username, authType);
		assertEquals(username, result.getUsername());
		assertEquals(authType, result.getAuthType());
	}

	@Test
	public void getAllUserInfoNoData() {
		Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
		SecurityContextHolder.setContext(securityContext);
		List<GrantedAuthority> authorities = List.of(
				(GrantedAuthority) () -> Constant.ROLE_SUPERADMIN
		);

		Mockito.when(authentication.getAuthorities()).thenReturn((List) authorities);
		List<String> roles = authorities
				.stream()
				.map(GrantedAuthority::getAuthority)
				.toList();
		List<UserInfo> userInfoList = new ArrayList<>();
		when(dataAccessService.getMembersForUser(roles,authentication.getName())).thenReturn(userInfoList);
		ServiceResponse result = service.getAllUserInfo();
		assertEquals(0, ((ArrayList<UserInfo>) result.getData()).size());
	}

	@Test
	public void getAllUserInfoWithData() {

		Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
		SecurityContextHolder.setContext(securityContext);
		List<GrantedAuthority> authorities = List.of(
				(GrantedAuthority) () -> Constant.ROLE_SUPERADMIN
		);

		Mockito.when(authentication.getAuthorities()).thenReturn((List) authorities);
		List<String> roles = authorities
				.stream()
				.map(GrantedAuthority::getAuthority)
				.toList();

		UserInfo testUser = new UserInfo();
		testUser.setUsername("UnitTest");
		testUser.setAuthorities(List.of(Constant.ROLE_SUPERADMIN));
		ArrayList<UserInfo> userInfoList = new ArrayList<UserInfo>();
		userInfoList.add(testUser);
		when(dataAccessService.getMembersForUser(roles,authentication.getName())).thenReturn(userInfoList);
		ServiceResponse result = service.getAllUserInfo();
		assertEquals(1, ((ArrayList<UserInfo>) result.getData()).size());
	}

	@Test
	public void validateHasRoleSuperadmin() {
		ProjectsAccessDTO pa = new ProjectsAccessDTO();
		pa.setRole(ROLE_SUPERADMIN);
		List<ProjectsAccessDTO> paList = new ArrayList<>();
		paList.add(pa);
		UserInfoDTO u = new UserInfoDTO();
		u.setProjectsAccess(paList);
		assertTrue(service.hasRoleSuperadmin(u));
	}

	@Test
	public void validateUpdateUserRole() {
		ProjectsAccess pa = new ProjectsAccess();
		pa.setRole(ROLE_SUPERADMIN);
		pa.setAccessNodes(new ArrayList<>());

		List<ProjectsAccess> paList = new ArrayList<>();
		paList.add(pa);

		UserInfo testUser = new UserInfo();
		testUser.setUsername("User");
		testUser.setProjectsAccess(paList);

		List<String> auth = new ArrayList<>();
		auth.add(ROLE_SUPERADMIN);
		auth.add(ROLE_VIEWER);
		testUser.setAuthorities(auth);

		UserInfo userInfoDTO = new UserInfo();
		userInfoDTO.setProjectsAccess(paList);

		when(userInfoRepository.findByUsername("User")).thenReturn(testUser);
		when(projectAccessManager.updateAccessOfUserInfo(any(UserInfo.class), any(UserInfo.class))).thenReturn(testUser);
		ServiceResponse result = service.updateUserRole("User", userInfoDTO);
		assertTrue(result.getSuccess());
	}

	@Test
	public void validateUpdateUserRole_Null_UserInfo() {
		when(userInfoRepository.findByUsername("User")).thenReturn(null);
		ServiceResponse result = service.updateUserRole("User", new UserInfo());
		assertFalse(result.getSuccess());
	}

	@Test
	public void validateUpdateUserRole_NotSuperAdmin() {
		ProjectsAccess pa = new ProjectsAccess();
		pa.setRole("Role");
		pa.setAccessNodes(new ArrayList<>());

		List<ProjectsAccess> paList = new ArrayList<>();
		paList.add(pa);

		UserInfo testUser = new UserInfo();
		testUser.setUsername("User");
		testUser.setProjectsAccess(paList);

		UserInfo u = new UserInfo();
		u.setProjectsAccess(paList);

		when(userInfoRepository.findByUsername("User")).thenReturn(testUser);
		when(projectAccessManager.updateAccessOfUserInfo(any(UserInfo.class), any(UserInfo.class))).thenReturn(testUser);
		ServiceResponse result = service.updateUserRole("User", u);
		assertTrue(result.getSuccess());
	}

	@Test
	public void validateUpdateUserRole_UserNotFound() {
		ProjectsAccess pa = new ProjectsAccess();
		pa.setRole("Role");
		pa.setAccessNodes(new ArrayList<>());

		List<ProjectsAccess> paList = new ArrayList<>();
		paList.add(pa);

		UserInfo testUser = new UserInfo();
		testUser.setUsername("User");
		testUser.setAuthType(AuthType.SSO);
		testUser.setAuthorities(Arrays.asList("ROLE_VIEWER"));
		testUser.setProjectsAccess(paList);

		UserInfo u = new UserInfo();
		u.setProjectsAccess(paList);
		u.setUsername("user");
		u.setAuthType(AuthType.SSO);
		u.setEmailAddress("testEmail@test.com");
		when(userInfoRepository.findByUsername("User")).thenReturn(null);
		when(userInfoRepository.save(any())).thenReturn(testUser);
		when(projectAccessManager.updateAccessOfUserInfo(any(UserInfo.class), any(UserInfo.class))).thenReturn(testUser);
		ServiceResponse result = service.updateUserRole("User", u);
		assertTrue(result.getSuccess());
	}

	/**
	 * method to test deleteUser() ;
	 *
	 * <p>
	 * Delete User
	 */
	@Test
	public void deleteUserTest() {
		ServiceResponse result = service.deleteUser("testuser", false);
		assertTrue(result.getSuccess());
	}

	@Test
	public void getUserInfoByAuthType() {
		when(userInfoRepository.findByAuthType("STANDARD")).thenReturn(Arrays.asList(new UserInfo()));
		service.getUserInfoByAuthType("STANDARD");
		verify(userInfoRepository, times(1)).findByAuthType("STANDARD");
	}

	@Test
	public void getUserDetailsByToken() {
		UserInfo user = new UserInfo();
		when(cookieUtil.getAuthCookie(any(HttpServletRequest.class)))
				.thenReturn(new Cookie("authCookie", AuthenticationFixture.getJwtToken("dummyUser", "dummyData", 100000L)));
		when(userTokenReopository.findByUserToken(anyString()))
				.thenReturn(new UserTokenData("dummyUser", "dummyToken", null));
		when(authenticationRepository.findByUsername(anyString())).thenReturn(new Authentication());

		user.setUsername("dummyUser");
		user.setAuthType(AuthType.STANDARD);
		user.setAuthorities(Lists.newArrayList("ROLE_SUPERADMIN"));
		user.setEmailAddress("email");

		Authentication authentication1 = new Authentication();
		authentication1.setUsername("dummyUser");
		authentication1.setEmail("emailId");

		List<RoleWiseProjects> roleWiseProjects = new ArrayList<>();

		when(userInfoRepository.findByUsername(Mockito.anyString())).thenReturn(user);
		when(authenticationRepository.findByUsername(Mockito.anyString())).thenReturn(null);
		when(projectAccessManager.getProjectAccessesWithRole(Mockito.anyString())).thenReturn(roleWiseProjects);

		UserDetailsResponseDTO userDetailsResponseDTO = service.getUserInfoByToken(httpServletRequest);
		assertNotNull(userDetailsResponseDTO);
	}

	@Test
	public void getOrSaveDefaultUser() {
		UserInfo user = new UserInfo();
		user.setUsername("testUser");
		user.setAuthType(AuthType.STANDARD);
		user.setAuthorities(Lists.newArrayList("ROLE_SUPERADMIN"));
		user.setEmailAddress("email");
		when(userInfoRepository.count()).thenReturn(1l);
		when(userInfoRepository.save(any())).thenReturn(user);
		UserInfoDTO userInfo = service.getOrSaveDefaultUserInfo("user", AuthType.STANDARD, "email");
		assertNotNull(userInfo);
	}

	@Test
	public void getOrSaveUserInfo() {
		UserInfo user = new UserInfo();
		user.setUsername("testUser");
		user.setAuthType(AuthType.STANDARD);
		user.setAuthorities(Lists.newArrayList("ROLE_SUPERADMIN"));
		user.setEmailAddress("email");
		when(userInfoRepository.save(any())).thenReturn(user);
		UserInfo userInfo = service.getOrSaveUserInfo("user", AuthType.STANDARD, new ArrayList<>());
		assertNotNull(userInfo);
	}

	@Test
	public void updateNotificationEmailTest() {
		Map<String, Boolean> notificationEmail = new HashMap<>();
		notificationEmail.put("accessAlertNotification", true);
		notificationEmail.put("errorAlertNotification", false);
		UserInfo user = new UserInfo();
		user.setUsername("testUser");
		user.setAuthType(AuthType.STANDARD);
		user.setAuthorities(Lists.newArrayList("ROLE_PROJECT_ADMIN"));
		user.setEmailAddress("email");
		when(userInfoRepository.findByUsername("testUser")).thenReturn(user);
		when(userInfoRepository.save(any())).thenReturn(user);
		UserInfo userInfo = service.updateNotificationEmail("testUser", notificationEmail);
		assertNotNull(userInfo);
		assertNotNull(userInfo.getNotificationEmail());
	}
}
