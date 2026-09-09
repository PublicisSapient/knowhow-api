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
 * Seeds the Refinement-to-Defect Linkage KPI (kpi226) — Slingshot / Quality.
 *
 * <p>Registers the kpi_master document, the excel column configuration and the five field mapping
 * structure entries that drive the project level configuration screen.
 *
 * <p>Note: kpi225 belongs to Mid-Sprint Re-Refinement Rate, seeded by {@code
 * BacklogAgingSlingshotChangeUnit}.
 */
@ChangeUnit(
		id = "refinement_to_defect_linkage_kpi_insert",
		order = "17204",
		author = "knowhow",
		systemVersion = "17.1.0")
public class RefinementToDefectLinkageChangeUnit {

	private static final String KPI_ID = "kpi226";
	private static final String KPI_NAME = "Refinement-to-Defect Linkage";
	private static final String KPI_ID_FIELD = "kpiId";
	private static final String KPI_NAME_FIELD = "kpiName";
	private static final String KPI_MASTER_COLLECTION = "kpi_master";
	private static final String KPI_COLUMN_CONFIGS_COLLECTION = "kpi_column_configs";
	private static final String FIELD_MAPPING_STRUCTURE_COLLECTION = "field_mapping_structure";
	private static final String COLUMN_NAME = "columnName";
	private static final String ROOT_CAUSE_CATEGORY_COLUMN = "Root Cause Category";
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
	private static final String CHIPS = "chips";
	private static final String OPTIONS = "options";
	private static final String NESTED_FIELDS = "nestedFields";
	private static final String FILTER_GROUP = "filterGroup";
	private static final String LABEL = "label";
	private static final String VALUE = "value";
	private static final String LABELS_OPTION = "Labels";
	private static final String DEFECT_MAPPING_SECTION = "Defects Mapping";
	private static final String IN = "$in";

	private static final String ISSUE_TYPE_FIELD = "jiraIssueTypeKPI226";
	private static final String PRODUCTION_DEFECT_IDENTIFICATION_FIELD =
			"jiraProductionDefectIdentificationKPI226";
	private static final String PRODUCTION_DEFECT_VALUE_FIELD = "jiraProductionDefectValueKPI226";
	private static final String REFINEMENT_ROOT_CAUSE_FIELD = "jiraRefinementRootCauseValuesKPI226";
	private static final String THRESHOLD_FIELD = "thresholdValueKPI226";

