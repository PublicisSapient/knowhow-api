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

package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1400;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Change unit to rollback the Defect Severity Index KPI
 *
 * @author girpatha
 */
@ChangeUnit(id = "r_defect_severity_index", order = "014001", author = "girpatha", systemVersion = "14.0.0")
public class DefectSeverityIndexChangeUnit {

    public static final String FIELD_NAME = "fieldName";
    public static final String FIELD_LABEL = "fieldLabel";
    public static final String FIELD_TYPE = "fieldType";
    public static final String SECTION = "section";
    public static final String TOOLTIP = "tooltip";
    public static final String FIELD_DISPLAY_ORDER = "fieldDisplayOrder";
    public static final String SECTION_ORDER = "sectionOrder";
    public static final String MANDATORY = "mandatory";
    public static final String WORK_FLOW_STATUS_MAPPING = "WorkFlow Status Mapping";
    public static final String FIELD_CATEGORY = "fieldCategory";
    public static final String KPI_ID = "kpiId";
    public static final String KPI_194 = "kpi194";
    public static final String CHIPS = "chips";
    public static final String DEFINITION = "definition";
    public static final String NODE_SPECIFIC = "nodeSpecific";
    public static final String CUSTOM_FIELD = "CustomField";
    public static final String PROCESSOR_COMMON = "processorCommon";
    private final MongoTemplate mongoTemplate;

