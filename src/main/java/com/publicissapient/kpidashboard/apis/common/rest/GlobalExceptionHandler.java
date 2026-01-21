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

package com.publicissapient.kpidashboard.apis.common.rest;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolationException;

import org.apache.commons.lang3.NotImplementedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.publicissapient.kpidashboard.apis.auth.exceptions.DeleteLastAdminException;
import com.publicissapient.kpidashboard.apis.auth.exceptions.InvalidAuthTypeConfigException;
import com.publicissapient.kpidashboard.apis.auth.exceptions.PendingApprovalException;
import com.publicissapient.kpidashboard.apis.auth.exceptions.UserNotFoundException;
import com.publicissapient.kpidashboard.apis.errors.ProjectNotFoundException;
import com.publicissapient.kpidashboard.apis.errors.ToolNotFoundException;
import com.publicissapient.kpidashboard.apis.model.ErrorResponse;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.pushdata.util.PushDataException;
import com.publicissapient.kpidashboard.common.exceptions.ApplicationException;
import com.publicissapient.kpidashboard.common.util.UnsafeDeleteException;

import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;

/** Controller advice to handle exceptions globally. */
@Slf4j
@EnableWebMvc
@ControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {
	private static final String ERROR_CODE_STR = "Error code=: ";
	private static final String GENERIC_ERROR_MESSAGE = "An error occurred. Please try again later.";

	@ExceptionHandler(UnsafeDeleteException.class)
	public ResponseEntity<Map<String, String>> handleUnsafeDelete(UnsafeDeleteException ex) {
		log.error(ex.getMessage());
		Map<String, String> errorResponse = new HashMap<>();
		errorResponse.put("errorMessage", ex.getMessage());
		return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(RuntimeException.class)
	public ResponseEntity<ErrorResponse> handleConflict(RuntimeException exception) {
		return new ResponseEntity<>(
				logAndBuildResponse(
						HttpStatus.INTERNAL_SERVER_ERROR, "Please try after some time.", exception),
				HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleGeneralException(Exception exception) {
		return new ResponseEntity<>(
				logAndBuildResponse(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_ERROR_MESSAGE, exception),
				HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@ExceptionHandler(ApplicationException.class)
	public ResponseEntity<ErrorResponse> handleSpeedyException(
			ApplicationException applicationException) {
		log.error(applicationException.getMessage(), applicationException);
		return switch (applicationException.getErrorCode()) {
			case ApplicationException.DUPLICATE_DATA, ApplicationException.JSON_FORMAT_ERROR ->
					new ResponseEntity<>(
							logAndBuildResponse(
									HttpStatus.BAD_REQUEST,
									String.format(
											"Bad request.%s%s%s",
											applicationException.getMessage(),
											ERROR_CODE_STR,
											applicationException.getErrorCode()),
									applicationException),
							HttpStatus.BAD_REQUEST);
			case ApplicationException.NOTHING_TO_UPDATE ->
					new ResponseEntity<>(
							logAndBuildResponse(
									HttpStatus.NOT_MODIFIED,
									String.format(
											"Internal logError.%s%s%s",
											applicationException.getMessage(),
											ERROR_CODE_STR,
											applicationException.getErrorCode()),
									applicationException),
							HttpStatus.NOT_MODIFIED);
			default ->
					new ResponseEntity<>(
							logAndBuildResponse(
									HttpStatus.INTERNAL_SERVER_ERROR,
									String.format(
											"Internal logError.%s%s%s",
											applicationException.getMessage(),
											ERROR_CODE_STR,
											applicationException.getErrorCode()),
									applicationException),
							HttpStatus.INTERNAL_SERVER_ERROR);
		};
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> handleAccessDenied(
			AccessDeniedException accessDeniedException) {
		return new ResponseEntity<>(
				logAndBuildResponse(HttpStatus.FORBIDDEN, "Access Denied.", accessDeniedException),
				HttpStatus.FORBIDDEN);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
			MethodArgumentNotValidException methodArgumentNotValidException) {
		return new ResponseEntity<>(
				logAndBuildResponse(
						HttpStatus.BAD_REQUEST,
						getMethodArgumentNotValidExceptionDetails(methodArgumentNotValidException),
						methodArgumentNotValidException),
				HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(UnrecognizedPropertyException.class)
	public ResponseEntity<ErrorResponse> handleUnrecognizedProperty(
			UnrecognizedPropertyException ex) {
		log.error(ex.getMessage());
		ErrorResponse response = new ErrorResponse();
		response.addFieldError(ex.getPropertyName(), ex.getMessage());
		response.setCode(HttpStatus.BAD_REQUEST.value());
		response.setError(HttpStatus.BAD_REQUEST.getReasonPhrase());
		response.setTimestamp(System.currentTimeMillis());
		return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(DeleteLastAdminException.class)
	public ResponseEntity<ErrorResponse> handleDeletingLastAdmin(
			DeleteLastAdminException deleteLastAdminException) {
		return new ResponseEntity<>(
				logAndBuildResponse(HttpStatus.BAD_REQUEST, deleteLastAdminException),
				HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(UserNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException exception) {
		return new ResponseEntity<>(
				logAndBuildResponse(HttpStatus.NOT_FOUND, exception), HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(jakarta.ws.rs.BadRequestException.class)
	public ResponseEntity<ErrorResponse> handleJakartaBadRequestException(
			jakarta.ws.rs.BadRequestException exception) {
		return new ResponseEntity<>(
				logAndBuildResponse(HttpStatus.BAD_REQUEST, exception), HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
			IllegalArgumentException exception) {
		return new ResponseEntity<>(
				logAndBuildResponse(HttpStatus.BAD_REQUEST, exception), HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ErrorResponse> handleIllegalStateException(
			IllegalStateException exception) {
		return new ResponseEntity<>(
				logAndBuildResponse(HttpStatus.BAD_REQUEST, exception), HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolationException(
			ConstraintViolationException exception) {
		return new ResponseEntity<>(
				logAndBuildResponse(
						HttpStatus.BAD_REQUEST, getConstraintViolationExceptionDetails(exception), exception),
				HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(NotImplementedException.class)
	public ResponseEntity<ErrorResponse> handleNotImplementedException(
			NotImplementedException exception) {
		return new ResponseEntity<>(
				logAndBuildResponse(HttpStatus.NOT_IMPLEMENTED, exception), HttpStatus.NOT_IMPLEMENTED);
	}

	@ExceptionHandler(ToolNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleToolNotFoundException(
			ToolNotFoundException exception) {
		return new ResponseEntity<>(
				logAndBuildResponse(HttpStatus.NOT_FOUND, exception), HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(ProjectNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleProjectNotFound(ProjectNotFoundException exception) {
		return new ResponseEntity<>(
				logAndBuildResponse(HttpStatus.NOT_FOUND, exception), HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFoundException(
			NotFoundException notFoundException) {
		return new ResponseEntity<>(
				logAndBuildResponse(HttpStatus.NOT_FOUND, notFoundException), HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(PendingApprovalException.class)
	public ResponseEntity<ErrorResponse> handlePendingApprovalException(
			PendingApprovalException exception) {
		return new ResponseEntity<>(
				logAndBuildResponse(HttpStatus.ACCEPTED, exception), HttpStatus.ACCEPTED);
	}

	@ExceptionHandler(InvalidAuthTypeConfigException.class)
	public ResponseEntity<ErrorResponse> handleInvalidAuthTypeConfigException(
			InvalidAuthTypeConfigException invalidAuthTypeConfigException) {
		return new ResponseEntity<>(
				logAndBuildResponse(HttpStatus.BAD_REQUEST, invalidAuthTypeConfigException),
				HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(PushDataException.class)
	public ResponseEntity<Object> handlePushDataExceptions(PushDataException ex) {
		log.error(ex.getMessage());
		if (ex.getPushBuildDeployResponse() != null) {
			return new ResponseEntity<>(
					new ServiceResponse(false, ex.getMessage(), ex.getPushBuildDeployResponse()),
					HttpStatus.BAD_REQUEST);
		}
		if (ex.getCode() != null) {
			return new ResponseEntity<>(
					new ServiceResponse(false, ex.getMessage(), ex.getPushBuildDeployResponse()),
					ex.getCode());
		}
		Map<String, String> errorResponse = new HashMap<>();
		errorResponse.put("error", ex.getMessage());
		return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(InternalServerErrorException.class)
	public ResponseEntity<ErrorResponse> handleInternalServerErrorException(
			InternalServerErrorException exception) {
		return new ResponseEntity<>(
				logAndBuildResponse(HttpStatus.BAD_REQUEST, exception), HttpStatus.BAD_REQUEST);
	}

	private static ErrorResponse logAndBuildResponse(HttpStatus httpStatus, Throwable exception) {
		log.error(exception.getMessage());
		return ErrorResponse.builder()
				.code(httpStatus.value())
				.error(httpStatus.getReasonPhrase())
				.message(exception.getMessage())
				.build();
	}

	private static ErrorResponse logAndBuildResponse(
			HttpStatus httpStatus, String customMessage, Throwable exception) {
		log.error(exception.getMessage());
		return ErrorResponse.builder()
				.code(httpStatus.value())
				.error(httpStatus.getReasonPhrase())
				.message(customMessage)
				.build();
	}

	private static String getConstraintViolationExceptionDetails(
			ConstraintViolationException exception) {
		StringBuilder message = new StringBuilder();
		exception.getConstraintViolations().stream()
				.map(
						constraintViolation ->
								String.format(
										"Invalid value %s - %s. ",
										constraintViolation.getInvalidValue(), constraintViolation.getMessage()))
				.forEach(message::append);
		return message.toString();
	}

	private static String getMethodArgumentNotValidExceptionDetails(
			MethodArgumentNotValidException methodArgumentNotValidException) {
		return methodArgumentNotValidException.getBindingResult().getFieldErrors().stream()
				.map(FieldError::getDefaultMessage)
				.collect(Collectors.joining("; "));
	}
}
