package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1320;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Arrays;
import java.util.List;

@Slf4j
@ChangeUnit(id = "open_defect_rate_kpi", order = "13204", author = "aksshriv1", systemVersion = "13.2.0")
public class ODRNewKpi {

    private final MongoTemplate mongoTemplate;

    public ODRNewKpi(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void execution() {
        // Insert documents in field_mapping_structure
        List<Document> fieldMappingDocs = Arrays.asList(
                new Document("fieldName", "jiraDefectRemovalStatusKPI191")
                        .append("fieldLabel", "Status to identify closed defects")
                        .append("fieldType", "chips")
                        .append("fieldCategory", "workflow")
                        .append("fieldDisplayOrder", 8)
                        .append("sectionOrder", 4)
                        .append("section", "WorkFlow Status Mapping")
                        .append("tooltip", new Document("definition", "All workflow statuses used to identify defects in closed state")),

                new Document("fieldName", "resolutionTypeForRejectionKPI191")
                        .append("fieldLabel", "Resolution type to be excluded")
                        .append("fieldType", "chips")
                        .append("section", "WorkFlow Status Mapping")
                        .append("fieldDisplayOrder", 6)
                        .append("sectionOrder", 4)
                        .append("tooltip", new Document("definition", "Resolution types for defects that can be excluded from Defect count by Priority calculation")),

                new Document("fieldName", "jiraDefectRejectionStatusKPI191")
                        .append("fieldLabel", "Status to identify rejected defects")
                        .append("fieldType", "text")
                        .append("fieldCategory", "workflow")
                        .append("section", "WorkFlow Status Mapping")
                        .append("fieldDisplayOrder", 2)
                        .append("sectionOrder", 4)
                        .append("tooltip", new Document("definition", "All workflow statuses used to reject defects.")),

                new Document("fieldName", "thresholdValueKPI191")
                        .append("fieldLabel", "Target KPI Value")
                        .append("fieldType", "number")
                        .append("section", "Project Level Threshold")
                        .append("fieldDisplayOrder", 1)
                        .append("sectionOrder", 6)
                        .append("tooltip", new Document("definition", "Target KPI value denotes the bare minimum a project should maintain for a KPI."))
        );
        mongoTemplate.getCollection("field_mapping_structure").insertMany(fieldMappingDocs);

        // Insert document in kpi_master
        Document kpiMasterDoc = new Document("kpiId", "kpi191")
                .append("kpiName", "Open Defect Rate")
                .append("maxValue", "100")
                .append("kpiUnit", "%")
                .append("isDeleted", false)
                .append("defaultOrder", 5)
                .append("kpiSource", "Jira")
                .append("groupId", 3)
                .append("thresholdValue", "90")
                .append("kanban", false)
                .append("chartType", "line")
                .append("kpiInfo", new Document("definition", "Measure of percentage of defects not closed against the total count tagged to the iteration")
                        .append("formula", Arrays.asList(
                                new Document("lhs", "ODR for a sprint")
                                        .append("operator", "division")
                                        .append("operands", Arrays.asList("No. of defects in the iteration that are not fixed", "Total no. of defects in a iteration"))
                        ))
                        .append("details", Arrays.asList(
                                new Document("type", "link")
                                        .append("kpiLinkDetail", new Document("text", "Detailed Information at")
                                                .append("link", "https://knowhow.tools.publicis.sapient.com/wiki/kpi191-Open+Defect+Rate"))
                        ))
                )
                .append("xAxisLabel", "Sprints")
                .append("yAxisLabel", "Percentage")
                .append("isPositiveTrend", false)
                .append("showTrend", true)
                .append("aggregationCriteria", "average")
                .append("isAdditionalFilterSupport", true)
                .append("calculateMaturity", true)
                .append("maturityRange", Arrays.asList("-25", "25-50", "50-75", "75-90", "90-"));
        mongoTemplate.getCollection("kpi_master").insertOne(kpiMasterDoc);

        // Insert document in kpi_category_mapping
        Document kpiCategoryMappingDoc = new Document("kpiId", "kpi191")
                .append("categoryId", "categoryTwo")
                .append("kpiOrder", 15)
                .append("kanban", false);
        mongoTemplate.getCollection("kpi_category_mapping").insertOne(kpiCategoryMappingDoc);

        // Insert document in kpi_column_configs
        Document kpiColumnConfigDoc = new Document("basicProjectConfigId", null)
                .append("kpiId", "kpi191")
                .append("kpiColumnDetails", Arrays.asList(
                        new Document("columnName", "Sprint Name").append("order", 1).append("isShown", true).append("isDefault", true),
                        new Document("columnName", "Defect ID").append("order", 2).append("isShown", true).append("isDefault", true),
                        new Document("columnName", "Defect Description").append("order", 3).append("isShown", true).append("isDefault", true),
                        new Document("columnName", "Story ID").append("order", 4).append("isShown", true).append("isDefault", true),
                        new Document("columnName", "Story Description").append("order", 5).append("isShown", true).append("isDefault", true),
                        new Document("columnName", "Squad").append("order", 6).append("isShown", true).append("isDefault", true),
                        new Document("columnName", "Root Cause").append("order", 7).append("isShown", true).append("isDefault", true),
                        new Document("columnName", "Defect Priority").append("order", 8).append("isShown", true).append("isDefault", true),
                        new Document("columnName", "Defect Status").append("order", 10).append("isShown", true).append("isDefault", true),
                        new Document("columnName", "Time Spent (in hours)").append("order", 11).append("isShown", true).append("isDefault", true)
                ));
        mongoTemplate.getCollection("kpi_column_configs").insertOne(kpiColumnConfigDoc);
    }

    @RollbackExecution
    public void rollback() {
        // Rollback for field_mapping_structure
        mongoTemplate.getCollection("field_mapping_structure").deleteMany(new Document("fieldName", new Document("$in", Arrays.asList(
                "jiraDefectRemovalStatusKPI191", "resolutionTypeForRejectionKPI191", "jiraDefectRejectionStatusKPI191", "thresholdValueKPI191"))));

        // Rollback for kpi_master
        mongoTemplate.getCollection("kpi_master").deleteOne(new Document("kpiId", "kpi191"));

        // Rollback for kpi_category_mapping
        mongoTemplate.getCollection("kpi_category_mapping").deleteOne(new Document("kpiId", "kpi191"));

        // Rollback for kpi_column_configs
        mongoTemplate.getCollection("kpi_column_configs").deleteOne(new Document("kpiId", "kpi191"));
    }
}
