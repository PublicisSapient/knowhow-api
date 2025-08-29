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

package com.publicissapient.kpidashboard.apis.executive.dto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

@RunWith(MockitoJUnitRunner.class)
public class ExecutiveDashboardRequestDTOTest {

	private Validator validator;

	@Before
	public void setUp() {
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@Test
	public void testBuilder() {
		ExecutiveDashboardRequestDTO request = ExecutiveDashboardRequestDTO.builder().level(1).label("account")
				.date("Weeks").duration(4).build();

		assertNotNull(request);
		assertEquals("account", request.getLabel());
		assertEquals("Weeks", request.getDate());
	}

	@Test
	public void testNoArgsConstructor() {
		ExecutiveDashboardRequestDTO request = ExecutiveDashboardRequestDTO.builder().build();
		assertNull(request.getLevel());
		assertNull(request.getLabel());
		assertNull(request.getDate());
		assertNull(request.getDuration());
	}

	@Test
	public void testAllArgsConstructor() {
		ExecutiveDashboardRequestDTO request = new ExecutiveDashboardRequestDTO(1, "account", "Weeks", 4, null);

		assertNotNull(request);
		assertEquals("account", request.getLabel());
		assertEquals("Weeks", request.getDate());
	}

	@Test
	public void testValidation_ValidRequest() {
		ExecutiveDashboardRequestDTO request = new ExecutiveDashboardRequestDTO(1, "account", "Weeks", 4, null);
		var violations = validator.validate(request);
		assertTrue(violations.isEmpty());
	}

	@Test
	public void testEqualsAndHashCode() {
		ExecutiveDashboardRequestDTO request1 = new ExecutiveDashboardRequestDTO(1, "account", "Weeks", 4, null);
		ExecutiveDashboardRequestDTO request2 = new ExecutiveDashboardRequestDTO(1, "account", "Weeks", 4, null);

		assertEquals(request1, request2);
		assertEquals(request1.hashCode(), request2.hashCode());
	}

	@Test
	public void testNotEquals() {
		ExecutiveDashboardRequestDTO request1 = new ExecutiveDashboardRequestDTO(1, "account", "Weeks", 4, null);
		ExecutiveDashboardRequestDTO request2 = new ExecutiveDashboardRequestDTO(2, "project", "Months", 3, null);

		assertNotEquals(request1, request2);
		assertNotEquals(request1.hashCode(), request2.hashCode());
	}

	@Test
	public void testToString() {
		ExecutiveDashboardRequestDTO request = new ExecutiveDashboardRequestDTO(1, "account", "Weeks", 4, "abc");
		String expected = "ExecutiveDashboardRequestDTO(level=1, label=account, date=Weeks, duration=4, parentId=abc)";
		assertEquals(expected, request.toString());
	}
}
