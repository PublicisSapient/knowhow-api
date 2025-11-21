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
package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1410;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.Filters;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(id = "code_quality_metrics_kpi", order = "014108", author = "valanil", systemVersion = "14.1.0")
public class CodeQualityMetricsChangeUnit {

    private static final String KPI_201 = "kpi201";
    private static final String KPI_180 = "kpi180";
    private static final String KPI_173 = "kpi173";
    private static final String KPI_ID = "kpiId";
    private static final String KPI_MASTER = "kpi_master";
    private static final String KPI_COLUMN_CONFIG = "kpi_column_config";
    private static final String COMBINED_KPI_SOURCE = "Bitbucket/AzureRepository/GitHub/GitLab";

    private static final String PROJECT = "Project";
    private static final String REPO = "Repo";
    private static final String BRANCH = "Branch";
    private static final String DEVELOPER = "Developer";
    private static final String AUTHOR = "Author";
    private static final String DAYS_WEEKS = "Days/Weeks";
    private static final String REWORK_RATE = "Rework Rate";
    private static final String REVERT_RATE = "Revert Rate";
    private static final String REVERT_PR = "Revert PR";
    private static final String NO_OF_MERGE = "No of Merge";

    private static final String DEVELOPER_CATEGORY = "Developer";
    private static final String BITBUCKET_SOURCE = "BitBucket";
    private static final String PERCENTAGE_UNIT = "%";
    private static final String WEEKS_LABEL = "Weeks";
    private static final String PERCENTAGE_LABEL = "Percentage";
    private static final String DROPDOWN_FILTER = "dropDown";
    private static final String AVERAGE_AGGREGATION = "average";
    private static final String LINK_TYPE = "link";
    private static final String MATURITY_RANGE_VALUES = "-80";
    private static final String RED_COLOR = "red";
    private static final String WHITE_COLOR = "white";
    private static final String THRESHOLD_50 = "50";

    private static final String DEFINITION_KEY = "definition";
    private static final String DETAILS_KEY = "details";
    private static final String PARAGRAPH_TYPE = "paragraph";
    private static final String LINE_CHART = "line";
    private static final String PROGRESSBAR_CHART = "progressbar";
    private static final String DETAILED_INFO_TEXT = "Detailed Information at";
    private static final String KPI_INFO_KEY = "kpiInfo";

    private final MongoTemplate mongoTemplate;

