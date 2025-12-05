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

import java.util.Optional;

import org.apache.commons.lang.StringUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import com.publicissapient.kpidashboard.apis.auth.config.AuthApiKeyEndpointsConfig;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for handling API key-based authentication for KPI Dashboard endpoints.
 *
 * <p>This service provides functionality to validate API key authentication for specific endpoints
 * configured in the application. It supports header-based API key authentication and integrates
 * with Spring Security's authentication framework.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyAuthenticationService {

    private final CustomApiConfig customApiConfig;

    private final AuthApiKeyEndpointsConfig authApiKeyEndpointsConfig;

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    /**
     * Creates an API key authentication token if the request is valid and contains a proper API key.
     *
     * <p>This method validates that:</p>
     * <ul>
     *   <li>The request targets an API key-enabled endpoint</li>
     *   <li>The API key header is present and matches the configured value</li>
     *   <li>The request URI matches one of the configured patterns</li>
     * </ul>
     *
     * @param request the HTTP servlet request to validate
     * @return an {@link Optional} containing the authentication token if valid, empty otherwise
     */
    public Optional<ApiKeyAuthenticationToken> createApiKeyAuthentication(HttpServletRequest request) {
        if(isRequestValid(request)) {
            return Optional.of(new ApiKeyAuthenticationToken(request.getHeader(authApiKeyEndpointsConfig.getHeaderName())));
        }
        return Optional.empty();
    }

    /**
     * Determines if the incoming request is targeting an endpoint that supports API key authentication.
     *
     * <p>This method checks two conditions:</p>
     * <ol>
     *   <li>The request contains the configured API key header</li>
     *   <li>The request URI matches one of the configured endpoint patterns</li>
     * </ol>
     *
     * <p><strong>Note:</strong> This method does not validate the API key value, only its presence
     * and the endpoint pattern match.</p>
     *
     * @param request the HTTP servlet request to check, may be null
     * @return {@code true} if the request targets an API key endpoint and contains the header,
     *         {@code false} otherwise
     */
    public boolean isRequestToApiKeyEndpoint(HttpServletRequest request) {
        if(request == null) {
            return false;
        }

        String apiKey = request.getHeader(authApiKeyEndpointsConfig.getHeaderName());
        String path = request.getRequestURI();
        return StringUtils.isNotBlank(apiKey)
                && authApiKeyEndpointsConfig.getPaths().stream().anyMatch(pattern -> antPathMatcher.match(pattern, path));
    }

    /**
     * Checks if the current thread's security context contains an API key authentication.
     *
     * <p>This is a thread-safe static method that examines the Spring Security context
     * to determine if the current request was authenticated using an API key. This is useful
     * for conditional logic that needs to behave differently for API key vs. other authentication methods.</p>
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe as it accesses the
     * {@link SecurityContextHolder} which maintains thread-local security contexts.</p>
     *
     * <p><strong>Usage Scenarios:</strong></p>
     * <ul>
     *   <li>Skipping cache operations for API key requests</li>
     *   <li>Applying different rate limiting rules</li>
     *   <li>Conditional logging or monitoring</li>
     * </ul>
     *
     * @return {@code true} if the current authentication is an API key authentication,
     *         {@code false} otherwise (including when no authentication is present)
     */
    public static boolean isApiKeyRequest() {
        return SecurityContextHolder.getContext().getAuthentication() instanceof ApiKeyAuthenticationToken;
    }

    /**
     * Validates that the request contains a valid API key for an API key-enabled endpoint.
     *
     * <p>This private method performs the complete validation chain:</p>
     * <ol>
     *   <li>Verifies the request targets an API key endpoint using {@link #isRequestToApiKeyEndpoint(HttpServletRequest)}</li>
     *   <li>Extracts the API key from the configured header</li>
     *   <li>Compares the provided API key with the configured expected value</li>
     * </ol>
     *
     * <p><strong>Security Considerations:</strong></p>
     * <ul>
     *   <li>API key comparison uses {@link String#equals(Object)} for exact matching</li>
     *   <li>No timing attack protection is implemented (consider for high-security environments)</li>
     *   <li>API keys are logged at debug level - ensure appropriate log configuration</li>
     * </ul>
     *
     * @param request the HTTP servlet request to validate
     * @return {@code true} if the request contains a valid API key for an enabled endpoint,
     *         {@code false} otherwise
     */
    private boolean isRequestValid(HttpServletRequest request) {
        if(isRequestToApiKeyEndpoint(request)) {
            return request.getHeader(authApiKeyEndpointsConfig.getHeaderName()).equals(this.customApiConfig.getxApiKey());
        }
        return false;
    }
}
