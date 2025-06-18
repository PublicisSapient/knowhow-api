package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1330;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@ChangeUnit(id = "update_code_violations_kpi_column_config", order = "13303", author = "kunkambl", systemVersion = "13.3.0")
public class CycleTimeKpiChangeLog {
    private final MongoTemplate mongoTemplate;

    public CycleTimeKpiChangeLog(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void execution() {
        Document kpiInfo = new Document()
                .append("definition", "Cycle time helps ascertain time spent on each step of the complete issue lifecycle. It is being depicted in the visualization as 3 core cycles - Intake to DOR, DOR to DOD, DOD to Live")
                .append("details", List.of(
                        new Document("type", "link")
                                .append("kpiLinkDetail", new Document()
                                        .append("text", "Detailed Information at")
                                        .append("link", "https://knowhow.tools.publicis.sapient.com/wiki/kpi171-Cycle+Time")
                                )
                ));

        Document kpiDocument = new Document()
                .append("kpiId", "kpi193")
                .append("kpiName", "Cycle Time")
                .append("maxValue", "")
                .append("kpiUnit", "Days")
                .append("isDeleted", "False")
                .append("defaultOrder", 1)
                .append("kpiSource", "Jira")
                .append("groupId", 11)
                .append("thresholdValue", "")
                .append("kanban", false)
                .append("chartType", "table")
                .append("yAxisLabel", "")
                .append("xAxisLabel", "")
                .append("isAdditionalfFilterSupport", false)
                .append("kpiFilter", "multiSelectDropDown")
                .append("calculateMaturity", false)
                .append("kpiInfo", kpiInfo)
                .append("combinedKpiSource", "Jira/Azure/Rally");

        mongoTemplate.getCollection("kpi_master").insertOne(kpiDocument);
    }

    @RollbackExecution
    public void rollback() {
        mongoTemplate.getCollection("kpi_master").deleteOne(new Document("kpiId", "kpi193"));
    }
}
