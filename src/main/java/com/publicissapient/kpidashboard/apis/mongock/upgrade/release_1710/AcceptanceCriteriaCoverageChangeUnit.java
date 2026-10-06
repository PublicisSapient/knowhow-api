/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.ReplaceOptions;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Seeds the Acceptance Criteria Coverage KPI (kpi227) — Slingshot / Intake.
 *
 * <p>Registers the kpi_master document, the excel column configuration and the four field mapping
 * structure entries that drive the project level configuration screen.
 *
 * <p>The KPI also reads {@code jiraAcceptanceCriteriaCustomField}, which is seeded separately by
 * {@code AcceptanceCriteriaFieldMappingChangeUnit} because it is a processor level setting shared
 * by every consumer of the acceptance criteria, not something this KPI owns.
 */
@ChangeUnit(
		id = "acceptance_criteria_coverage_kpi_insert",
		order = "17206",
		author = "knowhow",
		systemVersion = "17.1.0")
public class AcceptanceCriteriaCoverageChangeUnit {

	private static final String KPI_ID = "kpi227";
	private static final String KPI_NAME = "Acceptance Criteria Coverage";
	private static final String KPI_ID_FIELD = "kpiId";
	private static final String KPI_MASTER_COLLECTION = "kpi_master";
	private static final String KPI_COLUMN_CONFIGS_COLLECTION = "kpi_column_configs";
	private static final String FIELD_MAPPING_STRUCTURE_COLLECTION = "field_mapping_structure";
	private static final String COLUMN_NAME = "columnName";
	private static final String ORDER = "order";
	private static final String IS_SHOWN = "isShown";
	private static final String IS_DEFAULT = "isDefault";
	private static final String FIELD_NAME = "fieldName";
	private static final String FIELD_LABEL = "fieldLabel";
	private static final String FIELD_TYPE = "fieldType";
	private static final String FIELD_CATEGORY = "fieldCategory";
	private static final String FIELD_DISPLAY_ORDER = "fieldDisplayOrder";
	private static final String SECTION = "section";
	private static final String SECTION_ORDER = "sectionOrder";
	private static final String TOOLTIP = "tooltip";
	private static final String DEFINITION = "definition";
	private static final String MANDATORY = "mandatory";
	private static final String OPTIONS = "options";
	private static final String LABEL = "label";
	private static final String VALUE = "value";
	private static final String CHIPS = "chips";
	private static final String IN = "$in";

	private static final String STORY_TYPE_FIELD = "jiraStoryIdentificationKPI227";
	private static final String IN_PROGRESS_STATUS_FIELD = "jiraStatusForInProgressKPI227";
	private static final String FORMAT_FIELD = "acceptanceCriteriaFormatKPI227";
	private static final String THRESHOLD_FIELD = "thresholdValueKPI227";

	private static final String KPI_DEFINITION =
			"Average count of acceptance criteria attached to a story at the moment it enters In Progress. "
					+ "A team consistently shipping with 0–1 ACs per story is a quality risk; "
					+ "a team with 8+ ACs per story may be over-specifying.";

	@Execution
	public void execution(MongoTemplate mongoTemplate) {
		insertKpiMaster(mongoTemplate);
		insertKpiColumnConfig(mongoTemplate);
		insertFieldMappingStructure(mongoTemplate);
	}

	public void insertKpiMaster(MongoTemplate mongoTemplate) {
		Document kpiMaster =
				new Document()
						.append(KPI_ID_FIELD, KPI_ID)
						.append("kpiName", KPI_NAME)
						.append("isDeleted", "False")
						.append("defaultOrder", 4)
						.append("kpiCategory", "Slingshot")
						.append("kpiSubCategory", "Intake")
						.append("kpiUnit", "Count")
						.append("chartType", "line")
						.append("xAxisLabel", "Weeks")
						.append("yAxisLabel", "Count")
						.append("showTrend", true)
						.append("isPositiveTrend", true)
						// No universal target - the healthy number depends on story size, so a maturity
						// ladder would be misleading. The band drill-down carries the interpretation.
						.append("calculateMaturity", false)
						.append("hideOverallFilter", true)
						.append("kpiSource", "Jira")
						.append("maxValue", 12)
						.append("thresholdValue", 3.0)
						.append("kanban", false)
						.append("groupId", 317)
						.append("kpiInfo", new Document().append(DEFINITION, KPI_DEFINITION))
						// no KPI filter: the split is exposed as a per-period coverage band drill-down
						.append("kpiFilter", null)
						.append("aggregationCriteria", "average")
						.append("isTrendCalculative", false)
						.append("isAdditionalFilterSupport", false)
						.append("combinedKpiSource", "Jira/Azure/Rally")
						.append("upperThresholdBG", "white")
						.append("lowerThresholdBG", "red")
						.append("forecastModel", "thetaMethod")
						.append("kpiWidth", 50)
						.append("kpiSubCategoryOrder", 4);

		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.replaceOne(
						new Document(KPI_ID_FIELD, KPI_ID), kpiMaster, new ReplaceOptions().upsert(true));
	}

