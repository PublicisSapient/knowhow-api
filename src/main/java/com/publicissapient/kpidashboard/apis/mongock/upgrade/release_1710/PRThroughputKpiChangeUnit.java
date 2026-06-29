package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(
		id = "pr_throughput_kpi_insert",
		order = "17123",
		author = "kunkambl",
		systemVersion = "17.1.0")
public class PRThroughputKpiChangeUnit {

	private static final String KPI_ID = "kpi208";
	private static final String KPI_ID_FIELD = "kpiId";
	private static final String KPI_MASTER_COLLECTION = "kpi_master";
	private static final String KPI_COLUMN_CONFIGS_COLLECTION = "kpi_column_configs";
	private static final String FIELD_MAPPING_STRUCTURE_COLLECTION = "field_mapping_structure";
	private static final String COLUMN_NAME = "columnName";
	private static final String ORDER = "order";
	private static final String IS_SHOWN = "isShown";
	private static final String IS_DEFAULT = "isDefault";

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
						.append("kpiName", "PR Throughput")
						.append("isDeleted", "False")
						.append("defaultOrder", 1)
						.append("kpiCategory", "Slingshot")
						.append("kpiSubCategory", "Speed")
						.append("kpiUnit", "PRs")
						.append("chartType", "line")
						.append("xAxisLabel", "Weeks")
						.append("yAxisLabel", "Count")
						.append("showTrend", true)
						.append("isPositiveTrend", true)
						.append("calculateMaturity", false)
						.append("hideOverallFilter", true)
						.append("kpiSource", "BitBucket")
						.append("maxValue", 15)
						.append("thresholdValue", 55.0)
						.append("kanban", false)
						.append("groupId", 6)
						.append(
								"kpiInfo",
								new Document()
										.append(
												"definition",
												"Merged pull requests per engineer per week, at team / org level only. "))
						.append("kpiFilter", "dropDown")
						.append("aggregationCriteria", "average")
						.append("isTrendCalculative", false)
						.append("isAdditionalFilterSupport", false)
						.append("isRepoToolKpi", true)
						.append("combinedKpiSource", "Bitbucket/AzureRepository/GitHub/GitLab")
						.append("forecastModel", "thetaMethod");

		mongoTemplate.getCollection(KPI_MASTER_COLLECTION).insertOne(kpiMaster);

		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.updateOne(
						new Document(KPI_ID, "kpi206"),
						new Document("$set", new Document("kpiFilter", "dropDown")));
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
												.append(COLUMN_NAME, "Project")
												.append(ORDER, 1)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Repo")
												.append(ORDER, 2)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Branch")
												.append(ORDER, 3)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Days/Weeks")
												.append(ORDER, 4)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Developer")
												.append(ORDER, 5)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "No of Merge")
												.append(ORDER, 7)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true)));

		mongoTemplate.getCollection(KPI_COLUMN_CONFIGS_COLLECTION).insertOne(columnConfig);
	}

	public void insertFieldMappingStructure(MongoTemplate mongoTemplate) {
		Document fieldMapping =
				new Document()
						.append("fieldName", "thresholdValueKPI208")
						.append("fieldLabel", "Target KPI Value")
						.append("fieldType", "number")
						.append("section", "Project Level Threshold")
						.append("processorCommon", false)
						.append(
								"tooltip",
								new Document()
										.append(
												"definition",
												"Target KPI value denotes the bare minimum a project should maintain for a KPI. User should just input the number and the unit like percentage, hours will automatically be considered. If the threshold is empty, then a common target KPI line will be shown"))
						.append("fieldDisplayOrder", 1)
						.append("sectionOrder", 6)
						.append("mandatory", false)
						.append("nodeSpecific", false);

		mongoTemplate.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION).insertOne(fieldMapping);
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
				.deleteOne(new Document("fieldName", "thresholdValueKPI208"));
	}
}
