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

package com.publicissapient.kpidashboard.apis.repotools.model;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lombok.Data;

@Data
public class RepoToolKpiBulkMetricResponse {
	private List<List<RepoToolKpiMetricResponse>> values;

	public static RepoToolKpiBulkMetricResponse createMockResponse() {
		List<RepoToolKpiMetricResponse> mockResponses =
				IntStream.rangeClosed(1, 20)
						.mapToObj(RepoToolKpiBulkMetricResponse::createMockResponse)
						.collect(Collectors.toList());

		RepoToolKpiBulkMetricResponse response = new RepoToolKpiBulkMetricResponse();
		response.setValues(Arrays.asList(mockResponses));
		return response;
	}

	private static RepoToolKpiMetricResponse createMockResponse(int index) {
		String[] dates = {
			"2025-12-15",
			"2025-12-16",
			"2025-12-17",
			"2025-12-18",
			"2025-12-19",
			"2025-12-20",
			"2025-12-21",
			"2025-12-22",
			"2025-12-23",
			"2025-12-24",
			"2025-12-25",
			"2025-12-26",
			"2025-12-27",
			"2025-12-28",
			"2025-12-29",
			"2025-12-30",
			"2025-12-31",
			"2026-01-01",
			"2026-01-02",
			"2026-01-18"
		};

		RepoToolUserDetails user1 =
				createUser("pratik.basak@publicissapient.com", 85.5 + index, 5 + index, 1200 + index * 50);
		RepoToolUserDetails user2 =
				createUser("valsa.anil@publicissapient.com", 78.2 + index, 7 + index, 1800 + index * 60);
		RepoToolRepositories repo =
				createRepository("knowhow-api", 82.0 + index, 553 + index * 10, 85.5 + index, 12 + index);

		RepoToolKpiMetricResponse response = new RepoToolKpiMetricResponse();
		response.setProjectName("knowhow-api");
		response.setProjectCode("KH-API");
		response.setCommitCount(553 + index * 10);
		response.setMrCount(12 + index);
		response.setMergeRequests(12 + index);
		response.setPrLinesChanged(174143 + index * 1000);
		response.setProjectGrade(85.5 + index * 0.5);
		response.setProjectHours(120.0 + index * 2);
		response.setAverage(78.2 + index * 0.3);
		response.setProjectReworkRateGrade(75.0 + index * 0.2);
		response.setRevertRateGrade(90.0 - index * 0.1);
		response.setProjectRevertPercentage(5.2 + index * 0.1);
		response.setProjectReworkRatePercent(12.8 + index * 0.2);
		response.setInnovationRatePercentage(65.3 + index * 0.4);
		response.setMergeRequestsNumber(12 + index);
		response.setProjectDefectMergeRequestPercentage(8.5 + index * 0.1);
		response.setProjectPercentage(88.7 + index * 0.2);
		response.setDateLabel(dates[index - 1]);
		response.setUsers(Arrays.asList(user1, user2));
		response.setRepositories(Arrays.asList(repo));
		response.setProjectRepositories(Arrays.asList(repo));
		return response;
	}

	private static RepoToolUserDetails createUser(
			String email, double average, long mergeRequests, long linesChanged) {
		RepoToolUserDetails user = new RepoToolUserDetails();
		user.setEmail(email);
		user.setCommitterEmail(email);
		user.setAverage(average);
		user.setMergeRequests(mergeRequests);
		user.setLinesChanged(linesChanged);
		user.setHours(40.0);
		user.setMrCount(mergeRequests);
		user.setAddedLines(linesChanged * 2 / 3);
		user.setChangedLines(linesChanged);
		return user;
	}

	private static RepoToolRepositories createRepository(
			String name, double average, long commitCount, double grade, int mrNumber) {
		RepoToolRepositories repo = new RepoToolRepositories();
		repo.setName(name);
		repo.setRepository(name);
		repo.setRepositoryName(name);
		repo.setAverage(average);
		repo.setCommitCount(commitCount);
		repo.setRepositoryGrade(grade);
		repo.setMergeRequestsNumber(mrNumber);
		return repo;
	}
}