    public DefectSeverityIndexChangeUnit(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void execution() {
        mongoTemplate.getCollection("kpi_master").deleteOne(new Document(KPI_ID, KPI_194));
        mongoTemplate.getCollection("field_mapping_structure")
                .deleteMany(new Document(FIELD_NAME, new Document("$in", List.of(
                        "jiraDefectSeverityKPI194",
                        "jiraDefectSeverityByCustomFieldKPI194",
                        "jiraDefectCountlIssueTypeKPI194",
                        "jiraIssueTypeNamesKPI194",
                        "resolutionTypeForRejectionKPI194",
                        "jiraDefectRejectionStatusKPI194",
                        "thresholdValueKPI194"
                ))));
        mongoTemplate.getCollection("kpi_category_mapping").deleteOne(new Document(KPI_ID, KPI_194));
        mongoTemplate.getCollection("kpi_column_configs").deleteOne(new Document(KPI_ID, KPI_194));
    }

    private void insertKpiCategoryMapping() {
        Document kpiCategoryMapping = new Document()
                .append(KPI_ID, KPI_194)
                .append("categoryId", "quality")
                .append("kpiOrder", 7.0)
                .append("kanban", false);

        mongoTemplate.getCollection("kpi_category_mapping").insertOne(kpiCategoryMapping);
    }

    private void insertFieldMapping() {
        List<Document> fieldMappings = new ArrayList<>();
        
        // Add jiraDefectSeverityKPI194 with nested fields
        Document severityField = new Document()
                .append(FIELD_NAME, "jiraDefectSeverityKPI194")
                .append(FIELD_LABEL, "Defect Severity")
                .append(FIELD_TYPE, "radiobutton")
                .append(SECTION, "Custom Fields Mapping")
                .append(FIELD_DISPLAY_ORDER, 1)
                .append(SECTION_ORDER, 3)
                .append(MANDATORY, true)
                .append(TOOLTIP, new Document(DEFINITION, "Custom field to consider for a defect severity."))
                .append(PROCESSOR_COMMON, true)
                .append(NODE_SPECIFIC, false)
                .append("options", List.of(
                        new Document()
                                .append("label", CUSTOM_FIELD)
                                .append("value", CUSTOM_FIELD)
                ));
        
        // Add nested fields
        List<Document> nestedFields = new ArrayList<>();
        nestedFields.add(new Document()
                .append(FIELD_NAME, "jiraDefectSeverityByCustomFieldKPI194")
                .append(FIELD_LABEL, "Custom field to identify defect severity")
                .append("placeHolderText", "Custom field to identify defect severity")
                .append(FIELD_TYPE, "text")
                .append(FIELD_CATEGORY, "fields")
                .append("filterGroup", List.of(CUSTOM_FIELD))
                .append(TOOLTIP, new Document(DEFINITION, "Provide field name to identify a defect severity. Example: customfield_10500 or customfield_10103<hr>"))
        );
        severityField.append("nestedFields", nestedFields);
        fieldMappings.add(severityField);
        
        // Add jiraDefectCountlIssueTypeKPI194
        fieldMappings.add(new Document()
                .append(FIELD_NAME, "jiraDefectCountlIssueTypeKPI194")
                .append(FIELD_LABEL, "Issue types with defect linkages")
                .append(FIELD_TYPE, CHIPS)
                .append(FIELD_CATEGORY, "Issue_Type")
                .append(SECTION, "Issue Types Mapping")
                .append(PROCESSOR_COMMON, false)
                .append(TOOLTIP, new Document(DEFINITION, "All issue types that can have valid defect linkages"))
                .append(FIELD_DISPLAY_ORDER, 1)
                .append(SECTION_ORDER, 2)
                .append(MANDATORY, true)
                .append(NODE_SPECIFIC, false)
        );
        
        // Add jiraIssueTypeNamesKPI194
        fieldMappings.add(new Document()
                .append(FIELD_NAME, "jiraIssueTypeNamesKPI194")
                .append(FIELD_LABEL, "Issue types to be included")
                .append("placeHolderText", "Issue types to be included")
                .append(FIELD_TYPE, CHIPS)
                .append(FIELD_CATEGORY, "Issue_Type")
                .append(SECTION, WORK_FLOW_STATUS_MAPPING)
                .append(SECTION_ORDER, 1)
                .append(FIELD_DISPLAY_ORDER, 1)
                .append(TOOLTIP, new Document(DEFINITION, "All issue types used by your Jira project"))
                .append(MANDATORY, true)
                .append(PROCESSOR_COMMON, false)
                .append(NODE_SPECIFIC, false)
        );
        
        // Add resolutionTypeForRejectionKPI194
        fieldMappings.add(new Document()
                .append(FIELD_NAME, "resolutionTypeForRejectionKPI194")
                .append(FIELD_LABEL, "Resolution type to be excluded")
                .append(FIELD_TYPE, CHIPS)
                .append(SECTION, WORK_FLOW_STATUS_MAPPING)
                .append(PROCESSOR_COMMON, false)
                .append(TOOLTIP, new Document(DEFINITION, "Resolution types for defects that can be excluded from Defect count by Severity calculation"))
                .append(FIELD_DISPLAY_ORDER, 6)
                .append(SECTION_ORDER, 4)
                .append(MANDATORY, false)
                .append(NODE_SPECIFIC, false)
        );
        
        // Add jiraDefectRejectionStatusKPI194
        fieldMappings.add(new Document()
                .append(FIELD_NAME, "jiraDefectRejectionStatusKPI194")
                .append(FIELD_LABEL, "Status to identify rejected defects")
                .append(FIELD_TYPE, "text")
                .append(FIELD_CATEGORY, "workflow")
                .append(SECTION, WORK_FLOW_STATUS_MAPPING)
                .append(PROCESSOR_COMMON, false)
                .append(TOOLTIP, new Document(DEFINITION, "All workflow statuses used to reject defects."))
                .append(FIELD_DISPLAY_ORDER, 2)
                .append(SECTION_ORDER, 4)
                .append(MANDATORY, false)
                .append(NODE_SPECIFIC, false)
        );
        
        // Add thresholdValueKPI194
        fieldMappings.add(new Document()
                .append(FIELD_NAME, "thresholdValueKPI194")
                .append(FIELD_LABEL, "Target KPI Value")
                .append(FIELD_TYPE, "number")
                .append(SECTION, "Project Level Threshold")
                .append(PROCESSOR_COMMON, false)
                .append(TOOLTIP, new Document(DEFINITION, "Target KPI value denotes the bare minimum a project should maintain for a KPI. User should just input the number and the unit like percentage, hours will automatically be considered. If the threshold is empty, then a common target KPI line will be shown"))
                .append(FIELD_DISPLAY_ORDER, 1)
                .append(SECTION_ORDER, 6)
                .append(MANDATORY, false)
                .append(NODE_SPECIFIC, false)
        );
        
        mongoTemplate.getCollection("field_mapping_structure").insertMany(fieldMappings);
    }

    private static Document insertKPIMaster() {
        return new Document()
                .append(KPI_ID, KPI_194)
                .append("kpiName", "Defect Severity Index")
                .append("isDeleted", "False")
                .append("defaultOrder", 7)
                .append("kpiUnit", "Number")
                .append("chartType", "line")
                .append("upperThresholdBG", "red")
                .append("lowerThresholdBG", "white")
                .append("xAxisLabel", "Sprints")
                .append("yAxisLabel", "Count")
                .append("showTrend", true)
                .append("isPositiveTrend", false)
                .append("calculateMaturity", false)
                .append("hideOverallFilter", false)
                .append("kpiSource", "Jira")
                .append("maxValue", "90")
                .append("thresholdValue", 55.0)
                .append("kanban", false)
                .append("groupId", 24)
                .append("kpiInfo", new Document()
                        .append(DEFINITION, "Measures the number of defects grouped by severity in an iteration")
                        .append("formula", List.of(
                                new Document().append("lhs", "Defect Count By Severity=No. of defects linked to stories grouped by severity")
                        ))
                        .append("details", List.of(
                                new Document()
                                        .append("type", "link")
                                        .append("kpiLinkDetail", new Document()
                                                .append("text", "Detailed Information at")
                                                .append("link", "https://knowhow.suite.publicissapient.com/wiki/spaces/PS/pages/257720322/Defect+Severity+Index+DSI")
                                        )
                        )))
                .append("kpiFilter", "multiSelectDropDown")
                .append("aggregationCriteria", "sum")
                .append("isTrendCalculative", false)
                .append("isAdditionalFilterSupport", true)
                .append("combinedKpiSource", "Jira/Azure/Rally");
    }

    private void insertKPIColumnConfig() {
        Document kpiColumnConfig = new Document()
                .append("basicProjectConfigId", null)
                .append(KPI_ID, KPI_194)
                .append("kpiColumnDetails", List.of(
                        createColumnDetail("Sprint Name", 1),
                        createColumnDetail("Defect ID", 2),
                        createColumnDetail("Defect Description", 3),
                        createColumnDetail("Defect Severity", 4)
                ));

        mongoTemplate.getCollection("kpi_column_configs").insertOne(kpiColumnConfig);
    }

    private Document createColumnDetail(String columnName, int order) {
        return new Document()
                .append("columnName", columnName)
                .append("order", order)
                .append("isShown", true)
                .append("isDefault", true);
    }

    @RollbackExecution
    public void rollback() {
        mongoTemplate.getCollection("kpi_master").insertOne(insertKPIMaster());
        insertFieldMapping();
        insertKpiCategoryMapping();
        insertKPIColumnConfig();
    }
}
