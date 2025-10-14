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

package com.publicissapient.kpidashboard.apis.errors;

import org.springframework.http.HttpStatus;

/** Exception class for executive dashboard related exceptions. */
public class ExecutiveDataException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	private final HttpStatus status;

	/**
	 * Constructs a new executive data exception with the specified detail message and HTTP status.
	 *
	 * @param message the detail message
	 * @param status the HTTP status code
	 */
	public ExecutiveDataException(String message, HttpStatus status) {
		super(message);
		this.status = status;
	}

	/**
	 * Constructs a new executive data exception with the specified detail message, HTTP status, and
	 * cause.
	 *
	 * @param message the detail message
	 * @param status the HTTP status code
	 * @param cause the cause (which is saved for later retrieval by the getCause() method)
	 */
	public ExecutiveDataException(String message, HttpStatus status, Throwable cause) {
		super(message, cause);
		this.status = status;
	}

	/**
	 * Gets the HTTP status associated with this exception.
	 *
	 * @return the HTTP status
	 */
	public HttpStatus getStatus() {
		return status;
	}

	/**
	 * Constructs a new executive data exception with the specified cause and a detail message of
	 * (cause==null ? null : cause.toString()). Uses HTTP 500 (Internal Server Error) as the status
	 * code.
	 *
	 * @param cause the cause (which is saved for later retrieval by the getCause() method)
	 */
	public ExecutiveDataException(Throwable cause) {
		this(cause != null ? cause.toString() : null, HttpStatus.INTERNAL_SERVER_ERROR, cause);
	}

	/**
	 * Constructs a new executive data exception with the specified detail message. Uses HTTP 500
	 * (Internal Server Error) as the status code.
	 *
	 * @param message the detail message
	 */
	public ExecutiveDataException(String message) {
		this(message, HttpStatus.INTERNAL_SERVER_ERROR);
	}

	/**
	 * Constructs a new executive data exception with the specified detail message and cause. Uses
	 * HTTP 500 (Internal Server Error) as the status code.
	 *
	 * @param message the detail message
	 * @param cause the cause (which is saved for later retrieval by the getCause() method)
	 */
	public ExecutiveDataException(String message, Throwable cause) {
		this(message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
	}
}
