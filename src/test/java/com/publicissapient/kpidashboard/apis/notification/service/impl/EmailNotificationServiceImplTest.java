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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.apis.common.service.CommonService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.notification.util.NotificationUtility;
import com.publicissapient.kpidashboard.common.model.notification.EmailRequestPayload;
import com.publicissapient.kpidashboard.common.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceImplTest {

	@InjectMocks private EmailNotificationServiceImpl emailNotificationService;

	@Mock private NotificationService notificationService;

	@Mock private CustomApiConfig customApiConfig;
	@Mock private CommonService commonService;

	private EmailRequestPayload createValidPayload() {
		EmailRequestPayload payload = new EmailRequestPayload();
		payload.setUserName("testUser");
		payload.setUserEmail("test@test.com");
		payload.setAccessLevel("level1");
		payload.setAccessItems("item");
		payload.setUserProjects("project");
		payload.setUserRoles("role");
		payload.setAccountName("account");
		payload.setTeamName("team");
		payload.setUploadedBy("uploader");
		payload.setServerHost("host");
		payload.setFeedbackContent("feedback");
		payload.setFeedbackCategory("cat");
		payload.setFeedbackType("type");
		payload.setAdminEmail("admin@test.com");
		payload.setToolName("tool");
		payload.setFixUrl("fixUrl");
		payload.setExpiryTime("expiry");
		payload.setResetUrl("resetUrl");
		payload.setPdfAttachment("pdfData");
		payload.setRecipients(
				new ArrayList<String>() {
					{
						add("recipient@test.com");
					}
				});
		return payload;
	}

	@Test
	void testSendEmailSuccess() {
		EmailRequestPayload payload = createValidPayload();
		String templateKey = "TP";
		String templateName = "Forgot_Password_Template";
		String subject = "Test Subject";
		String notificationSubjectKey = "TP";

		Map<String, String> mailTemplateMap = new HashMap<>();
		mailTemplateMap.put(templateKey, templateName);
		Map<String, String> subjectMap = new HashMap<>();
		subjectMap.put(templateKey, subject);

		when(customApiConfig.getMailTemplate()).thenReturn(mailTemplateMap);
		when(customApiConfig.getNotificationSubject()).thenReturn(subjectMap);
		when(customApiConfig.isNotificationSwitch()).thenReturn(true);

		final Map<String, String> customData = getTemplateDataMap(payload);

		try (MockedStatic<NotificationUtility> mockedUtil = mockStatic(NotificationUtility.class)) {
			mockedUtil
					.when(() -> NotificationUtility.toCustomDataMap(payload, customApiConfig, commonService))
					.thenReturn(customData);
			mockedUtil
					.when(() -> NotificationUtility.extractEmailTemplateVariables(templateName))
					.thenReturn(new HashSet<>());
			ServiceResponse response =
					emailNotificationService.sendEmail(templateKey, notificationSubjectKey, payload);

			verify(notificationService)
					.sendNotificationEvent(
							eq(payload.getRecipients()), eq(customData), eq(subject), eq(true), eq(templateName));
			assertTrue(response.getSuccess());
			assertEquals("Email sent successfully.", response.getMessage());
		}
	}

	@Test
	void testSendEmailNoTemplateMapping() {
		EmailRequestPayload payload = createValidPayload();
		String templateKey = "NO_KEY";
		String notificationSubjectKey = "NO_KEY";
		// Return an empty mail template map so that getTemplateName fails.
		when(customApiConfig.getMailTemplate()).thenReturn(new HashMap<>());

		ServiceResponse response =
				emailNotificationService.sendEmail(templateKey, notificationSubjectKey, payload);
		assertFalse(response.getSuccess());
		assertTrue(response.getMessage().contains("No email template found for key"));
	}

	@Test
	void testSendEmailMissingRequiredTemplateVariable() {
		EmailRequestPayload payload = createValidPayload();
		String templateKey = "TP";
		String notificationSubjectKey = "TP";
		String templateName = "Forgot_Password_Template";
		Map<String, String> mailTemplateMap = new HashMap<>();
		mailTemplateMap.put(templateKey, templateName);
		Map<String, String> subjectMap = new HashMap<>();
		subjectMap.put(templateKey, "Test Subject");
		when(customApiConfig.getMailTemplate()).thenReturn(mailTemplateMap);

		Map<String, String> customData = new HashMap<>();
		customData.put("USER_NAME", payload.getUserName());
		customData.put("USER_EMAIL", payload.getUserEmail());

		try (MockedStatic<NotificationUtility> mockedUtil = mockStatic(NotificationUtility.class)) {
			mockedUtil
					.when(() -> NotificationUtility.toCustomDataMap(payload, customApiConfig, commonService))
					.thenReturn(customData);
			Set<String> requiredVars = new HashSet<>();
			requiredVars.add("MISSING_VAR");
			mockedUtil
					.when(() -> NotificationUtility.extractEmailTemplateVariables(templateName))
					.thenReturn(requiredVars);

			ServiceResponse response =
					emailNotificationService.sendEmail(templateKey, notificationSubjectKey, payload);
			assertFalse(response.getSuccess());
			assertEquals("Missing required template variable: MISSING_VAR", response.getMessage());
		}
	}

	@Test
	void testSendEmailExceptionInNotification() {
		EmailRequestPayload payload = createValidPayload();
		String templateKey = "TP";
		String notificationSubjectKey = "TP";
		String templateName = "Forgot_Password_Template";
		String subject = "Test Subject";

		Map<String, String> mailTemplateMap = new HashMap<>();
		mailTemplateMap.put(templateKey, templateName);
		Map<String, String> subjectMap = new HashMap<>();
		subjectMap.put(templateKey, subject);

		when(customApiConfig.getMailTemplate()).thenReturn(mailTemplateMap);
		when(customApiConfig.getNotificationSubject()).thenReturn(subjectMap);
		when(customApiConfig.isNotificationSwitch()).thenReturn(true);

		final Map<String, String> customData = getTemplateDataMap(payload);

		try (MockedStatic<NotificationUtility> mockedUtil = mockStatic(NotificationUtility.class)) {
			mockedUtil
					.when(() -> NotificationUtility.toCustomDataMap(payload, customApiConfig, commonService))
					.thenReturn(customData);
			mockedUtil
					.when(() -> NotificationUtility.extractEmailTemplateVariables(templateName))
					.thenReturn(new HashSet<>());
			doThrow(new RuntimeException("Notification failure"))
					.when(notificationService)
					.sendNotificationEvent(
							Collections.singletonList(anyString()),
							anyMap(),
							anyString(),
							anyBoolean(),
							anyString());

			ServiceResponse response =
					emailNotificationService.sendEmail(templateKey, notificationSubjectKey, payload);
			assertFalse(response.getSuccess());
			assertTrue(response.getMessage().contains("Failed to send email:"));
		}
	}

	@NotNull
	private static Map<String, String> getTemplateDataMap(EmailRequestPayload payload) {
		Map<String, String> customData = new HashMap<>();
		customData.put("USER_NAME", payload.getUserName());
		customData.put("USER_EMAIL", payload.getUserEmail());
		customData.put("ACCESS_LEVEL", payload.getAccessLevel());
		customData.put("ACCESS_ITEMS", payload.getAccessItems());
		customData.put("USER_PROJECTS", payload.getUserProjects());
		customData.put("USER_ROLES", payload.getUserRoles());
		customData.put("ACCOUNT_NAME", payload.getAccountName());
		customData.put("TEAM_NAME", payload.getTeamName());
		customData.put("UPLOADED_BY", payload.getUploadedBy());
		customData.put("SERVER_HOST", payload.getServerHost());
		customData.put("FEEDBACK_CONTENT", payload.getFeedbackContent());
		customData.put("FEEDBACK_CATEGORY", payload.getFeedbackCategory());
		customData.put("FEEDBACK_TYPE", payload.getFeedbackType());
		customData.put("ADMIN_EMAIL", payload.getAdminEmail());
		customData.put("TOOL_NAME", payload.getToolName());
		customData.put("HELP_URL", "");
		customData.put("FIX_URL", payload.getFixUrl());
		customData.put("EXPIRY_TIME", payload.getExpiryTime());
		customData.put("RESET_URL", payload.getResetUrl());
		customData.put("PDF_ATTACHMENT", payload.getPdfAttachment());
		return customData;
	}
}
