package com.publicissapient.kpidashboard.apis.util;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;

import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.data.JiraIssueHistoryDataFactory;
import com.publicissapient.kpidashboard.common.model.application.CycleTime;
import com.publicissapient.kpidashboard.common.model.application.CycleTimeValidationData;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BacklogKpiHelperTest {

    private Map<String, Map<String, List<JiraIssueCustomHistory>>> rangeWiseJiraIssuesMap;
    List<String> xAxisRange;
    private Map<Long, String> monthRangeMap;
    @Mock
    private CustomApiConfig customApiConfig;
    private Map<String, Map<String, Object>> uniqueProjectMap;
    List<JiraIssueCustomHistory> projectHistories;
    String startDate;
    String endDate;

    @Before
    public void setUp() {
        rangeWiseJiraIssuesMap = new HashMap<>();
        xAxisRange = List.of("< 1 Week","< 2 Weeks","< 1 Months","< 3 Months","< 6 Months");
        monthRangeMap = new HashMap<>();
        startDate = "2023-01-01";
        endDate = "2023-12-31";
        JiraIssueHistoryDataFactory issueHistoryFactory = JiraIssueHistoryDataFactory.newInstance();
        projectHistories = issueHistoryFactory.getJiraIssueCustomHistory();
        uniqueProjectMap= new HashMap<>();

        Map<String, Object> filters = new HashMap<>();
        filters.put("storyType", Collections.singletonList(Pattern.compile("Bug")));
        filters.put(BacklogKpiHelper.STATUS_UPDATION_LOG_STORY_CHANGED_TO, Collections.singletonList(Pattern.compile("Done")));
        uniqueProjectMap.put("project1", filters);

    }

    @Test
    public void testInitializeRangeMapForProjects() {
        BacklogKpiHelper.initializeRangeMapForProjects(rangeWiseJiraIssuesMap, xAxisRange, monthRangeMap);

        assertEquals(5, rangeWiseJiraIssuesMap.size());
        assertEquals(5, monthRangeMap.size());
    }

    @Test
    public void testSetRangeWiseJiraIssuesMap() {
        JiraIssueCustomHistory issueCustomHistory = new JiraIssueCustomHistory();
        issueCustomHistory.setStoryType("Bug");
        LocalDateTime closedDate = LocalDateTime.now().minusDays(10);

        boolean result = BacklogKpiHelper.setRangeWiseJiraIssuesMap(rangeWiseJiraIssuesMap, issueCustomHistory, closedDate, monthRangeMap);

        assertFalse(result);
        assertTrue(rangeWiseJiraIssuesMap.isEmpty());
    }


    @Test
    public void testSetLiveTime() {
        CycleTimeValidationData cycleTimeValidationData = new CycleTimeValidationData();
        CycleTime cycleTime = new CycleTime();
        JiraHistoryChangeLog statusUpdateLog = new JiraHistoryChangeLog();
        DateTime updatedOn = DateTime.now();
        List<String> liveStatus = Arrays.asList("done", "live");

        statusUpdateLog.setChangedTo("live");
        statusUpdateLog.setUpdatedOn(LocalDateTime.now());
        BacklogKpiHelper.setLiveTime(cycleTimeValidationData, cycleTime, statusUpdateLog, updatedOn, liveStatus);

        assertNotNull(cycleTime.getLiveTime());
        assertEquals(updatedOn, cycleTime.getLiveTime());
    }

    @Test
    public void testSetReadyTime() {
        CycleTimeValidationData cycleTimeValidationData = new CycleTimeValidationData();
        CycleTime cycleTime = new CycleTime();
        JiraHistoryChangeLog statusUpdateLog = new JiraHistoryChangeLog();
        DateTime updatedOn = DateTime.now();
        List<String> dorStatus = Arrays.asList("ready", "in progress");

        statusUpdateLog.setChangedTo("ready");
        statusUpdateLog.setUpdatedOn(LocalDateTime.now());
        BacklogKpiHelper.setReadyTime(cycleTimeValidationData, cycleTime, statusUpdateLog, updatedOn, dorStatus);

        assertNotNull(cycleTime.getReadyTime());
        assertEquals(updatedOn, cycleTime.getReadyTime());
    }

    @Test
    public void testSetDODTime() {
        JiraHistoryChangeLog statusUpdateLog = new JiraHistoryChangeLog();
        DateTime updatedOn = new DateTime();
        List<String> dodStatus = Arrays.asList("done", "closed");
        String storyFirstStatus = "open";
        Map<String, DateTime> dodStatusDateMap = new HashMap<>();

        statusUpdateLog.setChangedTo("done");
        BacklogKpiHelper.setDODTime(statusUpdateLog, updatedOn, dodStatus, storyFirstStatus, dodStatusDateMap);

        assertTrue(dodStatusDateMap.containsKey("done"));
        assertEquals(updatedOn, dodStatusDateMap.get("done"));
    }

    @Test
    public void testSetValueInCycleTime() {
        DateTime startTime = new DateTime().minusDays(5);
        DateTime endTime = new DateTime();
        CycleTimeValidationData cycleTimeValidationData = new CycleTimeValidationData();
        Set<String> issueTypes = new HashSet<>();

        String result = BacklogKpiHelper.setValueInCycleTime(startTime, endTime, "LEAD TIME", cycleTimeValidationData, issueTypes);

        assertNotEquals(Constant.NOT_AVAILABLE, result);
        assertTrue(cycleTimeValidationData.getLeadTime() > 0);
    }

    @Test
    public void testFilterProjectHistories_Positive() {
        List<JiraIssueCustomHistory> filteredHistories = BacklogKpiHelper.filterProjectHistories(
                projectHistories, uniqueProjectMap, startDate, endDate);

        assertEquals(0, filteredHistories.size());
    }

    @Test
    public void testFilterProjectHistories_Negative_NoMatch() {
        uniqueProjectMap.get("project1").put("storyType", Collections.singletonList(Pattern.compile("Feature")));

        List<JiraIssueCustomHistory> filteredHistories = BacklogKpiHelper.filterProjectHistories(
                projectHistories, uniqueProjectMap, startDate, endDate);

        assertEquals(0, filteredHistories.size());
    }

    @Test
    public void testFilterProjectHistories_Negative_EmptyHistory() {
        List<JiraIssueCustomHistory> filteredHistories = BacklogKpiHelper.filterProjectHistories(
                new ArrayList<>(), uniqueProjectMap, startDate, endDate);

        assertEquals(0, filteredHistories.size());
    }

    @Test
    public void testFilterProjectHistories_Negative_NoFilters() {
        uniqueProjectMap.clear();

        List<JiraIssueCustomHistory> filteredHistories = BacklogKpiHelper.filterProjectHistories(
                projectHistories, uniqueProjectMap, startDate, endDate);

        assertEquals(0, filteredHistories.size());
    }
    // Additional test methods for other methods in BacklogKpiHelper
}
