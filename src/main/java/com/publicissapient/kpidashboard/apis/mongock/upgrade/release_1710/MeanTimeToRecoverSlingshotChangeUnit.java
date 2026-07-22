package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(
		id = "mean_time_to_recover_slingshot_kpi_insert",
		order = "17162",
		author = "knowhow",
		systemVersion = "17.1.0")
public class MeanTimeToRecoverSlingshotChangeUnit {

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
						.append("kpiName", "Mean Time to Recover")
						.append("isDeleted", "False")
						.append("defaultOrder", 3)
						.append("kpiCategory", "Slingshot")
						.append("kpiSubCategory", "Quality")
						.append("kpiUnit", "Hours")
						.append("chartType", "line")
						.append("xAxisLabel", "Weeks")
						.append("yAxisLabel", "Hours")
						.append("showTrend", true)
						.append("isPositiveTrend", false)
						.append("calculateMaturity", true)
						.append("maturityRange", Arrays.asList("336-", "168-336", "24-168", "1-24", "-1"))
						.append("hideOverallFilter", false)
						.append("kpiSource", "Jira")
						.append("thresholdValue", 24.0)
						.append("kanban", false)
						.append("groupId", 69)
						.append(
								"kpiInfo",
								new Document()
										.append(
												DEFINITION,
												"Median time from production incident detected to service restored."))
						.append("kpiFilter", "dropDown")
						.append("aggregationCriteria", "average")
						.append("isAdditionalFilterSupport", false)
						.append("combinedKpiSource", "Jira/Azure/Rally")
						.append("upperThresholdBG", "red")
						.append("lowerThresholdBG", "white")
						.append("forecastModel", "thetaMethod");

		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.replaceOne(
						new Document(KPI_ID_FIELD, KPI_ID),
						kpiMaster,
						new com.mongodb.client.model.ReplaceOptions().upsert(true));
	}

	public void insertKpiColumnConfig(MongoTemplate mongoTemplate) {
		Document columnConfig =
				new Document()
						.append("basicProjectConfigId", null)
						.append(KPI_ID_FIELD, KPI_ID)
						.append(
								"kpiColumnDetails",
								Arrays.asList(
										new Document()
												.append(COLUMN_NAME, "Days/Weeks")
												.append(ORDER, 1)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Issue ID")
												.append(ORDER, 2)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Issue Type")
												.append(ORDER, 3)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Issue Description")
												.append(ORDER, 4)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Created Time")
												.append(ORDER, 5)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Closed Time")
												.append(ORDER, 6)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Time to Recover (In Hours)")
												.append(ORDER, 7)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true)));

		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS_COLLECTION)
				.replaceOne(
						new Document(KPI_ID_FIELD, KPI_ID),
						columnConfig,
						new com.mongodb.client.model.ReplaceOptions().upsert(true));
	}

	public void insertFieldMappingStructure(MongoTemplate mongoTemplate) {
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.insertOne(
						new Document()
								.append(FIELD_NAME, "jiraStoryIdentificationKPI217")
								.append("fieldLabel", "Issue type to identify Production incidents")
								.append("fieldType", "chips")
								.append("fieldCategory", "Issue_Type")
								.append("section", "Issue Types Mapping")
								.append(
										"tooltip",
										new Document()
												.append(
														DEFINITION,
														"All issue types that are used as/equivalent to Production incidents.")));

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.insertOne(
						new Document()
								.append(FIELD_NAME, "jiraProductionIncidentIdentificationKPI217")
								.append("fieldLabel", "Production incidents identification")
								.append("fieldType", "radiobutton")
								.append("section", "Defects Mapping")
								.append(
										"tooltip",
										new Document()
												.append(
														DEFINITION,
														"This field is used to identify if a production incident is raised by third party or client:<br>1. CustomField : If a separate custom field is used<br>2. Labels : If a label is used to identify. Example: PROD_DEFECT (This has to be one value).<hr>"))
								.append(
										"options",
										Arrays.asList(
												new Document()
														.append("label", "CustomField")
														.append("value", "CustomField"),
												new Document().append("label", "Labels").append("value", "Labels"))));

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.insertOne(
						new Document()
								.append(FIELD_NAME, "jiraDodKPI217")
								.append("fieldLabel", "Status to identify completed issues")
								.append("fieldType", "chips")
								.append("fieldCategory", "workflow")
								.append("fieldDisplayOrder", 8)
								.append("sectionOrder", 4)
								.append("section", "WorkFlow Status Mapping")
								.append(
										"tooltip",
										new Document()
												.append(
														DEFINITION,
														"All workflow statuses used to identify completed issues based on Definition of Done (DoD).")));

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.insertOne(
						new Document()
								.append(FIELD_NAME, "thresholdValueKPI217")
								.append("fieldLabel", "Target KPI Value")
								.append("fieldType", "number")
								.append("section", "Project Level Threshold")
								.append("processorCommon", false)
								.append(
										"tooltip",
										new Document()
												.append(
														DEFINITION,
														"Target KPI value denotes the bare minimum a project should maintain for a KPI. User should just input the number and the unit like percentage, hours will automatically be considered. If the threshold is empty, then a common target KPI line will be shown"))
								.append("fieldDisplayOrder", 1)
								.append("sectionOrder", 6)
								.append("mandatory", false)
								.append("nodeSpecific", false));
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
								new Document(
										"$in",
										Arrays.asList(
												"jiraStoryIdentificationKPI217",
												"jiraProductionIncidentIdentificationKPI217",
												"jiraDodKPI217",
												"thresholdValueKPI217"))));
	}
}
