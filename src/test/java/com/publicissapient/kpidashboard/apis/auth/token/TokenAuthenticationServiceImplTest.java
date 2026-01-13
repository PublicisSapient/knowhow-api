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

package com.publicissapient.kpidashboard.apis.auth.token;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.crypto.SecretKey;

import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.google.common.collect.Sets;
import com.publicissapient.kpidashboard.apis.abac.ProjectAccessManager;
import com.publicissapient.kpidashboard.apis.auth.AuthProperties;
import com.publicissapient.kpidashboard.apis.auth.service.AuthenticationService;
import com.publicissapient.kpidashboard.apis.common.UserTokenAuthenticationDTO;
import com.publicissapient.kpidashboard.apis.common.service.UserInfoService;
import com.publicissapient.kpidashboard.apis.common.service.UsersSessionService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.common.constant.AuthType;
import com.publicissapient.kpidashboard.common.model.rbac.AccessItem;
import com.publicissapient.kpidashboard.common.model.rbac.AccessNode;
import com.publicissapient.kpidashboard.common.model.rbac.RoleWiseProjects;
import com.publicissapient.kpidashboard.common.model.rbac.UserInfo;
import com.publicissapient.kpidashboard.common.model.rbac.UserTokenData;
import com.publicissapient.kpidashboard.common.repository.rbac.UserTokenReopository;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RunWith(MockitoJUnitRunner.class)
public class TokenAuthenticationServiceImplTest {

	private static final String USERNAME = "username";
	private static final String AUTH_RESPONSE_HEADER = "X-Authentication-Token";
	private static final String VALID_JWT_SECRET =
			"mySecretKeyThatIsAtLeast512BitsLongForHS512AlgorithmToWorkProperlyWithExtraCharactersToEnsure512Bits1234567890";
	private static final String ROLES_CLAIM = "roles";
	private static final String DETAILS_CLAIM = "details";

	@Mock UserTokenReopository userTokenReopository;
	@Mock Authentication authentication;
	@Mock SecurityContext securityContext;
	List<AccessNode> listAccessNode = new ArrayList<>();
	AccessNode accessNodes;
	AccessItem accessItem;
	List<AccessItem> accessItems = new ArrayList<>();
	@InjectMocks private TokenAuthenticationServiceImpl service;
	@Mock private AuthProperties tokenAuthProperties;
	@Mock private HttpServletResponse response;
	@Mock private HttpServletRequest request;
	@Mock private UserInfoService userInfoService;
	@Mock private ProjectAccessManager projectAccessManager;
	@Mock private AuthenticationService authenticationService;
	@Mock private CookieUtil cookieUtil;
	@Mock private Cookie cookie;
	@Mock private CustomApiConfig customApiConfig;
	@Mock UsersSessionService usersSessionService;
	@Mock UserTokenAuthenticationDTO userTokenAuthenticationDTO;

	private String createTestJwtToken(String username, String secret, long expirationTime) {
		Authentication auth = createTestAuthentication(username);
		List<String> authorities = Arrays.asList("ROLE_VIEWER", "ROLE_SUPERADMIN");
		SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());

