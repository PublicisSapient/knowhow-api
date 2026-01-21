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

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.common.service.UserInfoService;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.common.constant.AuthType;
import com.publicissapient.kpidashboard.common.model.rbac.UserInfoDTO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/sso")
@AllArgsConstructor
@Tag(name = "SSO Controller", description = "APIs for SSO User Management")
public class SSOController {
	private final UserInfoService userInfoService;

	@PostMapping(
			value = "/users/{username}",
			consumes = APPLICATION_JSON_VALUE,
			produces = APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> fetchOrSaveUserInfo(@PathVariable String username) {
		ServiceResponse response = new ServiceResponse(false, "Unauthorized", null);
		UserInfoDTO userInfoDTO =
				userInfoService.getOrSaveDefaultUserInfo(username, AuthType.SSO, null);
		if (null != userInfoDTO && userInfoDTO.getAuthType().equals(AuthType.SSO)) {
			response = new ServiceResponse(true, "Success", userInfoDTO);
		}
		return ResponseEntity.status(HttpStatus.OK).body(response);
	}
}
