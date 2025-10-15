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

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1410;

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
@ChangeUnit(id = "ai_usage_analytics", order = "14100", author = "vladinu", systemVersion = "14.1.0")
public class AiUsageAnalyticsChangeUnit {

    private static final String KPI_ID = "kpiId";
    private static final String KPI_198 = "kpi198";
    private static final String DEFINITION = "definition";

    private static final String FIELD_NAME = "fieldName";
    private static final String FIELD_LABEL = "fieldLabel";
    private static final String FIELD_DISPLAY_ORDER = "fieldDisplayOrder";
    private static final String FIELD_TYPE = "fieldType";
    private static final String FIELD_CATEGORY = "fieldCategory";
    private static final String SECTION = "section";
    private static final String CHIPS = "chips";
    private static final String SECTION_ORDER = "sectionOrder";
    private static final String MANDATORY = "mandatory";
    private static final String TOOLTIP = "tooltip";
    private static final String WORKFLOW_STATUS_MAPPING = "Workflow Status Mapping";

    private final MongoTemplate mongoTemplate;

    @Execution
    public void execution() {
        mongoTemplate.getCollection("kpi_master").insertOne(constructKpiMasterDocument());
        mongoTemplate.getCollection("field_mapping_structure").insertMany(constructFieldMappingStructureDocuments());
    }

    @RollbackExecution
    public void rollback() {
        log.error("Mongock upgrade failed");

        mongoTemplate.getCollection("kpi_master").deleteOne(new Document(KPI_ID, KPI_198));
        mongoTemplate.getCollection("field_mapping_structure")
                .deleteMany(new Document(FIELD_NAME, new Document("$in", List.of(
                        "aiUsageTypeJiraCustomFieldKPI198",
                        "aiEfficiencyGainJiraCustomFieldKPI198",
                        "includedIssueTypesFromTheCompletedIssuesKPI198",
                        "includedStatusesToIdentifyCompletedIssuesKPI198"
                ))));
    }

    private static List<Document> constructFieldMappingStructureDocuments() {
        List<Document> fieldMappingStructureDocuments = new ArrayList<>();

        fieldMappingStructureDocuments.add(new Document()
                .append(FIELD_NAME, "aiUsageTypeJiraCustomFieldKPI198")
                .append(FIELD_LABEL, "AI Usage Type")
                .append(FIELD_DISPLAY_ORDER, 1)
                .append(FIELD_TYPE, "text")
                .append(FIELD_CATEGORY, "fields")
                .append(SECTION, "Custom Fields Mapping")
                .append(SECTION_ORDER, 1)
                .append(MANDATORY, true)
                .append(TOOLTIP, new Document()
                        .append(DEFINITION,
                                "Custom Jira field representing the kind of AI capability applied in this project or " +
                                        "ticket. Provide the field name which can be used to extract this information" +
                                        " from a Jira issue"))
        );

        fieldMappingStructureDocuments.add(new Document()
                .append(FIELD_NAME, "aiEfficiencyGainJiraCustomFieldKPI198")
                .append(FIELD_LABEL, "AI Efficiency Gain")
                .append(FIELD_DISPLAY_ORDER, 2)
                .append(FIELD_TYPE, "text")
                .append(FIELD_CATEGORY, "fields")
                .append(SECTION, "Custom Fields Mapping")
                .append(SECTION_ORDER, 1)
                .append(MANDATORY, true)
                .append(TOOLTIP, new Document()
                        .append(DEFINITION,
                                "Custom Jira field representing the measurable improvement in productivity or " +
                                        "process efficiency resulting from AI adoption, e.g., reduced cycle " +
                                        "time, faster delivery or fewer manual steps. Provide the field name which " +
                                        "can be used to extract this information" +
                                        " from a Jira issue"))
        );

        // Issue types which will be included in the analytics data from the completed issues
        fieldMappingStructureDocuments.add(new Document()
                .append(FIELD_NAME, "includedIssueTypesFromTheCompletedIssuesKPI198")
                .append(FIELD_LABEL, "Issue types filter for completed issues")
                .append(FIELD_TYPE, CHIPS)
                .append(FIELD_CATEGORY, "Issue_Type")
                .append(SECTION, "Issue Types Mapping")
                .append(FIELD_DISPLAY_ORDER, 1)
                .append(SECTION_ORDER, 2)
                .append(MANDATORY, false)
                .append(TOOLTIP, new Document()
                        .append(DEFINITION, "Completed issues of the specified issue types will be considered. If " +
                                "left blank, all issue types will be included by default"))
        );

        // Status to identify completed issues
        fieldMappingStructureDocuments.add(new Document()
                .append(FIELD_NAME, "includedStatusesToIdentifyCompletedIssuesKPI198")
                .append(FIELD_LABEL, "Status to identify completed issues")
                .append(FIELD_TYPE, CHIPS)
                .append(FIELD_CATEGORY, "workflow")
                .append(SECTION, WORKFLOW_STATUS_MAPPING)
                .append(FIELD_DISPLAY_ORDER, 1)
                .append(MANDATORY, false)
                .append(SECTION_ORDER, 2)
                .append(TOOLTIP, new Document()
                        .append(DEFINITION, "Completed issues of the specified issue types will be considered. If " +
                                "left blank, all statuses found to be relating to a 'Complete' state will be used"))
        );

        return fieldMappingStructureDocuments;
    }

	private static Document constructKpiMasterDocument() {
        return new Document()
                .append(KPI_ID, KPI_198)
                .append("kpiName", "AI Usage Analytics")
                .append("isDeleted", "False")
                .append("kpiSource", "Jira");
    }
}
