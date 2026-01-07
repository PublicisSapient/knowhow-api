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

import java.io.IOException;

import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.publicissapient.kpidashboard.apis.auth.apikey.ApiKeyAuthenticationService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final CookieUtil cookieUtil;

	private final TokenAuthenticationService tokenAuthenticationService;
	private final ApiKeyAuthenticationService apiKeyAuthenticationService;

	@Override
	protected boolean shouldNotFilter(@NotNull HttpServletRequest request) {
		return this.apiKeyAuthenticationService.isRequestToApiKeyEndpoint(request);
	}

	@Override
	protected void doFilterInternal(
			@NotNull HttpServletRequest request,
			@NotNull HttpServletResponse response,
			@NotNull FilterChain filterChain)
			throws ServletException, IOException {
		Cookie authCookie = cookieUtil.getAuthCookie(request);

		if (authCookie == null) {
			filterChain.doFilter(request, response);
			return;
		}

		try {
			Authentication authentication = tokenAuthenticationService.validateAuthentication(request, response);
			SecurityContextHolder.getContext().setAuthentication(authentication);
		} catch (Exception e) {
			// Invalid JWT token - clear cookie and return 400 Bad Request
			cookieUtil.deleteCookie(request, response, CookieUtil.AUTH_COOKIE);
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			response.setContentType("application/json");
			response.getWriter().write("{\"timestamp\":" + System.currentTimeMillis() + ",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid authentication token\"}");
			return;
		}

		filterChain.doFilter(request, response);
	}
}
