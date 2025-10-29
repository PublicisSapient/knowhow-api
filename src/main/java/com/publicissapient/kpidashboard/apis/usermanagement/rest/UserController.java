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

package com.publicissapient.kpidashboard.apis.usermanagement.rest;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.usermanagement.dto.request.UserRequestDTO;
import com.publicissapient.kpidashboard.apis.usermanagement.dto.response.UserResponseDTO;
import com.publicissapient.kpidashboard.apis.usermanagement.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** Controller for handling user information operations */
@AllArgsConstructor
@RestController
@RequestMapping("/usermanagement")
@Tag(name = "User Management", description = "Endpoints for managing user information")
public class UserController {

	private final UserService userService;

	@PostMapping("/save")
	@Operation(
			summary = "Save User Information",
			description = "Saves user information with SAML authentication type")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully saved user information",
						content = {
							@Content(
									mediaType = "application/json",
									schema = @Schema(implementation = UserResponseDTO.class))
						}),
				@ApiResponse(responseCode = "400", description = "Bad request. Username is required"),
				@ApiResponse(responseCode = "500", description = "Unexpected server error occurred")
			})
	@PreAuthorize("hasPermission(null, 'ADD_USER') or hasPermission(null, 'GRANT_ACCESS')")
	public ServiceResponse saveUserInfo(@Valid @RequestBody UserRequestDTO userRequestDTO) {
		return userService.saveUserInfo(userRequestDTO.getUsername());
	}
}
