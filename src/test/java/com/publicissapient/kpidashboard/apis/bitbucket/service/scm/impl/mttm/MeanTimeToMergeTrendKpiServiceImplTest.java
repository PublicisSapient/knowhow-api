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

package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.mttm;

import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;

import com.publicissapient.kpidashboard.apis.repotools.model.RepoToolValidationData;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class MeanTimeToMergeTrendKpiServiceImplTest {

	@InjectMocks
	private MeanTimeToMergeTrendKpiServiceImpl service;

	private Tool tool;
	private Set<Assignee> assignees;
	private Map<String, List<ScmMergeRequests>> userWiseMergeRequests;
	private Map<String, List<DataCount>> kpiTrendDataByGroup;

	@BeforeEach
	void setUp() {
		tool = new Tool();
		tool.setBranch("main");
		tool.setRepositoryName("test-repo");

		assignees = new HashSet<>();
		userWiseMergeRequests = new HashMap<>();
		kpiTrendDataByGroup = new LinkedHashMap<>();
	}

	@Test
	void testPrepareUserValidationData_WithSingleUser() throws Exception {
		ScmMergeRequests mr = createMergeRequest(LocalDateTime.of(2024, 1, 10, 10, 0),
				LocalDateTime.of(2024, 1, 12, 14, 0), "MERGED");
		userWiseMergeRequests.put("user1@test.com", List.of(mr));

		try (MockedStatic<DeveloperKpiHelper> helperMock = mockStatic(DeveloperKpiHelper.class);
			 MockedStatic<KpiHelperService> kpiHelperMock = mockStatic(KpiHelperService.class)) {

			helperMock.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), any(String.class))).thenReturn("main");
			helperMock.when(() -> DeveloperKpiHelper.getDeveloperName(any(String.class), any(Set.class))).thenReturn("User One");
			helperMock.when(() -> DeveloperKpiHelper.setDataCount(any(String.class), any(String.class), any(String.class),
					any(), any(Map.class), any(Map.class))).thenAnswer(inv -> null);
			kpiHelperMock.when(() -> KpiHelperService.convertMilliSecondsToHours(anyLong())).thenReturn(52L);

			List<RepoToolValidationData> result = invokePrepareUserValidationData("TestProject", "2024-01-01 to 2024-01-07");

			assertNotNull(result);
			assertEquals(1, result.size());
		}
	}

	@Test
	void testPrepareUserValidationData_WithMultipleUsers() throws Exception {
		ScmMergeRequests mr1 = createMergeRequest(LocalDateTime.of(2024, 1, 10, 10, 0),
				LocalDateTime.of(2024, 1, 12, 14, 0), "MERGED");
		ScmMergeRequests mr2 = createMergeRequest(LocalDateTime.of(2024, 1, 11, 9, 0),
				LocalDateTime.of(2024, 1, 13, 15, 0), "MERGED");
		userWiseMergeRequests.put("user1@test.com", List.of(mr1));
		userWiseMergeRequests.put("user2@test.com", List.of(mr2));

		try (MockedStatic<DeveloperKpiHelper> helperMock = mockStatic(DeveloperKpiHelper.class);
			 MockedStatic<KpiHelperService> kpiHelperMock = mockStatic(KpiHelperService.class)) {

			helperMock.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), any(String.class))).thenReturn("main");
			helperMock.when(() -> DeveloperKpiHelper.getDeveloperName(any(String.class), any(Set.class)))
					.thenReturn("User One", "User Two");
			helperMock.when(() -> DeveloperKpiHelper.setDataCount(any(String.class), any(String.class), any(String.class),
					any(), any(Map.class), any(Map.class))).thenAnswer(inv -> null);
			kpiHelperMock.when(() -> KpiHelperService.convertMilliSecondsToHours(anyLong())).thenReturn(52L);

			List<RepoToolValidationData> result = invokePrepareUserValidationData("TestProject", "2024-01-01 to 2024-01-07");

			assertNotNull(result);
			assertEquals(2, result.size());
		}
	}

	@Test
	void testPrepareUserValidationData_WithNullDates() throws Exception {
		ScmMergeRequests mr = createMergeRequest(null, null, "MERGED");
		userWiseMergeRequests.put("user1@test.com", List.of(mr));

		try (MockedStatic<DeveloperKpiHelper> helperMock = mockStatic(DeveloperKpiHelper.class);
			 MockedStatic<KpiHelperService> kpiHelperMock = mockStatic(KpiHelperService.class)) {

			helperMock.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), any(String.class))).thenReturn("main");
			helperMock.when(() -> DeveloperKpiHelper.getDeveloperName(any(String.class), any(Set.class))).thenReturn("User One");
			helperMock.when(() -> DeveloperKpiHelper.setDataCount(any(String.class), any(String.class), any(String.class),
					any(), any(Map.class), any(Map.class))).thenAnswer(inv -> null);
			kpiHelperMock.when(() -> KpiHelperService.convertMilliSecondsToHours(anyLong())).thenReturn(0L);

			List<RepoToolValidationData> result = invokePrepareUserValidationData("TestProject", "2024-01-01 to 2024-01-07");

			assertNotNull(result);
			assertTrue(result.isEmpty());
		}
	}

	@Test
	void testGetStrategyType() {
		assertEquals("REPO_TOOL_MEAN_TIME_TO_MERGE_TREND", service.getStrategyType());
	}

	private ScmMergeRequests createMergeRequest(LocalDateTime createdDate, LocalDateTime mergedAt, String state) {
		ScmMergeRequests mr = new ScmMergeRequests();
		if (createdDate != null) {
			mr.setCreatedDate(createdDate.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
		}
		mr.setMergedAt(mergedAt);
		mr.setState(state);
		mr.setMergeRequestUrl("https://example.com/mr/1");
		return mr;
	}

	private List<RepoToolValidationData> invokePrepareUserValidationData(String projectName, String dateLabel) throws Exception {
		Method method = MeanTimeToMergeTrendKpiServiceImpl.class.getDeclaredMethod(
				"prepareUserValidationData", Map.class, Set.class, Tool.class, String.class, String.class, Map.class);
		method.setAccessible(true);
		return (List<RepoToolValidationData>) method.invoke(service, userWiseMergeRequests, assignees, tool,
				projectName, dateLabel, kpiTrendDataByGroup);
	}
}
