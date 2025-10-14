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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ExecutiveDashboardResponseDTOTest {

	@Test
	public void testBuilder() {
		ExecutiveDashboardDataDTO data = ExecutiveDashboardDataDTO.builder().build();
		ExecutiveDashboardResponseDTO response =
				ExecutiveDashboardResponseDTO.builder().data(data).build();

		assertNotNull(response);
		assertNotNull(response.getData());
	}

	@Test
	public void testNoArgsConstructor() {
		ExecutiveDashboardResponseDTO response = ExecutiveDashboardResponseDTO.builder().build();
		assertNull(response.getData());
	}

	@Test
	public void testAllArgsConstructor() {
		ExecutiveDashboardDataDTO data = ExecutiveDashboardDataDTO.builder().build();
		ExecutiveDashboardResponseDTO response = new ExecutiveDashboardResponseDTO(data);

		assertNotNull(response);
		assertEquals(data, response.getData());
	}

	@Test
	public void testEqualsAndHashCode() {
		ExecutiveDashboardDataDTO data1 = ExecutiveDashboardDataDTO.builder().build();
		ExecutiveDashboardResponseDTO response1 = new ExecutiveDashboardResponseDTO(data1);
		ExecutiveDashboardResponseDTO response2 = new ExecutiveDashboardResponseDTO(data1);

		assertEquals(response1, response2);
		assertEquals(response1.hashCode(), response2.hashCode());
	}

	@Test
	public void testToString() {
		ExecutiveDashboardDataDTO data = ExecutiveDashboardDataDTO.builder().build();
		ExecutiveDashboardResponseDTO response = new ExecutiveDashboardResponseDTO(data);

		String expected = "ExecutiveDashboardResponseDTO(data=" + data + ")";
		assertEquals(expected, response.toString());
	}
}
