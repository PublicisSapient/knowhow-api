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

package com.publicissapient.kpidashboard.apis.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.enums.ForecastingModel;
import com.publicissapient.kpidashboard.common.model.application.DataCount;

/** Test class for AbstractForecastService. */
@RunWith(MockitoJUnitRunner.class)
public class AbstractForecastServiceTest {

	private TestForecastService testForecastService;

	/** Concrete implementation for testing abstract class */
	private static class TestForecastService extends AbstractForecastService {
		@Override
		public List<DataCount> generateForecast(List<DataCount> historicalData, String kpiId) {
			return new ArrayList<>();
		}

		@Override
		public ForecastingModel getModelType() {
			return ForecastingModel.LINEAR_REGRESSION;
		}
	}

	@Before
	public void setUp() {
		testForecastService = new TestForecastService();
	}

	@Test
	public void testExtractValues_WithValidNumericData() {
		// Arrange
		List<DataCount> dataCounts = new ArrayList<>();
		DataCount dc1 = new DataCount();
		dc1.setValue(10.5);
		DataCount dc2 = new DataCount();
		dc2.setValue(20);
		DataCount dc3 = new DataCount();
		dc3.setValue(30.75);
		dataCounts.add(dc1);
		dataCounts.add(dc2);
		dataCounts.add(dc3);

		// Act
		List<Double> result = testForecastService.extractValues(dataCounts);

		// Assert
		assertNotNull(result);
		assertEquals(3, result.size());
		assertEquals(10.5, result.get(0), 0.01);
		assertEquals(20.0, result.get(1), 0.01);
		assertEquals(30.75, result.get(2), 0.01);
	}

	@Test
	public void testExtractValues_WithStringValues() {
		// Arrange
		List<DataCount> dataCounts = new ArrayList<>();
		DataCount dc1 = new DataCount();
		dc1.setValue("15.5");
		DataCount dc2 = new DataCount();
		dc2.setValue("25");
		dataCounts.add(dc1);
		dataCounts.add(dc2);

		// Act
		List<Double> result = testForecastService.extractValues(dataCounts);

		// Assert
		assertNotNull(result);
		assertEquals(2, result.size());
		assertEquals(15.5, result.get(0), 0.01);
		assertEquals(25.0, result.get(1), 0.01);
	}

