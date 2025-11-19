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

package com.publicissapient.kpidashboard.apis.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Class used for common response from All services */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ServiceResponse extends BaseResponse {

	private Object data;
	/**
	 * @param isSuccess Field representing the status of the response
	 * @param msg Field representing the status message of the response
	 * @param data Generic field representing the data which the response will contain
	 */
	public ServiceResponse(Boolean isSuccess, String msg, Object data) {
		super();
		this.data = data;
		super.setMessage(msg);
		super.setSuccess(isSuccess);
	}
}
