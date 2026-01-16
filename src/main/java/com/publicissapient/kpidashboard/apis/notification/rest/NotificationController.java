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

package com.publicissapient.kpidashboard.apis.notification.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.notification.service.EmailNotificationService;
import com.publicissapient.kpidashboard.common.model.notification.EmailRequestPayload;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/notifications")
@Tag(name = "Notification", description = "Operations related to notifications")
@RequiredArgsConstructor
@Tag(name = "Notification API", description = "APIs for Notification Management")
public class NotificationController {

	private final EmailNotificationService emailNotificationService;

	@Operation(
			summary = "Send an email notification",
			description =
					"Send an email notification based on the specified template key and payload data.",
			responses = {
				@ApiResponse(
						responseCode = "200",
						description = "Email sent successfully",
						content =
								@Content(
										mediaType = "application/json",
										schema = @Schema(implementation = ServiceResponse.class))),
				@ApiResponse(responseCode = "400", description = "Invalid input", content = @Content)
			})
	@PostMapping("/email")
	public ResponseEntity<ServiceResponse> sendEmail(
			@RequestParam String templateKey,
			@RequestParam String notificationSubjectKey,
			@Valid @RequestBody EmailRequestPayload emailRequestPayload) {
		ServiceResponse response =
				emailNotificationService.sendEmail(
						templateKey, notificationSubjectKey, emailRequestPayload);
		return ResponseEntity.ok(response);
	}
}
