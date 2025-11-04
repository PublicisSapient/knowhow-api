/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

package com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.dto.SprintDataPoint;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.model.SprintMetricContext;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;

@RunWith(MockitoJUnitRunner.class)
public class SprintAnalyticsUtilTest {

	@Test
	public void testRoundingOff_WithDecimals() {
		double result = SprintAnalyticsUtil.roundingOff(12.3456);
		assertEquals(12.35, result, 0.001);
	}

	@Test
	public void testRoundingOff_WithOneDecimal() {
		double result = SprintAnalyticsUtil.roundingOff(12.3);
		assertEquals(12.3, result, 0.001);
	}

	@Test
	public void testRoundingOff_WithNoDecimals() {
		double result = SprintAnalyticsUtil.roundingOff(12.0);
		assertEquals(12.0, result, 0.001);
	}

	@Test
	public void testRoundingOff_RoundsUp() {
		double result = SprintAnalyticsUtil.roundingOff(12.567);
		assertEquals(12.57, result, 0.001);
	}

	@Test
	public void testRoundingOff_RoundsDown() {
		double result = SprintAnalyticsUtil.roundingOff(12.562);
		assertEquals(12.56, result, 0.001);
	}

	@Test
	public void testRoundingOff_WithZero() {
		double result = SprintAnalyticsUtil.roundingOff(0.0);
		assertEquals(0.0, result, 0.001);
	}

	@Test
	public void testRoundingOff_WithNegativeNumber() {
		double result = SprintAnalyticsUtil.roundingOff(-12.567);
		assertEquals(-12.57, result, 0.001);
	}

	@Test
	public void testCreateDataPoint_WithValidData() {
		SprintDetails sprintDetails = new SprintDetails();
		sprintDetails.setSprintName("Sprint 1");

		SprintDataPoint dataPoint = SprintAnalyticsUtil.createDataPoint(sprintDetails, 25.5, 10.2, 0);

		assertNotNull(dataPoint);
		assertEquals("Sprint 1", dataPoint.getSprint());
		assertEquals("Sprint 1", dataPoint.getName());
		assertEquals("25.5", dataPoint.getValue());
		assertEquals("10.2", dataPoint.getTrend());
	}

	@Test
	public void testCreateDataPoint_WithIntegerValues() {
		SprintDetails sprintDetails = new SprintDetails();
		sprintDetails.setSprintName("Sprint 2");

		SprintDataPoint dataPoint = SprintAnalyticsUtil.createDataPoint(sprintDetails, 5, 3, 1);

		assertNotNull(dataPoint);
		assertEquals("Sprint 2", dataPoint.getSprint());
		assertEquals("Sprint 2", dataPoint.getName());
		assertEquals("5", dataPoint.getValue());
		assertEquals("3", dataPoint.getTrend());
	}

	@Test
	public void testCreateDataPoint_WithZeroValues() {
		SprintDetails sprintDetails = new SprintDetails();
		sprintDetails.setSprintName("Sprint Zero");

		SprintDataPoint dataPoint = SprintAnalyticsUtil.createDataPoint(sprintDetails, 0, 0, 5);

		assertNotNull(dataPoint);
		assertEquals("Sprint 6", dataPoint.getSprint());
		assertEquals("Sprint Zero", dataPoint.getName());
		assertEquals("0", dataPoint.getValue());
		assertEquals("0", dataPoint.getTrend());
	}

	@Test
	public void testCreateNADataPoint_WithReason() {
		SprintDetails sprintDetails = new SprintDetails();
		sprintDetails.setSprintName("Test Sprint");

		SprintMetricContext context = SprintMetricContext.builder()
			.basicProjectConfigId(new org.bson.types.ObjectId("507f1f77bcf86cd799439011"))
			.build();

		SprintDataPoint dataPoint = SprintAnalyticsUtil.createNADataPoint(sprintDetails, "No data available", 2, context);

		assertNotNull(dataPoint);
		assertEquals("Sprint 3", dataPoint.getSprint());
		assertEquals("Test Sprint", dataPoint.getName());
		assertEquals(Constant.NOT_AVAILABLE, dataPoint.getValue());
		assertEquals(Constant.NOT_AVAILABLE, dataPoint.getTrend());
	}

