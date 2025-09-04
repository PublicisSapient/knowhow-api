/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1400;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@ChangeUnit(id = "defects_breached_slas", order = "14003", author = "vladinu", systemVersion = "14.0.0")
public class DefectsBreachedSLAsChangeUnit {

    private static final String KPI_ID = "kpiId";
    private static final String KPI_195 = "kpi195";
    private static final String DEFINITION = "definition";
    private static final String KANBAN = "kanban";

    private static final String FIELD_NAME = "fieldName";
    private static final String FIELD_LABEL = "fieldLabel";
    private static final String FIELD_DISPLAY_ORDER = "fieldDisplayOrder";
    private static final String FIELD_TYPE = "fieldType";
    private static final String FIELD_CATEGORY = "fieldCategory";
    private static final String SECTION = "section";
    private static final String SECTION_ORDER = "sectionOrder";
    private static final String MANDATORY = "mandatory";
    private static final String TOOLTIP = "tooltip";
    private static final String PLACEHOLDER_TEXT = "placeHolderText";
    private static final String OPTIONS = "options";
    private static final String STRUCTURED_VALUE = "structuredValue";
    private static final String SEVERITY = "severity";
    private static final String SLA = "sla";
    private static final String TIMEUNIT = "timeUnit";
    private static final String PROCESSOR_COMMON = "processorCommon";
    private static final String NODE_SPECIFIC = "nodeSpecific";
    private static final String CHIPS = "chips";
    private static final String LABEL = "label";
    private static final String VALUE = "value";
    private static final String WORKFLOW_STATUS_MAPPING = "Workflow Status Mapping";

    private final MongoTemplate mongoTemplate;

    @Execution
    public void execution() {
        mongoTemplate.getCollection("kpi_master").insertOne(constructKpiMasterDocument());
		mongoTemplate.getCollection("field_mapping_structure").insertMany(constructFieldMappingStructureDocuments());
        mongoTemplate.getCollection("kpi_category_mapping").insertOne(constructKpiCategoryMappingDocument());
        mongoTemplate.getCollection("kpi_column_configs").insertOne(constructKpiColumnConfigDocument());
    }

    @RollbackExecution
	public void rollback() {

        log.error("Mongock upgrade failed");

        mongoTemplate.getCollection("kpi_master").deleteOne(new Document(KPI_ID, KPI_195));
        mongoTemplate.getCollection("field_mapping_structure")
                .deleteMany(new Document(FIELD_NAME, new Document("$in", List.of(
                        "includedSeveritySlasKPI195",
                        "excludedDefectPrioritiesKPI195",
                        "includedDefectRootCausesKPI195",
                        "excludedDefectRejectionStatusesKPI195",
                        "excludedDefectResolutionTypesKPI195",
                        "includedDefectClosureStatusesKPI195",
                        "thresholdValueKPI195"
                ))));
        mongoTemplate.getCollection("kpi_category_mapping").deleteOne(new Document(KPI_ID, KPI_195));
        mongoTemplate.getCollection("kpi_column_configs").deleteOne(new Document(KPI_ID, KPI_195));
	}

    private Document createColumnDetail(String columnName, int order) {
        return new Document()
                .append("columnName", columnName)
                .append("order", order)
                .append("isShown", true)
                .append("isDefault", true);
    }

    private Document constructKpiColumnConfigDocument() {
        return new Document()
                .append("basicProjectConfigId", null)
                .append(KPI_ID, KPI_195)
                .append("kpiColumnDetails", List.of(
                        createColumnDetail("Sprint Name", 0),
                        createColumnDetail("Defect ID", 1),
                        createColumnDetail("Story ID", 2),
                        createColumnDetail("Defect Priority", 3),
                        createColumnDetail("Defect Severity", 4),
                        createColumnDetail("Defect Status", 5),
                        createColumnDetail("Time Spent (in hours)", 6),
                        createColumnDetail("Resolution Time", 7),
                        createColumnDetail("Defect SLA", 8),
                        createColumnDetail("SLA Breached (Y / N)", 9)
                ));
    }

