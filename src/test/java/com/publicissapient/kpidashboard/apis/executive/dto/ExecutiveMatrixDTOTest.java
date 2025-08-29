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

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ExecutiveMatrixDTOTest {

	@Test
	public void testBuilder() {
		List<ProjectMetricsDTO> rows = Arrays.asList(ProjectMetricsDTO.builder().build());
		List<ColumnDefinitionDTO> columns = Arrays.asList(ColumnDefinitionDTO.builder().build());

		ExecutiveMatrixDTO matrix = ExecutiveMatrixDTO.builder().rows(rows).columns(columns).build();

		assertNotNull(matrix);
		assertEquals(1, matrix.getRows().size());
		assertEquals(1, matrix.getColumns().size());
	}

	@Test
	public void testNoArgsConstructor() {
		ExecutiveMatrixDTO matrix = ExecutiveMatrixDTO.builder().build();
		assertNull(matrix.getRows());
		assertNull(matrix.getColumns());
	}

	@Test
	public void testAllArgsConstructor() {
		List<ProjectMetricsDTO> rows = Arrays.asList(ProjectMetricsDTO.builder().build());
		List<ColumnDefinitionDTO> columns = Arrays.asList(ColumnDefinitionDTO.builder().build());

		ExecutiveMatrixDTO matrix = new ExecutiveMatrixDTO(rows, columns);

		assertNotNull(matrix);
		assertEquals(rows, matrix.getRows());
		assertEquals(columns, matrix.getColumns());
	}

	@Test
	public void testEqualsAndHashCode() {
		List<ProjectMetricsDTO> rows = Arrays.asList(ProjectMetricsDTO.builder().build());
		List<ColumnDefinitionDTO> columns = Arrays.asList(ColumnDefinitionDTO.builder().build());

		ExecutiveMatrixDTO matrix1 = new ExecutiveMatrixDTO(rows, columns);
		ExecutiveMatrixDTO matrix2 = new ExecutiveMatrixDTO(rows, columns);

		assertEquals(matrix1, matrix2);
		assertEquals(matrix1.hashCode(), matrix2.hashCode());
	}

	@Test
	public void testToString() {
		ExecutiveMatrixDTO matrix = ExecutiveMatrixDTO.builder().build();
		String expected = "ExecutiveMatrixDTO(rows=" + matrix.getRows() + ", columns=" + matrix.getColumns() + ")";
		assertEquals(expected, matrix.toString());
	}
}