	@Test
	public void testCreateNADataPoint_WithEmptyReason() {
		SprintDetails sprintDetails = new SprintDetails();
		sprintDetails.setSprintName("Empty Reason Sprint");

		SprintMetricContext context = SprintMetricContext.builder()
			.basicProjectConfigId(new org.bson.types.ObjectId("507f1f77bcf86cd799439011"))
			.build();

		SprintDataPoint dataPoint = SprintAnalyticsUtil.createNADataPoint(sprintDetails, "", 0, context);

		assertNotNull(dataPoint);
		assertEquals(Constant.NOT_AVAILABLE, dataPoint.getValue());
		assertEquals(Constant.NOT_AVAILABLE, dataPoint.getTrend());
	}

	@Test
	public void testNormalizeSprintId_FirstSprint() {
		String normalized = SprintAnalyticsUtil.normalizeSprintId(0);
		assertEquals("Sprint 1", normalized);
	}

	@Test
	public void testNormalizeSprintId_SecondSprint() {
		String normalized = SprintAnalyticsUtil.normalizeSprintId(1);
		assertEquals("Sprint 2", normalized);
	}

	@Test
	public void testNormalizeSprintId_TenthSprint() {
		String normalized = SprintAnalyticsUtil.normalizeSprintId(9);
		assertEquals("Sprint 10", normalized);
	}

	@Test
	public void testCalculatePercentage_ValidValues() {
		double percentage = SprintAnalyticsUtil.calculatePercentage(25, 100);
		assertEquals(25.0, percentage, 0.001);
	}

	@Test
	public void testCalculatePercentage_ZeroDenominator() {
		double percentage = SprintAnalyticsUtil.calculatePercentage(10, 0);
		assertEquals(0.0, percentage, 0.001);
	}

	@Test
	public void testCalculatePercentage_ZeroNumerator() {
		double percentage = SprintAnalyticsUtil.calculatePercentage(0, 100);
		assertEquals(0.0, percentage, 0.001);
	}

	@Test
	public void testCalculatePercentage_BothZero() {
		double percentage = SprintAnalyticsUtil.calculatePercentage(0, 0);
		assertEquals(0.0, percentage, 0.001);
	}

	@Test
	public void testCalculatePercentage_WithRounding() {
		double percentage = SprintAnalyticsUtil.calculatePercentage(33, 100);
		assertEquals(33.0, percentage, 0.001);
	}

	@Test
	public void testCalculatePercentage_ComplexRounding() {
		double percentage = SprintAnalyticsUtil.calculatePercentage(2, 3);
		assertEquals(66.67, percentage, 0.001);
	}

	@Test
	public void testCalculatePercentage_HigherNumerator() {
		double percentage = SprintAnalyticsUtil.calculatePercentage(150, 100);
		assertEquals(150.0, percentage, 0.001);
	}

	@Test
	public void testCalculatePercentage_SmallValues() {
		double percentage = SprintAnalyticsUtil.calculatePercentage(1, 1000);
		assertEquals(0.1, percentage, 0.001);
	}

	@Test
	public void testIsValidSprintDetails_WithValidSprint() {
		SprintDetails sprintDetails = new SprintDetails();
		sprintDetails.setSprintName("Test Sprint");

		boolean isValid = SprintAnalyticsUtil.isValidSprintDetails(sprintDetails);
		assertTrue(isValid);
	}

	@Test
	public void testIsValidSprintDetails_WithNullSprint() {
		boolean isValid = SprintAnalyticsUtil.isValidSprintDetails(null);
		assertFalse(isValid);
	}

	@Test
	public void testIsValidFieldMapping_WithValidMapping() {
		FieldMapping fieldMapping = new FieldMapping();

		boolean isValid = SprintAnalyticsUtil.isValidFieldMapping(fieldMapping, "Test Project");
		assertTrue(isValid);
	}

	@Test
	public void testIsValidFieldMapping_WithNullMapping() {
		boolean isValid = SprintAnalyticsUtil.isValidFieldMapping(null, "Test Project");
		assertFalse(isValid);
	}

	@Test
	public void testIsValidFieldMapping_WithEmptyProjectName() {
		FieldMapping fieldMapping = new FieldMapping();

		boolean isValid = SprintAnalyticsUtil.isValidFieldMapping(fieldMapping, "");
		assertTrue(isValid);
	}

	@Test
	public void testIsValidFieldMapping_WithNullProjectName() {
		FieldMapping fieldMapping = new FieldMapping();

		boolean isValid = SprintAnalyticsUtil.isValidFieldMapping(fieldMapping, null);
		assertTrue(isValid);
	}

	@Test
	public void testIsValidFieldMapping_WithNullBoth() {
		boolean isValid = SprintAnalyticsUtil.isValidFieldMapping(null, null);
		assertFalse(isValid);
	}
}
