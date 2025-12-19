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

package com.publicissapient.kpidashboard.apis.comments.rest;

import java.util.List;
import java.util.Map;
import javax.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.comments.service.CommentsService;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.common.model.comments.CommentRequestDTO;
import com.publicissapient.kpidashboard.common.model.comments.CommentSubmitDTO;
import com.publicissapient.kpidashboard.common.model.comments.CommentViewRequestDTO;
import com.publicissapient.kpidashboard.common.model.comments.CommentViewResponseDTO;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Mahesh
 */
@RestController
@RequestMapping("/comments")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Comments Controller", description = "APIs for managing comments related operations")
public class CommentsController {

	private final CommentsService commentsService;

	/**
	 * This method will get the comments data based on the selected project from the organization
	 * level. This feature will work for both, Scrum and Kanban KPIs.
	 *
	 * @param commentRequestDTO the comment request DTO containing KPI ID and other details
	 * @return ResponseEntity containing the service response with comments data
	 */
	@Operation(
			summary = "Get comments by KPI ID",
			description = "Fetches comments for a specific KPI ID based on the provided request."
	)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "200",
					description = "Successfully retrieved comments",
					content = @Content(
							mediaType = "application/json",
							schema = @Schema(implementation = ServiceResponse.class),
							examples = @ExampleObject(
									value = "{\"success\": true, \"message\": \"Found comments\", \"data\": {\"kpiId\": \"123\", \"comments\": [...]}}"
							)
					)
			),
			@ApiResponse(responseCode = "400", description = "Invalid request payload"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@PostMapping("/getCommentsByKpiId")
	public ResponseEntity<ServiceResponse> getCommentsByKPI(
			@RequestBody CommentRequestDTO commentRequestDTO) {

		final Map<String, Object> mappedCommentInfo =
				commentsService.findCommentByKPIId(
						commentRequestDTO.getNode(),
						commentRequestDTO.getLevel(),
						commentRequestDTO.getNodeChildId(),
						commentRequestDTO.getKpiId());
		if (MapUtils.isEmpty(mappedCommentInfo)) {
			return ResponseEntity.status(HttpStatus.OK)
					.body(new ServiceResponse(false, "Comment not found", mappedCommentInfo));
		}
		return ResponseEntity.status(HttpStatus.OK)
				.body(new ServiceResponse(true, "Found comments", mappedCommentInfo));
	}

	/**
	 * This method will save the comment for a selected project from the organization level. Only one
	 * comment can submit at a time for the project & selected KPI.
	 *
	 * @param comment the comment submit DTO containing comment details
	 * @return ResponseEntity containing the service response after submitting the comment
	 */
	@Operation(
			summary = "Submit a comment",
			description = "Saves a comment for a specific KPI and project."
	)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "200",
					description = "Comment submitted successfully",
					content = @Content(
							mediaType = "application/json",
							schema = @Schema(implementation = ServiceResponse.class),
							examples = @ExampleObject(
									value = "{\"success\": true, \"message\": \"Your comment is submitted successfully.\", \"data\": {\"comment\": \"Great work!\"}}"
							)
					)
			),
			@ApiResponse(responseCode = "400", description = "Invalid request payload"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@PostMapping("/submitComments")
	public ResponseEntity<ServiceResponse> submitComments(
			@Valid @RequestBody CommentSubmitDTO comment) {
		boolean responseStatus = commentsService.submitComment(comment);
		if (responseStatus) {
			return ResponseEntity.status(HttpStatus.OK)
					.body(
							new ServiceResponse(
									responseStatus, "Your comment is submitted successfully.", comment));
		} else {
			return ResponseEntity.status(HttpStatus.OK)
					.body(
							new ServiceResponse(
									responseStatus, "Issue occurred while saving the comment.", comment));
		}
	}

	/**
	 * Get the KPI-wise comments count
	 *
	 * @param commentViewRequestDTO the comment view request DTO
	 * @return ResponseEntity containing the service response with KPI-wise comments count
	 */
	@Operation(
			summary = "Get KPI-wise comments count",
			description = "Fetches the count of comments for each KPI based on the provided request."
	)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "200",
					description = "Successfully retrieved comments count",
					content = @Content(
							mediaType = "application/json",
							schema = @Schema(implementation = ServiceResponse.class),
							examples = @ExampleObject(
									value = "{\"success\": true, \"message\": \"Found Comments Count\", \"data\": {\"kpiId1\": 5, \"kpiId2\": 3}}"
							)
					)
			),
			@ApiResponse(responseCode = "400", description = "Invalid request payload"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@PostMapping("/getCommentCount")
	public ResponseEntity<ServiceResponse> getKpiWiseCommentsCount(
			@RequestBody CommentViewRequestDTO commentViewRequestDTO) {
		Map<String, Integer> kpiWiseCount =
				commentsService.findCommentByBoard(
						commentViewRequestDTO.getNodes(),
						commentViewRequestDTO.getLevel(),
						commentViewRequestDTO.getNodeChildId(),
						commentViewRequestDTO.getKpiIds());
		if (MapUtils.isEmpty(kpiWiseCount)) {
			return ResponseEntity.status(HttpStatus.OK)
					.body(new ServiceResponse(false, "Comments not found", null));
		} else {
			return ResponseEntity.status(HttpStatus.OK)
					.body(new ServiceResponse(true, "Found Comments Count", kpiWiseCount));
		}
	}

	/**
	 * Delete a comment based on id
	 *
	 * @param commentId the comment id that will get deleted
	 * @return ResponseEntity containing the service response after deleting the comment
	 */
	@Operation(
			summary = "Delete a comment by ID",
			description = "Deletes a comment based on the provided comment ID."
	)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "200",
					description = "Comment deleted successfully",
					content = @Content(
							mediaType = "application/json",
							schema = @Schema(implementation = ServiceResponse.class),
							examples = @ExampleObject(
									value = "{\"success\": true, \"message\": \"Successfully Deleted Comment\", \"data\": \"commentId123\"}"
							)
					)
			),
			@ApiResponse(responseCode = "404", description = "Comment not found"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@DeleteMapping("/deleteCommentById/{commentId}")
	public ResponseEntity<ServiceResponse> deleteComments(@PathVariable String commentId) {
		try {
			commentsService.deleteComments(commentId);
			return ResponseEntity.status(HttpStatus.OK)
					.body(new ServiceResponse(true, "Successfully Deleted Comment", commentId));
		} catch (Exception ex) {
			return ResponseEntity.status(HttpStatus.OK)
					.body(new ServiceResponse(false, "Comments Not Deleted", ex.getMessage()));
		}
	}

	/**
	 * Get latest comments summary for the provided KPIs.
	 *
	 * @param commentViewRequestDTO the comment view request DTO
	 * @return ResponseEntity containing the service response with latest comments summary
	 */
	@Operation(
			summary = "Get comments summary",
			description = "Fetches a summary of the latest comments for the provided KPIs."
	)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "200",
					description = "Successfully retrieved comments summary",
					content = @Content(
							mediaType = "application/json",
							schema = @Schema(implementation = ServiceResponse.class),
							examples = @ExampleObject(
									value = "{\"success\": true, \"message\": \"Found comments\", \"data\": [{\"kpiId\": \"123\", \"latestComment\": \"Great work!\"}]}"
							)
					)
			),
			@ApiResponse(responseCode = "400", description = "Invalid request payload"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@PostMapping("/commentsSummary")
	public ResponseEntity<ServiceResponse> getCommentsSummary(
			@RequestBody CommentViewRequestDTO commentViewRequestDTO) {

		List<CommentViewResponseDTO> commentViewAllByBoard =
				commentsService.findLatestCommentSummary(
						commentViewRequestDTO.getNodes(),
						commentViewRequestDTO.getLevel(),
						commentViewRequestDTO.getNodeChildId(),
						commentViewRequestDTO.getKpiIds());
		if (CollectionUtils.isEmpty(commentViewAllByBoard)) {
			return ResponseEntity.status(HttpStatus.OK)
					.body(new ServiceResponse(false, "Comments not found", null));
		} else {
			return ResponseEntity.status(HttpStatus.OK)
					.body(new ServiceResponse(true, "Found comments", commentViewAllByBoard));
		}
	}
}