    public CodeQualityMetricsChangeUnit(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void execution() {
        insertKpi201();
        insertColumnConfig(KPI_201, Arrays.asList(PROJECT, REPO, BRANCH, DEVELOPER, DAYS_WEEKS, REWORK_RATE, REVERT_RATE));
        deleteKpis(KPI_180, KPI_173);
    }

    @RollbackExecution
    public void rollback() {
        deleteKpis(KPI_201);

        insertKpi173();
        insertColumnConfig(KPI_173, Arrays.asList(PROJECT, REPO, BRANCH, DEVELOPER, DAYS_WEEKS, REWORK_RATE));
        insertKpi180();
        insertColumnConfig(KPI_180, Arrays.asList(PROJECT, REPO, BRANCH, AUTHOR, DAYS_WEEKS, REVERT_PR, NO_OF_MERGE, REVERT_RATE));
    }

    private void deleteKpis(String... kpiIds) {
        for (String kpiId : kpiIds) {
            Bson filter = Filters.eq(KPI_ID, kpiId);
            mongoTemplate.getCollection(KPI_MASTER).deleteOne(filter);
            mongoTemplate.getCollection(KPI_COLUMN_CONFIG).deleteMany(filter);
        }
    }

    private void insertColumnConfig(String kpiId, List<String> columnNames) {
        Map<String, Object> document = new HashMap<>();
        document.put("basicProjectConfigId", null);
        document.put(KPI_ID, kpiId);
        document.put("kpiColumnDetails", createColumns(columnNames));
        mongoTemplate.getCollection(KPI_COLUMN_CONFIG).insertOne(new Document(document));
    }

    private List<Map<String, Object>> createColumns(List<String> columnNames) {
        return columnNames.stream()
                .map(name -> createColumn(name, columnNames.indexOf(name) + 1))
                .toList();
    }

    private Map<String, Object> createColumn(String name, int order) {
        Map<String, Object> col = new HashMap<>();
        col.put("columnName", name);
        col.put("order", order);
        col.put("isShown", true);
        col.put("isDefault", true);
        return col;
    }

    private void insertKpi201() {
        Map<String, Object> document = createBaseKpiDocument(KPI_201, "Code Quality Metrics", 7, 2, PROGRESSBAR_CHART);

        Map<String, Object> kpiInfo = new HashMap<>();
        kpiInfo.put(DEFINITION_KEY, "Code Quality Metrics - a combination of revert rate and rework rate percentage values");
        kpiInfo.put(DETAILS_KEY, Arrays.asList(
                createDetail(PARAGRAPH_TYPE, "Revert Rate: The percentage of total pull requests opened that are reverts.", null),
                createDetail(LINK_TYPE, "Revert Rate - Detailed Information at", "https://knowhow.tools.publicis.sapient.com/wiki/kpi180-Revert+Rate"),
                createDetail(PARAGRAPH_TYPE, "Rework Rate: Percentage of code changes in which an engineer rewrites code that they recently updated (within the past three weeks).", null),
                createDetail(LINK_TYPE, "Rework Rate - Detailed Information at", "https://knowhow.tools.publicis.sapient.com/wiki/kpi173-Rework+Rate")
        ));
        document.put(KPI_INFO_KEY, kpiInfo);
        mongoTemplate.getCollection(KPI_MASTER).insertOne(new Document(document));
    }

    private void insertKpi173() {
        Map<String, Object> document = createBaseKpiDocument(KPI_173, REWORK_RATE, 5, 7, LINE_CHART);

        Map<String, Object> kpiInfo = new HashMap<>();
        kpiInfo.put(DEFINITION_KEY, "Percentage of code changes in which an engineer rewrites code that they recently updated (within the past three weeks).");
        kpiInfo.put(DETAILS_KEY, Arrays.asList(
                createDetail(LINK_TYPE, DETAILED_INFO_TEXT, "https://knowhow.tools.publicis.sapient.com/wiki/kpi173-Rework+Rate")
        ));
        document.put(KPI_INFO_KEY, kpiInfo);

        addAxisLabels(document);
        document.put("maturityRange", Arrays.asList(MATURITY_RANGE_VALUES, "80-50", "50-20", "20-5", "5-"));

        mongoTemplate.getCollection(KPI_MASTER).insertOne(new Document(document));
    }

    private void insertKpi180() {
        Map<String, Object> document = createBaseKpiDocument(KPI_180, REVERT_RATE, 5, 7, LINE_CHART);

        Map<String, Object> kpiInfo = new HashMap<>();
        kpiInfo.put(DEFINITION_KEY, "The percentage of total pull requests opened that are reverts.");
        kpiInfo.put(DETAILS_KEY, Arrays.asList(
                createDetail(LINK_TYPE, DETAILED_INFO_TEXT, "https://knowhow.tools.publicis.sapient.com/wiki/kpi180-Revert+Rate")
        ));
        document.put(KPI_INFO_KEY, kpiInfo);

        addAxisLabels(document);
        document.put("upperThresholdBG", RED_COLOR);
        document.put("lowerThresholdBG", WHITE_COLOR);
        document.put("thresholdValue", THRESHOLD_50);
        document.put("maturityRange", Arrays.asList(MATURITY_RANGE_VALUES, "80-50", "50-20", "20-5", "5-"));

        mongoTemplate.getCollection(KPI_MASTER).insertOne(new Document(document));
    }

    private Map<String, Object> createBaseKpiDocument(String kpiId, String kpiName, int defaultOrder, int groupId, String chartType) {
        Map<String, Object> document = new HashMap<>();
        document.put(KPI_ID, kpiId);
        document.put("kpiName", kpiName);
        document.put("maxValue", "");
        document.put("kpiUnit", PERCENTAGE_UNIT);
        document.put("isDeleted", false);
        document.put("defaultOrder", defaultOrder);
        document.put("groupId", groupId);
        document.put("kpiSource", BITBUCKET_SOURCE);
        document.put("kanban", false);
        document.put("chartType", chartType);
        document.put("isPositiveTrend", false);
        document.put("showTrend", true);
        document.put("kpiFilter", DROPDOWN_FILTER);
        document.put("aggregationCriteria", AVERAGE_AGGREGATION);
        document.put("isAdditionalFilterSupport", false);
        document.put("calculateMaturity", false);
        document.put("hideOverallFilter", true);
        document.put("isRepoToolKpi", true);
        document.put("kpiCategory", DEVELOPER_CATEGORY);
        document.put("combinedKpiSource", COMBINED_KPI_SOURCE);
        return document;
    }

    private void addAxisLabels(Map<String, Object> document) {
        document.put("xAxisLabel", WEEKS_LABEL);
        document.put("yAxisLabel", PERCENTAGE_LABEL);
    }

    private Map<String, Object> createDetail(String type, String text, String link) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("type", type);
        if (LINK_TYPE.equals(type)) {
            Map<String, Object> linkDetail = new HashMap<>();
            linkDetail.put("text", text);
            linkDetail.put("link", link);
            detail.put("kpiLinkDetail", linkDetail);
        } else {
            detail.put("text", text);
        }
        return detail;
    }
}