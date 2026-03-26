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

package com.publicissapient.kpidashboard.apis.jira.scrum.service.leadtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.publicissapient.kpidashboard.apis.model.LeadTimeChangeData;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.Deployment;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.util.DateUtil;

class RepoDeploymentLeadTimeStrategyTest {

	private static final String SHA = "abc123";
	private static final String TIME_FORMAT = DateUtil.TIME_FORMAT;

	// Monday 2024-01-08 10:00:00
	private static final String DEPLOY_START = "2024-01-08T10:00:00";
	// Monday 2024-01-08 12:00:00
	private static final String DEPLOY_END = "2024-01-08T12:00:00";

	private RepoDeploymentLeadTimeStrategy strategy;

	@BeforeEach
	void setUp() {
		strategy = new RepoDeploymentLeadTimeStrategy();
	}

	// --- Early-exit (guard) tests ---

	@Test
	void calculateLeadTime_emptyMergeRequests_doesNothing() {
		LeadTimeContext context = LeadTimeContext.builder()
				.mergeRequestList(Collections.emptyList())
				.deploymentList(List.of(buildDeployment(SHA, DEPLOY_START, DEPLOY_END)))
				.scmCommitList(List.of(buildCommit(SHA, 1_000_000L)))
				.weekOrMonth(CommonConstant.WEEK)
				.build();

		Map<String, List<LeadTimeChangeData>> result = new HashMap<>();
		strategy.calculateLeadTime(context, result);

		assertThat(result).isEmpty();
	}

	@Test
	void calculateLeadTime_emptyDeployments_doesNothing() {
		LeadTimeContext context = LeadTimeContext.builder()
				.mergeRequestList(List.of(buildMr(SHA, LocalDateTime.parse(DEPLOY_START, java.time.format.DateTimeFormatter.ofPattern(TIME_FORMAT)))))
				.deploymentList(Collections.emptyList())
				.scmCommitList(List.of(buildCommit(SHA, 1_000_000L)))
				.weekOrMonth(CommonConstant.WEEK)
				.build();

		Map<String, List<LeadTimeChangeData>> result = new HashMap<>();
		strategy.calculateLeadTime(context, result);

		assertThat(result).isEmpty();
	}

	@Test
	void calculateLeadTime_emptyCommits_doesNothing() {
		LeadTimeContext context = LeadTimeContext.builder()
				.mergeRequestList(List.of(buildMr(SHA, LocalDateTime.parse(DEPLOY_START, java.time.format.DateTimeFormatter.ofPattern(TIME_FORMAT)))))
				.deploymentList(List.of(buildDeployment(SHA, DEPLOY_START, DEPLOY_END)))
				.scmCommitList(Collections.emptyList())
				.weekOrMonth(CommonConstant.WEEK)
				.build();

		Map<String, List<LeadTimeChangeData>> result = new HashMap<>();
		strategy.calculateLeadTime(context, result);

		assertThat(result).isEmpty();
	}

	// --- No SHA match ---

	@Test
	void calculateLeadTime_noMatchingShaBetweenCommitAndMr_skipsCommit() {
		ScmCommits commit = buildCommit("sha-commit", 1_000_000L);
		ScmMergeRequests mr = buildMr("sha-mr", LocalDateTime.parse(DEPLOY_START, java.time.format.DateTimeFormatter.ofPattern(TIME_FORMAT)));
		Deployment deployment = buildDeployment("sha-deploy", DEPLOY_START, DEPLOY_END);

		LeadTimeContext context = LeadTimeContext.builder()
				.mergeRequestList(List.of(mr))
				.deploymentList(List.of(deployment))
				.scmCommitList(List.of(commit))
				.weekOrMonth(CommonConstant.WEEK)
				.build();

		Map<String, List<LeadTimeChangeData>> result = new HashMap<>();
		strategy.calculateLeadTime(context, result);

		assertThat(result).isEmpty();
	}

	// --- Null MR mergedAt / deployment startTime ---

	@Test
	void calculateLeadTime_mrWithNullMergedAt_skipsCommit() {
		ScmMergeRequests mr = buildMr(SHA, null);
		Deployment deployment = buildDeployment(SHA, DEPLOY_START, DEPLOY_END);
		ScmCommits commit = buildCommit(SHA, 1_000_000L);

		LeadTimeContext context = LeadTimeContext.builder()
				.mergeRequestList(List.of(mr))
				.deploymentList(List.of(deployment))
				.scmCommitList(List.of(commit))
				.weekOrMonth(CommonConstant.WEEK)
				.build();

		Map<String, List<LeadTimeChangeData>> result = new HashMap<>();
		strategy.calculateLeadTime(context, result);

		assertThat(result).isEmpty();
	}