	@Test
	public void testExtractValues_WithInvalidValues() {
		// Arrange
		List<DataCount> dataCounts = new ArrayList<>();
		DataCount dc1 = new DataCount();
		dc1.setValue(null);
		DataCount dc2 = new DataCount();
		dc2.setValue("invalid");
		DataCount dc3 = new DataCount();
		dc3.setValue(Double.NaN);
		DataCount dc4 = new DataCount();
		dc4.setValue(Double.POSITIVE_INFINITY);
		dataCounts.add(dc1);
		dataCounts.add(dc2);
		dataCounts.add(dc3);
		dataCounts.add(dc4);

		// Act
		List<Double> result = testForecastService.extractValues(dataCounts);

		// Assert
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	public void testExtractValues_WithEmptyList() {
		// Arrange
		List<DataCount> dataCounts = new ArrayList<>();

		// Act
		List<Double> result = testForecastService.extractValues(dataCounts);

		// Assert
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	public void testExtractValues_WithNullList() {
		// Act
		List<Double> result = testForecastService.extractValues(null);

		// Assert
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	public void testExtractNumericValue_WithNumberValue() {
		// Arrange
		DataCount dataCount = new DataCount();
		dataCount.setValue(42);

		// Act
		Double result = testForecastService.extractNumericValue(dataCount);

		// Assert
		assertNotNull(result);
		assertEquals(42.0, result, 0.01);
	}

	@Test
	public void testExtractNumericValue_WithDoubleValue() {
		// Arrange
		DataCount dataCount = new DataCount();
		dataCount.setValue(3.14159);

		// Act
		Double result = testForecastService.extractNumericValue(dataCount);

		// Assert
		assertNotNull(result);
		assertEquals(3.14159, result, 0.00001);
	}

	@Test
	public void testExtractNumericValue_WithValidStringValue() {
		// Arrange
		DataCount dataCount = new DataCount();
		dataCount.setValue("99.99");

		// Act
		Double result = testForecastService.extractNumericValue(dataCount);

		// Assert
		assertNotNull(result);
		assertEquals(99.99, result, 0.01);
	}

	@Test
	public void testExtractNumericValue_WithInvalidString() {
		// Arrange
		DataCount dataCount = new DataCount();
		dataCount.setValue("not a number");

		// Act
		Double result = testForecastService.extractNumericValue(dataCount);

		// Assert
		assertNull(result);
	}

	@Test
	public void testExtractNumericValue_WithNullDataCount() {
		// Act
		Double result = testForecastService.extractNumericValue(null);

		// Assert
		assertNull(result);
	}

	@Test
	public void testExtractNumericValue_WithNullValue() {
		// Arrange
		DataCount dataCount = new DataCount();
		dataCount.setValue(null);

		// Act
		Double result = testForecastService.extractNumericValue(dataCount);

		// Assert
		assertNull(result);
	}

	@Test
	public void testCanForecast_WithSufficientData() {
		// Arrange
		List<DataCount> dataCounts = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			DataCount dc = new DataCount();
			dc.setValue(i * 10.0);
			dataCounts.add(dc);
		}

		// Act
		boolean result = testForecastService.canForecast(dataCounts, "kpi123");

		// Assert
		assertTrue(result);
	}

	@Test
	public void testCanForecast_WithMinimumData() {
		// Arrange
		List<DataCount> dataCounts = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			DataCount dc = new DataCount();
			dc.setValue(i * 10.0);
			dataCounts.add(dc);
		}

		// Act
		boolean result = testForecastService.canForecast(dataCounts, "kpi123");

		// Assert
		assertTrue(result);
	}

	@Test
	public void testCanForecast_WithInsufficientData() {
		// Arrange
		List<DataCount> dataCounts = new ArrayList<>();
		DataCount dc1 = new DataCount();
		dc1.setValue(10.0);
		DataCount dc2 = new DataCount();
		dc2.setValue(20.0);
		dataCounts.add(dc1);
		dataCounts.add(dc2);

		// Act
		boolean result = testForecastService.canForecast(dataCounts, "kpi123");

		// Assert
		assertTrue(result);
	}

	@Test
	public void testCanForecast_WithEmptyData() {
		// Arrange
		List<DataCount> dataCounts = new ArrayList<>();

		// Act
		boolean result = testForecastService.canForecast(dataCounts, "kpi123");

		// Assert
		assertFalse(result);
	}

	@Test
	public void testCanForecast_WithNullData() {
		// Act
		boolean result = testForecastService.canForecast(null, "kpi123");

		// Assert
		assertFalse(result);
	}

	@Test
	public void testCreateForecastDataCount() {
		// Arrange
		Double forecastValue = 42.567;
		String projectName = "TestProject";
		String kpiGroup = "TestGroup";

		// Act
		DataCount result =
				testForecastService.createForecastDataCount(forecastValue, projectName, kpiGroup, "");

		// Assert
		assertNotNull(result);
		assertEquals("42.57", result.getData());
		assertEquals(42.57, (Double) result.getValue(), 0.01);
		assertEquals(projectName, result.getSProjectName());
		assertEquals(kpiGroup, result.getKpiGroup());
	}

	@Test
	public void testCreateForecastDataCount_WithZeroValue() {
		// Arrange
		Double forecastValue = 0.0;
		String projectName = "TestProject";
		String kpiGroup = "TestGroup";

		// Act
		DataCount result =
				testForecastService.createForecastDataCount(forecastValue, projectName, kpiGroup, "");

		// Assert
		assertNotNull(result);
		assertEquals("0.0", result.getData());
		assertEquals(0.0, (Double) result.getValue(), 0.01);
	}

	@Test
	public void testCreateForecastDataCount_WithLargeValue() {
		// Arrange
		Double forecastValue = 9999.999;
		String projectName = "TestProject";
		String kpiGroup = "TestGroup";

		// Act
		DataCount result =
				testForecastService.createForecastDataCount(forecastValue, projectName, kpiGroup, "");

		// Assert
		assertNotNull(result);
		assertEquals(10000.0, (Double) result.getValue(), 0.01);
	}
}
