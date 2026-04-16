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

package com.publicissapient.kpidashboard.apis.common.service;

import java.util.Map;

import org.json.simple.JSONObject;

import com.publicissapient.kpidashboard.apis.auth.model.UserInfoPrincipal;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Interface to provide methods for analytics details
 *
 * @author prijain3
 */
public interface CustomAnalyticsService {

	/**
	 * Creates and return JSON object containing analytics data.
	 *
	 * @param httpServletResponse HttpServletResponse
	 * @param user name
	 * @return JSON of analytics data
	 */
	JSONObject addAnalyticsData(HttpServletResponse httpServletResponse, UserInfoPrincipal user);

	/**
	 * Creates and return JSON object containing analytics data.
	 *
	 * @param httpServletResponse HttpServletResponse
	 * @param username user name
	 * @return JSON of analytics data
	 */
	Map<String, Object> addAnalyticsDataAndSaveCentralUser(
			HttpServletResponse httpServletResponse,
			String username,
			String authType,
			String email,
			String authToken);

	JSONObject getAnalyticsCheck();
}
