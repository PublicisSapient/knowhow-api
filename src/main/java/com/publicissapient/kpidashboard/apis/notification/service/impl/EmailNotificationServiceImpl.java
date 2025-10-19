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

package com.publicissapient.kpidashboard.apis.notification.service.impl;

import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.common.service.CommonService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.notification.service.EmailNotificationService;
import com.publicissapient.kpidashboard.apis.notification.util.NotificationUtility;
import com.publicissapient.kpidashboard.common.model.notification.EmailRequestPayload;
import com.publicissapient.kpidashboard.common.service.NotificationService;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@AllArgsConstructor
public class EmailNotificationServiceImpl implements EmailNotificationService {

	private final NotificationService notificationService;
	private final CustomApiConfig customApiConfig;
	private final CommonService commonService;

	@Override
	public ServiceResponse sendEmail(
			String templateKey, String notificationSubjectKey, EmailRequestPayload request) {
		try {
			Map<String, String> customData =
					NotificationUtility.toCustomDataMap(request, customApiConfig, commonService);
			String templateName = getTemplateName(templateKey);
			validateTemplateData(templateName, customData);
			String notificationSubject = getNotificationSubject(notificationSubjectKey);

			notificationService.sendNotificationEvent(
					request.getRecipients(),
					customData,
					notificationSubject,
					customApiConfig.isNotificationSwitch(),
					templateName);
		} catch (IllegalArgumentException e) {
			log.error("Validation error: {}", e.getMessage());
			return new ServiceResponse(false, e.getMessage(), null);
		} catch (Exception e) {
			log.error("Failed to send email: {}", e.getMessage(), e);
			return new ServiceResponse(false, "Failed to send email: " + e.getMessage(), null);
		}
		return new ServiceResponse(true, "Email sent successfully.", null);
	}

	private String getTemplateName(String templateKey) {
		Map<String, String> mailTemplateMap = customApiConfig.getMailTemplate();
		if (!mailTemplateMap.containsKey(templateKey)) {
			throw new IllegalArgumentException("No email template found for key: " + templateKey);
		}
		return mailTemplateMap.get(templateKey);
	}

	private void validateTemplateData(String templateName, Map<String, String> customData) {
		Set<String> requiredVars = NotificationUtility.extractEmailTemplateVariables(templateName);
		for (String var : requiredVars) {
			if (!customData.containsKey(var)
					|| customData.get(var) == null
					|| customData.get(var).trim().isEmpty()) {
				throw new IllegalArgumentException("Missing required template variable: " + var);
			}
		}
	}

	private String getNotificationSubject(String notificationSubjectKey) {
		Map<String, String> notificationSubjectMap = customApiConfig.getNotificationSubject();
		String subject = notificationSubjectMap.get(notificationSubjectKey);
		if (StringUtils.isBlank(subject)) {
			throw new IllegalArgumentException(
					"No notification subject found for key: " + notificationSubjectKey);
		}
		return subject;
	}
}
