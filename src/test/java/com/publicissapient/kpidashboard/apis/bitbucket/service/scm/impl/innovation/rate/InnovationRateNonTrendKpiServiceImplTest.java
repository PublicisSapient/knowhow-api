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

import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.model.IterationKpiValue;
import com.publicissapient.kpidashboard.apis.repotools.model.RepoToolValidationData;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.util.DateUtil;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class InnovationRateNonTrendKpiServiceImplTest {

	@InjectMocks
	private InnovationRateNonTrendKpiServiceImpl service;

	private CustomDateRange currentPeriodRange;
	private CustomDateRange previousPeriodRange;
	private Map<String, List<ScmCommits>> userWiseCommits;
	private Set<Assignee> assignees;
	private Tool tool;
	private List<IterationKpiValue> iterationKpiValueList;

	@BeforeEach
	void setUp() {
		LocalDateTime now = LocalDateTime.of(2024, 1, 15, 0, 0);
		currentPeriodRange = new CustomDateRange();
        currentPeriodRange.setStartDateTime(now.minusDays(7));
        currentPeriodRange.setEndDateTime(now);
		previousPeriodRange = new CustomDateRange();
        previousPeriodRange.setStartDateTime(now.minusDays(14));
        previousPeriodRange.setEndDateTime(now.minusDays(7));

		tool = new Tool();
		tool.setBranch("main");
		tool.setRepositoryName("test-repo");

		assignees = new HashSet<>();
		iterationKpiValueList = new ArrayList<>();
		userWiseCommits = new HashMap<>();
	}

	@Test
	void testPrepareUserValidationData_WithSingleUser() throws Exception {
		ScmCommits commit1 = createCommit(100, 80, currentPeriodRange.getStartDateTime().plusDays(1));
		ScmCommits commit2 = createCommit(50, 40, currentPeriodRange.getStartDateTime().plusDays(2));
		userWiseCommits.put("user1@test.com", List.of(commit1, commit2));

		try (MockedStatic<DeveloperKpiHelper> helperMock = mockStatic(DeveloperKpiHelper.class);
			 MockedStatic<DateUtil> dateUtilMock = mockStatic(DateUtil.class)) {

			helperMock.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), any(String.class))).thenReturn("main");
			helperMock.when(() -> DeveloperKpiHelper.getDeveloperName(any(String.class), any(Set.class))).thenReturn("User One");
			dateUtilMock.when(() -> DateUtil.convertMillisToLocalDateTime(any(Long.class))).thenAnswer(inv -> {
				long millis = inv.getArgument(0);
				return LocalDateTime.ofEpochSecond(millis / 1000, 0, java.time.ZoneOffset.UTC);
			});
			dateUtilMock.when(() -> DateUtil.isWithinDateTimeRange(any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(true);

			List<RepoToolValidationData> result = invokePrepareUserValidationData();

			assertNotNull(result);
			assertEquals(1, result.size());
			assertFalse(iterationKpiValueList.isEmpty());
		}
	}

	@Test
	void testPrepareUserValidationData_WithMultipleUsers() throws Exception {
		ScmCommits commit1 = createCommit(100, 80, currentPeriodRange.getStartDateTime().plusDays(1));
		ScmCommits commit2 = createCommit(200, 150, currentPeriodRange.getStartDateTime().plusDays(2));
		userWiseCommits.put("user1@test.com", List.of(commit1));
		userWiseCommits.put("user2@test.com", List.of(commit2));

		try (MockedStatic<DeveloperKpiHelper> helperMock = mockStatic(DeveloperKpiHelper.class);
			 MockedStatic<DateUtil> dateUtilMock = mockStatic(DateUtil.class)) {

			helperMock.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), any(String.class))).thenReturn("main");
			helperMock.when(() -> DeveloperKpiHelper.getDeveloperName(any(String.class), any(Set.class))).thenReturn("User One", "User Two");
			dateUtilMock.when(() -> DateUtil.convertMillisToLocalDateTime(any(Long.class))).thenAnswer(inv -> {
				long millis = inv.getArgument(0);
				return LocalDateTime.ofEpochSecond(millis / 1000, 0, java.time.ZoneOffset.UTC);
			});
			dateUtilMock.when(() -> DateUtil.isWithinDateTimeRange(any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(true);

			List<RepoToolValidationData> result = invokePrepareUserValidationData();

			assertNotNull(result);
			assertEquals(2, result.size());
			assertEquals(2, iterationKpiValueList.size());
		}
	}

	@Test
	void testPrepareUserValidationData_WithEmptyCommits() throws Exception {
		userWiseCommits.put("user1@test.com", Collections.emptyList());

		try (MockedStatic<DeveloperKpiHelper> helperMock = mockStatic(DeveloperKpiHelper.class);
			 MockedStatic<DateUtil> dateUtilMock = mockStatic(DateUtil.class)) {

			helperMock.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), any(String.class))).thenReturn("main");
			helperMock.when(() -> DeveloperKpiHelper.getDeveloperName(any(String.class), any(Set.class))).thenReturn("User One");
			dateUtilMock.when(() -> DateUtil.isWithinDateTimeRange(any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(false);

			List<RepoToolValidationData> result = invokePrepareUserValidationData();

			assertNotNull(result);
			assertEquals(1, result.size());
		}
	}

	@Test
	void testPrepareUserValidationData_WithNoCurrentPeriodCommits() throws Exception {
		ScmCommits commit = createCommit(100, 80, previousPeriodRange.getStartDateTime().plusDays(1));
		userWiseCommits.put("user1@test.com", List.of(commit));

		try (MockedStatic<DeveloperKpiHelper> helperMock = mockStatic(DeveloperKpiHelper.class);
			 MockedStatic<DateUtil> dateUtilMock = mockStatic(DateUtil.class)) {

			helperMock.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), any(String.class))).thenReturn("main");
			helperMock.when(() -> DeveloperKpiHelper.getDeveloperName(any(String.class), any(Set.class))).thenReturn("User One");
			dateUtilMock.when(() -> DateUtil.convertMillisToLocalDateTime(any(Long.class))).thenAnswer(inv -> {
				long millis = inv.getArgument(0);
				return LocalDateTime.ofEpochSecond(millis / 1000, 0, java.time.ZoneOffset.UTC);
			});
			dateUtilMock.when(() -> DateUtil.isWithinDateTimeRange(any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(false);

			List<RepoToolValidationData> result = invokePrepareUserValidationData();

			assertNotNull(result);
			assertEquals(1, result.size());
		}
	}

	private ScmCommits createCommit(long totalLines, long addedLines, LocalDateTime commitTime) {
		ScmCommits commit = new ScmCommits();
		commit.setAddedLines((int) addedLines);
		commit.setRemovedLines((int) (totalLines - addedLines));
		commit.setCommitTimestamp(commitTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
		return commit;
	}

	private List<RepoToolValidationData> invokePrepareUserValidationData() throws Exception {
		Method method = InnovationRateNonTrendKpiServiceImpl.class.getDeclaredMethod(
				"prepareUserValidationData", CustomDateRange.class, CustomDateRange.class,
				Map.class, Set.class, Tool.class, String.class, List.class);
		method.setAccessible(true);
		return (List<RepoToolValidationData>) method.invoke(service, currentPeriodRange, previousPeriodRange,
				userWiseCommits, assignees, tool, "TestProject", iterationKpiValueList);
	}
}