    private List<Document> constructFieldMappingStructureDocuments() {
        List<Document> fieldMappingStructureDocuments = new ArrayList<>();

        fieldMappingStructureDocuments.add(new Document()
                .append(FIELD_NAME, "includedSeveritySlasKPI195")
                .append(FIELD_LABEL, "Defect SLA")
                .append(FIELD_DISPLAY_ORDER, 1)
                .append(FIELD_TYPE, "multiselect-dropdown")
                .append(FIELD_CATEGORY, "fields")
                .append(SECTION, "Custom Fields Mapping")
                .append(SECTION_ORDER, 1)
                .append(MANDATORY, true)
                .append(TOOLTIP, new Document()
                        .append(DEFINITION, "The agreed-upon time limit within which a defect must be resolved, as defined by service-level agreements (SLAs)"))
                .append(OPTIONS, List.of(
                        new Document()
                                .append(LABEL, "s1")
                                .append(STRUCTURED_VALUE,
                                        new Document()
                                                .append(SEVERITY, "s1")
                                                .append(SLA, 24)
                                                .append(TIMEUNIT, "Hours")),
                        new Document()
                                .append(LABEL, "s2")
                                .append(STRUCTURED_VALUE,
                                        new Document()
                                                .append(SEVERITY, "s2")
                                                .append(SLA, 48)
                                                .append(TIMEUNIT, "Hours")),
                        new Document()
                                .append(LABEL, "s3")
                                .append(STRUCTURED_VALUE, new Document()
                                        .append(SEVERITY, "s3")
                                        .append(SLA, 5)
                                        .append(TIMEUNIT, "Days")),
                        new Document()
                                .append(LABEL, "s4")
                                .append(STRUCTURED_VALUE, new Document()
                                        .append(SEVERITY, "s4")
                                        .append(SLA, 10)
                                        .append(TIMEUNIT, "Days"))
                ))
        );

        // Priority to be excluded
        fieldMappingStructureDocuments.add(new Document()
                .append(FIELD_NAME, "excludedDefectPrioritiesKPI195")
                .append(FIELD_LABEL, "Priority to be excluded")
                .append(PLACEHOLDER_TEXT, "Select values to be excluded")
                .append(FIELD_TYPE, "multiselect")
                .append(SECTION, "Defects Mapping")
                .append(FIELD_DISPLAY_ORDER, 1)
                .append(SECTION_ORDER, 2)
                .append(TOOLTIP, new Document()
                        .append(DEFINITION, "Priority values of defects that can be excluded from Defect Density calculation"))
                .append(OPTIONS, List.of(
                        new Document().append(LABEL, "p2").append(VALUE, "p2"),
                        new Document().append(LABEL, "p1").append(VALUE, "p1"),
                        new Document().append(LABEL, "p3").append(VALUE, "p3"),
                        new Document().append(LABEL, "p4").append(VALUE, "p4"),
                        new Document().append(LABEL, "p5").append(VALUE, "p5")
                ))
        );

        // Root cause values to be included
        fieldMappingStructureDocuments.add(new Document()
                .append(FIELD_NAME, "includedDefectRootCausesKPI195")
                .append(FIELD_LABEL, "Root cause values to be included")
                .append(PLACEHOLDER_TEXT, " Root cause values to be included")
                .append(FIELD_TYPE, CHIPS)
                .append(SECTION, "Defects Mapping")
                .append(FIELD_DISPLAY_ORDER, 2)
                .append(SECTION_ORDER, 2)
                .append(TOOLTIP, new Document()
                        .append(DEFINITION, "Root cause reasons for defects to be included In Defect Density calculation."))
        );

        // Status to identify rejected defects
        fieldMappingStructureDocuments.add(new Document()
                .append(FIELD_NAME, "excludedDefectRejectionStatusesKPI195")
                .append(FIELD_LABEL, "Status to identify rejected defects")
                .append(FIELD_TYPE, "text")
                .append(FIELD_CATEGORY, "workflow")
                .append(SECTION, WORKFLOW_STATUS_MAPPING)
                .append(FIELD_DISPLAY_ORDER, 1)
                .append(SECTION_ORDER, 3)
                .append(TOOLTIP, new Document()
                        .append(DEFINITION, "All workflow statuses used to reject defects."))
        );

        // Resolution type to be excluded
        fieldMappingStructureDocuments.add(new Document()
                .append(FIELD_NAME, "excludedDefectResolutionTypesKPI195")
                .append(FIELD_LABEL, "Resolution type to be excluded")
                .append(FIELD_TYPE, CHIPS)
                .append(SECTION, WORKFLOW_STATUS_MAPPING)
                .append(FIELD_DISPLAY_ORDER, 2)
                .append(SECTION_ORDER, 3)
                .append(TOOLTIP, new Document()
                        .append(DEFINITION, "Resolution types for defects that can be excluded from Defect Density calculation."))
        );

        // Status Consider for Issue Closure
        fieldMappingStructureDocuments.add(new Document()
                .append(FIELD_NAME, "includedDefectClosureStatusesKPI195")
                .append(FIELD_LABEL, "Status Consider for Issue Closure")
                .append(FIELD_TYPE, CHIPS)
                .append(FIELD_CATEGORY, "workflow")
                .append(SECTION, WORKFLOW_STATUS_MAPPING)
                .append(FIELD_DISPLAY_ORDER, 3)
                .append(SECTION_ORDER, 4)
                .append(TOOLTIP, new Document()
                        .append(DEFINITION, "Status considered for defect closure (Mention completed status of all types of defects)"))
                .append(MANDATORY, true)
        );

        // Target KPI Value
        fieldMappingStructureDocuments.add(new Document()
                .append(FIELD_NAME, "thresholdValueKPI195")
                .append(FIELD_LABEL, "Target KPI Value")
                .append(FIELD_TYPE, "number")
                .append(SECTION, "Project Level Threshold")
                .append(PROCESSOR_COMMON, false)
                .append(TOOLTIP, new Document()
                        .append(DEFINITION, "Target KPI value denotes the bare minimum a project should maintain for a KPI. User should just input the number and the unit like percentage, hours will automatically be considered. If the threshold is empty, then a common target KPI line will be shown")
                .append(FIELD_DISPLAY_ORDER, 1)
                .append(SECTION_ORDER, 4)
                .append(MANDATORY, false)
                .append(NODE_SPECIFIC, false)
        ));

        return fieldMappingStructureDocuments;
    }

