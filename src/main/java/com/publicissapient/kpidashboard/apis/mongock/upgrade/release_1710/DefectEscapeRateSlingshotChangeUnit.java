package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(
		id = "defect_escape_rate_slingshot_kpi_insert",
		order = "17154",
		author = "knowhow",
		systemVersion = "17.1.0")
public class DefectEscapeRateSlingshotChangeUnit {

	private static final String KPI_ID = "kpi216";
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
						.append("kpiName", "Defect Escape Rate")
						.append("isDeleted", "False")
						.append("defaultOrder", 2)
						.append("kpiCategory", "Slingshot")
						.append("kpiSubCategory", "Quality")
						.append("kpiUnit", "%")
						.append("chartType", "line")
						.append("xAxisLabel", "Weeks")
						.append("yAxisLabel", "Percentage")
						.append("showTrend", true)
						.append("isPositiveTrend", false)
						.append("calculateMaturity", true)
						.append("maturityRange", List.of("30-", "25-30", "20-25", "15-20", "-15"))
						.append("hideOverallFilter", false)
						.append("kpiSource", "Jira")
						.append("maxValue", 100)
						.append("thresholdValue", 10.0)
						.append("kanban", false)
						.append("groupId", 68)
						.append(
								"kpiInfo",
								new Document()
										.append(
												DEFINITION,
												"% of defects discovered in production vs. discovered preproduction."))
						.append("kpiFilter", "dropDown")
						.append("aggregationCriteria", "average")
						.append("isTrendCalculative", false)
						.append("isAdditionalFilterSupport", true)
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
												.append(COLUMN_NAME, "Sprint Name")
												.append(ORDER, 2)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Defect ID")
												.append(ORDER, 3)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Description")
												.append(ORDER, 4)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Escaped Defect")
												.append(ORDER, 5)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Escaped defect identifier")
												.append(ORDER, 6)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Defect Priority")
												.append(ORDER, 7)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Defect Status")
												.append(ORDER, 8)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Story ID")
												.append(ORDER, 9)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Squad")
												.append(ORDER, 10)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, false),
										new Document()
												.append(COLUMN_NAME, "Time Spent (in hours)")
												.append(ORDER, 11)
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
						new Document(FIELD_NAME, "jiraIssueTypeKPI216"),
						new Document()
								.append(FIELD_NAME, "jiraIssueTypeKPI216")
								.append("fieldLabel", "Issue types with defect linkages")
								.append("fieldType", "chips")
								.append("fieldCategory", "Issue_Type")
								.append("section", "Issue Types Mapping")
								.append("fieldDisplayOrder", 1)
								.append("sectionOrder", 2)
								.append(
										"tooltip",
										new Document()
												.append(DEFINITION, "All issue types that can have valid defect linkages"))
								.append("mandatory", true),
						new com.mongodb.client.model.ReplaceOptions().upsert(true));

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.replaceOne(
						new Document(FIELD_NAME, "defectPriorityKPI216"),
						new Document()
								.append(FIELD_NAME, "defectPriorityKPI216")
								.append("fieldLabel", "Priority to be excluded")
								.append("placeHolderText", "Select values to be excluded")
								.append("fieldType", "multiselect")
								.append("section", "Defects Mapping")
								.append("fieldDisplayOrder", 1)
								.append("sectionOrder", 3)
								.append(
										"tooltip",
										new Document()
												.append(
														DEFINITION,
														"Priority values of defects that can be excluded from Defect Escape Rate calculation"))
								.append(
										"options",
										Arrays.asList(
												new Document().append("label", "p1").append("value", "p1"),
												new Document().append("label", "p2").append("value", "p2"),
												new Document().append("label", "p3").append("value", "p3"),
												new Document().append("label", "p4").append("value", "p4"),
												new Document().append("label", "p5").append("value", "p5"))),
						new com.mongodb.client.model.ReplaceOptions().upsert(true));

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.replaceOne(
						new Document(FIELD_NAME, "includeRCAForKPI216"),
						new Document()
								.append(FIELD_NAME, "includeRCAForKPI216")
								.append("fieldLabel", "Root cause values to be included")
								.append("placeHolderText", " Root cause values to be included")
								.append("fieldType", "chips")
								.append("section", "Defects Mapping")
								.append("fieldDisplayOrder", 2)
								.append("sectionOrder", 3)
								.append(
										"tooltip",
										new Document()
												.append(
														DEFINITION,
														"Root cause reasons for defects to be included In Defect Escape Rate calculation")),
						new com.mongodb.client.model.ReplaceOptions().upsert(true));

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.replaceOne(
						new Document(FIELD_NAME, "resolutionTypeForRejectionKPI216"),
						new Document()
								.append(FIELD_NAME, "resolutionTypeForRejectionKPI216")
								.append("fieldLabel", "Resolution type to be excluded")
								.append("fieldType", "chips")
								.append("section", "WorkFlow Status Mapping")
								.append("fieldDisplayOrder", 6)
								.append("sectionOrder", 4)
								.append(
										"tooltip",
										new Document()
												.append(
														DEFINITION,
														"Resolution types for defects that can be excluded from Defect Escape Rate calculation.")),
						new com.mongodb.client.model.ReplaceOptions().upsert(true));

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.replaceOne(
						new Document(FIELD_NAME, "jiraDefectRejectionStatusKPI216"),
						new Document()
								.append(FIELD_NAME, "jiraDefectRejectionStatusKPI216")
								.append("fieldLabel", "Status to identify rejected defects")
								.append("fieldType", "text")
								.append("fieldCategory", "workflow")
								.append("section", "WorkFlow Status Mapping")
								.append("fieldDisplayOrder", 2)
								.append("sectionOrder", 4)
								.append(
										"tooltip",
										new Document()
												.append(DEFINITION, "All workflow statuses used to reject defects")),
						new com.mongodb.client.model.ReplaceOptions().upsert(true));

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.replaceOne(
						new Document(FIELD_NAME, "thresholdValueKPI216"),
						new Document()
								.append(FIELD_NAME, "thresholdValueKPI216")
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
												"jiraIssueTypeKPI216",
												"defectPriorityKPI216",
												"includeRCAForKPI216",
												"resolutionTypeForRejectionKPI216",
												"jiraDefectRejectionStatusKPI216",
												"thresholdValueKPI216"))));
	}
}
