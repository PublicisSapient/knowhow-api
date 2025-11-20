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
package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1410;

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

@ChangeUnit(id = "r_code_quality_metrics_kpi", order = "014108", author = "valanil", systemVersion = "14.1.0")
public class CodeQualityMetricsChangeUnit {

    public static final String KPI_201 = "kpi201";
    public static final String KPI_ID = "kpiId";
    private final MongoTemplate mongoTemplate;
    private static final String PROJECT = "Project";
    private static final String REPO = "Repo";
    private static final String BRANCH = "Branch";
    private static final String DEVELOPER = "Developer";
    private static final String DAYS_WEEKS = "Days/Weeks";
    private static final String REWORK_RATE = "Rework Rate";
    private static final String REVERT_RATE = "Revert Rate";
    private static final String KPI_COLUMN_CONFIG = "kpi_column_configs";
    private static final String KPI_MASTER = "kpi_master";

    public CodeQualityMetricsChangeUnit(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void execution() {
        Bson filter = Filters.eq(KPI_ID, KPI_201);
        mongoTemplate.getCollection(KPI_MASTER).deleteOne(filter);
        mongoTemplate.getCollection(KPI_COLUMN_CONFIG).deleteMany(filter);
    }

    @RollbackExecution
    public void rollback() {
        insertKpi201();
        insertColumnConfig(KPI_201, Arrays.asList(PROJECT, REPO, BRANCH, DEVELOPER, DAYS_WEEKS, REWORK_RATE, REVERT_RATE));
    }

    public void insertKpi201() {
        Map<String, Object> document = new HashMap<>();
        document.put(KPI_ID,KPI_201 );
        document.put("kpiName", "Code Quality Metrics");
        document.put("maxValue", "");
        document.put("kpiUnit", "%");
        document.put("isDeleted", false);
        document.put("defaultOrder", 7);
        document.put("groupId", 2);
        document.put("kpiSource", "BitBucket");
        document.put("kanban", false);
        document.put("chartType", "progressbar");

        Map<String, Object> kpiInfo = new HashMap<>();
        kpiInfo.put("definition", "Code Quality Metrics - a combination of revert rate and rework rate percentage values");

        List<Map<String, Object>> details = Arrays.asList(
                createDetail("paragraph", "Revert Rate: The percentage of total pull requests opened that are reverts.", null),
                createDetail("link", "Revert Rate - Detailed Information at", "https://knowhow.tools.publicis.sapient.com/wiki/kpi180-Revert+Rate"),
                createDetail("paragraph", "Rework Rate: Percentage of code changes in which an engineer rewrites code that they recently updated (within the past three weeks).", null),
                createDetail("link", "Rework Rate - Detailed Information at", "https://knowhow.tools.publicis.sapient.com/wiki/kpi173-Rework+Rate")
        );

        kpiInfo.put("details", details);
        document.put("kpiInfo", kpiInfo);

        document.put("isPositiveTrend", false);
        document.put("showTrend", true);
        document.put("kpiFilter", "dropDown");
        document.put("aggregationCriteria", "average");
        document.put("isAdditionalFilterSupport", false);
        document.put("calculateMaturity", false);
        document.put("hideOverallFilter", true);
        document.put("isRepoToolKpi", true);
        document.put("kpiCategory", DEVELOPER);
        document.put("combinedKpiSource", "Bitbucket/AzureRepository/GitHub/GitLab");

        mongoTemplate.getCollection(KPI_MASTER).insertOne(new org.bson.Document(document));
    }

    private Map<String, Object> createDetail(String type, String text, String link) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("type", type);
        if ("link".equals(type)) {
            Map<String, Object> linkDetail = new HashMap<>();
            linkDetail.put("text", text);
            linkDetail.put("link", link);
            detail.put("kpiLinkDetail", linkDetail);
        } else {
            detail.put("text", text);
        }
        return detail;
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


}