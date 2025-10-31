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

package com.publicissapient.kpidashboard.apis.productivity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProductivityGainConfigTest {

	private ProductivityGainConfig productivityGainConfig;

	@BeforeEach
	void setup() {
		productivityGainConfig = new ProductivityGainConfig();
	}

	@Test
	void when_NoConfigurationIsSet_Expect_ErrorMessageIsContainedWithinConfigValidationIssues() {
		productivityGainConfig.setWeights(Collections.emptyMap());
		ReflectionTestUtils.invokeMethod(productivityGainConfig, "validateConfiguration");
		Set<String> resultedConfigValidationIssues = productivityGainConfig.getConfigValidationIssues();

		assertTrue(CollectionUtils.isNotEmpty(resultedConfigValidationIssues));
		assertTrue(
				resultedConfigValidationIssues.contains(
						"No productivity gain configuration could be found"));
	}

	@Test
	void
			when_InvalidCategoryIdIsSetThroughConfiguration_Expect_ErrorMessageIsContainedWithinConfigValidationIssues() {
		productivityGainConfig.setWeights(Map.of("sped", 10.0D));
		ReflectionTestUtils.invokeMethod(productivityGainConfig, "validateConfiguration");
		Set<String> resultedConfigValidationIssues = productivityGainConfig.getConfigValidationIssues();

		assertTrue(CollectionUtils.isNotEmpty(resultedConfigValidationIssues));
		assertTrue(
				resultedConfigValidationIssues.stream()
						.anyMatch(
								configValidationIssue ->
										configValidationIssue.contains("Category 'sped' is invalid")));
	}

	@Test
	void
			when_CategoryIsSetWithNegativeWeightage_Expect_ErrorMessageIsContainedWithinConfigValidationIssues() {
		productivityGainConfig.setWeights(Map.of("speed", -0.25D));
		ReflectionTestUtils.invokeMethod(productivityGainConfig, "validateConfiguration");
		Set<String> resultedConfigValidationIssues = productivityGainConfig.getConfigValidationIssues();

		assertTrue(CollectionUtils.isNotEmpty(resultedConfigValidationIssues));
		assertTrue(
				resultedConfigValidationIssues.stream()
						.anyMatch(
								configValidationIssue ->
										configValidationIssue.contains(
												"Invalid category 'speed' was found with a weight of '-0.25'")));
	}

	@Test
	void
			when_SumOfProvidedWeightagesIsDifferentThanOne_Expect_ErrorMessageIsContainedWithinConfigValidationIssues() {
		productivityGainConfig.setWeights(Map.of("speed", 0.25D));
		ReflectionTestUtils.invokeMethod(productivityGainConfig, "validateConfiguration");
		Set<String> resultedConfigValidationIssues = productivityGainConfig.getConfigValidationIssues();

		assertTrue(CollectionUtils.isNotEmpty(resultedConfigValidationIssues));
		assertTrue(
				resultedConfigValidationIssues.stream()
						.anyMatch(
								configValidationIssue ->
										configValidationIssue.contains(
												"The sum of all category weightages must be 1")));
	}

	@Test
	void
			when_ConfigurationIsValid_Expect_GetAllConfiguredCategoriesReturnAllCategoriesForWhichTheWeightIsHigherThanZero() {
		productivityGainConfig.setWeights(Map.of("speed", 1.0D, "efficiency", 0.0D));
		ReflectionTestUtils.invokeMethod(productivityGainConfig, "validateConfiguration");

		Set<String> expectedConfiguredCategories = Set.of("speed");
		Set<String> resultedConfiguredCategories = productivityGainConfig.getAllConfiguredCategories();

		assertTrue(CollectionUtils.isEmpty(productivityGainConfig.getConfigValidationIssues()));
		assertTrue(CollectionUtils.isNotEmpty(resultedConfiguredCategories));
		assertEquals(expectedConfiguredCategories, resultedConfiguredCategories);
	}
}
