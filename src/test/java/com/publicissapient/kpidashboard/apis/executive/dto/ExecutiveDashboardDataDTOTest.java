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
public class ExecutiveDashboardDataDTOTest {

	@Test
	public void testBuilder() {
		ExecutiveMatrixDTO matrix = ExecutiveMatrixDTO.builder().build();
		ExecutiveDashboardDataDTO data = ExecutiveDashboardDataDTO.builder().matrix(matrix).build();

		assertNotNull(data);
		assertEquals(matrix, data.getMatrix());
	}

	@Test
	public void testNoArgsConstructor() {
		ExecutiveDashboardDataDTO data = ExecutiveDashboardDataDTO.builder().build();
		assertNull(data.getMatrix());
	}

	@Test
	public void testAllArgsConstructor() {
		ExecutiveMatrixDTO matrix = ExecutiveMatrixDTO.builder().build();
		ExecutiveDashboardDataDTO data = new ExecutiveDashboardDataDTO(matrix);

		assertNotNull(data);
		assertEquals(matrix, data.getMatrix());
	}

	@Test
	public void testEqualsAndHashCode() {
		ExecutiveMatrixDTO matrix = ExecutiveMatrixDTO.builder().build();
		ExecutiveDashboardDataDTO data1 = new ExecutiveDashboardDataDTO(matrix);
		ExecutiveDashboardDataDTO data2 = new ExecutiveDashboardDataDTO(matrix);

		assertEquals(data1, data2);
		assertEquals(data1.hashCode(), data2.hashCode());
	}

	@Test
	public void testToString() {
		ExecutiveMatrixDTO matrix = ExecutiveMatrixDTO.builder().build();
		ExecutiveDashboardDataDTO data = new ExecutiveDashboardDataDTO(matrix);

		String expected = "ExecutiveDashboardDataDTO(matrix=" + matrix + ")";
		assertEquals(expected, data.toString());
	}
}
