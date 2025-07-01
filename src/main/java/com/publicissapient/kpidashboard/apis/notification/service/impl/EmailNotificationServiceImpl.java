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

import com.publicissapient.kpidashboard.apis.common.service.CommonService;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.common.model.notification.EmailRequestPayload;
import com.publicissapient.kpidashboard.apis.notification.service.EmailNotificationService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.notification.util.NotificationUtility;
import com.publicissapient.kpidashboard.common.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class EmailNotificationServiceImpl implements EmailNotificationService {

	@Autowired
	private NotificationService notificationService;
	@Autowired
	private CustomApiConfig customApiConfig;
	@Autowired
	private CommonService commonService;
	@Autowired
	private KafkaTemplate<String, Object> kafkaTemplate;

	@Override
	public ServiceResponse sendEmail(String templateKey, EmailRequestPayload request) {
		try {
			Map<String, String> customData = NotificationUtility.toCustomDataMap(request, customApiConfig, commonService);
			String templateName = getTemplateName(templateKey);
			validateTemplateData(templateName, customData);
			String notificationSubject = getNotificationSubject(templateKey);

			notificationService.sendNotificationEvent(request.getRecipients(), customData, notificationSubject,
					templateKey, customApiConfig.getKafkaMailTopic(), customApiConfig.isNotificationSwitch(),
					kafkaTemplate, templateName, customApiConfig.isMailWithoutKafka());
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
			if (!customData.containsKey(var) || customData.get(var) == null || customData.get(var).trim().isEmpty()) {
				throw new IllegalArgumentException("Missing required template variable: " + var);
			}
		}
	}

	private String getNotificationSubject(String templateKey) {
		Map<String, String> notificationSubjectMap = customApiConfig.getNotificationSubject();
		return notificationSubjectMap.get(templateKey);
	}

}