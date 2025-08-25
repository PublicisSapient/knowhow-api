package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.bitbucket.service.BitBucketKPIService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.ProjectFilter;
import com.publicissapient.kpidashboard.apis.repotools.model.RepoToolValidationData;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class ScmReworkRateServiceImpl extends BitBucketKPIService<Long, List<Object>, Map<String, Object>> {

    private static final String ASSIGNEE_SET = "assigneeSet";
    private static final String COMMIT_DETAILS_LIST = "commitDetailsList";

    @Autowired
    private ConfigHelperService configHelperService;

    @Autowired
    private KpiHelperService kpiHelperService;

    @Override
    public String getQualifierType() {
        return KPICode.REWORK_RATE.name();
    }

    @Override
    public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, Node projectNode) throws ApplicationException {
        Map<String, Node> nodeMap = Map.of(projectNode.getId(), projectNode);
        projectWiseLeafNodeValue(kpiElement, nodeMap, projectNode, kpiRequest);

        log.debug("[PROJECT-WISE][{}]. Values of leaf node after KPI calculation {}", kpiRequest.getRequestTrackerId(),
                projectNode);

        Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
        calculateAggregatedValueMap(projectNode, nodeWiseKPIValue, KPICode.REWORK_RATE);

        Map<String, List<DataCount>> trendValuesMap = getTrendValuesMap(kpiRequest, kpiElement, nodeWiseKPIValue,
                KPICode.REWORK_RATE);
        kpiElement.setTrendValueList(DeveloperKpiHelper.prepareDataCountGroups(trendValuesMap));
        return kpiElement;
    }

    @SuppressWarnings("unchecked")
    private void projectWiseLeafNodeValue(KpiElement kpiElement, Map<String, Node> mapTmp, Node projectLeafNode,
                                          KpiRequest kpiRequest) {
        CustomDateRange dateRange = KpiDataHelper.getStartAndEndDate(kpiRequest);
        String requestTrackerId = getRequestTrackerId();
        LocalDateTime currentDate = DateUtil.getTodayTime();
        int dataPoints = kpiRequest.getXAxisDataPoints();
        String duration = kpiRequest.getDuration();

        Map<ObjectId, Map<String, List<Tool>>> toolMap = configHelperService.getToolItemMap();
        ObjectId projectConfigId = Optional.ofNullable(projectLeafNode.getProjectFilter())
                .map(ProjectFilter::getBasicProjectConfigId).orElse(null);

        Map<String, List<Tool>> toolListMap = toolMap.getOrDefault(projectConfigId, Map.of());
        List<Tool> scmTools = kpiHelperService.populateSCMToolsRepoList(toolListMap);

        if (CollectionUtils.isEmpty(scmTools)) {
            log.error("[BITBUCKET-AGGREGATED-VALUE]. No SCM tools found for project {}",
                    projectLeafNode.getProjectFilter());
            return;
        }

        Map<String, Object> resultmap = fetchKPIDataFromDb(List.of(projectLeafNode),
                dateRange.getStartDate().toString(), dateRange.getEndDate().toString(), kpiRequest);
        List<ScmCommits> commitsList = (List<ScmCommits>) resultmap.get(COMMIT_DETAILS_LIST);
        Set<Assignee> assignees = new HashSet<>((Collection<Assignee>) resultmap.get(ASSIGNEE_SET));

        if (CollectionUtils.isEmpty(commitsList)) {
            log.error("[BITBUCKET-AGGREGATED-VALUE]. No merge requests found for project {}", projectLeafNode);
            return;
        }

        Map<String, List<DataCount>> aggregatedDataMap = new LinkedHashMap<>();
        List<RepoToolValidationData> validationDataList = new ArrayList<>();
    }
        @Override
    public Long calculateKPIMetrics(Map<String, Object> stringObjectMap) {
        return 0L;
    }

    @Override
    public Map<String, Object> fetchKPIDataFromDb(List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(ASSIGNEE_SET, getScmUsersFromBaseClass());
        resultMap.put(COMMIT_DETAILS_LIST, getMergeRequestsFromBaseClass());
        return resultMap;
    }
}
