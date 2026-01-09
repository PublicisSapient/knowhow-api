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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class InvalidLevelExceptionTest {

    @Test
    void testInvalidLevelException_WithMessage() {
        String message = "Invalid level name: invalid";
        InvalidLevelException exception = new InvalidLevelException(message);

        assertEquals(message, exception.getMessage());
        assertNotNull(exception);
    }

    @Test
    void testInvalidLevelException_WithMessageAndCause() {
        String message = "Level is required";
        Throwable cause = new RuntimeException("Root cause");
        InvalidLevelException exception = new InvalidLevelException(message, cause);

        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }
}