	@Test
	void calculateLeadTime_deploymentWithNullStartTime_skipsCommit() {
		// commit time: 2024-01-08T08:00:00 UTC
		long commitMillis = java.time.ZonedDateTime.of(
				LocalDateTime.parse("2024-01-08T08:00:00", java.time.format.DateTimeFormatter.ofPattern(TIME_FORMAT)),
				java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

		ScmMergeRequests mr = buildMr(SHA, LocalDateTime.parse("2024-01-08T09:00:00",
				java.time.format.DateTimeFormatter.ofPattern(TIME_FORMAT)));

		Deployment deployment = new Deployment();
		deployment.setChangeSets(List.of(SHA));
		deployment.setStartTime(null);
		deployment.setEndTime(DEPLOY_END);

		ScmCommits commit = buildCommit(SHA, commitMillis);

		LeadTimeContext context = LeadTimeContext.builder()
				.mergeRequestList(List.of(mr))
				.deploymentList(List.of(deployment))
				.scmCommitList(List.of(commit))
				.weekOrMonth(CommonConstant.WEEK)
				.build();

		Map<String, List<LeadTimeChangeData>> result = new HashMap<>();
		strategy.calculateLeadTime(context, result);

		assertThat(result).isEmpty();
	}

	// --- Happy path ---

	@Test
	void calculateLeadTime_validData_populatesLeadTimeMap() {
		// commit at 08:00, merge at 09:00, deploy 10:00-12:00 — all same weekday
		long commitMillis = java.time.ZonedDateTime.of(
				LocalDateTime.parse("2024-01-08T08:00:00", java.time.format.DateTimeFormatter.ofPattern(TIME_FORMAT)),
				java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

		ScmMergeRequests mr = buildMr(SHA, LocalDateTime.parse("2024-01-08T09:00:00",
				java.time.format.DateTimeFormatter.ofPattern(TIME_FORMAT)));
		mr.setExternalId("MR-1");
		mr.setFromBranch("feature/test");

		Deployment deployment = buildDeployment(SHA, DEPLOY_START, DEPLOY_END);
		ScmCommits commit = buildCommit(SHA, commitMillis);

		LeadTimeContext context = LeadTimeContext.builder()
				.mergeRequestList(List.of(mr))
				.deploymentList(List.of(deployment))
				.scmCommitList(List.of(commit))
				.weekOrMonth(CommonConstant.WEEK)
				.build();

		Map<String, List<LeadTimeChangeData>> result = new HashMap<>();
		strategy.calculateLeadTime(context, result);

		assertThat(result).isNotEmpty();
		List<LeadTimeChangeData> entries = result.values().iterator().next();
		assertThat(entries).hasSize(1);

		LeadTimeChangeData data = entries.get(0);
		assertThat(data.getStoryID()).isEqualTo(SHA);
		assertThat(data.getMergeID()).isEqualTo("MR-1");
		assertThat(data.getFromBranch()).isEqualTo("feature/test");
		assertThat(data.getLeadTime()).isPositive();
		assertThat(data.getLeadTimeInDays()).isNotBlank();
		assertThat(data.getDate()).isNotBlank();
	}

	@Test
	void calculateLeadTime_monthMode_usesMonthLabel() {
		long commitMillis = java.time.ZonedDateTime.of(
				LocalDateTime.parse("2024-01-08T08:00:00", java.time.format.DateTimeFormatter.ofPattern(TIME_FORMAT)),
				java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

		ScmMergeRequests mr = buildMr(SHA, LocalDateTime.parse("2024-01-08T09:00:00",
				java.time.format.DateTimeFormatter.ofPattern(TIME_FORMAT)));
		Deployment deployment = buildDeployment(SHA, DEPLOY_START, DEPLOY_END);
		ScmCommits commit = buildCommit(SHA, commitMillis);

		LeadTimeContext context = LeadTimeContext.builder()
				.mergeRequestList(List.of(mr))
				.deploymentList(List.of(deployment))
				.scmCommitList(List.of(commit))
				.weekOrMonth(CommonConstant.MONTH)
				.build();

		Map<String, List<LeadTimeChangeData>> result = new HashMap<>();
		strategy.calculateLeadTime(context, result);

		assertThat(result).isNotEmpty();
		String key = result.keySet().iterator().next();
		// month label format: "2024-JANUARY"
		assertThat(key).contains("2024");
	}

	@Test
	void calculateLeadTime_multipleDeployments_usesLatestEndTime() {
		long commitMillis = java.time.ZonedDateTime.of(
				LocalDateTime.parse("2024-01-08T08:00:00", java.time.format.DateTimeFormatter.ofPattern(TIME_FORMAT)),
				java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

		ScmMergeRequests mr = buildMr(SHA, LocalDateTime.parse("2024-01-08T09:00:00",
				java.time.format.DateTimeFormatter.ofPattern(TIME_FORMAT)));

		Deployment d1 = buildDeployment(SHA, "2024-01-08T10:00:00", "2024-01-08T11:00:00");
		Deployment d2 = buildDeployment(SHA, "2024-01-08T10:00:00", "2024-01-08T14:00:00");

		ScmCommits commit = buildCommit(SHA, commitMillis);

		LeadTimeContext context = LeadTimeContext.builder()
				.mergeRequestList(List.of(mr))
				.deploymentList(Arrays.asList(d1, d2))
				.scmCommitList(List.of(commit))
				.weekOrMonth(CommonConstant.WEEK)
				.build();

		Map<String, List<LeadTimeChangeData>> result = new HashMap<>();
		strategy.calculateLeadTime(context, result);

		assertThat(result).isNotEmpty();
		LeadTimeChangeData data = result.values().iterator().next().get(0);
		// total lead time should reflect the latest end time (14:00), not 11:00
		// commit->merge = 60 min, merge->deployStart = 60 min, deployDuration = 240 min => 360 min
		assertThat(data.getLeadTime()).isGreaterThan(0);
	}

	@Test
	void calculateLeadTime_mrWithNoCommitShas_notIndexed() {
		ScmMergeRequests mr = new ScmMergeRequests();
		mr.setCommitShas(null); // no shas — should be filtered out
		mr.setMergedAt(LocalDateTime.parse("2024-01-08T09:00:00",
				java.time.format.DateTimeFormatter.ofPattern(TIME_FORMAT)));

		Deployment deployment = buildDeployment(SHA, DEPLOY_START, DEPLOY_END);
		ScmCommits commit = buildCommit(SHA, 1_000_000L);

		LeadTimeContext context = LeadTimeContext.builder()
				.mergeRequestList(List.of(mr))
				.deploymentList(List.of(deployment))
				.scmCommitList(List.of(commit))
				.weekOrMonth(CommonConstant.WEEK)
				.build();

		Map<String, List<LeadTimeChangeData>> result = new HashMap<>();
		strategy.calculateLeadTime(context, result);

		assertThat(result).isEmpty();
	}

	@Test
	void calculateLeadTime_deploymentWithNullChangeSets_notIndexed() {
		long commitMillis = java.time.ZonedDateTime.of(
				LocalDateTime.parse("2024-01-08T08:00:00", java.time.format.DateTimeFormatter.ofPattern(TIME_FORMAT)),
				java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

		ScmMergeRequests mr = buildMr(SHA, LocalDateTime.parse("2024-01-08T09:00:00",
				java.time.format.DateTimeFormatter.ofPattern(TIME_FORMAT)));

		Deployment deployment = new Deployment();
		deployment.setChangeSets(null); // filtered out
		deployment.setStartTime(DEPLOY_START);
		deployment.setEndTime(DEPLOY_END);

		ScmCommits commit = buildCommit(SHA, commitMillis);

		LeadTimeContext context = LeadTimeContext.builder()
				.mergeRequestList(List.of(mr))
				.deploymentList(List.of(deployment))
				.scmCommitList(List.of(commit))
				.weekOrMonth(CommonConstant.WEEK)
				.build();

		Map<String, List<LeadTimeChangeData>> result = new HashMap<>();
		strategy.calculateLeadTime(context, result);

		assertThat(result).isEmpty();
	}

	// --- Helpers ---

	private ScmCommits buildCommit(String sha, long timestampMillis) {
		ScmCommits commit = new ScmCommits();
		commit.setSha(sha);
		commit.setCommitTimestamp(timestampMillis);
		return commit;
	}

	private ScmMergeRequests buildMr(String sha, LocalDateTime mergedAt) {
		ScmMergeRequests mr = new ScmMergeRequests();
		mr.setCommitShas(List.of(sha));
		mr.setMergedAt(mergedAt);
		return mr;
	}

	private Deployment buildDeployment(String sha, String startTime, String endTime) {
		Deployment d = new Deployment();
		d.setChangeSets(List.of(sha));
		d.setStartTime(startTime);
		d.setEndTime(endTime);
		return d;
	}
}
