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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.publicissapient.kpidashboard.apis.auth.apikey.ApiKeyAuthenticationService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

@RunWith(MockitoJUnitRunner.class)
public class JwtAuthenticationFilterTest {

	@Mock private HttpServletRequest request;
	@Mock private HttpServletResponse response;
	@Mock private TokenAuthenticationService authService;
	@Mock private CookieUtil cookieUtil;
	@Mock private FilterChain filterChain;
	@Mock private Authentication authentication;
	@Mock private Cookie cookie;
	@Mock private ApiKeyAuthenticationService apiKeyAuthenticationService;

	@InjectMocks private JwtAuthenticationFilter filter;

	@Before
	public void setup() {
		SecurityContextHolder.clearContext();
	}

	@Test
	public void testDoFilter() throws Exception {
		when(authService.validateAuthentication(
						any(HttpServletRequest.class), any(HttpServletResponse.class)))
				.thenReturn(authentication);
		when(cookieUtil.getAuthCookie(any(HttpServletRequest.class))).thenReturn(cookie);
		when(apiKeyAuthenticationService.isRequestToApiKeyEndpoint(request)).thenReturn(false);
		filter.doFilter(request, response, filterChain);
		assertNotNull(SecurityContextHolder.getContext().getAuthentication());
		assertEquals(authentication, SecurityContextHolder.getContext().getAuthentication());

		verify(authService)
				.validateAuthentication(any(HttpServletRequest.class), any(HttpServletResponse.class));
		verify(filterChain).doFilter(request, response);
	}

    @Test
    public void shouldHandleInvalidTokenException() throws ServletException, IOException {
        // Given
        Cookie authCookie = new Cookie(CookieUtil.AUTH_COOKIE, "invalid-token");
        PrintWriter writer = mock(PrintWriter.class);

        when(cookieUtil.getAuthCookie(request)).thenReturn(authCookie);
        when(authService.validateAuthentication(request, response))
                .thenThrow(new RuntimeException("Invalid token"));
        when(response.getWriter()).thenReturn(writer);

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        verify(cookieUtil).deleteCookie(request, response, CookieUtil.AUTH_COOKIE);
        verify(response).setStatus(HttpStatus.BAD_REQUEST.value());
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
        verify(writer).write(any(String.class));
        verify(filterChain, never()).doFilter(request, response);
    }
}
