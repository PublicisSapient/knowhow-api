package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(
		id = "project_hygiene_slingshot_kpi_insert",
		order = "17143",
		author = "knowhow",
		systemVersion = "17.1.0")
public class ProjectHygieneSlingshotChangeUnit {

	private static final String KPI_ID = "kpi217";
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
	private static final String THRESHOLD_FIELD = "thresholdValueKPI217";
	private static final String JIRA_FIELDS_SELECTION_FIELD = "jiraFieldsSelectionKPI217";

	// ...existing code...
	@Execution
	public void execution(MongoTemplate mongoTemplate) {
		insertKpiMaster(mongoTemplate);
		insertKpiColumnConfig(mongoTemplate);
		insertFieldMappingStructure(mongoTemplate);
	}

	// ...existing code...
	public void insertKpiMaster(MongoTemplate mongoTemplate) {
		Document kpiMaster =
				new Document()
						.append(KPI_ID_FIELD, KPI_ID)
						.append("kpiName", "Project Hygiene")
						.append("isDeleted", "False")
						.append("defaultOrder", 7)
						.append("kpiCategory", "Slingshot")
						.append("kpiSubCategory", "Sandbox")
						.append("kpiUnit", "%")
						.append("chartType", "line")
						.append("xAxisLabel", "Sprints")
						.append("yAxisLabel", "Percentage")
						.append("showTrend", true)
						.append("isPositiveTrend", true)
						.append("calculateMaturity", false)
						.append("hideOverallFilter", true)
						.append("kpiSource", "Jira")
						.append("maxValue", 100)
						.append("thresholdValue", 80.0)
						.append("kanban", false)
						.append("groupId", 48)
						.append(
								"kpiInfo",
								new Document()
										.append(
												DEFINITION,
												"AI-driven hygiene score (0-100) that evaluates every Jira issue in a sprint against a Definition-of-Ready style checklist (story points, summary, priority, assignee, labels, sprint, epic link, resolution, worklog, staleness, etc.) and reports the percentage of applicable rules passed."))
						.append("kpiFilter", "dropDown")
						.append("aggregationCriteria", "average")
						.append("isTrendCalculative", false)
						.append("isAdditionalFilterSupport", false)
						.append("combinedKpiSource", "Jira/Azure Boards/Rally")
						.append("forecastModel", "thetaMethod");
		mongoTemplate.getCollection(KPI_MASTER_COLLECTION).insertOne(kpiMaster);
	}

	// ...existing code...
	public void insertKpiColumnConfig(MongoTemplate mongoTemplate) {
		Document columnConfig =
				new Document()
						.append("basicProjectConfigId", null)
						.append(KPI_ID_FIELD, KPI_ID)
						.append(
								"kpiColumnDetails",
								Arrays.asList(
										new Document()
												.append(COLUMN_NAME, "Sprint Name")
												.append(ORDER, 1)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Issue Key")
												.append(ORDER, 2)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Issue Type")
												.append(ORDER, 3)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Assignee")
												.append(ORDER, 4)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Hygiene Score")
												.append(ORDER, 5)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Overall Status")
												.append(ORDER, 6)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Recommendations")
												.append(ORDER, 8)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, false)));

		mongoTemplate.getCollection(KPI_COLUMN_CONFIGS_COLLECTION).insertOne(columnConfig);
	}

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
												"Target hygiene score (0-100) the project should maintain. If the threshold is empty, a common target KPI line will be shown."))
						.append("fieldDisplayOrder", 1)
						.append("sectionOrder", 6)
						.append("mandatory", false)
						.append("nodeSpecific", false);

		Document jiraFieldsSelectionStructure =
				new Document()
						.append(FIELD_NAME, JIRA_FIELDS_SELECTION_FIELD)
						.append("fieldLabel", "fields to write prompts")
						.append("placeHolderText", "fields to write prompts")
						.append("fieldType", "chips")
						.append("section", "Custom Fields Mapping")
						.append("fieldCategory", "fields")
						.append("processorCommon", false)
						.append("tooltip", new Document().append(DEFINITION, "fields to write prompts"))
						.append("filterGroup", List.of("CustomField"))
						.append("nodeSpecific", false)
						.append("fieldDisplayOrder", 3)
						.append("toggleLabelLeft", null)
						.append("toggleLabelRight", null)
						.append("sectionOrder", 3)
						.append("mandatory", false)
						.append("readOnly", null);

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.insertMany(Arrays.asList(thresholdStructure, jiraFieldsSelectionStructure));
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
}
