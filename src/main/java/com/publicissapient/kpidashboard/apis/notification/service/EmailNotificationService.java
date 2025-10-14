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

package com.publicissapient.kpidashboard.apis.notification.service;

import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.common.model.notification.EmailRequestPayload;

public interface EmailNotificationService {

	/**
	 * Method to send an email using the specified template and request data.
	 *
	 * @param templateKey email template key
	 * @param notificationSubjectKey- email Subject
	 * @param request email request payload containing recipient and template data
	 * @return ServiceResponse containing the status of the operation
	 */
	ServiceResponse sendEmail(
			String templateKey, String notificationSubjectKey, EmailRequestPayload request);
}
