/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

// File: NotificationUtility.java

package com.publicissapient.kpidashboard.apis.notification.util;

import java.io.InputStream;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.publicissapient.kpidashboard.apis.common.service.CommonService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.enums.NotificationCustomDataEnum;
import com.publicissapient.kpidashboard.common.model.notification.EmailRequestPayload;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class NotificationUtility {

	private NotificationUtility() {
		// Prevent instantiation
	}

	public static Set<String> extractEmailTemplateVariables(String templateName) {
		if (templateName == null || templateName.trim().isEmpty()) {
			throw new IllegalArgumentException("Template name must not be null or empty");
		}
		Set<String> variables = new HashSet<>();
		try (InputStream is =
				NotificationUtility.class
						.getClassLoader()
						.getResourceAsStream("templates/" + templateName)) {
			if (is == null) {
				throw new IllegalArgumentException("Template not found: " + templateName);
			}
			String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
			Pattern pattern = Pattern.compile("\\$\\{([A-Za-z0-9_]+)}");
			Matcher matcher = pattern.matcher(content);
			while (matcher.find()) {
				variables.add(matcher.group(1));
			}
		} catch (Exception e) {
			throw new IllegalArgumentException(
					"Failed to extract variables from template: " + templateName, e);
		}
		return variables;
	}

	public static Map<String, String> toCustomDataMap(
			EmailRequestPayload emailRequestPayload,
			CustomApiConfig customApiConfig,
			CommonService commonService) {
		if (emailRequestPayload == null) {
			throw new IllegalArgumentException("EmailRequestPayload must not be null");
		}
		Map<String, String> customDataMap = new HashMap<>();
		customDataMap.put(
				NotificationCustomDataEnum.USER_NAME.getValue(), emailRequestPayload.getUserName());
		customDataMap.put(
				NotificationCustomDataEnum.USER_EMAIL.getValue(), emailRequestPayload.getUserEmail());
		customDataMap.put(
				NotificationCustomDataEnum.ACCESS_LEVEL.getValue(), emailRequestPayload.getAccessLevel());
		customDataMap.put(
				NotificationCustomDataEnum.ACCESS_ITEMS.getValue(), emailRequestPayload.getAccessItems());
		customDataMap.put(
				NotificationCustomDataEnum.USER_PROJECTS.getValue(), emailRequestPayload.getUserProjects());
		customDataMap.put(
				NotificationCustomDataEnum.USER_ROLES.getValue(), emailRequestPayload.getUserRoles());
		customDataMap.put(
				NotificationCustomDataEnum.ACCOUNT_NAME.getValue(), emailRequestPayload.getAccountName());
		customDataMap.put(
				NotificationCustomDataEnum.TEAM_NAME.getValue(), emailRequestPayload.getTeamName());
		customDataMap.put(
				NotificationCustomDataEnum.YEAR.getValue(),
				String.valueOf(java.time.Year.now().getValue()));
		customDataMap.put(
				NotificationCustomDataEnum.MONTH.getValue(),
				java.time.Month.from(java.time.LocalDate.now()).name());
		customDataMap.put(
				NotificationCustomDataEnum.UPLOADED_BY.getValue(), emailRequestPayload.getUploadedBy());
		try {
			customDataMap.put(
					NotificationCustomDataEnum.SERVER_HOST.getValue(), commonService.getApiHost());
		} catch (UnknownHostException e) {
			log.error("Failed to get API host: {}", e.getMessage(), e);
		}
		customDataMap.put(
				NotificationCustomDataEnum.FEEDBACK_CONTENT.getValue(),
				emailRequestPayload.getFeedbackContent());
		customDataMap.put(
				NotificationCustomDataEnum.FEEDBACK_CATEGORY.getValue(),
				emailRequestPayload.getFeedbackCategory());
		customDataMap.put(
				NotificationCustomDataEnum.FEEDBACK_TYPE.getValue(), emailRequestPayload.getFeedbackType());
		customDataMap.put(
				NotificationCustomDataEnum.ADMIN_EMAIL.getValue(), emailRequestPayload.getAdminEmail());
		customDataMap.put(
				NotificationCustomDataEnum.TOOL_NAME.getValue(), emailRequestPayload.getToolName());
		customDataMap.put(
				NotificationCustomDataEnum.HELP_URL.getValue(),
				customApiConfig.getBrokenConnectionHelpUrl());
		customDataMap.put(
				NotificationCustomDataEnum.FIX_URL.getValue(), emailRequestPayload.getFixUrl());
		customDataMap.put(
				NotificationCustomDataEnum.EXPIRY_TIME.getValue(), emailRequestPayload.getExpiryTime());
		customDataMap.put(
				NotificationCustomDataEnum.RESET_URL.getValue(), emailRequestPayload.getResetUrl());
		customDataMap.put(
				NotificationCustomDataEnum.PDF_ATTACHMENT.getValue(),
				emailRequestPayload.getPdfAttachment());
		return customDataMap;
	}
}
