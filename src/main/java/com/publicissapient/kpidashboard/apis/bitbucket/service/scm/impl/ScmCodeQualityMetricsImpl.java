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

package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl;

import com.publicissapient.kpidashboard.apis.bitbucket.service.BitBucketKPIService;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.model.*;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * SCM Code Quality Metrics Composite Service Implementation
 *
 * <h3>PURPOSE</h3>
 * Aggregates and combines multiple SCM code quality metrics (Rework Rate and Revert Rate)
 * into a unified response for comprehensive code quality analysis.
 *
 * <h3>METRICS INCLUDED</h3>
 * - <b>Rework Rate:</b> Percentage of code changes that modify recently changed lines (within 21 days)
 * - <b>Revert Rate:</b> Percentage of commits that are later reverted, indicating unstable changes
 *
 * <h3>OUTPUT FORMAT</h3>
 * Returns DataCountGroup list containing:
 * - filter1: "develop -> knowhow-common -> KnowHOW" (branch -> repository -> project)
 * - filter2: "Overall" or specific developer name
 * - value: Array of data objects with:
 *   - data: "Rework Rate" or "Revert Rate"
 *   - date: "10-Nov-2025 to 16-Nov-2025"
 *   - kpiGroup: "develop -> knowhow-common -> KnowHOW#Overall"
 *   - value: percentage (0.0 to 100.0)
 *   - sprojectName: "KnowHOW"
 *
 * @author valsa anil
 * @since 2025
 * @see ScmCodeQualityReworkRateServiceImpl
 * @see ScmCodeQualityRevertRateServiceImpl
 */
@Slf4j
@Service
@AllArgsConstructor
public class ScmCodeQualityMetricsImpl
        extends BitBucketKPIService<Double, List<Object>, Map<String, Object>> {

    @Autowired private ScmCodeQualityRevertRateServiceImpl scmCodeQualityRevertRateServiceImpl;
    @Autowired private ScmCodeQualityReworkRateServiceImpl scmCodeQualityReworkRateServiceImpl;

    /**
     * Returns the qualifier type for this KPI service.
     *
     * @return KPI code name for code quality revert rate
     */
    @Override
    public String getQualifierType() {
        return KPICode.CODE_QUALITY_REVERT_RATE.name();
    }


    /**
     * Retrieves and processes KPI data for both rework rate and revert rate metrics.
     * Sequentially fetches data from both services and combines results into unified DataCountGroup format for UI consumption.
     *
     * @param kpiRequest the KPI request containing filters, date range, and parameters
     * @param kpiElement the KPI element to populate with combined results
     * @param projectNode the project node for which to calculate metrics
     * @return populated KpiElement with List<DataCountGroup> in trendValueList
     * @throws ApplicationException if error occurs during data processing or service calls
     */
    @Override
    public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, Node projectNode)
            throws ApplicationException {
        Map<String, MetricsHolder> metricsHolderMap = new HashMap<>();
        kpiElement.setTrendValueList(metricsHolderMap);

        scmCodeQualityReworkRateServiceImpl.getKpiData(kpiRequest, kpiElement, projectNode);
        scmCodeQualityRevertRateServiceImpl.getKpiData(kpiRequest, kpiElement, projectNode);

        String dateLabel = KPIExcelUtility.getDateLabel(kpiRequest);
        Map<String, DataCountGroup> groupMap = convertMetricsToDataCountGroups(metricsHolderMap, dateLabel);
        kpiElement.setTrendValueList(new ArrayList<>(groupMap.values()));
        populateExcelData(kpiRequest.getRequestTrackerId() , groupMap, kpiElement,dateLabel);
        return kpiElement;
    }

    /**
     * Converts metrics holder map into DataCountGroup format for UI consumption.
     * Groups metrics by filter combinations and adds date labels for both rework and revert rates.
     *
     * @param metricsHolderMap map of metrics holders by filter key
     * @param dateLabel date label string
     * @return Map of DataCountGroup objects for UI display
     */
    private Map<String, DataCountGroup> convertMetricsToDataCountGroups(
            Map<String, MetricsHolder> metricsHolderMap,
            String dateLabel) {
        Map<String, DataCountGroup> groupMap = new HashMap<>();
        
        for (Map.Entry<String, MetricsHolder> entry : metricsHolderMap.entrySet()) {
            String key = entry.getKey();
            String[] filters = KPIExcelUtility.extractFilters(key);
            MetricsHolder holder = entry.getValue();

            DataCountGroup group = groupMap.computeIfAbsent(key, k -> createDataCountGroup(filters));
            
            // Add rework rate data
            DataCount reworkDataCount = createDataCount("Rework Rate", holder.getReworkPercentage(), filters[0], key, dateLabel);
            group.getValue().add(reworkDataCount);
            
            // Add revert rate data
            DataCount revertDataCount = createDataCount("Revert Rate", holder.calculateRevertPercentage(), filters[0], key, dateLabel);
            group.getValue().add(revertDataCount);
        }

        return groupMap;
    }

    private DataCountGroup createDataCountGroup(String[] filters) {
        DataCountGroup group = new DataCountGroup();
        group.setFilter1(filters[0]);
        group.setFilter2(filters[1]);
        group.setValue(new ArrayList<>());
        return group;
    }

    private DataCount createDataCount(String dataType, double value, String filter1, String key, String dateLabel) {
        DataCount dataCount = new DataCount();
        dataCount.setData(dataType);
        dataCount.setValue(value);
        dataCount.setSProjectName(KPIExcelUtility.extractProjectName(filter1));
        dataCount.setKpiGroup(key);
        dataCount.setDate(dateLabel);
        return dataCount;
    }

    /**
     * Calculates KPI metrics from provided data map.
     * Currently not implemented for this composite service.
     *
     * @param stringObjectMap map containing metric calculation data
     * @return null as this method is not used in current implementation
     */
    @Override
    public Double calculateKPIMetrics(Map<String, Object> stringObjectMap) {
        return null;
    }

    /**
     * Fetches KPI data from database.
     * Not implemented as this composite service delegates to individual services.
     *
     * @param leafNodeList list of leaf nodes to process
     * @param startDate start date for data retrieval
     * @param endDate end date for data retrieval
     * @param kpiRequest KPI request parameters
     * @return empty map as data fetching is handled by individual services
     */
    @Override
    public Map<String, Object> fetchKPIDataFromDb(
            List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {
        return Map.of();
    }

    /**
     * Populate excel data
     *
     * @param requestTrackerId request tracker id
     * @param groupMap group map
     * @param kpiElement kpi element
     */
    private void populateExcelData(
            String requestTrackerId,
            Map<String, DataCountGroup> groupMap,
            KpiElement kpiElement,
            String dateLabel) {
        if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
            List<KPIExcelData> excelData = new ArrayList<>();
            KPIExcelUtility.populateCodeQualityMetricsExcelData(groupMap, excelData,dateLabel);
            kpiElement.setExcelData(excelData);
            kpiElement.setExcelColumns(KPIExcelColumn.CODE_QUALITY_METRICS.getColumns());
        }
    }
}
