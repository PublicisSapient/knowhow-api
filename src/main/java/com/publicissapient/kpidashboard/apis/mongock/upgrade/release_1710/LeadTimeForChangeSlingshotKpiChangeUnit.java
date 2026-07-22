package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Mongock upgrade for the SCM + Deployment Lead Time For Change KPI (kpi214).
 *
 * <p>Inserts:
 *
 * <ul>
 *   <li>{@code kpi_master} document for kpi214.
 *   <li>{@code kpi_column_configs} default column configuration.
 *   <li>{@code field_mapping_structure} entries for {@code productionBranchKPI214} and {@code
 *       thresholdValueKPI214}.
 * </ul>
 */
@ChangeUnit(
		id = "lead_time_for_change_slingshot_kpi_insert",
		order = "17140",
		author = "kunkambl",
		systemVersion = "17.1.0")
public class LeadTimeForChangeSlingshotKpiChangeUnit {

	private static final String KPI_ID = "kpi214";
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

	private static final String PRODUCTION_BRANCH_FIELD = "productionBranchKPI214";
	private static final String THRESHOLD_FIELD = "thresholdValueKPI214";

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
						.append("kpiName", "Lead Time For Change")
						.append("isDeleted", "False")
						.append("defaultOrder", 6)
						.append("kpiCategory", "Slingshot")
						.append("kpiSubCategory", "Speed")
						.append("kpiUnit", "Hours")
						.append("chartType", "line")
						.append("xAxisLabel", "Weeks")
						.append("yAxisLabel", "Hours")
						.append("showTrend", true)
						.append("isPositiveTrend", false)
						.append("calculateMaturity", false)
						.append("hideOverallFilter", false)
						.append("kpiSource", "Jenkins")
						.append("maxValue", 15)
						.append("thresholdValue", 24.0)
						.append("kanban", false)
						.append("groupId", 9)
						.append(
								"kpiInfo",
								new Document()
										.append(
												DEFINITION,
												"Time from first commit on a PR branch to the change running in production."
														+ " Lead Time = ts(deploy to prod) - ts(first commit on PR branch)."))
						// Single dropdown filter: repository name.
						.append("kpiFilter", "dropDown")
						.append("aggregationCriteria", "average")
						.append("isTrendCalculative", false)
						.append("isAdditionalFilterSupport", false)
						.append(
								"combinedKpiSource",
								"Bitbucket/AzureRepository/GitHub/GitLab & Jenkins/Bamboo/GitHubAction/AzurePipeline/Teamcity/ArgoCD")
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
												.append(COLUMN_NAME, "Repo")
												.append(ORDER, 3)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Branch")
												.append(ORDER, 4)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Author")
												.append(ORDER, 5)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Merge Request Url")
												.append(ORDER, 6)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "First Commit Date (UTC)")
												.append(ORDER, 7)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Deployment Date (UTC)")
												.append(ORDER, 8)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Lead Time (Hrs)")
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
		Document productionBranchStructure =
				new Document()
						.append(FIELD_NAME, PRODUCTION_BRANCH_FIELD)
						.append("fieldLabel", "Production Branch Name")
						.append("fieldType", "text")
						.append("section", "Custom Fields Mapping")
						.append("processorCommon", false)
						.append(
								"tooltip",
								new Document()
										.append(
												DEFINITION,
												"Production branch in which all the child branches are merged. "
														+ "Merge requests whose target branch matches this value are used to compute lead time. <br> e.g. master <hr>"))
						.append("fieldDisplayOrder", 1)
						.append("sectionOrder", 5)
						.append("mandatory", false)
						.append("nodeSpecific", false);

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
												"Target KPI value denotes the bare minimum a project should maintain for a KPI. "
														+ "Value is interpreted in hours. If the threshold is empty, then a common target KPI line will be shown."))
						.append("fieldDisplayOrder", 1)
						.append("sectionOrder", 6)
						.append("mandatory", false)
						.append("nodeSpecific", false);

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.insertMany(Arrays.asList(productionBranchStructure, thresholdStructure));
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
				.deleteOne(new Document(FIELD_NAME, PRODUCTION_BRANCH_FIELD));
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.deleteOne(new Document(FIELD_NAME, THRESHOLD_FIELD));
	}
}
