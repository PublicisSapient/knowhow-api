package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1400;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@ChangeUnit(id = "test_execution_time_kpi", order = "14004", author = "rendk", systemVersion = "14.0.0")
public class TestExecutionTimeKpi {

    public static final String KPI_ID = "kpiId";
    public static final String KPI_196 = "kpi196";
    private final MongoTemplate mongoTemplate;

    public TestExecutionTimeKpi(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void execution() {
        // Insert document in kpi_master
        Document kpiMasterDoc = new Document(KPI_ID, KPI_196)
                .append("kpiName", "Test Execution Time")
                .append("isDeleted", false)
                .append("defaultOrder", 1)
                .append("kpiCategory", "Release")
                .append("kpiSubCategory", "Quality")
                .append("kpiSource", "Zypher")
                .append("combinedKpiSource", "Zephyr/Zypher/JiraTest")
                .append("groupId", 9)
                .append("kpiUnit", "min")
                .append("kanban", false)
                .append("chartType", "stacked-bar-chart")
                .append("kpiInfo", new Document("definition", " The Test Execution Time metric measures the average duration between the start and end of test executions. It helps QA leads track how long it takes to run test cases and identify delays or inconsistencies in execution speed across teams, test types, or modules.")
                        .append("details", java.util.Arrays.asList(
                                new Document("type", "link")
                                        .append("kpiLinkDetail", new Document("text", "Detailed Information at")
                                                .append("link", "https://knowhow.suite.publicissapient.com/wiki/spaces/PS/pages/272465922/Test+Execution+Time"))
                        )))
                .append("xaxisLabel", "Sprints")
                .append("yaxisLabel", "Avg. Execution Time (in minutes)")
                .append("isAdditionalFilterSupport", false)
                .append("kpiFilter", "")
                .append("boxType", "chart")
                .append("calculateMaturity", false);

        mongoTemplate.getCollection("kpi_master").insertOne(kpiMasterDoc);

        // Insert document in kpi_category_mapping
        Document kpiCategoryMappingDoc = new Document(KPI_ID, KPI_196)
                .append("categoryId", "categoryTwo")
                .append("kpiOrder", 4)
                .append("kanban", false);

        mongoTemplate.getCollection("kpi_category_mapping").insertOne(kpiCategoryMappingDoc);
    }

    @RollbackExecution
    public void rollback() {
        mongoTemplate.getCollection("kpi_master").deleteOne(new Document(KPI_ID, KPI_196));
        mongoTemplate.getCollection("kpi_category_mapping").deleteOne(new Document(KPI_ID, KPI_196));
    }
}
