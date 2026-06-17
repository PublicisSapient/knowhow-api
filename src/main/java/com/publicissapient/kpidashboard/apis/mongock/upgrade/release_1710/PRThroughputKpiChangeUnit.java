package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

import java.util.Arrays;

@ChangeUnit(id = "pr_throughput_kpi_insert", order = "17117", author = "kunkambl", systemVersion = "17.1.0")
public class PRThroughputKpiChangeUnit {

    private static final String KPI_ID = "kpi208";
    private static final String KPI_ID_FIELD = "kpiId";
    private static final String KPI_MASTER_COLLECTION = "kpi_master";
    private static final String KPI_COLUMN_CONFIGS_COLLECTION = "kpi_column_configs";
    private static final String LEVEL = "level";
    private static final String BG_COLOR = "bgColor";
    private static final String RANGE = "range";
    private static final String COLUMN_NAME = "columnName";
    private static final String ORDER = "order";
    private static final String IS_SHOWN = "isShown";
    private static final String IS_DEFAULT = "isDefault";

    @Execution
    public void execution(MongoTemplate mongoTemplate) {
        insertKpiMaster(mongoTemplate);
        insertKpiColumnConfig(mongoTemplate);
    }

    public void insertKpiMaster(MongoTemplate mongoTemplate) {
        Document kpiMaster = new Document()
                .append(KPI_ID_FIELD, KPI_ID)
                .append("kpiName", "Check-Ins & Merge Requests")
                .append("isDeleted", "False")
                .append("defaultOrder", 1)
                .append("kpiCategory", "Slingshot")
                .append("kpiSubCategory", "Speed")
                .append("kpiUnit", "MRs")
                .append("chartType", "grouped_column_plus_line")
                .append("xAxisLabel", "Days")
                .append("yAxisLabel", "Count")
                .append("showTrend", true)
                .append("isPositiveTrend", true)
                .append("calculateMaturity", true)
                .append("hideOverallFilter", true)
                .append("kpiSource", "BitBucket")
                .append("maxValue", 15)
                .append("thresholdValue", 55.0)
                .append("kanban", false)
                .append("groupId", 6)
                .append("kpiInfo", new Document()
                        .append("definition", "NUMBER OF CHECK-INS helps in measuring the transparency as well the how well the tasks have been broken down. NUMBER OF MERGE REQUESTS when looked at along with commits highlights the efficiency of the review process")
                        .append("details", Arrays.asList(
                                new Document()
                                        .append("type", "paragraph")
                                        .append("value", "It is calculated as a Count. Higher the count better is the 'Speed'"),
                                new Document()
                                        .append("type", "paragraph")
                                        .append("value", "A progress indicator shows trend of Number of Check-ins & Merge requests between last 2 days. An upward trend is considered positive"),
                                new Document()
                                        .append("type", "link")
                                        .append("kpiLinkDetail", new Document()
                                                .append("text", "Detailed Information at")
                                                .append("link", "https://knowhow.tools.publicis.sapient.com/wiki/kpi157-Check-Ins+&+Merge+Requests"))
                        ))
                        .append("maturityLevels", Arrays.asList(
                                new Document().append(LEVEL, "M5").append(BG_COLOR, "#6cab61").append(RANGE, "> 16"),
                                new Document().append(LEVEL, "M4").append(BG_COLOR, "#AEDB76").append(RANGE, "8-16"),
                                new Document().append(LEVEL, "M3").append(BG_COLOR, "#eff173").append(RANGE, "4-8"),
                                new Document().append(LEVEL, "M2").append(BG_COLOR, "#ffc35b").append(RANGE, "2-4"),
                                new Document().append(LEVEL, "M1").append(BG_COLOR, "#F06667").append(RANGE, "0-2")
                        ))
                        .append("_class", "com.publicissapient.kpidashboard.common.model.application.KpiInfo"))
                .append("kpiFilter", "dropDown")
                .append("aggregationCriteria", "average")
                .append("isTrendCalculative", false)
                .append("isAdditionalFilterSupport", false)
                .append("maturityRange", Arrays.asList("-2", "2-4", "4-8", "8-16", "16-"))
                .append("isRepoToolKpi", true)
                .append("combinedKpiSource", "Bitbucket/AzureRepository/GitHub/GitLab")
                .append("forecastModel", "thetaMethod");

        mongoTemplate.getCollection(KPI_MASTER_COLLECTION).insertOne(kpiMaster);
    }

    public void insertKpiColumnConfig(MongoTemplate mongoTemplate) {
        Document columnConfig = new Document()
                .append("basicProjectConfigId", null)
                .append(KPI_ID_FIELD, KPI_ID)
                .append("kpiColumnDetails", Arrays.asList(
                        new Document().append(COLUMN_NAME, "Project").append(ORDER, 1).append(IS_SHOWN, true).append(IS_DEFAULT, true),
                        new Document().append(COLUMN_NAME, "Repo").append(ORDER, 2).append(IS_SHOWN, true).append(IS_DEFAULT, true),
                        new Document().append(COLUMN_NAME, "Branch").append(ORDER, 3).append(IS_SHOWN, true).append(IS_DEFAULT, true),
                        new Document().append(COLUMN_NAME, "Days/Weeks").append(ORDER, 4).append(IS_SHOWN, true).append(IS_DEFAULT, true),
                        new Document().append(COLUMN_NAME, "Developer").append(ORDER, 5).append(IS_SHOWN, true).append(IS_DEFAULT, true),
                        new Document().append(COLUMN_NAME, "No of Merge").append(ORDER, 7).append(IS_SHOWN, true).append(IS_DEFAULT, true)
                ));

        mongoTemplate.getCollection(KPI_COLUMN_CONFIGS_COLLECTION).insertOne(columnConfig);
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.getCollection(KPI_MASTER_COLLECTION).deleteOne(new Document(KPI_ID_FIELD, KPI_ID));
        mongoTemplate.getCollection(KPI_COLUMN_CONFIGS_COLLECTION).deleteOne(new Document(KPI_ID_FIELD, KPI_ID));
    }
}
