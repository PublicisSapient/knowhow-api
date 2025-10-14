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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ColumnDefinitionDTOTest {

	@Test
	public void testBuilder() {
		ColumnDefinitionDTO column =
				ColumnDefinitionDTO.builder().field("projectName").header("Project Name").build();

		assertNotNull(column);
		assertEquals("projectName", column.getField());
		assertEquals("Project Name", column.getHeader());
	}

	@Test
	public void testNoArgsConstructor() {
		ColumnDefinitionDTO column = ColumnDefinitionDTO.builder().build();
		assertNull(column.getField());
		assertNull(column.getHeader());
	}

	@Test
	public void testAllArgsConstructor() {
		ColumnDefinitionDTO column = new ColumnDefinitionDTO("projectName", "Project Name");

		assertNotNull(column);
		assertEquals("projectName", column.getField());
		assertEquals("Project Name", column.getHeader());
	}

	@Test
	public void testEqualsAndHashCode() {
		ColumnDefinitionDTO column1 = new ColumnDefinitionDTO("projectName", "Project Name");
		ColumnDefinitionDTO column2 = new ColumnDefinitionDTO("projectName", "Project Name");

		assertEquals(column1, column2);
		assertEquals(column1.hashCode(), column2.hashCode());
	}

	@Test
	public void testNotEquals() {
		ColumnDefinitionDTO column1 = new ColumnDefinitionDTO("projectName", "Project Name");
		ColumnDefinitionDTO column2 = new ColumnDefinitionDTO("completion", "Completion %");

		assertNotEquals(column1, column2);
		assertNotEquals(column1.hashCode(), column2.hashCode());
	}

	@Test
	public void testToString() {
		ColumnDefinitionDTO column = new ColumnDefinitionDTO("projectName", "Project Name");
		String expected = "ColumnDefinitionDTO(field=projectName, header=Project Name)";
		assertEquals(expected, column.toString());
	}
}
