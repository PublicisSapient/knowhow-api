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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

class CustomErrorControllerTest {

    private final CustomErrorController controller = new CustomErrorController();

    @Test
    void testHandleError_RequestDispatcherError_Returns404() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Exception exception = new IllegalStateException("RequestDispatcher could not be located");
        
        when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(exception);
        when(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).thenReturn("/invalid-url");

        ResponseEntity<Object> response = controller.handleError(request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testHandleError_404Status_Returns404() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        
        when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);
        when(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).thenReturn("/not-found");

        ResponseEntity<Object> response = controller.handleError(request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}