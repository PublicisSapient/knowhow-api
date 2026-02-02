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

/** represents api token request input */
@Schema(description = "API Token Request Model")
public class ApiTokenRequest {

	@Schema(description = "API User", example = "apiUser1")
	@NotNull
	private String apiUser;

	@Schema(description = "Expiration DateTime in epoch milliseconds", example = "1704067200000")
	@NotNull
	private Long expirationDt;

	/**
	 * Gets api user.
	 *
	 * @return the api user
	 */
	public String getApiUser() {
		return apiUser;
	}

	/**
	 * Sets api user.
	 *
	 * @param apiUser the api user
	 */
	public void setApiUser(String apiUser) {
		this.apiUser = apiUser;
	}

	/**
	 * Gets expiration dt.
	 *
	 * @return the expiration dt
	 */
	public Long getExpirationDt() {
		return expirationDt;
	}

	/**
	 * Sets expiration dt.
	 *
	 * @param expirationDt the expiration dt
	 */
	public void setExpirationDt(Long expirationDt) {
		this.expirationDt = expirationDt;
	}
}