	public void insertKpiColumnConfig(MongoTemplate mongoTemplate) {
		Document columnConfig =
				new Document()
						.append("basicProjectConfigId", null)
						.append(KPI_ID_FIELD, KPI_ID)
						.append(
								"kpiColumnDetails",
								Arrays.asList(
										column("Days/Weeks", 1),
										column("Project", 2),
										column("Issue ID", 3),
										column("Issue Type", 4),
										column("Issue Description", 5),
										column("Status", 6),
										column("Dev Start Date", 7),
										column("Acceptance Criteria Format", 8),
										column("Coverage Band", 9),
										column("Acceptance Criteria Count", 10)));

		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS_COLLECTION)
				.replaceOne(
						new Document(KPI_ID_FIELD, KPI_ID), columnConfig, new ReplaceOptions().upsert(true));
	}

	private Document column(String name, int order) {
		return new Document()
				.append(COLUMN_NAME, name)
				.append(ORDER, order)
				.append(IS_SHOWN, true)
				.append(IS_DEFAULT, true);
	}

	public void insertFieldMappingStructure(MongoTemplate mongoTemplate) {
		upsertFieldMapping(
				mongoTemplate,
				new Document()
						.append(FIELD_NAME, STORY_TYPE_FIELD)
						.append(FIELD_LABEL, "Issue types to measure acceptance criteria for")
						.append(FIELD_TYPE, CHIPS)
						.append(FIELD_CATEGORY, "Issue_Type")
						.append(FIELD_DISPLAY_ORDER, 1)
						.append(SECTION_ORDER, 1)
						.append(SECTION, "Issue Types Mapping")
						.append(MANDATORY, true)
						.append(
								TOOLTIP,
								new Document()
										.append(
												DEFINITION,
												"All issue types that should carry acceptance criteria. "
														+ "Not every project tracks work as 'Story' - if the board delivers work as Task, "
														+ "QA Task, Enabler and so on, list those instead, otherwise this KPI reports nothing. "
														+ "Required - when left blank, this KPI shows no data. <hr>")));

		upsertFieldMapping(
				mongoTemplate,
				new Document()
						.append(FIELD_NAME, IN_PROGRESS_STATUS_FIELD)
						.append(FIELD_LABEL, "Status to identify issues in progress")
						.append(FIELD_TYPE, CHIPS)
						.append(FIELD_CATEGORY, "workflow")
						.append(FIELD_DISPLAY_ORDER, 10)
						.append(SECTION_ORDER, 4)
						.append(SECTION, "WorkFlow Status Mapping")
						.append(MANDATORY, true)
						.append(
								TOOLTIP,
								new Document()
										.append(
												DEFINITION,
												"Workflow statuses that mean development has started (e.g., In Progress, In Development). "
														+ "The <b>first</b> transition into any of these is the moment the acceptance criteria are counted, "
														+ "so a story that bounces in and out of In Progress is still counted only once. "
														+ "Required - when left blank, this KPI shows no data. <hr>")));

		upsertFieldMapping(
				mongoTemplate,
				new Document()
						.append(FIELD_NAME, FORMAT_FIELD)
						.append(FIELD_LABEL, "Acceptance criteria format")
						.append(FIELD_TYPE, "radiobutton")
						.append(FIELD_DISPLAY_ORDER, 6)
						.append(SECTION_ORDER, 1)
						.append(SECTION, "Custom Fields Mapping")
						.append("processorCommon", false)
						.append(MANDATORY, false)
						.append("nodeSpecific", false)
						.append(
								OPTIONS,
								Arrays.asList(
										option("Detect automatically", "AUTO"),
										option("Gherkin scenarios", "GHERKIN"),
										option("Bulleted / numbered list", "LIST"),
										option("One criterion per line", "LINE")))
						.append(
								TOOLTIP,
								new Document()
										.append(
												DEFINITION,
												"How the acceptance criteria text is split into individual criteria:"
														+ "<br>1. Detect automatically : looks for Gherkin scenarios first, then list items, then falls back to one per line. Recommended."
														+ "<br>2. Gherkin scenarios : counts 'Scenario:' / 'Scenario Outline:' headers, or 'Given' steps when scenarios are unnamed."
														+ "<br>3. Bulleted / numbered list : counts top level bullets, checkboxes and numbered items. Nested sub-points are treated as part of their parent criterion."
														+ "<br>4. One criterion per line : counts every non-empty line."
														+ "<br>Headings such as 'Acceptance Criteria:' are never counted. <hr>")));

		upsertFieldMapping(
				mongoTemplate,
				new Document()
						.append(FIELD_NAME, THRESHOLD_FIELD)
						.append(FIELD_LABEL, "Target KPI Value")
						.append(FIELD_TYPE, "number")
						.append(SECTION, "Project Level Threshold")
						.append("processorCommon", false)
						.append(
								TOOLTIP,
								new Document()
										.append(
												DEFINITION,
												"Target KPI value denotes the bare minimum a project should maintain for a KPI. User should just input the number and the unit like percentage, hours will automatically be considered. If the threshold is empty, then a common target KPI line will be shown"))
						.append(FIELD_DISPLAY_ORDER, 1)
						.append(SECTION_ORDER, 6)
						.append(MANDATORY, false)
						.append("nodeSpecific", false));
	}

	private Document option(String label, String value) {
		return new Document().append(LABEL, label).append(VALUE, value);
	}

	private void upsertFieldMapping(MongoTemplate mongoTemplate, Document fieldMapping) {
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.replaceOne(
						new Document(FIELD_NAME, fieldMapping.getString(FIELD_NAME)),
						fieldMapping,
						new ReplaceOptions().upsert(true));
	}

	@RollbackExecution
	public void rollback(MongoTemplate mongoTemplate) {
		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.deleteOne(new Document(KPI_ID_FIELD, KPI_ID));
		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS_COLLECTION)
				.deleteOne(new Document(KPI_ID_FIELD, KPI_ID));
		List<String> fieldNames =
				Arrays.asList(STORY_TYPE_FIELD, IN_PROGRESS_STATUS_FIELD, FORMAT_FIELD, THRESHOLD_FIELD);
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.deleteMany(new Document(FIELD_NAME, new Document(IN, fieldNames)));
	}
}
