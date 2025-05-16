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

    public static final String FIELD_NAME = "fieldName";
    public static final String DEFINITION = "definition";
    public static final String COLUMN_NAME = "columnName";
    public static final String IS_SHOWN = "isShown";
    public static final String FIELD_LABEL = "fieldLabel";
    public static final String FIELD_TYPE = "fieldType";
    public static final String FIELD_DISPLAY_ORDER = "fieldDisplayOrder";
    public static final String SECTION_ORDER = "sectionOrder";
    public static final String SECTION = "section";
    public static final String WORK_FLOW_STATUS_MAPPING = "WorkFlow Status Mapping";
    public static final String KPI_ID = "kpiId";
    public static final String KPI_191 = "kpi191";
    public static final String ORDER = "order";
    public static final String IS_DEFAULT = "isDefault";
    public static final String TOOLTIP = "tooltip";
    private final MongoTemplate mongoTemplate;

    public ODRNewKpi(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void execution() {
        // Insert documents in field_mapping_structure
        List<Document> fieldMappingDocs = Arrays.asList(
                new Document(FIELD_NAME, "jiraDefectRemovalStatusKPI191")
                        .append(FIELD_LABEL, "Status to identify closed defects")
                        .append(FIELD_TYPE, "chips")
                        .append("fieldCategory", "workflow")
                        .append(FIELD_DISPLAY_ORDER, 8)
                        .append(SECTION_ORDER, 4).append("mandatory", true)
                        .append(SECTION, WORK_FLOW_STATUS_MAPPING)
                        .append(TOOLTIP, new Document(DEFINITION, "All workflow statuses used to identify defects in closed state")),

                new Document(FIELD_NAME, "resolutionTypeForRejectionKPI191")
                        .append(FIELD_LABEL, "Resolution type to be excluded")
                        .append(FIELD_TYPE, "chips")
                        .append(SECTION, WORK_FLOW_STATUS_MAPPING)
                        .append(FIELD_DISPLAY_ORDER, 6)
                        .append(SECTION_ORDER, 4)
                        .append(TOOLTIP, new Document(DEFINITION, "Resolution types for defects that can be excluded from Defect count by Priority calculation")),

                new Document(FIELD_NAME, "jiraDefectRejectionStatusKPI191")
                        .append(FIELD_LABEL, "Status to identify rejected defects")
                        .append(FIELD_TYPE, "text")
                        .append("fieldCategory", "workflow")
                        .append(SECTION, WORK_FLOW_STATUS_MAPPING)
                        .append(FIELD_DISPLAY_ORDER, 2)
                        .append(SECTION_ORDER, 4)
                        .append(TOOLTIP, new Document(DEFINITION, "All workflow statuses used to reject defects.")),

                new Document(FIELD_NAME, "thresholdValueKPI191")
                        .append(FIELD_LABEL, "Target KPI Value")
                        .append(FIELD_TYPE, "number")
                        .append(SECTION, "Project Level Threshold")
                        .append(FIELD_DISPLAY_ORDER, 1)
                        .append(SECTION_ORDER, 6)
                        .append(TOOLTIP, new Document(DEFINITION, "Target KPI value denotes the bare minimum a project should maintain for a KPI."))
        );
        mongoTemplate.getCollection("field_mapping_structure").insertMany(fieldMappingDocs);

        // Insert document in kpi_master
        Document kpiMasterDoc = new Document(KPI_ID, KPI_191)
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
                .append("kpiInfo", new Document(DEFINITION, "Measure of percentage of defects unresolved against the total count tagged to the iteration")
                        .append("formula", Arrays.asList(
                                new Document("lhs", "ODR for a sprint (%)")
                                        .append("operator", "division")
                                        .append("operands", Arrays.asList("No. of defects in the iteration that are not fixed (unresolved)", "Total no. of defects in a iteration"))
                        ))
                        .append("details", Arrays.asList(
                                new Document("type", "link")
                                        .append("kpiLinkDetail", new Document("text", "Detailed Information at")
                                                .append("link", "https://knowhow.suite.publicissapient.com/wiki/spaces/PS/pages/189825034/Open+Defect+Rate"))
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
        Document kpiCategoryMappingDoc = new Document(KPI_ID, KPI_191)
                .append("categoryId", "quality")
                .append("kpiOrder", 15)
                .append("kanban", false);
        mongoTemplate.getCollection("kpi_category_mapping").insertOne(kpiCategoryMappingDoc);

        // Insert document in kpi_column_configs
        Document kpiColumnConfigDoc = new Document("basicProjectConfigId", null)
                .append(KPI_ID, KPI_191)
                .append("kpiColumnDetails", Arrays.asList(
                        new Document(COLUMN_NAME, "Sprint Name").append(ORDER, 1).append(IS_SHOWN, true).append(IS_DEFAULT, true),
                        new Document(COLUMN_NAME, "Defect ID").append(ORDER, 2).append(IS_SHOWN, true).append(IS_DEFAULT, true),
                        new Document(COLUMN_NAME, "Defect Description").append(ORDER, 3).append(IS_SHOWN, true).append(IS_DEFAULT, true),
                        new Document(COLUMN_NAME, "Story ID").append(ORDER, 4).append(IS_SHOWN, true).append(IS_DEFAULT, true),
                        new Document(COLUMN_NAME, "Story Description").append(ORDER, 5).append(IS_SHOWN, true).append(IS_DEFAULT, true),
                        new Document(COLUMN_NAME, "Squad").append(ORDER, 6).append(IS_SHOWN, true).append(IS_DEFAULT, true),
                        new Document(COLUMN_NAME, "Root Cause").append(ORDER, 7).append(IS_SHOWN, true).append(IS_DEFAULT, true),
                        new Document(COLUMN_NAME, "Defect Priority").append(ORDER, 8).append(IS_SHOWN, true).append(IS_DEFAULT, true),
                        new Document(COLUMN_NAME, "Defect Status").append(ORDER, 10).append(IS_SHOWN, true).append(IS_DEFAULT, true),
                        new Document(COLUMN_NAME, "Time Spent (in hours)").append(ORDER, 11).append(IS_SHOWN, true).append(IS_DEFAULT, true)
                ));
        mongoTemplate.getCollection("kpi_column_configs").insertOne(kpiColumnConfigDoc);
    }

    @RollbackExecution
    public void rollback() {
        // Rollback for field_mapping_structure
        mongoTemplate.getCollection("field_mapping_structure").
                deleteMany(new Document(FIELD_NAME, new Document("$in", Arrays.asList(
                        "jiraDefectRemovalStatusKPI191", "resolutionTypeForRejectionKPI191", "jiraDefectRejectionStatusKPI191",
                        "thresholdValueKPI191"))));

        // Rollback for kpi_master
        mongoTemplate.getCollection("kpi_master").deleteOne(new Document(KPI_ID, KPI_191));

        // Rollback for kpi_category_mapping
        mongoTemplate.getCollection("kpi_category_mapping").deleteOne(new Document(KPI_ID, KPI_191));

        // Rollback for kpi_column_configs
        mongoTemplate.getCollection("kpi_column_configs").deleteOne(new Document(KPI_ID, KPI_191));
    }
}
