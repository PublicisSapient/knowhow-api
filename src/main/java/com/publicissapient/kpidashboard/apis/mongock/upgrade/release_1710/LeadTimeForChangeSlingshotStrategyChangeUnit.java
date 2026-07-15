package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

/**
 * Adds the {@code calculationStrategyKPI214} field to the Lead Time For Change (kpi214) KPI:
 *
 * <ul>
 *   <li>Inserts a {@code field_mapping_structure} entry (radiobutton: DEPLOYMENT | COMMIT) so the
 *       new setting appears in project settings UI.
 *   <li>Updates the default {@code kpi_column_configs} entry for kpi214, renaming the "Deployment
 *       Date (UTC)" column to "Deployment / Merged Date (UTC)" so it remains meaningful in both
 *       strategy modes.
 * </ul>
 */
@ChangeUnit(
		id = "lead_time_for_change_slingshot_strategy",
		order = "17155",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class LeadTimeForChangeSlingshotStrategyChangeUnit {

	private static final String KPI_ID = "kpi214";
	private static final String KPI_ID_FIELD = "kpiId";
	private static final String KPI_COLUMN_CONFIGS_COLLECTION = "kpi_column_configs";
	private static final String FIELD_MAPPING_STRUCTURE_COLLECTION = "field_mapping_structure";
	private static final String FIELD_NAME = "fieldName";
	private static final String COLUMN_NAME = "columnName";
	private static final String STRATEGY_FIELD = "calculationStrategyKPI214";
	private static final String OLD_COLUMN = "Deployment Date (UTC)";
	private static final String NEW_COLUMN = "Deployment / Merged Date (UTC)";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		insertFieldMappingStructure();
		renameDeploymentDateColumn();
	}

	private void insertFieldMappingStructure() {
		Document doc =
				new Document()
						.append(FIELD_NAME, STRATEGY_FIELD)
						.append("fieldLabel", "Calculation Strategy")
						.append("fieldType", "radiobutton")
						.append("section", "Custom Fields Mapping")
						.append("processorCommon", false)
						.append(
								"tooltip",
								new Document()
										.append(
												"definition",
												"Choose how Lead Time is calculated. "
														+ "<br><b>Deployment</b>: uses CI/CD deployment records (requires Jenkins/ArgoCD integration). "
														+ "<br><b>Commit</b>: uses pull-request merge date on the production branch as a proxy for deployment (no CI/CD tool required). <hr>"))
						.append(
								"options",
								java.util.Arrays.asList(
										new Document().append("label", "Deployment").append("value", "DEPLOYMENT"),
										new Document().append("label", "Commit").append("value", "COMMIT")))
						.append("fieldDisplayOrder", 3)
						.append("sectionOrder", 5)
						.append("mandatory", false)
						.append("nodeSpecific", false);

		mongoTemplate.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION).insertOne(doc);
	}

	private void renameDeploymentDateColumn() {
		// Rename in the default (null basicProjectConfigId) column config.
		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS_COLLECTION)
				.updateOne(
						new Document(KPI_ID_FIELD, KPI_ID)
								.append("basicProjectConfigId", null)
								.append("kpiColumnDetails.columnName", OLD_COLUMN),
						new Document("$set", new Document("kpiColumnDetails.$.columnName", NEW_COLUMN)));

		// Also rename in any project-specific overrides so existing projects are
		// consistent.
		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS_COLLECTION)
				.updateMany(
						new Document(KPI_ID_FIELD, KPI_ID).append("kpiColumnDetails.columnName", OLD_COLUMN),
						new Document("$set", new Document("kpiColumnDetails.$.columnName", NEW_COLUMN)));
	}

	@RollbackExecution
	public void rollback() {
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.deleteOne(new Document(FIELD_NAME, STRATEGY_FIELD));

		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS_COLLECTION)
				.updateMany(
						new Document(KPI_ID_FIELD, KPI_ID).append("kpiColumnDetails.columnName", NEW_COLUMN),
						new Document("$set", new Document("kpiColumnDetails.$.columnName", OLD_COLUMN)));
	}
}