    private Document constructKpiCategoryMappingDocument() {
        return new Document()
                .append(KPI_ID, KPI_195)
                .append("categoryId", "quality")
                .append("kpiOrder", 17)
                .append(KANBAN, false);
    }

    private Document constructKpiMasterDocument() {
            return new Document()
                    .append(KPI_ID, KPI_195)
                    .append("kpiName", "Defects Breached SLAs")
                    .append("isDeleted", "False")
                    .append("defaultOrder", 28)
                    .append("kpiUnit", "%")
                    .append("chartType", "stacked-bar-chart")
                    .append("upperThresholdBG", "red")
                    .append("lowerThresholdBG", "white")
                    .append("xAxisLabel", "Sprints")
                    .append("yAxisLabel", "% defects that breached SLA")
                    .append("showTrend", true)
                    .append("isPositiveTrend", false)
                    .append("calculateMaturity", false)
                    .append("hideOverallFilter", false)
                    .append("kpiSource", "Jira")
                    .append("maxValue", 100)
                    .append("thresholdValue", 20)
                    .append(KANBAN, false)
                    .append("groupId", 26)
                    .append("kpiInfo",
                            new Document().append(DEFINITION,
                                            "Defects Breached SLAs (%) refers to the percentage of defects within a system or service that fail to meet the agreed-upon Service Level Agreement (SLA) timeframes.")
                            .append("formula", List.of(
                                    new Document()
                                            .append("lhs", "Defects Breached SLA (%)")
                                            .append("operator", "division")
                                            .append("operands", List.of("Number of Resolved Defects that breached SLA",
                                                    "Total Resolved Defects"))
                            ))
                            .append("details", List.of(
                                    new Document()
                                            .append("type", "link")
                                            .append("kpiLinkDetail", new Document()
                                                    .append("text", "Detailed Information at")
                                                    .append("link", "https://knowhow.suite.publicissapient.com/wiki/spaces/PS/pages/270794755/Defects+Breached+SLAs")
                                            )
                            )))
                    .append("aggregationCriteria", "average")
                    .append("isTrendCalculative", false)
                    .append("isAdditionalFilterSupport", true)
                    .append("combinedKpiSource", "Jira");
    }
}
