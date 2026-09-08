package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(
		id = "mid_sprint_re_refinement_rate_kpi_insert",
		order = "17204",
		author = "knowhow",
		systemVersion = "17.1.0")
public class MidSprintReRefinementRateSlingshotChangeUnit {

	private static final String KPI_ID = "kpi225";
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
						.append("kpiName", "Mid-Sprint Re-Refinement Rate")
						.append("isDeleted", "False")
						.append("defaultOrder", 3)
						.append("kpiSubCategoryOrder", 1)
						.append("kpiCategory", "Slingshot")
						.append("kpiSubCategory", "Intake")
						.append("kpiUnit", "%")
						.append("chartType", "line")
						.append("xAxisLabel", "Sprints")
						.append("yAxisLabel", "%")
						.append("showTrend", true)
						.append("isPositiveTrend", false)
						.append("calculateMaturity", true)
						.append("maturityRange", Arrays.asList("20-", "15-20", "10-15", "5-10", "-5"))
						.append("hideOverallFilter", false)
						.append("kpiSource", "Jira")
						.append("thresholdValue", 5.0)
						.append("kanban", false)
						.append("groupId", 316)
						.append(
								"kpiInfo",
								new Document()
										.append(
												DEFINITION,
												"% of stories that move backward to a refinement state (after dev has started) relative to the count of stories that entered development in the same period. A rate above 5% indicates systematic DOR gaps; above 15% signals that refinement is not completing before sprint start."))
						.append("kpiFilter", (Object) null)
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
												.append(COLUMN_NAME, "Project")
												.append(ORDER, 2)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Issue ID")
												.append(ORDER, 3)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Issue Type")
												.append(ORDER, 4)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Issue Description")
												.append(ORDER, 5)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Dev Start Date")
												.append(ORDER, 6)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "First Return Date")
												.append(ORDER, 7)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Return-to Status")
												.append(ORDER, 8)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Return Count")
												.append(ORDER, 9)
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
						new Document(FIELD_NAME, "jiraStoryIdentificationKPI225"),
						new Document()
								.append(FIELD_NAME, "jiraStoryIdentificationKPI225")
								.append("fieldLabel", "Issue types to include")
								.append("fieldType", "chips")
								.append("fieldCategory", "Issue_Type")
								.append("section", "Issue Types Mapping")
								.append(
										"tooltip",
										new Document()
												.append(
														DEFINITION,
														"All issue types to measure re-refinement for (e.g., Story, Bug, Task). Leave blank to include all types.")),
						new com.mongodb.client.model.ReplaceOptions().upsert(true));

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.replaceOne(
						new Document(FIELD_NAME, "jiraStatusStartDevKPI225"),
						new Document()
								.append(FIELD_NAME, "jiraStatusStartDevKPI225")
								.append("fieldLabel", "Status(es) marking dev start")
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
														"Workflow statuses that indicate development has begun (e.g., In Progress, In Development). The first transition to any of these statuses is treated as the dev start point.")),
						new com.mongodb.client.model.ReplaceOptions().upsert(true));

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.replaceOne(
						new Document(FIELD_NAME, "jiraStatusReturnToRefinementKPI225"),
						new Document()
								.append(FIELD_NAME, "jiraStatusReturnToRefinementKPI225")
								.append("fieldLabel", "Status(es) counting as returned to refinement")
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
														"Workflow statuses that represent a story moving back to refinement after dev has started (e.g., In Refinement, Needs Grooming, Backlog). Any transition to one of these statuses after dev start counts as a re-refinement event.")),
						new com.mongodb.client.model.ReplaceOptions().upsert(true));

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.replaceOne(
						new Document(FIELD_NAME, "thresholdValueKPI225"),
						new Document()
								.append(FIELD_NAME, "thresholdValueKPI225")
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
												"jiraStoryIdentificationKPI225",
												"jiraStatusStartDevKPI225",
												"jiraStatusReturnToRefinementKPI225",
												"thresholdValueKPI225"))));
	}
}
