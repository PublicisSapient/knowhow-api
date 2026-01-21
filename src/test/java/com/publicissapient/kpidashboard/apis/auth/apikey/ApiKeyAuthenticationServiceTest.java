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

package com.publicissapient.kpidashboard.apis.auth.apikey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.publicissapient.kpidashboard.apis.auth.config.AuthApiKeyEndpointsConfig;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationServiceTest {

	@Mock private CustomApiConfig customApiConfig;

	@Mock private AuthApiKeyEndpointsConfig authApiKeyEndpointsConfig;

	@Mock private HttpServletRequest request;

	@Mock private SecurityContext securityContext;

	@Mock private Authentication authentication;

	@InjectMocks private ApiKeyAuthenticationService apiKeyAuthenticationService;

	private static final String VALID_API_KEY = "valid-api-key-123";
	private static final String INVALID_API_KEY = "invalid-api-key";
	private static final String HEADER_NAME = "X-API-KEY";
	private static final String VALID_PATH = "/api/kpi/values";
	private static final String INVALID_PATH = "/api/unauthorized";

	@Test
	void when_ValidRequestWithCorrectApiKey_Then_ReturnAuthenticationToken() {
		// Arrange
		when(authApiKeyEndpointsConfig.getHeaderName()).thenReturn(HEADER_NAME);
		when(customApiConfig.getxApiKey()).thenReturn(VALID_API_KEY);
		when(authApiKeyEndpointsConfig.getPaths())
				.thenReturn(Set.of("/api/kpi/**", "/api/integration/**"));
		when(request.getHeader(HEADER_NAME)).thenReturn(VALID_API_KEY);
		when(request.getRequestURI()).thenReturn(VALID_PATH);

		// Act
		Optional<ApiKeyAuthenticationToken> result =
				apiKeyAuthenticationService.createApiKeyAuthentication(request);

		// Assert
		assertTrue(result.isPresent());
		assertInstanceOf(ApiKeyAuthenticationToken.class, result.get());
		assertEquals(VALID_API_KEY, result.get().getPrincipal());
	}

	@Test
	void when_InvalidApiKey_Then_ReturnEmptyOptional() {
		// Arrange
		when(authApiKeyEndpointsConfig.getHeaderName()).thenReturn(HEADER_NAME);
		when(request.getHeader(HEADER_NAME)).thenReturn(INVALID_API_KEY);
		when(request.getRequestURI()).thenReturn(VALID_PATH);

		// Act
		Optional<ApiKeyAuthenticationToken> result =
				apiKeyAuthenticationService.createApiKeyAuthentication(request);

		// Assert
		assertTrue(result.isEmpty());
	}

	@Test
	void when_RequestToNonApiKeyEndpoint_Then_ReturnEmptyOptional() {
		// Arrange
		when(authApiKeyEndpointsConfig.getHeaderName()).thenReturn(HEADER_NAME);
		when(request.getHeader(HEADER_NAME)).thenReturn(VALID_API_KEY);
		when(request.getRequestURI()).thenReturn(INVALID_PATH);

		// Act
		Optional<ApiKeyAuthenticationToken> result =
				apiKeyAuthenticationService.createApiKeyAuthentication(request);

		// Assert
		assertTrue(result.isEmpty());
	}

	@Test
	void when_NullRequest_Then_ReturnEmptyOptional() {
		// Act
		Optional<ApiKeyAuthenticationToken> result =
				apiKeyAuthenticationService.createApiKeyAuthentication(null);

		// Assert
		assertTrue(result.isEmpty());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   ", "\t", "\n"})
	void when_BlankApiKeyHeader_Then_ReturnEmptyOptional(String blankApiKey) {
		// Arrange
		when(authApiKeyEndpointsConfig.getHeaderName()).thenReturn(HEADER_NAME);
		when(request.getHeader(HEADER_NAME)).thenReturn(blankApiKey);
		when(request.getRequestURI()).thenReturn(VALID_PATH);

		// Act
		Optional<ApiKeyAuthenticationToken> result =
				apiKeyAuthenticationService.createApiKeyAuthentication(request);

		// Assert
		assertTrue(result.isEmpty());
	}

	@Test
	void when_ValidApiKeyAndMatchingPath_Then_ReturnTrue() {
		// Arrange
		when(authApiKeyEndpointsConfig.getHeaderName()).thenReturn(HEADER_NAME);
		when(authApiKeyEndpointsConfig.getPaths())
				.thenReturn(Set.of("/api/kpi/**", "/api/integration/**"));
		when(request.getHeader(HEADER_NAME)).thenReturn(VALID_API_KEY);
		when(request.getRequestURI()).thenReturn("/api/kpi/values");

		// Act
		boolean result = apiKeyAuthenticationService.isRequestToApiKeyEndpoint(request);

		// Assert
		assertTrue(result);
	}

	@Test
	void when_ValidApiKeyAndNestedMatchingPath_Then_ReturnTrue() {
		// Arrange
		when(authApiKeyEndpointsConfig.getHeaderName()).thenReturn(HEADER_NAME);
		when(authApiKeyEndpointsConfig.getPaths())
				.thenReturn(Set.of("/api/kpi/**", "/api/integration/**"));
		when(request.getHeader(HEADER_NAME)).thenReturn(VALID_API_KEY);
		when(request.getRequestURI()).thenReturn("/api/kpi/integration/values");

		// Act
		boolean result = apiKeyAuthenticationService.isRequestToApiKeyEndpoint(request);

		// Assert
		assertTrue(result);
	}

	@Test
	void when_ValidApiKeyButNonMatchingPath_Then_ReturnFalse() {
		// Arrange
		when(authApiKeyEndpointsConfig.getHeaderName()).thenReturn(HEADER_NAME);
		when(request.getHeader(HEADER_NAME)).thenReturn(VALID_API_KEY);
		when(request.getRequestURI()).thenReturn("/api/unauthorized/endpoint");

		// Act
		boolean result = apiKeyAuthenticationService.isRequestToApiKeyEndpoint(request);

		// Assert
		assertFalse(result);
	}

	@Test
	void when_NoApiKeyHeaderButMatchingPath_Then_ReturnFalse() {
		// Arrange
		when(authApiKeyEndpointsConfig.getHeaderName()).thenReturn(HEADER_NAME);
		when(request.getHeader(HEADER_NAME)).thenReturn(null);
		when(request.getRequestURI()).thenReturn(VALID_PATH);

		// Act
		boolean result = apiKeyAuthenticationService.isRequestToApiKeyEndpoint(request);

		// Assert
		assertFalse(result);
	}

	@Test
	void when_NullRequestForEndpointCheck_Then_ReturnFalse() {
		// Act
		boolean result = apiKeyAuthenticationService.isRequestToApiKeyEndpoint(null);

		// Assert
		assertFalse(result);
	}

	@Test
	void when_EmptyApiKeyHeader_Then_ReturnFalse() {
		// Arrange
		when(authApiKeyEndpointsConfig.getHeaderName()).thenReturn(HEADER_NAME);
		when(request.getHeader(HEADER_NAME)).thenReturn("");
		when(request.getRequestURI()).thenReturn(VALID_PATH);

		// Act
		boolean result = apiKeyAuthenticationService.isRequestToApiKeyEndpoint(request);

		// Assert
		assertFalse(result);
	}

	@Test
	void when_BlankApiKeyHeader_Then_ReturnFalse() {
		// Arrange
		when(authApiKeyEndpointsConfig.getHeaderName()).thenReturn(HEADER_NAME);
		when(request.getHeader(HEADER_NAME)).thenReturn("   ");
		when(request.getRequestURI()).thenReturn(VALID_PATH);

		// Act
		boolean result = apiKeyAuthenticationService.isRequestToApiKeyEndpoint(request);

		// Assert
		assertFalse(result);
	}

	@Test
	void when_SecurityContextContainsApiKeyAuthentication_Then_ReturnTrue() {
		// Arrange
		ApiKeyAuthenticationToken apiKeyToken = new ApiKeyAuthenticationToken(VALID_API_KEY);

		try (MockedStatic<SecurityContextHolder> mockedSecurityContextHolder =
				mockStatic(SecurityContextHolder.class)) {
			mockedSecurityContextHolder
					.when(SecurityContextHolder::getContext)
					.thenReturn(securityContext);
			when(securityContext.getAuthentication()).thenReturn(apiKeyToken);

			// Act
			boolean result = ApiKeyAuthenticationService.isApiKeyRequest();

			// Assert
			assertTrue(result);
		}
	}

	@Test
	void when_SecurityContextContainsOtherAuthentication_Then_ReturnFalse() {
		// Arrange
		try (MockedStatic<SecurityContextHolder> mockedSecurityContextHolder =
				mockStatic(SecurityContextHolder.class)) {
			mockedSecurityContextHolder
					.when(SecurityContextHolder::getContext)
					.thenReturn(securityContext);
			when(securityContext.getAuthentication()).thenReturn(authentication);

			// Act
			boolean result = ApiKeyAuthenticationService.isApiKeyRequest();

			// Assert
			assertFalse(result);
		}
	}

	@Test
	void when_SecurityContextContainsNullAuthentication_Then_ReturnFalse() {
		// Arrange
		try (MockedStatic<SecurityContextHolder> mockedSecurityContextHolder =
				mockStatic(SecurityContextHolder.class)) {
			mockedSecurityContextHolder
					.when(SecurityContextHolder::getContext)
					.thenReturn(securityContext);
			when(securityContext.getAuthentication()).thenReturn(null);

			// Act
			boolean result = ApiKeyAuthenticationService.isApiKeyRequest();

			// Assert
			assertFalse(result);
		}
	}

	@Test
	void when_ValidRequestToApiKeyEndpoint_Then_IsRequestValidReturnsTrue() {
		// Arrange
		when(authApiKeyEndpointsConfig.getHeaderName()).thenReturn(HEADER_NAME);
		when(customApiConfig.getxApiKey()).thenReturn(VALID_API_KEY);
		when(authApiKeyEndpointsConfig.getPaths())
				.thenReturn(Set.of("/api/kpi/**", "/api/integration/**"));
		when(request.getHeader(HEADER_NAME)).thenReturn(VALID_API_KEY);
		when(request.getRequestURI()).thenReturn(VALID_PATH);

		// Act
		boolean result =
				Boolean.TRUE.equals(
						ReflectionTestUtils.invokeMethod(
								apiKeyAuthenticationService, "isRequestValid", request));

		// Assert
		assertTrue(result);
	}

	@Test
	void when_InvalidApiKeyToApiKeyEndpoint_Then_IsRequestValidReturnsFalse() {
		// Arrange
		when(authApiKeyEndpointsConfig.getHeaderName()).thenReturn(HEADER_NAME);
		when(request.getHeader(HEADER_NAME)).thenReturn(INVALID_API_KEY);
		when(request.getRequestURI()).thenReturn(VALID_PATH);

		// Act
		boolean result =
				Boolean.TRUE.equals(
						ReflectionTestUtils.invokeMethod(
								apiKeyAuthenticationService, "isRequestValid", request));

		// Assert
		assertFalse(result);
	}

	@Test
	void when_ValidApiKeyToNonApiKeyEndpoint_Then_IsRequestValidReturnsFalse() {
		// Arrange
		when(authApiKeyEndpointsConfig.getHeaderName()).thenReturn(HEADER_NAME);
		when(request.getHeader(HEADER_NAME)).thenReturn(VALID_API_KEY);
		when(request.getRequestURI()).thenReturn(INVALID_PATH);

		// Act
		boolean result =
				Boolean.TRUE.equals(
						ReflectionTestUtils.invokeMethod(
								apiKeyAuthenticationService, "isRequestValid", request));

		// Assert
		assertFalse(result);
	}

	@Test
	void when_NullApiKeyHeader_Then_IsRequestValidReturnsFalse() {
		// Arrange
		when(authApiKeyEndpointsConfig.getHeaderName()).thenReturn(HEADER_NAME);
		when(request.getHeader(HEADER_NAME)).thenReturn(null);
		when(request.getRequestURI()).thenReturn(VALID_PATH);

		// Act
		boolean result =
				Boolean.TRUE.equals(
						ReflectionTestUtils.invokeMethod(
								apiKeyAuthenticationService, "isRequestValid", request));

		// Assert
		assertFalse(result);
	}

	@Test
	void when_CaseSensitiveApiKey_Then_HandleCorrectly() {
		// Arrange
		when(authApiKeyEndpointsConfig.getHeaderName()).thenReturn(HEADER_NAME);
		when(request.getHeader(HEADER_NAME)).thenReturn(VALID_API_KEY.toUpperCase());
		when(request.getRequestURI()).thenReturn(VALID_PATH);

		// Act
		Optional<ApiKeyAuthenticationToken> result =
				apiKeyAuthenticationService.createApiKeyAuthentication(request);

		// Assert
		assertTrue(result.isEmpty());
	}

	@Test
	void when_MultiplePathPatternsConfigured_Then_MatchAnyPattern() {
		// Arrange
		when(authApiKeyEndpointsConfig.getHeaderName()).thenReturn(HEADER_NAME);
		when(authApiKeyEndpointsConfig.getPaths())
				.thenReturn(Set.of("/api/kpi/**", "/api/integration/**", "/api/reports/**"));
		when(request.getHeader(HEADER_NAME)).thenReturn(VALID_API_KEY);
		when(request.getRequestURI()).thenReturn("/api/reports/dashboard");

		// Act
		boolean result = apiKeyAuthenticationService.isRequestToApiKeyEndpoint(request);

		// Assert
		assertTrue(result);
	}

	@Test
	void when_ExactPathMatch_Then_ReturnTrue() {
		// Arrange
		when(authApiKeyEndpointsConfig.getHeaderName()).thenReturn(HEADER_NAME);
		when(authApiKeyEndpointsConfig.getPaths()).thenReturn(Set.of("/api/exact/path"));
		when(request.getHeader(HEADER_NAME)).thenReturn(VALID_API_KEY);
		when(request.getRequestURI()).thenReturn("/api/exact/path");

		// Act
		boolean result = apiKeyAuthenticationService.isRequestToApiKeyEndpoint(request);

		// Assert
		assertTrue(result);
	}

	@Test
	void when_PathWithTrailingSlash_Then_HandleCorrectly() {
		// Arrange
		when(authApiKeyEndpointsConfig.getHeaderName()).thenReturn(HEADER_NAME);
		when(authApiKeyEndpointsConfig.getPaths()).thenReturn(Set.of("/api/kpi/**"));
		when(request.getHeader(HEADER_NAME)).thenReturn(VALID_API_KEY);
		when(request.getRequestURI()).thenReturn("/api/kpi/");

		// Act
		boolean result = apiKeyAuthenticationService.isRequestToApiKeyEndpoint(request);

		// Assert
		assertTrue(result);
	}

	@Test
	void when_DifferentApiKeyConfigured_Then_UseConfiguredApiKey() {
		// Arrange
		when(authApiKeyEndpointsConfig.getHeaderName()).thenReturn(HEADER_NAME);
		when(customApiConfig.getxApiKey()).thenReturn(VALID_API_KEY);
		when(authApiKeyEndpointsConfig.getPaths())
				.thenReturn(Set.of("/api/kpi/**", "/api/integration/**"));
		String customApiKey = "custom-configured-key";
		when(customApiConfig.getxApiKey()).thenReturn(customApiKey);
		when(request.getHeader(HEADER_NAME)).thenReturn(customApiKey);
		when(request.getRequestURI()).thenReturn(VALID_PATH);

		// Act
		Optional<ApiKeyAuthenticationToken> result =
				apiKeyAuthenticationService.createApiKeyAuthentication(request);

		// Assert
		assertTrue(result.isPresent());
		assertEquals(customApiKey, result.get().getPrincipal());
	}

	@Test
	void when_EmptyPathsSet_Then_NoEndpointsMatch() {
		// Arrange
		when(authApiKeyEndpointsConfig.getHeaderName()).thenReturn(HEADER_NAME);
		when(authApiKeyEndpointsConfig.getPaths()).thenReturn(Set.of());
		when(request.getHeader(HEADER_NAME)).thenReturn(VALID_API_KEY);
		when(request.getRequestURI()).thenReturn(VALID_PATH);

		// Act
		boolean result = apiKeyAuthenticationService.isRequestToApiKeyEndpoint(request);

		// Assert
		assertFalse(result);
	}
}
