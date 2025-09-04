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
import static org.testng.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BoardMaturityDTOTest {

	@Test
	public void testBuilder() {
		Map<String, String> metrics = new HashMap<>();
		metrics.put("speed", "M2");
		metrics.put("quality", "M3");

		BoardMaturityDTO maturity = BoardMaturityDTO.builder().metrics(metrics).build();

		assertNotNull(maturity);
		assertEquals(2, maturity.getMetrics().size());
		assertEquals("M2", maturity.getMetrics().get("speed"));
		assertEquals("M3", maturity.getMetrics().get("quality"));
	}

	@Test
	public void testNoArgsConstructor() {
		BoardMaturityDTO maturity = BoardMaturityDTO.builder().build();
		assertNotNull(maturity.getMetrics());
		assertTrue(maturity.getMetrics().isEmpty());
	}

	@Test
	public void testAddMetric() {
		BoardMaturityDTO maturity = BoardMaturityDTO.builder().build();
		maturity.addMetric("speed", "M2");

		assertEquals(1, maturity.getMetrics().size());
		assertEquals("M2", maturity.getMetrics().get("speed"));
	}

	@Test
	public void testEqualsAndHashCode() {
		BoardMaturityDTO maturity1 = BoardMaturityDTO.builder().build();
		maturity1.addMetric("speed", "M2");

		BoardMaturityDTO maturity2 = BoardMaturityDTO.builder().build();
		maturity2.addMetric("speed", "M2");

		assertEquals(maturity1, maturity2);
		assertEquals(maturity1.hashCode(), maturity2.hashCode());
	}

	@Test
	public void testToString() {
		BoardMaturityDTO maturity = BoardMaturityDTO.builder().build();
		maturity.addMetric("speed", "M2");

		String expected = "BoardMaturityDTO(metrics={speed=M2})";
		assertEquals(expected, maturity.toString());
	}
}
