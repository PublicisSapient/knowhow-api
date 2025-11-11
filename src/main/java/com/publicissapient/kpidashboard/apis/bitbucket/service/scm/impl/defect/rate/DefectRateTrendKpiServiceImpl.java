package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.defect.rate;

import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.strategy.KpiCalculationStrategy;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.repotools.model.RepoToolValidationData;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.util.DateUtil;
import org.apache.commons.collections4.CollectionUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefectRateTrendKpiServiceImpl implements KpiCalculationStrategy {

    private static final String MR_COUNT = "No of PRs";
    private static final List<String> DEFECT_KEYWORDS = List.of("fix", "bug", "repair", "defect");

	@Override
	public Object getTrendValueList(KpiRequest kpiRequest, List<ScmMergeRequests> mergeRequests, List<Tool> scmTools,
			List<RepoToolValidationData> validationDataList, Set<Assignee> assignees, String projectName) {
        Map<String, List<DataCount>> kpiTrendDataByGroup = new LinkedHashMap<>();
        int dataPoints = kpiRequest.getXAxisDataPoints();
        LocalDateTime currentDate = DateUtil.getTodayTime();
        String duration = kpiRequest.getDuration();
        for (int i = 0; i < dataPoints; i++) {
            CustomDateRange periodRange = KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, duration);
            String dateLabel = KpiHelperService.getDateRange(periodRange, duration);
            List<ScmMergeRequests> filteredMergeRequests = mergeRequests.stream()
                    .filter(request -> DateUtil.isWithinDateTimeRange(
                            DateUtil.convertMillisToLocalDateTime(request.getUpdatedDate()),
                            periodRange.getStartDateTime(), periodRange.getEndDateTime()))
                    .toList();

            scmTools.forEach(tool -> processToolData(tool, filteredMergeRequests, assignees, kpiTrendDataByGroup,
                    validationDataList, dateLabel, projectName));

            currentDate = DeveloperKpiHelper.getNextRangeDate(duration, currentDate);
        }
        return kpiTrendDataByGroup;
    }

    private void processToolData(Tool tool, List<ScmMergeRequests> mergeRequests, Set<Assignee> assignees,
                                 Map<String, List<DataCount>> kpiTrendDataByGroup, List<RepoToolValidationData> validationDataList,
                                 String dateLabel, String projectName) {
        if (!DeveloperKpiHelper.isValidTool(tool)) {
            return;
        }

        String branchName = DeveloperKpiHelper.getBranchSubFilter(tool, projectName);
        String overallKpiGroup = branchName + "#" + Constant.AGGREGATED_VALUE;

        List<ScmMergeRequests> mergeRequestsForBranch = DeveloperKpiHelper.filterMergeRequestsForBranch(mergeRequests,
                tool);

        double meanTimeToMergeSeconds = calculateMeanTimeToMerge(mergeRequestsForBranch);
        long meanTimeToMergeHours = KpiHelperService.convertMilliSecondsToHours(meanTimeToMergeSeconds * 1000);

        DeveloperKpiHelper.setDataCount(projectName, dateLabel, overallKpiGroup, meanTimeToMergeHours, new HashMap<>(),
                kpiTrendDataByGroup);

        Map<String, List<ScmMergeRequests>> userWiseMergeRequests = DeveloperKpiHelper
                .groupMergeRequestsByUser(mergeRequestsForBranch);

        validationDataList.addAll(prepareUserValidationData(userWiseMergeRequests, assignees, tool, projectName,
                dateLabel, kpiTrendDataByGroup));
    }

    private List<RepoToolValidationData> prepareUserValidationData(
            Map<String, List<ScmMergeRequests>> userWiseMergeRequests, Set<Assignee> assignees, Tool tool,
            String projectName, String dateLabel, Map<String, List<DataCount>> kpiTrendDataByGroup) {
        return userWiseMergeRequests.entrySet().stream().map(entry -> {
            String userEmail = entry.getKey();
            List<ScmMergeRequests> userMergeRequests = entry.getValue();

            String developerName = DeveloperKpiHelper.getDeveloperName(userEmail, assignees);
            long defectMergeRequestsCount = userMergeRequests.stream()
                    .filter(mergeRequest -> mergeRequest.getTitle() != null && DEFECT_KEYWORDS.stream()
                            .anyMatch(keyword -> mergeRequest.getTitle().toLowerCase().contains(keyword.toLowerCase())))
                    .count();

            long totalMergeRequests = userMergeRequests.size();

            double defectRate = totalMergeRequests > 0 ? (defectMergeRequestsCount * 100.0) / totalMergeRequests : 0.0;

            String userKpiGroup = DeveloperKpiHelper.getBranchSubFilter(tool, projectName) + "#" + developerName;

            DeveloperKpiHelper.setDataCount(projectName, dateLabel, userKpiGroup, defectRate,
                    Map.of(MR_COUNT, defectMergeRequestsCount), kpiTrendDataByGroup);

            return createValidationData(projectName, tool, developerName, dateLabel, defectRate,
                    defectMergeRequestsCount, totalMergeRequests);
        }).toList();
    }

    private RepoToolValidationData createValidationData(String projectName, Tool tool, String developerName,
                                                        String dateLabel, double defectRate, long defectMrCount, long mrCount) {
        RepoToolValidationData validationData = new RepoToolValidationData();
        validationData.setProjectName(projectName);
        validationData.setBranchName(tool.getBranch());
        validationData.setRepoUrl(tool.getRepositoryName() != null ? tool.getRepositoryName() : tool.getRepoSlug());
        validationData.setDeveloperName(developerName);
        validationData.setDate(dateLabel);
        validationData.setDefectRate(defectRate);
        validationData.setKpiPRs(defectMrCount);
        validationData.setMrCount(mrCount);
        return validationData;
    }

    private double calculateMeanTimeToMerge(List<ScmMergeRequests> mergeRequests) {
        if (CollectionUtils.isEmpty(mergeRequests)) {
            return 0.0;
        }

        List<Long> timeToMergeList = mergeRequests.stream()
                .filter(mr -> mr.getCreatedDate() != null && mr.getMergedAt() != null)
                .filter(mr -> ScmMergeRequests.MergeRequestState.MERGED.name().equalsIgnoreCase(mr.getState()))
                .map(mr -> {
                    LocalDateTime createdDateTime = DateUtil.convertMillisToLocalDateTime(mr.getCreatedDate());
                    return ChronoUnit.SECONDS.between(createdDateTime, mr.getMergedAt());
                }).toList();

        if (CollectionUtils.isEmpty(timeToMergeList)) {
            return 0.0;
        }

        return timeToMergeList.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }
}
