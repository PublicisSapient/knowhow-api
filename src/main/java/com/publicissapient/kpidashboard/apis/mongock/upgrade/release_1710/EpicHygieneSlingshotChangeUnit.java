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
 * Registers the Epic Hygiene KPI (kpi312) - the KPI master entry, its Excel column configuration
 * and the two field mapping structures that drive it.
 *
 * <p>Unlike Story Hygiene (kpi311) this KPI is not sprint scoped: it grades every Epic created in
 * the trailing six months, so it publishes no trend line and is rendered purely from its drill-down
 * table plus the project level readiness score.
 *
 * <p>{@code jiraFieldsSelectionKPI312} carries the readiness dimensions. Each configured entry
 * supplies a dimension label, the Jira field holding the evidence and the scoring criteria - the
 * dimensions are therefore fully data-driven and can be re-worded per project without a release.
 */
@ChangeUnit(
		id = "epic_hygiene_slingshot_kpi_insert",
		order = "17175",
		author = "kunkambl",
		systemVersion = "17.1.0")
public class EpicHygieneSlingshotChangeUnit {

	private static final String KPI_ID = "kpi312";
	private static final String KPI_ID_FIELD = "kpiId";
	private static final String KPI_MASTER_COLLECTION = "kpi_master";
	private static final String KPI_COLUMN_CONFIGS_COLLECTION = "kpi_column_configs";
	private static final String FIELD_MAPPING_STRUCTURE_COLLECTION = "field_mapping_structure";

	private static final String COLUMN_NAME = "columnName";
	private static final String ORDER = "order";
	private static final String IS_SHOWN = "isShown";
	private static final String IS_DEFAULT = "isDefault";
	private static final String FIELD_NAME = "fieldName";
	private static final String DEFINITION = "definition";

	private static final String THRESHOLD_FIELD = "thresholdValueKPI312";
	private static final String JIRA_FIELDS_SELECTION_FIELD = "jiraFieldsSelectionKPI312";
	private static final String READINESS_DIMENSIONS_LABEL = "Epic readiness dimensions";

	@Execution
	public void execution(MongoTemplate mongoTemplate) {
		insertKpiMaster(mongoTemplate);
		insertKpiColumnConfig(mongoTemplate);
		insertFieldMappingStructure(mongoTemplate);
	}

	/** Upserts the {@code kpi_master} definition of kpi312. */
	public void insertKpiMaster(MongoTemplate mongoTemplate) {
		Document kpiMaster =
				new Document()
						.append(KPI_ID_FIELD, KPI_ID)
						.append("kpiName", "Epic Hygiene")
						.append("isDeleted", "False")
						.append("defaultOrder", 2)
						.append("kpiCategory", "Slingshot")
						.append("kpiSubCategory", "Sandbox")
						.append("kpiUnit", "%")
						.append("chartType", "")
						.append("xAxisLabel", "")
						.append("yAxisLabel", "Percentage")
						.append("showTrend", false)
						.append("isPositiveTrend", true)
						.append("calculateMaturity", true)
						.append("maturityRange", Arrays.asList("0-20", "20-40", "40-60", "60-80", "80-"))
						.append("hideOverallFilter", true)
						.append("kpiSource", "Jira")
						.append("maxValue", 100)
						.append("thresholdValue", 70.0)
						.append("upperThresholdBG", "white")
						.append("lowerThresholdBG", "red")
						.append("kanban", false)
						.append("groupId", 312)
						.append(
								"kpiInfo",
								new Document()
										.append(
												DEFINITION,
												"AI-driven readiness score (0-100) that grades every Epic created in the last six months against the configured readiness dimensions "
														+ "(business clarity, scope definition, solution readiness, dependency readiness, delivery readiness and risk readiness). "
														+ "Each dimension is scored purely on evidence found in the Epic's own Jira fields; the Epic score is the weighted average of its dimensions."))
						.append("aggregationCriteria", "average")
						.append("isTrendCalculative", false)
						.append("isAdditionalFilterSupport", false)
						.append("combinedKpiSource", "Jira/Azure Boards/Rally");

		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.replaceOne(
						new Document(KPI_ID_FIELD, KPI_ID), kpiMaster, new ReplaceOptions().upsert(true));
	}

	/**
	 * Upserts the drill-down column layout. The per-dimension score columns are appended dynamically
	 * at runtime, so only the fixed columns are declared here.
	 */
	public void insertKpiColumnConfig(MongoTemplate mongoTemplate) {
		Document columnConfig =
				new Document()
						.append("basicProjectConfigId", null)
						.append(KPI_ID_FIELD, KPI_ID)
						.append(
								"kpiColumnDetails",
								Arrays.asList(
										column("Epic ID", 1),
										column("Epic Name", 2),
										column("Status", 3),
										column("Assignee", 4),
										column("Hygiene Score", 5),
										column("Overall Status", 6),
										column("Recommendations", 7)));

		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS_COLLECTION)
				.replaceOne(
						new Document(KPI_ID_FIELD, KPI_ID), columnConfig, new ReplaceOptions().upsert(true));
	}

	/** Upserts the project threshold and the readiness dimension field mappings. */
	public void insertFieldMappingStructure(MongoTemplate mongoTemplate) {
		Document thresholdStructure =
				new Document()
						.append(FIELD_NAME, THRESHOLD_FIELD)
						.append("fieldLabel", "Target KPI Value")
						.append("fieldType", "number")
						.append("section", "Project Level Threshold")
						.append("processorCommon", false)
						.append(
								"tooltip",
								new Document()
										.append(
												DEFINITION,
												"Target readiness score (0-100) the project should maintain for its Epics. If the threshold is empty, a common target KPI line will be shown."))
						.append("fieldDisplayOrder", 1)
						.append("sectionOrder", 6)
						.append("mandatory", false)
						.append("nodeSpecific", false);

		Document readinessDimensionsStructure =
				new Document()
						.append(FIELD_NAME, JIRA_FIELDS_SELECTION_FIELD)
						.append("fieldLabel", READINESS_DIMENSIONS_LABEL)
						.append("placeHolderText", READINESS_DIMENSIONS_LABEL)
						.append("fieldType", "chips")
						.append("section", "Custom Fields Mapping")
						.append("fieldCategory", "fields")
						.append("processorCommon", false)
						.append(
								"tooltip",
								new Document()
										.append(
												DEFINITION,
												"One entry per readiness dimension: pick the Jira field carrying the evidence and describe the 0-100 scoring criteria in the prompt. "
														+ "Prefix the prompt with [weight]: to make a dimension count more heavily than the others."))
						.append("filterGroup", List.of("CustomField"))
						.append("nodeSpecific", false)
						.append("fieldDisplayOrder", 4)
						.append("toggleLabelLeft", null)
						.append("toggleLabelRight", null)
						.append("sectionOrder", 3)
						.append("mandatory", false)
						.append("readOnly", null);

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.replaceOne(
						new Document(FIELD_NAME, THRESHOLD_FIELD),
						thresholdStructure,
						new ReplaceOptions().upsert(true));
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.replaceOne(
						new Document(FIELD_NAME, JIRA_FIELDS_SELECTION_FIELD),
						readinessDimensionsStructure,
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
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.deleteMany(
						new Document(
								FIELD_NAME,
								new Document("$in", Arrays.asList(THRESHOLD_FIELD, JIRA_FIELDS_SELECTION_FIELD))));
	}

	private static Document column(String columnName, int order) {
		return new Document()
				.append(COLUMN_NAME, columnName)
				.append(ORDER, order)
				.append(IS_SHOWN, true)
				.append(IS_DEFAULT, true);
	}
}
