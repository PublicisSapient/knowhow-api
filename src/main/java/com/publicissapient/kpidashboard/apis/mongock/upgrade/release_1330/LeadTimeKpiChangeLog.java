/*
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1330;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@ChangeUnit(id = "add_lead_time_kpi", order = "13305", author = "kunkambl", systemVersion = "13.3.0")
public class LeadTimeKpiChangeLog {

	private static final String KPI_ID = "kpi192";
	private static final String KPI_NAME = "Lead Time";
	private static final String KPI_UNIT = "Days";
	private static final String CHART_TYPE = "line";
	private static final String KPI_SOURCE = "Jira";
	private static final String COMBINED_KPI_SOURCE = "Jira/Azure/Rally";
	private static final String THRESHOLD_VALUE = "20";
	private static final int GROUP_ID = 4;
	private static final int DEFAULT_ORDER = 27;

	private static final String FIELD_JIRA_ISSUE_TYPE = "jiraIssueTypeKPI192";
	private static final String FIELD_THRESHOLD_VALUE = "thresholdValueKPI192";
	private static final String FIELD_JIRA_LIVE_STATUS = "jiraLiveStatusKPI192";

	private static final String KPI_MASTER_COLLECTION = "kpi_master";
	private static final String FIELD_MAPPING_STRUCTURE_COLLECTION = "field_mapping_structure";
	private static final String KPI_COLUMN_CONFIGS = "kpi_column_configs";

	private static final String FIELD_TYPE = "fieldType";
	private static final String FIELD_NAME = "fieldName";
	private static final String FIELD_LABEL = "fieldLabel";
	private static final String MANDATORY = "mandatory";
	private static final String DEFINITION = "definition";
	private static final String TOOLTIP = "tooltip";
	private static final String SECTION = "section";
	private static final String PROCESSOR_COMMON = "processorCommon";
	private static final String NODE_SPECIFIC = "nodeSpecific";
	
	private static final String COLUMN_NAME = "columnName";
	private static final String ORDER = "order";
	private static final String IS_SHOWN = "isShown";
	private static final String IS_DEFAULT = "isDefault";

	private final MongoTemplate mongoTemplate;

	public LeadTimeKpiChangeLog(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void execute() {
		addLeadTimeKpi();
		addFieldMappingStructure();
		addToKpiCategoryMapping();
	}

	private void addLeadTimeKpi() {
		Document kpiDocument = createKpiDocument();
		mongoTemplate.getCollection(KPI_MASTER_COLLECTION).insertOne(kpiDocument);
		mongoTemplate.getCollection(KPI_COLUMN_CONFIGS).insertOne(createKpiColumnConfig());
	}

	private Document createKpiDocument() {
		return new Document().append("kpiId", KPI_ID).append("kpiName", KPI_NAME)
				.append("isDeleted", Boolean.FALSE.toString()).append("defaultOrder", DEFAULT_ORDER)
				.append("kpiBaseLine", "0").append("kpiUnit", KPI_UNIT).append("chartType", CHART_TYPE)
				.append("upperThresholdBG", "red").append("lowerThresholdBG", "white").append("xAxisLabel", "Range")
				.append("yAxisLabel", KPI_UNIT).append("showTrend", Boolean.TRUE)
				.append("isPositiveTrend", Boolean.FALSE).append("calculateMaturity", Boolean.TRUE)
				.append("hideOverallFilter", Boolean.FALSE).append("kpiSource", KPI_SOURCE)
				.append("thresholdValue", THRESHOLD_VALUE).append("kanban", Boolean.FALSE).append("groupId", GROUP_ID)
				.append("kpiInfo", createKpiInfoDocument()).append("kpiFilter", "dropdown")
				.append("aggregationCriteria", "sum").append("isTrendCalculative", Boolean.FALSE)
				.append("isAdditionalFilterSupport", Boolean.FALSE).append("maturityRange", createMaturityRangeList())
				.append("combinedKpiSource", COMBINED_KPI_SOURCE);
	}

	private Document createKpiInfoDocument() {
		return new Document()
				.append(DEFINITION,
						"Lead Time is the time from the moment when the request was made by a client "
								+ "and placed on a board to when all work on this item is completed and the "
								+ "request was delivered to the client")
				.append("details", createKpiDetailsList())
				.append("_class", "com.publicissapient.kpidashboard.common.model.application.KpiInfo");
	}

	private List<Document> createKpiDetailsList() {
		Document linkDetail = new Document().append("text", "Detailed Information at").append("link",
				"https://knowhow.tools.publicis.sapient.com/wiki/kpi3-Lead+Time");

		Document detailDocument = new Document().append("type", "link").append("kpiLinkDetail", linkDetail);

		return Collections.singletonList(detailDocument);
	}

	private List<String> createMaturityRangeList() {
		return Arrays.asList("-60", "60-45", "45-30", "30-10", "10-");
	}

	private void addFieldMappingStructure() {
		List<Document> fieldDocuments = Arrays.asList(createIssueTypeField(), createThresholdField(),
				createLiveStatusField());

		mongoTemplate.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION).insertMany(fieldDocuments);
	}

	private Document createIssueTypeField() {
		return new Document().append(FIELD_NAME, FIELD_JIRA_ISSUE_TYPE).append(FIELD_LABEL, "Issue types to consider")
				.append(FIELD_TYPE, "chips").append("fieldCategory", "Issue_Type")
				.append(SECTION, "Issue Types Mapping").append(PROCESSOR_COMMON, Boolean.FALSE)
				.append(TOOLTIP, new Document(DEFINITION, "All issue types considered for Lead Time calculation."))
				.append("fieldDisplayOrder", 1).append("sectionOrder", 2).append(MANDATORY, Boolean.TRUE)
				.append(NODE_SPECIFIC, Boolean.FALSE);
	}

	private Document createThresholdField() {
		return new Document().append(FIELD_NAME, FIELD_THRESHOLD_VALUE).append(FIELD_LABEL, "Target KPI Value")
				.append(FIELD_TYPE, "number").append(SECTION, "Project Level Threshold")
				.append(PROCESSOR_COMMON, Boolean.FALSE)
				.append(TOOLTIP, new Document(DEFINITION,
						"Target KPI value denotes the bare minimum a project should maintain for a KPI. "
								+ "User should just input the number and the unit like percentage, hours will "
								+ "automatically be considered. If the threshold is empty, then a common target "
								+ "KPI line will be shown"))
				.append("fieldDisplayOrder", 1).append("sectionOrder", 6).append(MANDATORY, Boolean.FALSE)
				.append(NODE_SPECIFIC, Boolean.FALSE);
	}

	private Document createLiveStatusField() {
		return new Document().append(FIELD_NAME, FIELD_JIRA_LIVE_STATUS)
				.append(FIELD_LABEL, "Status to identify Live issues").append(FIELD_TYPE, "chips")
				.append("fieldCategory", "workflow").append(SECTION, "WorkFlow Status Mapping")
				.append(PROCESSOR_COMMON, Boolean.FALSE)
				.append(TOOLTIP,
						new Document(DEFINITION, "Workflow status/es to identify that an issue is live in Production."))
				.append(MANDATORY, Boolean.TRUE).append(NODE_SPECIFIC, Boolean.FALSE);
	}
	
	private Document createKpiColumnConfig() {
		return new Document(new Document("kpiId", KPI_ID).append("kpiColumnDetails", List.of(
				new Document(COLUMN_NAME, "Issue ID").append(ORDER, 1).append(IS_SHOWN, true).append(IS_DEFAULT, true),
				new Document(COLUMN_NAME, "Issue Type").append(ORDER, 2).append(IS_SHOWN, true).append(IS_DEFAULT,
						true),
				new Document(COLUMN_NAME, "Issue Description").append(ORDER, 3).append(IS_SHOWN, true)
						.append(IS_DEFAULT, true),
				new Document(COLUMN_NAME, "Created Date").append(ORDER, 4).append(IS_SHOWN, true).append(IS_DEFAULT,
						true),
				new Document(COLUMN_NAME, "Live Date").append(ORDER, 5).append(IS_SHOWN, true).append(IS_DEFAULT, true),
				new Document(COLUMN_NAME, "Lead Time (In Days)").append(ORDER, 6).append(IS_SHOWN, true)
						.append(IS_DEFAULT, true))));

	}

	public void addToKpiCategoryMapping() {
		Document kpiCategoryMappingDocument = new Document().append(KPI_ID, KPI_ID).append("categoryId", "speed")
				.append("kpiOrder", 11).append("kanban", false);
		mongoTemplate.getCollection("kpi_category_mapping").insertOne(kpiCategoryMappingDocument);
	}

	@RollbackExecution
	public void rollback() {
		removeLeadTimeKpi();
		removeFieldMappingStructure();
		removeKpiColumnConfig();
		removeKpiCategoryMapping();
	}

	private void removeLeadTimeKpi() {
		mongoTemplate.getCollection(KPI_MASTER_COLLECTION).deleteOne(new Document("kpiId", KPI_ID));
	}

	private void removeKpiColumnConfig() {
		mongoTemplate.getCollection(KPI_COLUMN_CONFIGS).deleteOne(new Document("kpiId", KPI_ID));
	}

	private void removeFieldMappingStructure() {
		List<String> fieldsToRemove = Arrays.asList(FIELD_JIRA_ISSUE_TYPE, FIELD_THRESHOLD_VALUE,
				FIELD_JIRA_LIVE_STATUS);
		mongoTemplate.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.deleteMany(new Document(FIELD_NAME, new Document("$in", fieldsToRemove)));
	}

	private void removeKpiCategoryMapping() {
		mongoTemplate.getCollection("kpi_category_mapping").deleteOne(new Document(KPI_ID, KPI_ID));
	}
}

