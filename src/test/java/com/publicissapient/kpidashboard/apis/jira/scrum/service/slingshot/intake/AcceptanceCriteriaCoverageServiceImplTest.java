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

package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.intake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.intake.AcceptanceCriteriaCoverageServiceImpl.CoverageBandValue;
import com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.intake.AcceptanceCriteriaCoverageServiceImpl.StoryRecord;
import com.publicissapient.kpidashboard.apis.util.AcceptanceCriteriaCounter;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.util.DateUtil;

/** Tests for {@link AcceptanceCriteriaCoverageServiceImpl} (kpi227 — Slingshot / Intake). */
class AcceptanceCriteriaCoverageServiceImplTest {

	private final AcceptanceCriteriaCoverageServiceImpl service =
			new AcceptanceCriteriaCoverageServiceImpl();

	@Test
	@DisplayName("the KPI is registered under its own qualifier")
	void qualifier() {
		assertEquals(KPICode.ACCEPTANCE_CRITERIA_COVERAGE.name(), service.getQualifierType());
		assertEquals("kpi227", KPICode.ACCEPTANCE_CRITERIA_COVERAGE.getKpiId());
	}

	@Test
	@DisplayName("coverage bands split on the documented boundaries")
	void bands() {
		assertEquals(
				AcceptanceCriteriaCoverageServiceImpl.BAND_NONE,
				AcceptanceCriteriaCoverageServiceImpl.band(0));
		assertEquals(
				AcceptanceCriteriaCoverageServiceImpl.BAND_THIN,
				AcceptanceCriteriaCoverageServiceImpl.band(1));
		assertEquals(
				AcceptanceCriteriaCoverageServiceImpl.BAND_THIN,
				AcceptanceCriteriaCoverageServiceImpl.band(2));
		assertEquals(
				AcceptanceCriteriaCoverageServiceImpl.BAND_HEALTHY,
				AcceptanceCriteriaCoverageServiceImpl.band(3));
		assertEquals(
				AcceptanceCriteriaCoverageServiceImpl.BAND_HEALTHY,
				AcceptanceCriteriaCoverageServiceImpl.band(5));
		assertEquals(
				AcceptanceCriteriaCoverageServiceImpl.BAND_DETAILED,
				AcceptanceCriteriaCoverageServiceImpl.band(6));
		assertEquals(
				AcceptanceCriteriaCoverageServiceImpl.BAND_DETAILED,
				AcceptanceCriteriaCoverageServiceImpl.band(7));
		assertEquals(
				AcceptanceCriteriaCoverageServiceImpl.BAND_OVER_SPECIFIED,
				AcceptanceCriteriaCoverageServiceImpl.band(8));
		assertEquals(
				AcceptanceCriteriaCoverageServiceImpl.BAND_OVER_SPECIFIED,
				AcceptanceCriteriaCoverageServiceImpl.band(40));
	}

	@Test
	@DisplayName("the earliest transition into an In Progress status is the sampling moment")
	void firstTransitionWins() {
		LocalDateTime firstStart = LocalDateTime.of(2026, 3, 4, 10, 0);
		JiraIssueCustomHistory history =
				history(
						"STORY-1",
						log("To Do", LocalDateTime.of(2026, 3, 2, 9, 0)),
						log("In Progress", firstStart),
						log("In Review", LocalDateTime.of(2026, 3, 6, 10, 0)),
						// bounced back and restarted - must not move the sampling moment
						log("In Progress", LocalDateTime.of(2026, 3, 9, 10, 0)));

		Map<String, LocalDateTime> startedOn =
				AcceptanceCriteriaCoverageServiceImpl.firstInProgressTransition(
						List.of(history), Set.of("in progress"));

		// the change log getter normalises to UTC on read, so compare against the same
		// normalisation
		assertEquals(DateUtil.localDateTimeToUTC(firstStart), startedOn.get("STORY-1"));
	}

