/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

package com.publicissapient.kpidashboard.apis.common.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;

import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.publicissapient.kpidashboard.apis.auth.exceptions.DeleteLastAdminException;
import com.publicissapient.kpidashboard.apis.auth.exceptions.InvalidAuthTypeConfigException;
import com.publicissapient.kpidashboard.apis.auth.exceptions.PendingApprovalException;
import com.publicissapient.kpidashboard.apis.auth.exceptions.UserNotFoundException;
import com.publicissapient.kpidashboard.apis.errors.InvalidLevelException;
import com.publicissapient.kpidashboard.apis.errors.ProjectNotFoundException;
import com.publicissapient.kpidashboard.apis.errors.ToolNotFoundException;
import com.publicissapient.kpidashboard.apis.model.ErrorResponse;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.pushdata.model.PushDataResponse;
import com.publicissapient.kpidashboard.apis.pushdata.util.PushDataException;
import com.publicissapient.kpidashboard.common.constant.AuthType;
import com.publicissapient.kpidashboard.common.exceptions.ApplicationException;
import com.publicissapient.kpidashboard.common.util.UnsafeDeleteException;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

	@InjectMocks private GlobalExceptionHandler globalExceptionHandler;

	@Mock private ConstraintViolation<Object> constraintViolation;

	@Mock private BindingResult bindingResult;

	@Mock private FieldError fieldError;

	@Test
	void when_UnsafeDeleteExceptionThrown_Then_ReturnBadRequestWithErrorMessage() {
		// Arrange
		UnsafeDeleteException exception = new UnsafeDeleteException("Cannot delete this resource");

		// Act
		ResponseEntity<Map<String, String>> response =
				globalExceptionHandler.handleUnsafeDelete(exception);

		// Assert
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertEquals(
				"Cannot delete this resource",
				Objects.requireNonNull(response.getBody()).get("errorMessage"));
	}

	@Test
	void when_RuntimeExceptionThrown_Then_ReturnInternalServerError() {
		// Arrange
		RuntimeException exception = new RuntimeException("Runtime error occurred");

		// Act
		ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleConflict(exception);

		// Assert
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertEquals(
				"Please try after some time.", Objects.requireNonNull(response.getBody()).getMessage());
	}

	@Test
	void when_GeneralExceptionThrown_Then_ReturnInternalServerErrorWithGenericMessage() {
		// Arrange
		Exception exception = new Exception("General exception");

		// Act
		ResponseEntity<ErrorResponse> response =
				globalExceptionHandler.handleGeneralException(exception);

		// Assert
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertEquals(
				"An error occurred. Please try again later.",
				Objects.requireNonNull(response.getBody()).getMessage());
	}

	@Test
	void when_ApplicationExceptionWithDuplicateDataThrown_Then_ReturnBadRequest() {
		// Arrange
		ApplicationException exception =
				new ApplicationException("Duplicate data found", ApplicationException.DUPLICATE_DATA);

		// Act
		ResponseEntity<ErrorResponse> response =
				globalExceptionHandler.handleSpeedyException(exception);

		// Assert
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertTrue(Objects.requireNonNull(response.getBody()).getMessage().contains("Bad request"));
		assertTrue(
				Objects.requireNonNull(response.getBody()).getMessage().contains("Duplicate data found"));
	}

	@Test
	void when_ApplicationExceptionWithJsonFormatErrorThrown_Then_ReturnBadRequest() {
		// Arrange
		ApplicationException exception =
				new ApplicationException("JSON format error", ApplicationException.JSON_FORMAT_ERROR);

		// Act
		ResponseEntity<ErrorResponse> response =
				globalExceptionHandler.handleSpeedyException(exception);

		// Assert
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertTrue(Objects.requireNonNull(response.getBody()).getMessage().contains("Bad request"));
		assertTrue(
				Objects.requireNonNull(response.getBody()).getMessage().contains("JSON format error"));
	}

	@Test
	void when_ApplicationExceptionWithNothingToUpdateThrown_Then_ReturnNotModified() {
		// Arrange
		ApplicationException exception =
				new ApplicationException("Nothing to update", ApplicationException.NOTHING_TO_UPDATE);

		// Act
		ResponseEntity<ErrorResponse> response =
				globalExceptionHandler.handleSpeedyException(exception);

		// Assert
		assertEquals(HttpStatus.NOT_MODIFIED, response.getStatusCode());
		assertTrue(
				Objects.requireNonNull(response.getBody()).getMessage().contains("Internal logError"));
		assertTrue(
				Objects.requireNonNull(response.getBody()).getMessage().contains("Nothing to update"));
	}

	@Test
	void when_ApplicationExceptionWithUnknownErrorCodeThrown_Then_ReturnInternalServerError() {
		// Arrange
		ApplicationException exception =
				new ApplicationException("Unknown error", HttpStatus.INTERNAL_SERVER_ERROR.value());

		// Act
		ResponseEntity<ErrorResponse> response =
				globalExceptionHandler.handleSpeedyException(exception);

		// Assert
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertTrue(
				Objects.requireNonNull(response.getBody()).getMessage().contains("Internal logError"));
		assertTrue(Objects.requireNonNull(response.getBody()).getMessage().contains("Unknown error"));
	}

	@Test
	void when_AccessDeniedExceptionThrown_Then_ReturnForbidden() {
		// Arrange
		AccessDeniedException exception = new AccessDeniedException("Access denied to resource");

		// Act
		ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleAccessDenied(exception);

		// Assert
		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
		assertEquals("Access Denied.", Objects.requireNonNull(response.getBody()).getMessage());
	}

	@Test
	void when_MethodArgumentNotValidExceptionThrown_Then_ReturnBadRequestWithValidationErrors() {
		// Arrange
		MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
		when(fieldError.getDefaultMessage()).thenReturn("Field validation error");
		when(exception.getBindingResult()).thenReturn(bindingResult);
		when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

		// Act
		ResponseEntity<ErrorResponse> response =
				globalExceptionHandler.handleMethodArgumentNotValidException(exception);

		// Assert
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertEquals("Field validation error", Objects.requireNonNull(response.getBody()).getMessage());
	}

	@Test
	void
			when_MethodArgumentNotValidExceptionWithMultipleErrorsThrown_Then_ReturnBadRequestWithJoinedErrors() {
		// Arrange
		MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
		FieldError fieldError2 = mock(FieldError.class);
		when(fieldError.getDefaultMessage()).thenReturn("First validation error");
		when(fieldError2.getDefaultMessage()).thenReturn("Second validation error");

		when(exception.getBindingResult()).thenReturn(bindingResult);
		when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError, fieldError2));

		// Act
		ResponseEntity<ErrorResponse> response =
				globalExceptionHandler.handleMethodArgumentNotValidException(exception);

		// Assert
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertEquals(
				"First validation error; Second validation error",
				Objects.requireNonNull(response.getBody()).getMessage());
	}

	@Test
	void when_UnrecognizedPropertyExceptionThrown_Then_ReturnBadRequestWithFieldError() {
		// Arrange
		UnrecognizedPropertyException exception = mock(UnrecognizedPropertyException.class);
		when(exception.getPropertyName()).thenReturn("unknownProperty");
		when(exception.getMessage()).thenReturn("Unrecognized property");

		// Act
		ResponseEntity<ErrorResponse> response =
				globalExceptionHandler.handleUnrecognizedProperty(exception);

		// Assert
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}

	@Test
	void when_DeleteLastAdminExceptionThrown_Then_ReturnBadRequest() {
		// Arrange
		DeleteLastAdminException exception = new DeleteLastAdminException();

		// Act
		ResponseEntity<ErrorResponse> response =
				globalExceptionHandler.handleDeletingLastAdmin(exception);

		// Assert
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}

	@Test
	void when_UserNotFoundExceptionThrown_Then_ReturnNotFound() {
		// Arrange
		UserNotFoundException exception = new UserNotFoundException("User not found", AuthType.SAML);

		// Act
		ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleUserNotFound(exception);

		// Assert
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

	@Test
	void when_JakartaBadRequestExceptionThrown_Then_ReturnBadRequest() {
		// Arrange
		BadRequestException exception = new BadRequestException("Jakarta bad request");

		// Act
		ResponseEntity<ErrorResponse> response =
				globalExceptionHandler.handleJakartaBadRequestException(exception);

		// Assert
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertEquals("Jakarta bad request", Objects.requireNonNull(response.getBody()).getMessage());
	}

	@Test
	void when_IllegalArgumentExceptionThrown_Then_ReturnBadRequest() {
		// Arrange
		IllegalArgumentException exception = new IllegalArgumentException("Illegal argument");

		// Act
		ResponseEntity<ErrorResponse> response =
				globalExceptionHandler.handleIllegalArgumentException(exception);

		// Assert
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertEquals("Illegal argument", Objects.requireNonNull(response.getBody()).getMessage());
	}

	@Test
	void when_IllegalStateExceptionThrown_Then_ReturnBadRequest() {
		// Arrange
		IllegalStateException exception = new IllegalStateException("Illegal state");

		// Act
		ResponseEntity<ErrorResponse> response =
				globalExceptionHandler.handleIllegalStateException(exception);

		// Assert
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertEquals("Illegal state", Objects.requireNonNull(response.getBody()).getMessage());
	}

	@Test
	void when_ConstraintViolationExceptionThrown_Then_ReturnBadRequestWithConstraintDetails() {
		// Arrange
		when(constraintViolation.getInvalidValue()).thenReturn("invalidValue");
		when(constraintViolation.getMessage()).thenReturn("Constraint violation message");
		Set<ConstraintViolation<Object>> violations = Set.of(constraintViolation);
		ConstraintViolationException exception =
				new ConstraintViolationException("Constraint violation", violations);

		// Act
		ResponseEntity<ErrorResponse> response =
				globalExceptionHandler.handleConstraintViolationException(exception);

		// Assert
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertTrue(
				Objects.requireNonNull(response.getBody())
						.getMessage()
						.contains("Invalid value invalidValue - Constraint " + "violation " + "message"));
	}

	@Test
	void
			when_ConstraintViolationExceptionWithMultipleViolationsThrown_Then_ReturnBadRequestWithAllDetails() {
		// Arrange
		ConstraintViolation<Object> violation2 = mock(ConstraintViolation.class);
		when(constraintViolation.getInvalidValue()).thenReturn("invalidValue1");
		when(constraintViolation.getMessage()).thenReturn("First constraint violation");
		when(violation2.getInvalidValue()).thenReturn("invalidValue2");
		when(violation2.getMessage()).thenReturn("Second constraint violation");

		Set<ConstraintViolation<Object>> violations = Set.of(constraintViolation, violation2);
		ConstraintViolationException exception =
				new ConstraintViolationException("Multiple violations", violations);

		// Act
		ResponseEntity<ErrorResponse> response =
				globalExceptionHandler.handleConstraintViolationException(exception);

		// Assert
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		String message = Objects.requireNonNull(response.getBody()).getMessage();
		assertTrue(
				message.contains("Invalid value invalidValue1 - First constraint violation")
						|| message.contains("Invalid value invalidValue2 - Second constraint violation"));
	}

	@Test
	void when_NotImplementedExceptionThrown_Then_ReturnNotImplemented() {
		// Arrange
		NotImplementedException exception = new NotImplementedException("Feature not implemented");

		// Act
		ResponseEntity<ErrorResponse> response =
				globalExceptionHandler.handleNotImplementedException(exception);

		// Assert
		assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
		assertEquals(
				"Feature not implemented", Objects.requireNonNull(response.getBody()).getMessage());
	}

	@Test
	void when_ToolNotFoundExceptionThrown_Then_ReturnNotFound() {
		// Arrange
		ToolNotFoundException exception = new ToolNotFoundException("Tool not found");

		// Act
		ResponseEntity<ErrorResponse> response =
				globalExceptionHandler.handleToolNotFoundException(exception);

		// Assert
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
		assertEquals("Tool not found", Objects.requireNonNull(response.getBody()).getMessage());
	}

	@Test
	void when_ProjectNotFoundExceptionThrown_Then_ReturnNotFound() {
		// Arrange
		ProjectNotFoundException exception = new ProjectNotFoundException("Project not found");

		// Act
		ResponseEntity<ErrorResponse> response =
				globalExceptionHandler.handleProjectNotFound(exception);

		// Assert
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
		assertEquals("Project not found", Objects.requireNonNull(response.getBody()).getMessage());
	}

	@Test
	void when_NotFoundExceptionThrown_Then_ReturnNotFound() {
		// Arrange
		NotFoundException exception = new NotFoundException("Resource not found");

		// Act
		ResponseEntity<ErrorResponse> response =
				globalExceptionHandler.handleNotFoundException(exception);

		// Assert
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
		assertEquals("Resource not found", Objects.requireNonNull(response.getBody()).getMessage());
	}

	@Test
	void when_PendingApprovalExceptionThrown_Then_ReturnAccepted() {
		// Arrange
		PendingApprovalException exception = new PendingApprovalException("Approval pending");

		// Act
		ResponseEntity<ErrorResponse> response =
				globalExceptionHandler.handlePendingApprovalException(exception);

		// Assert
		assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
		assertEquals("Approval pending", Objects.requireNonNull(response.getBody()).getMessage());
	}

	@Test
	void when_InvalidAuthTypeConfigExceptionThrown_Then_ReturnBadRequest() {
		// Arrange
		InvalidAuthTypeConfigException exception =
				new InvalidAuthTypeConfigException("Invalid auth config");

		// Act
		ResponseEntity<ErrorResponse> response =
				globalExceptionHandler.handleInvalidAuthTypeConfigException(exception);

		// Assert
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertEquals("Invalid auth config", Objects.requireNonNull(response.getBody()).getMessage());
	}

	@Test
	void when_PushDataExceptionWithResponseThrown_Then_ReturnBadRequestWithServiceResponse() {
		// Arrange
		PushDataResponse pushResponse = new PushDataResponse();
		PushDataException exception = new PushDataException("Push data error", pushResponse);

		// Act
		ResponseEntity<Object> response = globalExceptionHandler.handlePushDataExceptions(exception);

		// Assert
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertInstanceOf(ServiceResponse.class, response.getBody());
		ServiceResponse serviceResponse = (ServiceResponse) response.getBody();
		assertFalse(serviceResponse.getSuccess());
		assertEquals("Push data error", serviceResponse.getMessage());
	}

	@Test
	void when_PushDataExceptionWithCodeThrown_Then_ReturnWithSpecificCode() {
		// Arrange
		PushDataException exception =
				new PushDataException("Push data error with code", HttpStatus.CONFLICT);

		// Act
		ResponseEntity<Object> response = globalExceptionHandler.handlePushDataExceptions(exception);

		// Assert
		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
		assertInstanceOf(ServiceResponse.class, response.getBody());
		ServiceResponse serviceResponse = (ServiceResponse) response.getBody();
		assertFalse(serviceResponse.getSuccess());
		assertEquals("Push data error with code", serviceResponse.getMessage());
	}

	@Test
	void when_PushDataExceptionWithoutResponseOrCodeThrown_Then_ReturnBadRequestWithErrorMap() {
		// Arrange
		PushDataException exception = new PushDataException("Simple push data error");

		// Act
		ResponseEntity<Object> response = globalExceptionHandler.handlePushDataExceptions(exception);

		// Assert
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertInstanceOf(Map.class, response.getBody());
		@SuppressWarnings("unchecked")
		Map<String, String> errorMap = (Map<String, String>) response.getBody();
		assertEquals("Simple push data error", errorMap.get("error"));
	}

	@Test
	void when_InternalServerErrorExceptionThrown_Then_ReturnBadRequest() {
		// Arrange
		InternalServerErrorException exception =
				new InternalServerErrorException("Internal server error");

		// Act
		ResponseEntity<ErrorResponse> response =
				globalExceptionHandler.handleInternalServerErrorException(exception);

		// Assert
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertEquals("Internal server error", Objects.requireNonNull(response.getBody()).getMessage());
	}

	@Test
	void when_LogAndBuildResponseWithHttpStatusAndExceptionCalled_Then_ReturnErrorResponse() {
		// Arrange
		Exception exception = new Exception("Test exception");

		// Act
		ErrorResponse response =
				ReflectionTestUtils.invokeMethod(
						GlobalExceptionHandler.class, "logAndBuildResponse", HttpStatus.BAD_REQUEST, exception);

		// Assert
		assertNotNull(response);
		assertEquals(400, response.getCode());
		assertEquals("Bad Request", response.getError());
		assertEquals("Test exception", response.getMessage());
	}

	@Test
	void when_LogAndBuildResponseWithCustomMessageCalled_Then_ReturnErrorResponseWithCustomMessage() {
		// Arrange
		Exception exception = new Exception("Original exception");
		String customMessage = "Custom error message";

		// Act
		ErrorResponse response =
				ReflectionTestUtils.invokeMethod(
						GlobalExceptionHandler.class,
						"logAndBuildResponse",
						HttpStatus.INTERNAL_SERVER_ERROR,
						customMessage,
						exception);

		// Assert
		assertNotNull(response);
		assertEquals(500, response.getCode());
		assertEquals("Internal Server Error", response.getError());
		assertEquals(customMessage, response.getMessage());
	}

	@Test
	void when_GetConstraintViolationExceptionDetailsCalled_Then_ReturnFormattedMessage() {
		// Arrange
		when(constraintViolation.getInvalidValue()).thenReturn("invalidValue");
		when(constraintViolation.getMessage()).thenReturn("Constraint violation message");
		Set<ConstraintViolation<Object>> violations = Set.of(constraintViolation);
		ConstraintViolationException exception = new ConstraintViolationException("Test", violations);

		// Act
		String result =
				ReflectionTestUtils.invokeMethod(
						GlobalExceptionHandler.class, "getConstraintViolationExceptionDetails", exception);

		// Assert
		assertNotNull(result);
		assertTrue(result.contains("Invalid value invalidValue - Constraint violation message"));
	}

	@Test
	void
			when_GetConstraintViolationExceptionDetailsWithEmptyViolationsCalled_Then_ReturnEmptyString() {
		// Arrange
		Set<ConstraintViolation<Object>> violations = Set.of();
		ConstraintViolationException exception = new ConstraintViolationException("Test", violations);

		// Act
		String result =
				ReflectionTestUtils.invokeMethod(
						GlobalExceptionHandler.class, "getConstraintViolationExceptionDetails", exception);

		// Assert
		assertNotNull(result);
		assertEquals("", result);
	}

	@Test
	void when_GetMethodArgumentNotValidExceptionDetailsCalled_Then_ReturnJoinedFieldErrors() {
		// Arrange
		MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
		when(fieldError.getDefaultMessage()).thenReturn("Field validation error");
		when(exception.getBindingResult()).thenReturn(bindingResult);
		when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

		// Act
		String result =
				ReflectionTestUtils.invokeMethod(
						GlobalExceptionHandler.class, "getMethodArgumentNotValidExceptionDetails", exception);

		// Assert
		assertNotNull(result);
		assertEquals("Field validation error", result);
	}

	@Test
	void
			when_GetMethodArgumentNotValidExceptionDetailsWithEmptyErrorsCalled_Then_ReturnEmptyString() {
		// Arrange
		MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
		when(exception.getBindingResult()).thenReturn(bindingResult);
		when(bindingResult.getFieldErrors()).thenReturn(List.of());

		// Act
		String result =
				ReflectionTestUtils.invokeMethod(
						GlobalExceptionHandler.class, "getMethodArgumentNotValidExceptionDetails", exception);

		// Assert
		assertNotNull(result);
		assertEquals("", result);
	}

	@Test
	void when_ConstraintViolationExceptionWithNullInvalidValueThrown_Then_HandleGracefully() {
		// Arrange
		ConstraintViolation<Object> violationWithNull = mock(ConstraintViolation.class);
		when(violationWithNull.getInvalidValue()).thenReturn(null);
		when(violationWithNull.getMessage()).thenReturn("Null value violation");

		Set<ConstraintViolation<Object>> violations = Set.of(violationWithNull);
		ConstraintViolationException exception =
				new ConstraintViolationException("Null violation", violations);

		// Act
		ResponseEntity<ErrorResponse> response =
				globalExceptionHandler.handleConstraintViolationException(exception);

		// Assert
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertTrue(
				Objects.requireNonNull(response.getBody())
						.getMessage()
						.contains("Invalid value null - Null value violation"));
	}

	@Test
	void when_ApplicationExceptionWithNullMessageThrown_Then_HandleGracefully() {
		// Arrange
		ApplicationException exception =
				new ApplicationException(null, ApplicationException.DUPLICATE_DATA);

		// Act
		ResponseEntity<ErrorResponse> response =
				globalExceptionHandler.handleSpeedyException(exception);

		// Assert
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertTrue(Objects.requireNonNull(response.getBody()).getMessage().contains("Bad request"));
	}

	@Test
	void when_ExceptionWithNullMessageThrown_Then_HandleGracefully() {
		// Arrange
		Exception exception = new Exception((String) null);

		// Act
		ResponseEntity<ErrorResponse> response =
				globalExceptionHandler.handleGeneralException(exception);

		// Assert
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertEquals(
				"An error occurred. Please try again later.",
				Objects.requireNonNull(response.getBody()).getMessage());
	}

	@Test
	void when_InvalidLevelExceptionThrown_Then_ReturnNotFound() {
		// Arrange
		InvalidLevelException exception = new InvalidLevelException("Invalid level name");
		com.publicissapient.kpidashboard.apis.errors.CustomRestExceptionHandler handler = 
				new com.publicissapient.kpidashboard.apis.errors.CustomRestExceptionHandler();

		// Act - Using reflection to access protected method
		ResponseEntity<Object> response = ReflectionTestUtils.invokeMethod(
				handler, "handleInvalidLevelException", exception);

		// Assert
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

	@Test
	void when_InvalidLevelExceptionWithMissingLevelThrown_Then_ReturnNotFound() {
		// Arrange
		InvalidLevelException exception = new InvalidLevelException("Level is required");
		com.publicissapient.kpidashboard.apis.errors.CustomRestExceptionHandler handler = 
				new com.publicissapient.kpidashboard.apis.errors.CustomRestExceptionHandler();

		// Act - Using reflection to access protected method
		ResponseEntity<Object> response = ReflectionTestUtils.invokeMethod(
				handler, "handleInvalidLevelException", exception);

		// Assert
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}
}