	private static final String KPI_DEFINITION =
			"Percentage of production defects whose root cause traces back to a missed, ambiguous or wrong acceptance "
					+ "criterion at Definition of Ready: refinement_root_cause_count / total_defects. "
					+ "This is the only metric in the blueprint that requires manual tagging — at incident review every "
					+ "production defect is classified with one root cause out of refinement, design, code, infra or external, "
					+ "and KnowHow reads that classification from the Jira RCA field configured under 'Root Cause'. "
					+ "It is included because no automated method captures the signal, and the value of closing the loop "
					+ "between refinement and production is large. Every data point also reports Root Cause Coverage %, "
					+ "so a low linkage score sitting on low coverage reads as 'we do not know' rather than 'we are fine'.";

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
						.append(KPI_NAME_FIELD, KPI_NAME)
						.append("isDeleted", "False")
						.append("defaultOrder", 8)
						.append("kpiCategory", "Slingshot")
						.append("kpiSubCategory", "Intake")
						.append("kpiUnit", "%")
						.append("chartType", "line")
						.append("xAxisLabel", "Weeks")
						.append("yAxisLabel", "Percentage")
						.append("showTrend", true)
						.append("isPositiveTrend", false)
						.append("calculateMaturity", true)
						.append("maturityRange", Arrays.asList("40-", "30-40", "20-30", "10-20", "-10"))
						.append("hideOverallFilter", true)
						.append("kpiSource", "Jira")
						.append("maxValue", 100)
						.append("thresholdValue", 15.0)
						.append("kanban", false)
						.append("groupId", 74)
						.append("kpiInfo", new Document().append(DEFINITION, KPI_DEFINITION))
						// no KPI filter: the split is exposed as a per-period root cause drill-down
						.append("kpiFilter", null)
						.append("aggregationCriteria", "average")
						.append("isTrendCalculative", false)
						.append("isAdditionalFilterSupport", false)
						.append("combinedKpiSource", "Jira/Azure/Rally")
						.append("upperThresholdBG", "red")
						.append("lowerThresholdBG", "white")
						.append("kpiWidth", 50)
						.append("kpiSubCategoryOrder", 8);

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
										column("Priority", 6),
										column("Status", 7),
										column("Created Date", 8),
										column("Root Cause", 9),
										column(ROOT_CAUSE_CATEGORY_COLUMN, 10)));

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
						.append(FIELD_NAME, ISSUE_TYPE_FIELD)
						.append(FIELD_LABEL, "Issue type to identify Defects")
						.append(FIELD_TYPE, CHIPS)
						.append(FIELD_CATEGORY, "Issue_Type")
						.append(FIELD_DISPLAY_ORDER, 1)
						.append(SECTION_ORDER, 1)
						.append(SECTION, "Issue Types Mapping")
						.append(MANDATORY, false)
						.append(
								TOOLTIP,
								new Document()
										.append(
												DEFINITION,
												"All issue types that represent a defect (e.g., Bug, Defect). When left blank, 'Bug' and 'Defect' are used.")));

		upsertFieldMapping(
				mongoTemplate,
				new Document()
						.append(FIELD_NAME, PRODUCTION_DEFECT_IDENTIFICATION_FIELD)
						.append(FIELD_LABEL, "Production defect identification")
						.append(FIELD_TYPE, "radiobutton")
						.append(FIELD_DISPLAY_ORDER, 2)
						.append(SECTION_ORDER, 5)
						.append(SECTION, DEFECT_MAPPING_SECTION)
						.append(MANDATORY, false)
						.append(
								OPTIONS,
								Arrays.asList(
										option(LABELS_OPTION, LABELS_OPTION),
										option("Production Defect Mapping", "ProductionDefectMapping")))
						.append(
								NESTED_FIELDS,
								List.of(
										new Document(FIELD_NAME, PRODUCTION_DEFECT_VALUE_FIELD)
												.append(FIELD_LABEL, "Production Defect Values")
												.append(FIELD_TYPE, CHIPS)
												.append(FILTER_GROUP, List.of(LABELS_OPTION))
												.append(
														TOOLTIP,
														new Document(
																DEFINITION,
																"Jira labels that mark a defect as found in production. Example: PROD_DEFECT, Prod-Escape <hr>"))))
						.append(
								TOOLTIP,
								new Document()
										.append(
												DEFINITION,
												"How a defect is recognised as having escaped to production:<br>1. Labels : match the label value(s) configured below."
														+ "<br>2. Production Defect Mapping : reuse the project's existing global Production Defect configuration (Production Defect Identifier / Value), "
														+ "which the Jira processor already evaluates.<hr>")));

		upsertFieldMapping(
				mongoTemplate,
				new Document()
						.append(FIELD_NAME, REFINEMENT_ROOT_CAUSE_FIELD)
						.append(FIELD_LABEL, "Root cause value(s) that mean 'Refinement'")
						.append(FIELD_TYPE, CHIPS)
						.append(FIELD_DISPLAY_ORDER, 4)
						.append(SECTION_ORDER, 5)
						.append(SECTION, DEFECT_MAPPING_SECTION)
						.append(MANDATORY, false)
						.append(
								TOOLTIP,
								new Document()
										.append(
												DEFINITION,
												"Values of the Jira Root Cause (RCA) field that mean the defect was caused by a missed, ambiguous or wrong acceptance criterion at DOR "
														+ "(e.g., Refinement, Missing Acceptance Criteria, Requirement Gap). These form the numerator of the KPI. "
														+ "The RCA field itself is configured once under 'Root Cause' in the Defects Mapping section. "
														+ "When left blank, 'Refinement' is used.")));

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
				Arrays.asList(
						ISSUE_TYPE_FIELD,
						PRODUCTION_DEFECT_IDENTIFICATION_FIELD,
						PRODUCTION_DEFECT_VALUE_FIELD,
						REFINEMENT_ROOT_CAUSE_FIELD,
						THRESHOLD_FIELD);
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.deleteMany(new Document(FIELD_NAME, new Document(IN, fieldNames)));
	}
}