	@Test
	@DisplayName("status matching is case and whitespace insensitive")
	void statusMatchingIsLenient() {
		JiraIssueCustomHistory history =
				history("STORY-2", log("  In PROGRESS ", LocalDateTime.of(2026, 3, 4, 10, 0)));

		Map<String, LocalDateTime> startedOn =
				AcceptanceCriteriaCoverageServiceImpl.firstInProgressTransition(
						List.of(history), Set.of("in progress"));

		assertTrue(startedOn.containsKey("STORY-2"));
	}

	@Test
	@DisplayName("the KPI reads only its own mapping, never another KPI's")
	void readsOnlyItsOwnMapping() {
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setJiraStatusForInProgressKPI227(List.of("Construction"));
		fieldMapping.setJiraStoryIdentificationKPI227(List.of("Task", "QA Task"));
		// a neighbouring KPI saying something different must not leak in
		fieldMapping.setJiraStatusForInProgressKPI148(List.of("In Development"));
		fieldMapping.setJiraStoryIdentificationKPI129(List.of("Story"));

		assertEquals(
				Set.of("Construction"),
				AcceptanceCriteriaCoverageServiceImpl.resolveInProgressStatuses(fieldMapping));
		assertEquals(
				Set.of("Task", "QA Task"),
				AcceptanceCriteriaCoverageServiceImpl.resolveStoryTypes(fieldMapping));
	}

	@Test
	@DisplayName("an unset mapping falls back to the documented default, not to another KPI")
	void unsetMappingUsesDefaults() {
		// The migration seeds these fields once, so the value stays visible and
		// editable in the
		// project configuration. Nothing is resolved from a neighbouring KPI at read
		// time.
		FieldMapping borrowsNothing = new FieldMapping();
		borrowsNothing.setJiraStatusForInProgressKPI148(List.of("In Development"));
		borrowsNothing.setJiraStoryIdentificationKPI129(List.of("Enabler"));

		assertEquals(
				Set.of("In Progress"),
				AcceptanceCriteriaCoverageServiceImpl.resolveInProgressStatuses(borrowsNothing));
		assertEquals(
				Set.of("Story"), AcceptanceCriteriaCoverageServiceImpl.resolveStoryTypes(borrowsNothing));
	}

	@Test
	@DisplayName("an empty list is treated as unset rather than as an answer")
	void emptyListIsUnset() {
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setJiraStatusForInProgressKPI227(new ArrayList<>());

		assertEquals(
				Set.of("In Progress"),
				AcceptanceCriteriaCoverageServiceImpl.resolveInProgressStatuses(fieldMapping));
	}

	@Test
	@DisplayName("a missing field mapping does not blow up")
	void nullFieldMappingIsSafe() {
		assertEquals(
				Set.of("In Progress"),
				AcceptanceCriteriaCoverageServiceImpl.resolveInProgressStatuses(null));
		assertEquals(Set.of("Story"), AcceptanceCriteriaCoverageServiceImpl.resolveStoryTypes(null));
	}

	@Test
	@DisplayName("a story with no jira_issue document is still counted, from its history")
	void missingIssueFallsBackToHistory() {
		// jira_issue and jira_issue_custom_history are written together by the
		// processor, so a
		// history without its issue means the issue was removed afterwards. The story
		// still
		// entered In Progress, so dropping it would shrink the denominator and inflate
		// the average.
		JiraIssueCustomHistory history = history("STORY-8");
		history.setStoryType("QA Task");
		history.setUrl("http://jira/STORY-8");
		history.setDescription("from history");

		StoryRecord story =
				AcceptanceCriteriaCoverageServiceImpl.toStoryRecord(
						"STORY-8",
						null,
						history,
						LocalDateTime.of(2026, 3, 4, 10, 0),
						AcceptanceCriteriaCounter.count(null, AcceptanceCriteriaCounter.Format.AUTO));

		assertEquals("QA Task", story.issueType());
		assertEquals("http://jira/STORY-8", story.url());
		assertEquals("from history", story.description());
		assertEquals(0, story.criteriaCount());
		assertEquals(AcceptanceCriteriaCoverageServiceImpl.BAND_NONE, story.band());
	}

