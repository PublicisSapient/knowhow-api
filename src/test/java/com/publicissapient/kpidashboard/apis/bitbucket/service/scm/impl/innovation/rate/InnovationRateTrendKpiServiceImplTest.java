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

package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.innovation.rate;

import com.publicissapient.kpidashboard.apis.repotools.model.RepoToolValidationData;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class InnovationRateTrendKpiServiceImplTest {

	@InjectMocks
	private InnovationRateTrendKpiServiceImpl service;

	private Tool tool;

	@BeforeEach
	void setUp() {
		tool = new Tool();
		tool.setBranch("main");
		tool.setRepositoryName("test-repo");
	}

	@Test
	void testCreateValidationData_WithRepositoryName() throws Exception {
		RepoToolValidationData result = invokeCreateValidationData("TestProject", tool, "John Doe", 
				"2024-01-01 to 2024-01-07", 8.5, 100, 150);

		assertNotNull(result);
		assertEquals("TestProject", result.getProjectName());
		assertEquals("main", result.getBranchName());
		assertEquals("test-repo", result.getRepoUrl());
		assertEquals("John Doe", result.getDeveloperName());
		assertEquals("2024-01-01 to 2024-01-07", result.getDate());
		assertEquals(8.5, result.getInnovationRate());
		assertEquals(100, result.getAddedLines());
		assertEquals(150, result.getChangedLines());
	}

	@Test
	void testCreateValidationData_WithRepoSlug() throws Exception {
		tool.setRepositoryName(null);
		tool.setRepoSlug("repo-slug");

		RepoToolValidationData result = invokeCreateValidationData("TestProject", tool, "Jane Smith", 
				"2024-01-08 to 2024-01-14", 7.2, 80, 120);

		assertNotNull(result);
		assertEquals("repo-slug", result.getRepoUrl());
		assertEquals("Jane Smith", result.getDeveloperName());
		assertEquals(7.2, result.getInnovationRate());
		assertEquals(80, result.getAddedLines());
		assertEquals(120, result.getChangedLines());
	}

	@Test
	void testCreateValidationData_WithZeroValues() throws Exception {
		RepoToolValidationData result = invokeCreateValidationData("TestProject", tool, "Developer", 
				"2024-01-15 to 2024-01-21", 0.0, 0, 0);

		assertNotNull(result);
		assertEquals(0.0, result.getInnovationRate());
		assertEquals(0, result.getAddedLines());
		assertEquals(0, result.getChangedLines());
	}

	@Test
	void testGetStrategyType() {
		assertEquals("INNOVATION_RATE_TREND", service.getStrategyType());
	}

	private RepoToolValidationData invokeCreateValidationData(String projectName, Tool tool, String developerName,
			String dateLabel, double innovationRate, long addedLines, long changedLines) throws Exception {
		Method method = InnovationRateTrendKpiServiceImpl.class.getDeclaredMethod(
				"createValidationData", String.class, Tool.class, String.class, String.class, 
				double.class, long.class, long.class);
		method.setAccessible(true);
		return (RepoToolValidationData) method.invoke(service, projectName, tool, developerName, 
				dateLabel, innovationRate, addedLines, changedLines);
	}
}
