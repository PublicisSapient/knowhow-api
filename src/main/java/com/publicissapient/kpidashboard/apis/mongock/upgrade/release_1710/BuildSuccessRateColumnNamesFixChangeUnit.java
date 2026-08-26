package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

/**
 * Aligns kpi212 (Build Success Rate) column names with the KPIExcelColumn enum and KPIExcelData
 * JsonProperty annotations:
 *
 * <ul>
 *   <li>Renames "Project" → "Project" (already correct in DB — no-op for Project column)
 *   <li>Renames "Build URL" → "Build Url" to match {@code @JsonProperty("Build Url")} in
 *       KPIExcelData, which is the actual header written to the Excel file. The previous
 *       BuildSuccessRateChangeUnit used "Build URL" (capital L), causing a mismatch between the UI
 *       column picker label and the Excel column header.
 * </ul>
 */
@ChangeUnit(
		id = "build_success_rate_column_names_fix",
		order = "17188",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class BuildSuccessRateColumnNamesFixChangeUnit {

	private static final String KPI_ID = "kpi212";
	private static final String KPI_ID_FIELD = "kpiId";
	private static final String KPI_COLUMN_CONFIGS = "kpi_column_configs";
	private static final String OLD_BUILD_URL = "Build URL";
	private static final String NEW_BUILD_URL = "Build Url";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		// Rename in the default (null basicProjectConfigId) column config.
		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.updateOne(
						new Document(KPI_ID_FIELD, KPI_ID)
								.append("basicProjectConfigId", null)
								.append("kpiColumnDetails.columnName", OLD_BUILD_URL),
						new Document("$set", new Document("kpiColumnDetails.$.columnName", NEW_BUILD_URL)));

		// Also rename in any project-specific overrides.
		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.updateMany(
						new Document(KPI_ID_FIELD, KPI_ID).append("kpiColumnDetails.columnName", OLD_BUILD_URL),
						new Document("$set", new Document("kpiColumnDetails.$.columnName", NEW_BUILD_URL)));
	}

	@RollbackExecution
	public void rollback() {
		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.updateMany(
						new Document(KPI_ID_FIELD, KPI_ID).append("kpiColumnDetails.columnName", NEW_BUILD_URL),
						new Document("$set", new Document("kpiColumnDetails.$.columnName", OLD_BUILD_URL)));
	}
}
