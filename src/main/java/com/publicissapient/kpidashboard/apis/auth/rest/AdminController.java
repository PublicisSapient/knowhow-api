/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *    http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.auth.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.Collection;
import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.publicissapient.kpidashboard.apis.auth.access.Admin;
import com.publicissapient.kpidashboard.apis.auth.model.ApiToken;
import com.publicissapient.kpidashboard.apis.auth.service.ApiTokenRequest;
import com.publicissapient.kpidashboard.apis.auth.service.ApiTokenService;
import com.publicissapient.kpidashboard.common.util.EncryptionException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Rest Controller to handle admin request */
@RestController
@RequestMapping("/admin")
@Admin
@RequiredArgsConstructor
@Tag(name = "Admin Controller", description = "APIs for Admin Operations")
public class AdminController {

	private final ApiTokenService apiTokenService;

	/**
	 * Creates access token.
	 *
	 * @param apiTokenRequest the api token request
	 * @return api access token
	 */
	@Operation(
			summary = "Create API token",
			description =
					"Generates a new API token for the specified user with a defined expiration time.")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Token successfully generated"),
				@ApiResponse(
						responseCode = "400",
						description = "Invalid request data, encryption failure, or application error"),
				@ApiResponse(
						responseCode = "403",
						description = "Forbidden access (user does not have admin privileges)"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@PostMapping(
			value = "/createToken",
			consumes = APPLICATION_JSON_VALUE,
			produces = APPLICATION_JSON_VALUE)
	public ResponseEntity<String> createToken(
			@Parameter(
							description = "API Token Request",
							required = true,
							example = """
			{"apiUser": "user1", "expirationDt": 1704067200000}
			""")
					@Valid
					@RequestBody
					ApiTokenRequest apiTokenRequest) {
		try {
			return ResponseEntity.status(HttpStatus.OK)
					.body(
							apiTokenService.getApiToken(
									apiTokenRequest.getApiUser(), apiTokenRequest.getExpirationDt()));
		} catch (EncryptionException
				| com.publicissapient.kpidashboard.common.exceptions.ApplicationException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
		}
	}

	/**
	 * Returns list of tokens
	 *
	 * @return list of tokens
	 */
	@Operation(
			summary = "Get API tokens",
			description = "Returns all API tokens configured in the system.")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Tokens successfully retrieved"),
				@ApiResponse(
						responseCode = "403",
						description = "Forbidden access (user does not have admin privileges)"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@GetMapping(path = "/apitokens")
	public ResponseEntity<Collection<ApiToken>> getApiTokens() {
		return ResponseEntity.status(HttpStatus.OK).body(apiTokenService.getApiTokens());
	}
}
