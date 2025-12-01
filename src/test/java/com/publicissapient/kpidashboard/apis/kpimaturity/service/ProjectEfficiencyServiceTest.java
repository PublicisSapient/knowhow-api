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

package com.publicissapient.kpidashboard.apis.kpimaturity.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.publicissapient.kpidashboard.common.model.application.KpiCategory;
import com.publicissapient.kpidashboard.common.repository.application.KpiCategoryRepository;

@RunWith(MockitoJUnitRunner.class)
public class ProjectEfficiencyServiceTest {

	@Mock private KpiCategoryRepository kpiCategoryRepository;

	@InjectMocks private ProjectEfficiencyService projectEfficiencyService;

	@Before
	public void setup() {
		// Reset any configuration before each test
		ReflectionTestUtils.setField(projectEfficiencyService, "efficiencyWeightageConfig", "");
	}

	@Test
	public void testCalculateProjectEfficiency_WithValidMaturities() {
		// Setup
		Map<String, String> boardMaturities = new HashMap<>();
		boardMaturities.put("DORA", "4.5");
		boardMaturities.put("AGILE", "3.8");

		// Mock database categories
		when(kpiCategoryRepository.findAll()).thenReturn(Collections.emptyList());

		// Execute
		Map<String, Object> result =
				projectEfficiencyService.calculateProjectEfficiency(boardMaturities);

		// Verify
		assertNotNull(result);
		assertTrue((Double) result.get("score") > 0);
		assertTrue((Double) result.get("percentage") > 0);
		assertNotNull(result.get("healthStatus"));
		assertNotNull(result.get("weightages"));
	}

	@Test
	public void testCalculateProjectEfficiency_WithCustomWeightages() {
		// Setup custom weightages
		ReflectionTestUtils.setField(
				projectEfficiencyService, "efficiencyWeightageConfig", "DORA=60, AGILE=40");

		Map<String, String> boardMaturities = new HashMap<>();
		boardMaturities.put("DORA", "4.5");
		boardMaturities.put("AGILE", "3.8");

		// Execute
		Map<String, Object> result =
				projectEfficiencyService.calculateProjectEfficiency(boardMaturities);

		// Verify
		assertNotNull(result);
		Map<String, Integer> weightages = (Map<String, Integer>) result.get("weightages");
		assertEquals(60, weightages.get("DORA").intValue());
		assertEquals(40, weightages.get("AGILE").intValue());
	}

	@Test
	public void testCalculateProjectEfficiency_WithDatabaseCategories() {
		// Setup database categories
		KpiCategory category1 = new KpiCategory();
		category1.setCategoryName("QUALITY");
		when(kpiCategoryRepository.findAll()).thenReturn(Collections.singletonList(category1));

		Map<String, String> boardMaturities = new HashMap<>();
		boardMaturities.put("QUALITY", "4.2");
		boardMaturities.put("DORA", "4.5");

		// Execute
		Map<String, Object> result =
				projectEfficiencyService.calculateProjectEfficiency(boardMaturities);

		// Verify both DORA (default) and QUALITY (from DB) are considered
		Map<String, Integer> weightages = (Map<String, Integer>) result.get("weightages");
		assertTrue(weightages.containsKey("DORA"));
		assertTrue(weightages.containsKey("QUALITY"));
	}

	@Test
	public void testCalculateProjectEfficiency_WithEmptyMaturities() {
		Map<String, String> emptyMaturities = Collections.emptyMap();

		Map<String, Object> result =
				projectEfficiencyService.calculateProjectEfficiency(emptyMaturities);

		assertEquals(0.0, (Double) result.get("score"), 0.01);
		assertEquals(0.0, (Double) result.get("percentage"), 0.01);
		assertEquals("RED", result.get("healthStatus"));
	}

	@Test
	public void testCalculateHealthStatus() {
		// Test health status thresholds
		assertEquals("GREEN", projectEfficiencyService.calculateHealthStatus(80.0));
		assertEquals("GREEN", projectEfficiencyService.calculateHealthStatus(90.0));
		assertEquals("AMBER", projectEfficiencyService.calculateHealthStatus(79.9));
		assertEquals("AMBER", projectEfficiencyService.calculateHealthStatus(50.0));
		assertEquals("RED", projectEfficiencyService.calculateHealthStatus(49.9));
		assertEquals("RED", projectEfficiencyService.calculateHealthStatus(0.0));
	}

	@Test
	public void testParseWeightageConfig_WithValidConfig() {
		// Setup
		ReflectionTestUtils.setField(
				projectEfficiencyService, "efficiencyWeightageConfig", "DORA=60,AGILE=40");

		// Execute
		Map<String, Integer> result = projectEfficiencyService.parseWeightageConfig();

		// Verify
		assertEquals(2, result.size());
		assertEquals(60, result.get("DORA").intValue());
		assertEquals(40, result.get("AGILE").intValue());
	}

	@Test
	public void testParseWeightageConfig_WithInvalidConfig() {
		// Setup invalid config
		ReflectionTestUtils.setField(
				projectEfficiencyService, "efficiencyWeightageConfig", "invalid=format");

		// Execute
		Map<String, Integer> result = projectEfficiencyService.parseWeightageConfig();

		// Should fall back to default categories
		assertTrue(result.containsKey("DORA"));
	}

	@Test
	public void testNormalizeWeightages_WithUnevenSum() {
		Map<String, Integer> weightages = new HashMap<>();
		weightages.put("DORA", 30);
		weightages.put("AGILE", 40); // Sum = 70, needs normalization

		// Execute
		projectEfficiencyService.normalizeWeightages(weightages);

		// Verify sum is 100
		int sum = weightages.values().stream().mapToInt(Integer::intValue).sum();
		assertEquals(100, sum);
	}

	@Test
	public void testGetCategoriesFromDatabase() {
		// Setup
		KpiCategory category1 = new KpiCategory();
		category1.setCategoryName("QUALITY");
		KpiCategory category2 = new KpiCategory();
		category2.setCategoryName("SECURITY");
		when(kpiCategoryRepository.findAll()).thenReturn(Arrays.asList(category1, category2));

		// Execute
		Set<String> categories = projectEfficiencyService.getCategoriesFromDatabase();

		// Verify
		assertEquals(2, categories.size());
		assertTrue(categories.contains("QUALITY"));
		assertTrue(categories.contains("SECURITY"));
	}

	@Test
	public void testFindBestMatchingBoardMaturity() {
		// Setup
		Map<String, String> boardMaturities = new HashMap<>();
		boardMaturities.put("DORA", "4.5");
		boardMaturities.put("DORA_METRICS", "3.8");
		boardMaturities.put("OTHER", "2.5");

		// Execute
		int doraMaturity =
				projectEfficiencyService.findBestMatchingBoardMaturity("DORA", boardMaturities);
		int otherMaturity =
				projectEfficiencyService.findBestMatchingBoardMaturity("NON_EXISTENT", boardMaturities);

		// Verify
		assertEquals(5, doraMaturity); // 4.5 should be rounded to 4
		assertEquals(0, otherMaturity); // No match found
	}
}