	@Test
	@DisplayName("a story that never reached In Progress is excluded entirely")
	void neverStartedIsExcluded() {
		JiraIssueCustomHistory history =
				history(
						"STORY-3",
						log("To Do", LocalDateTime.of(2026, 3, 2, 9, 0)),
						log("Blocked", LocalDateTime.of(2026, 3, 3, 9, 0)));

		Map<String, LocalDateTime> startedOn =
				AcceptanceCriteriaCoverageServiceImpl.firstInProgressTransition(
						List.of(history), Set.of("in progress"));

		assertTrue(startedOn.isEmpty());
	}

	@Test
	@DisplayName("a null timestamp on a change log entry is skipped, not fatal")
	void defensiveAgainstBadData() {
		JiraIssueCustomHistory empty = new JiraIssueCustomHistory();
		empty.setStoryID("STORY-4");
		empty.setStatusUpdationLog(null);

		JiraIssueCustomHistory blank =
				history("STORY-5", log("   ", LocalDateTime.of(2026, 3, 2, 9, 0)), log(null, null));

		// an In Progress transition with no timestamp at all must not blow up the scan
		JiraIssueCustomHistory undated = history("STORY-6", log("In Progress", null));

		Map<String, LocalDateTime> startedOn =
				AcceptanceCriteriaCoverageServiceImpl.firstInProgressTransition(
						List.of(empty, blank, undated), Set.of("in progress"));

		assertTrue(startedOn.isEmpty());
	}

	@Test
	@DisplayName("an undated entry does not hide a later, properly dated transition")
	void undatedEntryDoesNotHideRealTransition() {
		LocalDateTime realStart = LocalDateTime.of(2026, 3, 4, 10, 0);
		JiraIssueCustomHistory history =
				history("STORY-7", log("In Progress", null), log("In Progress", realStart));

		Map<String, LocalDateTime> startedOn =
				AcceptanceCriteriaCoverageServiceImpl.firstInProgressTransition(
						List.of(history), Set.of("in progress"));

		assertEquals(DateUtil.localDateTimeToUTC(realStart), startedOn.get("STORY-7"));
	}

	@Test
	@DisplayName("every band is always present in the drill-down, even at zero")
	void drillDownHasStableShape() {
		List<CoverageBandValue> drillDown =
				AcceptanceCriteriaCoverageServiceImpl.buildBandDrillDown(
						List.of(record("S-1", 0), record("S-2", 4), record("S-3", 4)));

		Map<String, Long> byBand =
				drillDown.stream()
						.collect(Collectors.toMap(CoverageBandValue::band, CoverageBandValue::count));

		assertEquals(5, drillDown.size());
		assertEquals(1L, byBand.get(AcceptanceCriteriaCoverageServiceImpl.BAND_NONE));
		assertEquals(0L, byBand.get(AcceptanceCriteriaCoverageServiceImpl.BAND_THIN));
		assertEquals(2L, byBand.get(AcceptanceCriteriaCoverageServiceImpl.BAND_HEALTHY));
		assertEquals(0L, byBand.get(AcceptanceCriteriaCoverageServiceImpl.BAND_DETAILED));
		assertEquals(0L, byBand.get(AcceptanceCriteriaCoverageServiceImpl.BAND_OVER_SPECIFIED));
	}

