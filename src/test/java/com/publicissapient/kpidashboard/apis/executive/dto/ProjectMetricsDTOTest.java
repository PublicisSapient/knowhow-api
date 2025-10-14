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
public class ProjectMetricsDTOTest {

	@Test
	public void testBuilder() {
		ProjectMetricsDTO metrics =
				ProjectMetricsDTO.builder()
						.id("proj123")
						.name("Sample Project")
						.completion("75%")
						.health("Healthy")
						.build();

		assertNotNull(metrics);
		assertEquals("proj123", metrics.getId());
		assertEquals("Sample Project", metrics.getName());
		assertEquals("75%", metrics.getCompletion());
		assertEquals("Healthy", metrics.getHealth());
	}

	@Test
	public void testNoArgsConstructor() {
		ProjectMetricsDTO metrics = ProjectMetricsDTO.builder().build();
		assertNull(metrics.getId());
		assertNull(metrics.getName());
		assertNull(metrics.getCompletion());
		assertNull(metrics.getHealth());
	}

	@Test
	public void testAllArgsConstructor() {
		ProjectMetricsDTO metrics =
				new ProjectMetricsDTO("proj123", "Sample Project", "75%", "Healthy", null);

		assertNotNull(metrics);
		assertEquals("proj123", metrics.getId());
		assertEquals("Sample Project", metrics.getName());
		assertEquals("75%", metrics.getCompletion());
		assertEquals("Healthy", metrics.getHealth());
	}

	@Test
	public void testEqualsAndHashCode() {
		ProjectMetricsDTO metrics1 =
				new ProjectMetricsDTO("proj123", "Sample Project", "75%", "Healthy", null);
		ProjectMetricsDTO metrics2 =
				new ProjectMetricsDTO("proj123", "Sample Project", "75%", "Healthy", null);

		assertEquals(metrics1, metrics2);
		assertEquals(metrics1.hashCode(), metrics2.hashCode());
	}

	@Test
	public void testNotEquals() {
		ProjectMetricsDTO metrics1 =
				new ProjectMetricsDTO("proj123", "Sample Project", "75%", "Healthy", null);
		ProjectMetricsDTO metrics2 =
				new ProjectMetricsDTO("proj124", "Another Project", "50%", "At Risk", null);

		assertNotEquals(metrics1, metrics2);
		assertNotEquals(metrics1.hashCode(), metrics2.hashCode());
	}

	@Test
	public void testToString() {
		ProjectMetricsDTO metrics =
				new ProjectMetricsDTO("proj123", "Sample Project", "75%", "Healthy", null);
		String expected =
				"ProjectMetricsDTO(id=proj123, name=Sample Project, completion=75%, health=Healthy, boardMaturity=null)";
		assertEquals(expected, metrics.toString());
	}
}
