package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

/**
 * Adds support for {@code productionJobNameKPI214} to the kpi214 Lead Time For Change KPI:
 *
 * <ul>
 *   <li>Inserts a {@code field_mapping_structure} entry so the new field appears in the project
 *       settings UI.
 *   <li>Replaces the default {@code kpi_column_configs} entry for kpi214 to include the two new
 *       Excel columns "Job Name / Pipeline Name" (order 4) and "Environment" (order 5) after
 *       "Repo".
 * </ul>
 *
 * <p>When {@code productionJobNameKPI214} is configured, only deployments whose job name matches
 * are used to compute lead time, preventing non-production deployments from under-reporting the
 * metric. When left blank, all deployments are considered (backward-compatible default).
 */
@ChangeUnit(
		id = "lead_time_for_change_slingshot_production_job",
		order = "17148",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class LeadTimeForChangeSlingshotProductionJobChangeUnit {

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
	private static final String PRODUCTION_JOB_FIELD = "productionJobNameKPI214";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		insertFieldMappingStructure();
		replaceDefaultColumnConfig();
		updateKpiMasterDefaultOrder();
	}

	private void insertFieldMappingStructure() {
		Document doc =
				new Document()
						.append(FIELD_NAME, PRODUCTION_JOB_FIELD)
						.append("fieldLabel", "Production Deployment Job Name")
						.append("fieldType", "text")
						.append("section", "Custom Fields Mapping")
						.append("processorCommon", false)
						.append(
								"tooltip",
								new Document()
										.append(
												"definition",
												"Job name of the production deployment pipeline. "
														+ "Only deployments with this job name are used to compute lead time. "
														+ "When left blank, all deployments are considered. <br> e.g. my-service-prod <hr>"))
						.append("fieldDisplayOrder", 2)
						.append("sectionOrder", 5)
						.append("mandatory", false)
						.append("nodeSpecific", false);

		mongoTemplate.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION).insertOne(doc);
	}

	private void replaceDefaultColumnConfig() {
		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS_COLLECTION)
				.deleteOne(new Document(KPI_ID_FIELD, KPI_ID).append("basicProjectConfigId", null));

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
												.append(COLUMN_NAME, "Job Name / Pipeline Name")
												.append(ORDER, 4)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Environment")
												.append(ORDER, 5)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Branch")
												.append(ORDER, 6)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Author")
												.append(ORDER, 7)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Merge Request Url")
												.append(ORDER, 8)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "First Commit Date (UTC)")
												.append(ORDER, 9)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Deployment Date (UTC)")
												.append(ORDER, 10)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Lead Time (Hrs)")
												.append(ORDER, 11)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true)));

		mongoTemplate.getCollection(KPI_COLUMN_CONFIGS_COLLECTION).insertOne(columnConfig);
	}

	private void updateKpiMasterDefaultOrder() {
		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.updateOne(
						new Document(KPI_ID_FIELD, KPI_ID),
						new Document("$set", new Document("defaultOrder", 7)));
	}

	@RollbackExecution
	public void rollback() {
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.deleteOne(new Document(FIELD_NAME, PRODUCTION_JOB_FIELD));
		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.updateOne(
						new Document(KPI_ID_FIELD, KPI_ID),
						new Document("$set", new Document("defaultOrder", 6)));
		// Column config rollback: re-run
		// LeadTimeForChangeSlingshotColumnRefinementChangeUnit
		// restores the previous 9-column layout.
	}
}
