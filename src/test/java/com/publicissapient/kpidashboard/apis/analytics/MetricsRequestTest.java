/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.analytics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test class for MetricsRequest DTO
 * 
 * @author KnowHow Team
 */
@ExtendWith(MockitoExtension.class)
class MetricsRequestTest {

	@Test
	void testGettersAndSetters() {
		// Given
		String expectedMetrics = "# HELP page_views_total Total page views\n" +
				"# TYPE page_views_total counter\n" +
				"page_views_total{page=\"/dashboard\",user_role=\"admin\"} 5";

		// When
		MetricsRequest request = new MetricsRequest();
		request.setMetrics(expectedMetrics);

		// Then
		assertNotNull(request);
		assertEquals(expectedMetrics, request.getMetrics());
	}

	@Test
	void testSetMetricsWithNull() {
		// Given
		MetricsRequest request = new MetricsRequest();

		// When
		request.setMetrics(null);

		// Then
		assertNull(request.getMetrics());
	}

	@Test
	void testSetMetricsWithEmptyString() {
		// Given
		MetricsRequest request = new MetricsRequest();
		String emptyMetrics = "";

		// When
		request.setMetrics(emptyMetrics);

		// Then
		assertEquals("", request.getMetrics());
	}

	@Test
	void testSetMetricsWithMultipleLines() {
		// Given
		MetricsRequest request = new MetricsRequest();
		String multiLineMetrics = "# HELP page_views_total Total page views\n" +
				"# TYPE page_views_total counter\n" +
				"page_views_total{page=\"/dashboard\",user_role=\"admin\"} 5\n" +
				"page_views_total{page=\"/projects\",user_role=\"user\"} 3\n" +
				"# HELP user_sessions_total Total user sessions\n" +
				"# TYPE user_sessions_total counter\n" +
				"user_sessions_total{user_id=\"user123\",login_type=\"standard\"} 1";

		// When
		request.setMetrics(multiLineMetrics);

		// Then
		assertEquals(multiLineMetrics, request.getMetrics());
		assertTrue(request.getMetrics().contains("page_views_total"));
		assertTrue(request.getMetrics().contains("user_sessions_total"));
	}

	@Test
	void testEqualsAndHashCode() {
		// Given
		String metricsData = "page_views_total{page=\"/dashboard\"} 5";
		MetricsRequest request1 = new MetricsRequest();
		MetricsRequest request2 = new MetricsRequest();

		// When
		request1.setMetrics(metricsData);
		request2.setMetrics(metricsData);

		// Then
		assertEquals(request1, request2);
		assertEquals(request1.hashCode(), request2.hashCode());
	}

	@Test
	void testNotEquals() {
		// Given
		MetricsRequest request1 = new MetricsRequest();
		MetricsRequest request2 = new MetricsRequest();

		// When
		request1.setMetrics("page_views_total{page=\"/dashboard\"} 5");
		request2.setMetrics("page_views_total{page=\"/projects\"} 3");

		// Then
		assertNotEquals(request1, request2);
	}

	@Test
	void testToString() {
		// Given
		String metricsData = "page_views_total{page=\"/dashboard\"} 5";
		MetricsRequest request = new MetricsRequest();
		request.setMetrics(metricsData);

		// When
		String toString = request.toString();

		// Then
		assertNotNull(toString);
		assertTrue(toString.contains("MetricsRequest"));
		assertTrue(toString.contains(metricsData));
	}

	@Test
	void testDefaultConstructor() {
		// When
		MetricsRequest request = new MetricsRequest();

		// Then
		assertNotNull(request);
		assertNull(request.getMetrics());
	}

	@Test
	void testSetMetricsWithSpecialCharacters() {
		// Given
		MetricsRequest request = new MetricsRequest();
		String metricsWithSpecialChars = "page_views_total{page=\"/dashboard?filter=test&sort=asc\",user_role=\"admin\"} 5";

		// When
		request.setMetrics(metricsWithSpecialChars);

		// Then
		assertEquals(metricsWithSpecialChars, request.getMetrics());
	}

	@Test
	void testSetMetricsWithLargeData() {
		// Given
		MetricsRequest request = new MetricsRequest();
		StringBuilder largeMetrics = new StringBuilder();
		for (int i = 0; i < 100; i++) {
			largeMetrics.append("page_views_total{page=\"/page").append(i).append("\"} ").append(i).append("\n");
		}

		// When
		request.setMetrics(largeMetrics.toString());

		// Then
		assertNotNull(request.getMetrics());
		assertEquals(largeMetrics.toString(), request.getMetrics());
		assertTrue(request.getMetrics().length() > 1000);
	}

	@Test
	void testSetMetricsOverwrite() {
		// Given
		MetricsRequest request = new MetricsRequest();
		String firstMetrics = "page_views_total{page=\"/dashboard\"} 5";
		String secondMetrics = "user_sessions_total{user_id=\"user123\"} 1";

		// When
		request.setMetrics(firstMetrics);
		assertEquals(firstMetrics, request.getMetrics());

		request.setMetrics(secondMetrics);

		// Then
		assertEquals(secondMetrics, request.getMetrics());
		assertNotEquals(firstMetrics, request.getMetrics());
	}
}