		return Jwts.builder()
				.subject(auth.getName())
				.claim(DETAILS_CLAIM, auth.getDetails())
				.claim(ROLES_CLAIM, authorities)
				.expiration(new Date(System.currentTimeMillis() + expirationTime))
				.signWith(key)
				.compact();
	}

	private Authentication createTestAuthentication(String username) {
		Collection<GrantedAuthority> authorities =
				Sets.newHashSet(new SimpleGrantedAuthority("ROLE_ADMIN"));
		UsernamePasswordAuthenticationToken auth =
				new UsernamePasswordAuthenticationToken(username, "password", authorities);
		auth.setDetails(AuthType.STANDARD.name());
		return auth;
	}

	@Before
	public void setup() {
		MockitoAnnotations.openMocks(this);
		SecurityContextHolder.clearContext();
		when(cookieUtil.getAuthCookie(any(HttpServletRequest.class)))
				.thenReturn(
						new Cookie("authCookie", createTestJwtToken(USERNAME, VALID_JWT_SECRET, 100000L)));
	}

	@Test
	public void testValidateAuthentication() {
		when(tokenAuthProperties.getSecret()).thenReturn(VALID_JWT_SECRET);
		when(cookieUtil.getAuthCookie(any(HttpServletRequest.class)))
				.thenReturn(
						new Cookie("authCookie", createTestJwtToken(USERNAME, VALID_JWT_SECRET, 100000L)));
		service.validateAuthentication(request, response);
		Assert.assertNotNull(authentication);
	}

	@Test
	public void testAddAuthentication() {
		HttpServletResponse response = mock(HttpServletResponse.class);
		when(tokenAuthProperties.getExpirationTime()).thenReturn(0l);
		when(tokenAuthProperties.getSecret()).thenReturn(VALID_JWT_SECRET);
		when(cookieUtil.createAccessTokenCookie(any()))
				.thenReturn(
						new Cookie("authCookie", createTestJwtToken(USERNAME, VALID_JWT_SECRET, 100000L)));
		service.addAuthentication(response, createTestAuthentication(USERNAME));
		verify(response).addHeader(eq(AUTH_RESPONSE_HEADER), anyString());
	}

	@Test
	public void testGetAuthenticationWhenTokenNotProvided() {
		when(tokenAuthProperties.getSecret()).thenReturn(VALID_JWT_SECRET);
		when(cookieUtil.getAuthCookie(any(HttpServletRequest.class)))
				.thenReturn(
						new Cookie("authCookie", createTestJwtToken(USERNAME, VALID_JWT_SECRET, 100000L)));
		Authentication authentication = service.getAuthentication(request, response);
		Assert.assertNotNull(authentication);
		assertTrue(authentication.isAuthenticated());
		assertNotNull(authentication.getAuthorities());
		assertEquals(authentication.getName(), USERNAME);
		assertNotNull(authentication.getDetails());
	}

	@Test
	public void testGetAuthenticationWhenValidTokenProvided() {
		when(tokenAuthProperties.getSecret()).thenReturn(VALID_JWT_SECRET);
		Authentication authentication = service.getAuthentication(request, response);
		Assert.assertNotNull(authentication);
		assertTrue(authentication.isAuthenticated());
		assertNotNull(authentication.getAuthorities());
		assertEquals(authentication.getName(), USERNAME);
		assertNotNull(authentication.getDetails());
	}

	@Test
	public void validateGetUserProjects() {
		SecurityContextHolder.setContext(securityContext);
		when(authenticationService.getLoggedInUser()).thenReturn("SUPERADMIN");
		Set<String> result = service.getUserProjects();
		assertNotNull(result);
	}

	@Test
	public void validateRefreshToken() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		SecurityContextHolder.setContext(securityContext);
		when(authenticationService.getLoggedInUser()).thenReturn("SUPERADMIN");
		List<RoleWiseProjects> result = service.refreshToken(request, response);
		assertEquals(result.size(), 0);
	}

	@Test
	public void invalidateAuthToken() {
		List<String> users = Arrays.asList("Test");
		doNothing().when(userTokenReopository).deleteByUserNameIn(users);
		service.invalidateAuthToken(users);
		verify(userTokenReopository, times(1)).deleteByUserNameIn(users);
	}

	@Test
	public void getOrSaveUserByToken() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		UserTokenData userTokenData =
				new UserTokenData(
						USERNAME,
						createTestJwtToken(USERNAME, VALID_JWT_SECRET, 100000L),
						"2023-01-19T12:33:14.013");
		UserInfo testUser = new UserInfo();
		Object auth = "STANDARD";
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("username", USERNAME);
		jsonObject.put("authorities", null);
		jsonObject.put("emailAddress", null);
		jsonObject.put("projectsAccess", null);
		ArrayList<UserTokenData> userTokenDataList = new ArrayList<>();
		userTokenDataList.add(userTokenData);
		testUser.setUsername(USERNAME);
		when(projectAccessManager.getProjectAccessesWithRole(USERNAME)).thenReturn(null);
		when(userTokenReopository.findAllByUserName(null)).thenReturn(userTokenDataList);
		when(authentication.getDetails()).thenReturn(auth);
		when(userInfoService.getOrSaveUserInfo(USERNAME, AuthType.STANDARD, new ArrayList<>()))
				.thenReturn(testUser);
		assertEquals(service.getOrSaveUserByToken(request, authentication), jsonObject);
	}
}
