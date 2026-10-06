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
 * Seeds the DOR Acceptance Rate KPI (kpi228) — Slingshot / Intake.
 *
 * <p>Registers the kpi_master document, the excel column configuration and the seven field mapping
 * structure entries that drive the project level configuration screen.
 *
 * <p>The KPI also reads {@code jiraAcceptanceCriteriaCustomField}, which is seeded separately by
 * {@code AcceptanceCriteriaFieldMappingChangeUnit} because it is a processor level setting shared
 * by every consumer of the acceptance criteria, not something this KPI owns.
 */
@ChangeUnit(
		id = "dor_acceptance_rate_kpi_insert",
		order = "17211",
		author = "knowhow",
		systemVersion = "17.1.0")
public class DorAcceptanceRateChangeUnit {

	private static final String KPI_ID = "kpi228";
	private static final String KPI_NAME = "DOR Acceptance Rate";
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
	private static final String NODE_SPECIFIC = "nodeSpecific";
	private static final String PROCESSOR_COMMON = "processorCommon";
	private static final String OPTIONS = "options";
	private static final String LABEL = "label";
	private static final String VALUE = "value";
	private static final String CHIPS = "chips";
	private static final String NUMBER = "number";
	private static final String WORKFLOW = "workflow";
	private static final String WORKFLOW_SECTION = "WorkFlow Status Mapping";
	private static final String CUSTOM_FIELDS_SECTION = "Custom Fields Mapping";
	private static final String IN = "$in";

	private static final String STORY_TYPE_FIELD = "jiraStoryIdentificationKPI228";
	private static final String READY_STATUS_FIELD = "jiraStatusForReadyKPI228";
	private static final String IN_PROGRESS_STATUS_FIELD = "jiraStatusForInProgressKPI228";
	private static final String REVISION_FIELDS_FIELD = "dorRevisionFieldsKPI228";
	private static final String CHANGE_PERCENT_FIELD = "dorSubstantiveChangePercentKPI228";
	private static final String REWRITE_COUNT_FIELD = "dorMajorRewriteRevisionCountKPI228";
	private static final String THRESHOLD_FIELD = "thresholdValueKPI228";

	private static final String KPI_DEFINITION =
			"Percentage of refined stories that pass through Definition of Ready without a major rewrite. "
					+ "A story counts once it has been marked Ready and has entered development; it fails when its "
					+ "Description and/or Acceptance Criteria were substantively revised more than the configured "
					+ "number of times inside the revision window. 80%+ is healthy. Below 60% means DOR is theatre - "
					+ "stories are being marked Ready prematurely.";

	@Execution
	public void execution(MongoTemplate mongoTemplate) {
		insertKpiMaster(mongoTemplate);
		insertKpiColumnConfig(mongoTemplate);
		insertFieldMappingStructure(mongoTemplate);
	}

