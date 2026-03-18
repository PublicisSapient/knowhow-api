/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *    http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.feedback.rest;

import java.util.List;
import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.auth.service.AuthenticationService;
import com.publicissapient.kpidashboard.apis.feedback.service.FeedbackService;
import com.publicissapient.kpidashboard.apis.model.FeedbackSubmitDTO;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author sanbhand1
 */
@RestController
@RequestMapping("/feedback")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Feedback API", description = "APIs for Feedback Management")
public class FeedbackController {

	/** Instantiates the SubmitFeedbackService */
	private final FeedbackService submitFeedbackService;

	private final AuthenticationService authenticationService;

	/**
	 * @return feedback categories
	 */
	@Operation(
			summary = "Get Feedback Categories",
			description = "Retrieve all feedback categories available for submission.")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully retrieved feedback categories"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@GetMapping("/categories")
	public ResponseEntity<ServiceResponse> getFeedbackCategories() {
		List<String> feedbackCategories = submitFeedbackService.getFeedBackCategories();
		log.info("Fetching data for Feedback Categories");
		return ResponseEntity.status(HttpStatus.OK)
				.body(new ServiceResponse(true, "Found all feedback categories", feedbackCategories));
	}

	/**
	 * Create an method to submit feedback.
	 *
	 * @return responseEntity with message and status
	 */
	@Operation(
			summary = "Submit Feedback",
			description = "Submit feedback with details such as category, subject, and message.")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Feedback submitted successfully"),
				@ApiResponse(responseCode = "400", description = "Invalid feedback data"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@PostMapping("/submitfeedback")
	public ResponseEntity<ServiceResponse> submitFeedback(
			@Parameter(description = "Feedback submission details", required = true) @Valid @RequestBody
					FeedbackSubmitDTO feedback,
			HttpServletRequest httpServletRequest) {
		log.info("creating new request");
		String loggedUserEmail = authenticationService.getLoggedInUser();
		if (loggedUserEmail != null) {
			boolean responseStatus = submitFeedbackService.submitFeedback(feedback, loggedUserEmail);
			if (responseStatus) {
				return ResponseEntity.status(HttpStatus.OK)
						.body(new ServiceResponse(responseStatus, "Your request has been submitted", feedback));
			} else {
				return ResponseEntity.status(HttpStatus.OK)
						.body(
								new ServiceResponse(
										responseStatus,
										"Email Not Sent ,check emailId and Subject configuration ",
										feedback));
			}
		}
		return ResponseEntity.status(HttpStatus.OK)
				.body(new ServiceResponse(false, "User is not Valid for feedback", feedback));
	}
}
