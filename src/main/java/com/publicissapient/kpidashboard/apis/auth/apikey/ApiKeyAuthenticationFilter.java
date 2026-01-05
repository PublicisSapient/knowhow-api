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

import java.io.IOException;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

	private final ApiKeyAuthenticationService apiKeyAuthenticationService;

	@Override
	protected boolean shouldNotFilter(@NotNull HttpServletRequest request) {
		return !this.apiKeyAuthenticationService.isRequestToApiKeyEndpoint(request);
	}

	@Override
	protected void doFilterInternal(
			@NotNull HttpServletRequest request,
			@NotNull HttpServletResponse response,
			@NotNull FilterChain filterChain)
			throws ServletException, IOException {
		Optional<ApiKeyAuthenticationToken> apiKeyAuthenticationTokenOptional =
				this.apiKeyAuthenticationService.createApiKeyAuthentication(request);
		if (apiKeyAuthenticationTokenOptional.isEmpty()) {
			response.setStatus(HttpStatus.UNAUTHORIZED.value());
		} else {
			SecurityContextHolder.getContext().setAuthentication(apiKeyAuthenticationTokenOptional.get());
			filterChain.doFilter(request, response);
		}
	}
}
