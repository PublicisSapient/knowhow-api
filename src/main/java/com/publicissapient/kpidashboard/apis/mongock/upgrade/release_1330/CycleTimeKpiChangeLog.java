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

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1330;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@ChangeUnit(id = "add_cycle_time_kpi", order = "13305", author = "kunkambl", systemVersion = "13.3.0")
public class CycleTimeKpiChangeLog {

	private static final String FIELD_MAPPING_STRUCTURE_COLLECTION = "field_mapping_structure";
	private static final String KPI_MASTER_COLLECTION = "kpi_master";
	private static final String KPI_ID = "kpi193";

	private static final String WORKFLOW_CATEGORY = "workflow";
	private static final String ISSUE_TYPE_CATEGORY = "Issue_Type";
	private static final String WORKFLOW_SECTION = "WorkFlow Status Mapping";

	private static final String FIELD_TYPE = "fieldType";
	private static final String FIELD_NAME = "fieldName";
	private static final String FIELD_LABEL = "fieldLabel";
	private static final String MANDATORY = "mandatory";
	private static final String DEFINITION = "definition";
	private static final String TOOLTIP = "tooltip";
	private static final String SECTION = "section";
	private static final String PROCESSOR_COMMON = "processorCommon";
	private static final String NODE_SPECIFIC = "nodeSpecific";
	private static final String CHIPS = "chips";
	private static final String FIELD_CATEGORY = "fieldCategory";
	private static final String FIELD_DISPLAY_ORDER = "fieldDisplayOrder";

	private final MongoTemplate mongoTemplate;

	public CycleTimeKpiChangeLog(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void execution() {
		addCycleTimeKpi();
		addFieldMappingStructure();
	}

	private void addCycleTimeKpi() {
		Document kpiInfo = new Document().append(DEFINITION,
				"Cycle time helps ascertain time spent on each step of the complete issue lifecycle. It is depicted as 3 core cycles - Intake to DOR, DOR to DOD, DOD to Live.")
				.append("details",
						List.of(new Document("type", "link").append("kpiLinkDetail",
								new Document().append("text", "Detailed Information at").append("link",
										"https://knowhow.tools.publicis.sapient.com/wiki/kpi171-Cycle+Time"))));

		Document kpiDocument = new Document().append("kpiId", KPI_ID).append("kpiName", "Cycle Time")
				.append("maxValue", "").append("kpiUnit", "Days").append("isDeleted", "False").append("defaultOrder", 28)
				.append("kpiSource", "Jira").append("groupId", 4).append("thresholdValue", "").append("kanban", false)
				.append("chartType", "table").append("yAxisLabel", "").append("xAxisLabel", "")
				.append("isAdditionalfFilterSupport", false).append("kpiFilter", "multiSelectDropDown")
				.append("calculateMaturity", false).append("kpiInfo", kpiInfo)
				.append("combinedKpiSource", "Jira/Azure/Rally");

		mongoTemplate.getCollection(KPI_MASTER_COLLECTION).insertOne(kpiDocument);
	}

	private void addFieldMappingStructure() {
		List<Document> fieldDocuments = List.of(createJiraIssueTypeDocument(), createJiraDodDocument(),
				createJiraLiveStatusDocument(), createJiraDorDocument(), createStoryFirstStatusDocument());

		mongoTemplate.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION).insertMany(fieldDocuments);
	}

	private Document createJiraIssueTypeDocument() {

		return new Document().append(FIELD_NAME, "jiraIssueTypeKPI193").append(FIELD_LABEL, "Issue types to consider")
				.append(FIELD_TYPE, CHIPS).append(FIELD_CATEGORY, ISSUE_TYPE_CATEGORY)
				.append(SECTION, "Issue Types Mapping").append(PROCESSOR_COMMON, false)
				.append(TOOLTIP, new Document(DEFINITION, "All issue types considered for Cycle calculation"))
				.append(FIELD_DISPLAY_ORDER, 1).append(MANDATORY, false).append(NODE_SPECIFIC, false)
				.append("sectionOrder", 2);
	}

	private Document createJiraDodDocument() {

		return new Document().append(FIELD_NAME, "jiraDodKPI193").append(FIELD_LABEL, "Status to identify DOD")
				.append(FIELD_TYPE, CHIPS).append(FIELD_CATEGORY, WORKFLOW_CATEGORY).append(SECTION, WORKFLOW_SECTION)
				.append(PROCESSOR_COMMON, false)
				.append(TOOLTIP, new Document(DEFINITION,
						"Workflow status/es to identify when an issue is delivered based on Definition of Done (DOD)"))
				.append(FIELD_DISPLAY_ORDER, 3).append(MANDATORY, true).append(NODE_SPECIFIC, false)
				.append("sectionOrder", 4);
	}

	private Document createJiraLiveStatusDocument() {

		return new Document().append(FIELD_NAME, "jiraLiveStatusKPI193")
				.append(FIELD_LABEL, "Status to identify Live issues").append(FIELD_TYPE, CHIPS)
				.append(FIELD_CATEGORY, WORKFLOW_CATEGORY).append(SECTION, WORKFLOW_SECTION)
				.append(PROCESSOR_COMMON, false)
				.append(TOOLTIP,
						new Document(DEFINITION, "Workflow status/es to identify that an issue is live in Production"))
				.append(FIELD_DISPLAY_ORDER, 4).append(MANDATORY, true).append(NODE_SPECIFIC, false);
	}

	private Document createJiraDorDocument() {

		return new Document().append(FIELD_NAME, "jiraDorKPI193").append(FIELD_LABEL, "Status to identify DOR")
				.append(FIELD_TYPE, CHIPS).append(FIELD_CATEGORY, WORKFLOW_CATEGORY).append(SECTION, WORKFLOW_SECTION)
				.append(PROCESSOR_COMMON, false)
				.append(TOOLTIP, new Document(DEFINITION,
						"Workflow status/es that identify that a backlog item is ready to be taken in a sprint based on Definition of Ready (DOR)"))
				.append(FIELD_DISPLAY_ORDER, 2).append(MANDATORY, true).append(NODE_SPECIFIC, false);
	}

	private Document createStoryFirstStatusDocument() {

		return new Document().append(FIELD_NAME, "storyFirstStatusKPI193")
				.append(FIELD_LABEL, "First Status when issue is created").append(FIELD_TYPE, "text")
				.append(FIELD_CATEGORY, WORKFLOW_CATEGORY).append(SECTION, WORKFLOW_SECTION)
				.append(PROCESSOR_COMMON, false)
				.append(TOOLTIP, new Document(DEFINITION, "Default first status of all issue types in consideration"))
				.append(FIELD_DISPLAY_ORDER, 1).append(MANDATORY, true).append(NODE_SPECIFIC, false);
	}

	@RollbackExecution
	public void rollback() {
		mongoTemplate.getCollection(KPI_MASTER_COLLECTION).deleteOne(new Document("kpiId", KPI_ID));

		List<String> fieldsToRemove = List.of("jiraIssueTypeKPI193", "jiraDodKPI193", "jiraLiveStatusKPI193",
				"jiraDorKPI193", "storyFirstStatusKPI193");
		mongoTemplate.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.deleteMany(new Document(FIELD_NAME, new Document("$in", fieldsToRemove)));
	}
}
