package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(
		id = "refinement_cycle_time_slingshot_kpi_insert",
		order = "17185",
		author = "knowhow",
		systemVersion = "17.1.0")
public class RefinementCycleTimeSlingshotChangeUnit {

	private static final String KPI_ID = "kpi222";
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
						.append("kpiName", "Refinement Cycle Time")
						.append("isDeleted", "False")
						.append("defaultOrder", 1)
						.append("kpiCategory", "Slingshot")
						.append("kpiSubCategory", "Intake")
						.append("kpiUnit", "Days")
						.append("chartType", "line")
						.append("xAxisLabel", "Weeks")
						.append("yAxisLabel", "Days")
						.append("showTrend", true)
						.append("isPositiveTrend", false)
						.append("calculateMaturity", true)
						.append("maturityRange", Arrays.asList("14-", "10-14", "7-10", "5-7", "-5"))
						.append("hideOverallFilter", false)
						.append("kpiSource", "Jira")
						.append("thresholdValue", 5.0)
						.append("kanban", false)
						.append("groupId", 313)
						.append(
								"kpiInfo",
								new Document()
										.append(
												DEFINITION,
												"Time from issue creation to issue meeting Definition of Ready (status='Ready' or equivalent). Reported as weekly median over a rolling 12-week window. Below 5 days is healthy; above 14 days suggests refinement is being deferred or PMs are overloaded."))
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
												.append(COLUMN_NAME, "Start Time")
												.append(ORDER, 5)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Ready Time")
												.append(ORDER, 6)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Refinement Cycle Time (Days)")
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
				.replaceOne(
						new Document(FIELD_NAME, "jiraStoryIdentificationKPI222"),
						new Document()
								.append(FIELD_NAME, "jiraStoryIdentificationKPI222")
								.append("fieldLabel", "Issue type to include in Refinement Cycle Time")
								.append("fieldType", "chips")
								.append("fieldCategory", "Issue_Type")
								.append("section", "Issue Types Mapping")
								.append(
										"tooltip",
										new Document()
												.append(
														DEFINITION,
														"All issue types to measure refinement for (e.g., Story, Bug, Task). Leave blank to include all types.")),
						new com.mongodb.client.model.ReplaceOptions().upsert(true));

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.replaceOne(
						new Document(FIELD_NAME, "jiraStatusToStartRefinementKPI222"),
						new Document()
								.append(FIELD_NAME, "jiraStatusToStartRefinementKPI222")
								.append("fieldLabel", "Status(es) marking start of refinement (optional)")
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
														"Optional. Workflow statuses that mark when active refinement begins (e.g., In Refinement, Being Groomed). When configured, cycle time is measured from the first transition to one of these statuses. When left blank, issue creation date is used as the start.")),
						new com.mongodb.client.model.ReplaceOptions().upsert(true));

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.replaceOne(
						new Document(FIELD_NAME, "jiraStatusForRefinementKPI222"),
						new Document()
								.append(FIELD_NAME, "jiraStatusForRefinementKPI222")
								.append("fieldLabel", "Status(es) marking Definition of Ready met")
								.append("fieldType", "chips")
								.append("fieldCategory", "workflow")
								.append("fieldDisplayOrder", 9)
								.append("sectionOrder", 4)
								.append("section", "WorkFlow Status Mapping")
								.append(
										"tooltip",
										new Document()
												.append(
														DEFINITION,
														"All workflow statuses that represent an issue meeting the Definition of Ready (e.g., Ready, Ready for Dev, Refined). The first transition to any of these statuses is used to compute cycle time.")),
						new com.mongodb.client.model.ReplaceOptions().upsert(true));

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.replaceOne(
						new Document(FIELD_NAME, "thresholdValueKPI222"),
						new Document()
								.append(FIELD_NAME, "thresholdValueKPI222")
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
								.append("nodeSpecific", false),
						new com.mongodb.client.model.ReplaceOptions().upsert(true));
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
												"jiraStoryIdentificationKPI222",
												"jiraStatusToStartRefinementKPI222",
												"jiraStatusForRefinementKPI222",
												"thresholdValueKPI222"))));
	}
}
