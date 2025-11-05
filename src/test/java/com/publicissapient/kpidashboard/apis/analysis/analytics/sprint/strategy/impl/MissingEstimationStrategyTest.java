/*
*  Copyright 2024 <Sapient Corporation>
*
*  Licensed under 	@Test
public void testCalculateForSprint_NoFieldMapping() {
	context.setFieldMapping(null);

	SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

	assertNotNull(result);
	assertEquals(Constant.NOT_AVAILABLE, result.getValue());
}

@Test
public void testCalculateForSprint_NoTotalIssues() {
	sprintDetails.setTotalIssues(null);

	SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

	assertNotNull(result);
	assertEquals(Constant.NOT_AVAILABLE, result.getValue());
} Version 2.0 (the "License");
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

package com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.strategy.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.dto.SprintDataPoint;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.enums.SprintMetricType;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.model.SprintMetricContext;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintIssue;

@RunWith(MockitoJUnitRunner.class)
public class MissingEstimationStrategyTest {

	@InjectMocks private MissingEstimationStrategy strategy;

	private SprintDetails sprintDetails;
	private SprintMetricContext context;
	private FieldMapping fieldMapping;

	@Before
	public void setUp() {
		sprintDetails = new SprintDetails();
		sprintDetails.setSprintID("sprint-1");
		sprintDetails.setSprintName("Sprint 1");

		fieldMapping = new FieldMapping();
		fieldMapping.setEstimationCriteria("Story Point");

		context = new SprintMetricContext();
		context.setProjectName("Test Project");
		context.setFieldMapping(fieldMapping);
	}

	@Test
	public void testGetMetricType() {
		assertEquals(SprintMetricType.MISSING_ESTIMATION, strategy.getMetricType());
	}

	@Test
	public void testCalculateForSprint_NoFieldMapping() {
		context.setFieldMapping(null);

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals(Constant.NOT_AVAILABLE, result.getValue());
	}

	@Test
	public void testCalculateForSprint_NoEstimationCriteria() {
		fieldMapping.setEstimationCriteria(null);

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals(Constant.NOT_AVAILABLE, result.getValue());
	}

	@Test
	public void testCalculateForSprint_StoryPoint_WithMissing() {
		fieldMapping.setEstimationCriteria("Story Point");

		Set<SprintIssue> totalIssues = new HashSet<>();
		totalIssues.add(createSprintIssueWithStoryPoints("ISSUE-1", 5.0));
		totalIssues.add(createSprintIssueWithStoryPoints("ISSUE-2", null));
		totalIssues.add(createSprintIssueWithStoryPoints("ISSUE-3", 0.0));
		totalIssues.add(createSprintIssueWithStoryPoints("ISSUE-4", 3.0));
		sprintDetails.setTotalIssues(totalIssues);

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals("2", result.getValue()); // 2 missing (null and 0)
		assertEquals("50.0", result.getTrend()); // (2/4) * 100 = 50%
	}

	@Test
	public void testCalculateForSprint_OriginalEstimate_WithMissing() {
		fieldMapping.setEstimationCriteria("Original Estimate");

		Set<SprintIssue> totalIssues = new HashSet<>();
		totalIssues.add(createSprintIssueWithOriginalEstimate("ISSUE-1", 100.0));
		totalIssues.add(createSprintIssueWithOriginalEstimate("ISSUE-2", null));
		totalIssues.add(createSprintIssueWithOriginalEstimate("ISSUE-3", 0.0));
		sprintDetails.setTotalIssues(totalIssues);

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals("2", result.getValue());
		assertEquals("66.67", result.getTrend()); // (2/3) * 100 = 66.67%, rounded
	}

	@Test
	public void testCalculateForSprint_NoMissingEstimation() {
		Set<SprintIssue> totalIssues = new HashSet<>();
		totalIssues.add(createSprintIssueWithStoryPoints("ISSUE-1", 5.0));
		totalIssues.add(createSprintIssueWithStoryPoints("ISSUE-2", 3.0));
		sprintDetails.setTotalIssues(totalIssues);

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals("0", result.getValue());
		assertEquals("0.0", result.getTrend());
	}

	private SprintIssue createSprintIssueWithStoryPoints(String number, Double storyPoints) {
		SprintIssue issue = new SprintIssue();
		issue.setNumber(number);
		issue.setStoryPoints(storyPoints);
		return issue;
	}

	private SprintIssue createSprintIssueWithOriginalEstimate(
			String number, Double originalEstimate) {
		SprintIssue issue = new SprintIssue();
		issue.setNumber(number);
		issue.setOriginalEstimate(originalEstimate);
		return issue;
	}
}
