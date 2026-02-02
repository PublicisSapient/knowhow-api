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

package com.publicissapient.kpidashboard.apis.auth.service;

import javax.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Change password request
 *
 * @author vijkumar18
 */
@Data
@Schema(description = "Change Password Request Model")
public class ChangePasswordRequest {
	@Schema(description = "E-mail address", example = "john.doe@example.com")
	@NotNull
	private String email;

	@Schema(description = "Old Password", example = "OldP@ssw0rd!")
	@NotNull
	private String oldPassword;

	@Schema(description = "New Password", example = "NewP@ssw0rd!")
	@NotNull
	private String password;

	@Schema(description = "Username", example = "john.doe")
	@NotNull
	private String user;
}
