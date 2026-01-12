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

package com.publicissapient.kpidashboard.apis.errors;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Custom error controller to handle servlet container level errors.
 * 
 * <p>This controller implements Spring Boot's ErrorController interface to intercept
 * errors that occur at the servlet container level, before they reach Spring's
 * exception handling mechanism. It specifically handles RequestDispatcher errors
 * that occur when invalid URLs are accessed, converting them to proper 404 responses
 * instead of 500 internal server errors.
 * 
 * <p>The controller handles three main scenarios:
 * <ul>
 *   <li>RequestDispatcher errors (invalid URLs) → 404 Not Found</li>
 *   <li>Other HTTP error status codes → Appropriate HTTP status</li>
 *   <li>Unknown errors → 500 Internal Server Error</li>
 * </ul>
 */
@RestController
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<Object> handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

        // Check if this is a RequestDispatcher error
        if (exception instanceof Exception ex && ex.getMessage() != null &&
                ex.getMessage().contains("RequestDispatcher")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(System.currentTimeMillis(), 404, "Not Found",
                            (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)));
        }

        // Handle other error status codes
        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());
            HttpStatus httpStatus = HttpStatus.valueOf(statusCode);
            return ResponseEntity.status(httpStatus)
                    .body(new ErrorResponse(System.currentTimeMillis(), statusCode, httpStatus.getReasonPhrase(),
                            (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)));
        }

        // Default fallback
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(System.currentTimeMillis(), 500, "Internal Server Error",
                        (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)));
    }

    public static class ErrorResponse {
        public long timestamp;
        public int status;
        public String error;
        public String path;

        public ErrorResponse(long timestamp, int status, String error, String path) {
            this.timestamp = timestamp;
            this.status = status;
            this.error = error;
            this.path = path;
        }
    }
}