	@Test
	@DisplayName("band distributions add up when several projects roll into one node")
	void drillDownAggregation() {
		Object projectOne =
				AcceptanceCriteriaCoverageServiceImpl.buildBandDrillDown(
						List.of(record("S-1", 0), record("S-2", 4)));
		Object projectTwo =
				AcceptanceCriteriaCoverageServiceImpl.buildBandDrillDown(
						List.of(record("S-3", 4), record("S-4", 9)));

		@SuppressWarnings("unchecked")
		List<CoverageBandValue> merged =
				(List<CoverageBandValue>) service.calculateDrillDownValue(List.of(projectOne, projectTwo));

		Map<String, Long> byBand =
				merged.stream()
						.collect(Collectors.toMap(CoverageBandValue::band, CoverageBandValue::count));

		assertEquals(1L, byBand.get(AcceptanceCriteriaCoverageServiceImpl.BAND_NONE));
		assertEquals(2L, byBand.get(AcceptanceCriteriaCoverageServiceImpl.BAND_HEALTHY));
		assertEquals(1L, byBand.get(AcceptanceCriteriaCoverageServiceImpl.BAND_OVER_SPECIFIED));
	}

	@Test
	@DisplayName("merging tolerates null and unexpected drill-down payloads")
	void drillDownAggregationIsDefensive() {
		@SuppressWarnings("unchecked")
		List<CoverageBandValue> merged =
				(List<CoverageBandValue>) service.calculateDrillDownValue(null);
		assertEquals(5, merged.size());
		assertTrue(merged.stream().allMatch(value -> value.count() == 0L));
	}

	@Test
	@DisplayName("the plotted window starts on a Monday for weeks and the 1st for months")
	void windowStart() {
		assertEquals(
				java.time.DayOfWeek.MONDAY,
				AcceptanceCriteriaCoverageServiceImpl.windowStart(CommonConstant.WEEK, 12).getDayOfWeek());
		assertEquals(
				1,
				AcceptanceCriteriaCoverageServiceImpl.windowStart(CommonConstant.MONTH, 6).getDayOfMonth());
	}

	@Test
	@DisplayName("period labels are week ranges or year-month, and are stable within a period")
	void periodLabels() {
		String wednesday =
				AcceptanceCriteriaCoverageServiceImpl.periodLabel(
						CommonConstant.WEEK, LocalDateTime.of(2026, 3, 4, 10, 0));
		String fridaySameWeek =
				AcceptanceCriteriaCoverageServiceImpl.periodLabel(
						CommonConstant.WEEK, LocalDateTime.of(2026, 3, 6, 18, 0));

		assertEquals(wednesday, fridaySameWeek);
		assertTrue(wednesday.contains(" to "));

		assertEquals(
				"2026-3",
				AcceptanceCriteriaCoverageServiceImpl.periodLabel(
						CommonConstant.MONTH, LocalDateTime.of(2026, 3, 4, 10, 0)));
		assertFalse(
				AcceptanceCriteriaCoverageServiceImpl.periodLabel(
								CommonConstant.MONTH, LocalDateTime.of(2026, 4, 4, 10, 0))
						.equals("2026-3"));
	}

	// ── helpers ─────────────────────────────────────────────────────────────

	private static JiraIssueCustomHistory history(String storyId, JiraHistoryChangeLog... logs) {
		JiraIssueCustomHistory history = new JiraIssueCustomHistory();
		history.setStoryID(storyId);
		history.setStatusUpdationLog(new ArrayList<>(List.of(logs)));
		return history;
	}

	private static JiraHistoryChangeLog log(String changedTo, LocalDateTime updatedOn) {
		JiraHistoryChangeLog changeLog = new JiraHistoryChangeLog();
		changeLog.setChangedTo(changedTo);
		changeLog.setUpdatedOn(updatedOn);
		return changeLog;
	}

	private static StoryRecord record(String storyId, int criteriaCount) {
		return new StoryRecord(
				storyId,
				"http://jira/" + storyId,
				"Story",
				"desc",
				"In Progress",
				LocalDateTime.of(2026, 3, 4, 10, 0),
				criteriaCount,
				"AUTO",
				AcceptanceCriteriaCoverageServiceImpl.band(criteriaCount));
	}
}