	public void insertKpiMaster(MongoTemplate mongoTemplate) {
		Document formula =
				new Document()
						.append("lhs", "DOR Acceptance Rate")
						.append("operator", "division")
						.append(
								"operands",
								Arrays.asList(
										"No. of stories started with at most the allowed substantive revisions",
										"Total no. of refined stories that started development"));

		Document kpiMaster =
				new Document()
						.append(KPI_ID_FIELD, KPI_ID)
						.append("kpiName", KPI_NAME)
						.append("isDeleted", "False")
						.append("defaultOrder", 7)
						.append("kpiCategory", "Slingshot")
						.append("kpiSubCategory", "Intake")
						.append("kpiUnit", "%")
						.append("chartType", "line")
						.append("xAxisLabel", "Sprints")
						.append("yAxisLabel", "Percentage")
						.append("showTrend", true)
						.append("isPositiveTrend", true)
						.append("calculateMaturity", true)
						// Anchored on the published baselines: 80%+ healthy, below 60% means DOR is
						// theatre
						.append("maturityRange", Arrays.asList("-60", "60-70", "70-80", "80-90", "90-"))
						.append("hideOverallFilter", true)
						.append("kpiSource", "Jira")
						.append("maxValue", 100)
						.append("thresholdValue", 80.0)
						.append("kanban", false)
						.append("groupId", 318)
						.append(
								"kpiInfo",
								new Document()
										.append(DEFINITION, KPI_DEFINITION)
										.append("formula", List.of(formula)))
						// no KPI filter: the Sprint / Weekly split is exposed as a data count group
						.append("kpiFilter", null)
						.append("aggregationCriteria", "average")
						.append("isTrendCalculative", false)
						.append("isAdditionalFilterSupport", false)
						.append("combinedKpiSource", "Jira/Azure/Rally")
						.append("upperThresholdBG", "white")
						.append("lowerThresholdBG", "red")
						.append("forecastModel", "thetaMethod")
						.append("kpiWidth", 50)
						.append("kpiSubCategoryOrder", 7);

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
										column("Ready Time", 7),
										column("Dev Start Date", 8),
										column("Description Revisions", 9),
										column("Acceptance Criteria Revisions", 10),
										column("Substantive Revisions", 11),
										column("DOR Outcome", 12)));

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
						.append(FIELD_LABEL, "Issue types to measure DOR acceptance for")
						.append(FIELD_TYPE, CHIPS)
						.append(FIELD_CATEGORY, "Issue_Type")
						.append(FIELD_DISPLAY_ORDER, 1)
						.append(SECTION_ORDER, 1)
						.append(SECTION, "Issue Types Mapping")
						.append(MANDATORY, true)
						.append(
								TOOLTIP,
								tooltip(
										"All issue types that go through Definition of Ready. Not every project tracks work as "
												+ "'Story' - if the board delivers work as Task, Enabler and so on, list those instead, "
												+ "otherwise this KPI reports nothing. Required - when left blank, this KPI shows no data. <hr>")));

		upsertFieldMapping(
				mongoTemplate,
				new Document()
						.append(FIELD_NAME, READY_STATUS_FIELD)
						.append(FIELD_LABEL, "Status(es) marking a story as Ready")
						.append(FIELD_TYPE, CHIPS)
						.append(FIELD_CATEGORY, WORKFLOW)
						.append(FIELD_DISPLAY_ORDER, 11)
						.append(SECTION_ORDER, 4)
						.append(SECTION, WORKFLOW_SECTION)
						.append(MANDATORY, true)
						.append(
								TOOLTIP,
								tooltip(
										"Workflow statuses that mean the story has passed Definition of Ready (e.g., Ready, "
												+ "Ready for Dev, Sprint Ready). The <b>first</b> transition into any of these opens the "
												+ "revision window. A story that was never marked Ready is excluded from this KPI entirely. "
												+ "Required - when left blank, this KPI shows no data. <hr>")));

		upsertFieldMapping(
				mongoTemplate,
				new Document()
						.append(FIELD_NAME, IN_PROGRESS_STATUS_FIELD)
						.append(FIELD_LABEL, "Status(es) marking development start")
						.append(FIELD_TYPE, CHIPS)
						.append(FIELD_CATEGORY, WORKFLOW)
						.append(FIELD_DISPLAY_ORDER, 12)
						.append(SECTION_ORDER, 4)
						.append(SECTION, WORKFLOW_SECTION)
						.append(MANDATORY, true)
						.append(
								TOOLTIP,
								tooltip(
										"Workflow statuses that mean development has started (e.g., In Progress, In Development). "
												+ "The first transition into any of these <b>after</b> the Ready transition closes the revision "
												+ "window and places the story in the denominator for that period. Required - when left blank, "
												+ "this KPI shows no data. <hr>")));

		upsertFieldMapping(
				mongoTemplate,
				new Document()
						.append(FIELD_NAME, REVISION_FIELDS_FIELD)
						.append(FIELD_LABEL, "Fields to count revisions on")
						.append("placeHolderText", "Select the fields to inspect")
						.append(FIELD_TYPE, "multiselect")
						.append(FIELD_DISPLAY_ORDER, 7)
						.append(SECTION_ORDER, 1)
						.append(SECTION, CUSTOM_FIELDS_SECTION)
						.append(PROCESSOR_COMMON, false)
						.append(MANDATORY, false)
						.append(NODE_SPECIFIC, false)
						.append(
								OPTIONS,
								Arrays.asList(
										option("Description", "DESCRIPTION"),
										option("Acceptance Criteria", "ACCEPTANCE_CRITERIA")))
						.append(
								TOOLTIP,
								tooltip(
										"Which issue fields are inspected for rewrites after a story is marked Ready. Leave blank "
												+ "to inspect both. Acceptance Criteria is read from the field configured as 'Custom field for "
												+ "Acceptance Criteria' - when that is not set, only Description revisions are available. <hr>")));

		upsertFieldMapping(
				mongoTemplate,
				new Document()
						.append(FIELD_NAME, CHANGE_PERCENT_FIELD)
						.append(FIELD_LABEL, "Minimum change % for a substantive revision")
						.append(FIELD_TYPE, NUMBER)
						.append(FIELD_DISPLAY_ORDER, 8)
						.append(SECTION_ORDER, 1)
						.append(SECTION, CUSTOM_FIELDS_SECTION)
						.append(PROCESSOR_COMMON, false)
						.append(MANDATORY, false)
						.append(NODE_SPECIFIC, false)
						.append(
								TOOLTIP,
								tooltip(
										"How much of the text must change for an edit to count as a real rewrite rather than a typo "
												+ "fix or a formatting tweak. Both versions are stripped of markup and punctuation and compared "
												+ "word by word; the edit counts when the changed share of the larger version reaches this "
												+ "percentage. Defaults to 20. Filling in a previously empty field always counts. <hr>")));

		upsertFieldMapping(
				mongoTemplate,
				new Document()
						.append(FIELD_NAME, REWRITE_COUNT_FIELD)
						.append(FIELD_LABEL, "Substantive revisions allowed before a major rewrite")
						.append(FIELD_TYPE, NUMBER)
						.append(FIELD_DISPLAY_ORDER, 9)
						.append(SECTION_ORDER, 1)
						.append(SECTION, CUSTOM_FIELDS_SECTION)
						.append(PROCESSOR_COMMON, false)
						.append(MANDATORY, false)
						.append(NODE_SPECIFIC, false)
						.append(
								TOOLTIP,
								tooltip(
										"A story fails Definition of Ready when it has strictly more substantive revisions than this "
												+ "number. Defaults to 2, so a third substantive revision marks the story as a major rewrite. <hr>")));

		upsertFieldMapping(
				mongoTemplate,
				new Document()
						.append(FIELD_NAME, THRESHOLD_FIELD)
						.append(FIELD_LABEL, "Target KPI Value")
						.append(FIELD_TYPE, NUMBER)
						.append(SECTION, "Project Level Threshold")
						.append(PROCESSOR_COMMON, false)
						.append(
								TOOLTIP,
								tooltip(
										"Target KPI value denotes the bare minimum a project should maintain for a KPI. User should "
												+ "just input the number and the unit like percentage, hours will automatically be considered. "
												+ "If the threshold is empty, then a common target KPI line will be shown"))
						.append(FIELD_DISPLAY_ORDER, 1)
						.append(SECTION_ORDER, 6)
						.append(MANDATORY, false)
						.append(NODE_SPECIFIC, false));
	}

	private Document tooltip(String definition) {
		return new Document().append(DEFINITION, definition);
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
				Arrays.asList(
						STORY_TYPE_FIELD,
						READY_STATUS_FIELD,
						IN_PROGRESS_STATUS_FIELD,
						REVISION_FIELDS_FIELD,
						CHANGE_PERCENT_FIELD,
						REWRITE_COUNT_FIELD,
						THRESHOLD_FIELD);
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.deleteMany(new Document(FIELD_NAME, new Document(IN, fieldNames)));
	}
}
