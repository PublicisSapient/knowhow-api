package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

/**
 * Refines kpi213 (Deployment Frequency Slingshot) column config for existing installations:
 *
 * <ul>
 *   <li>Renames "Project Name" → "Project" (consistent with kpi214)
 *   <li>Adds "Repository Name" column
 *   <li>Renames "Job Name / Pipeline Name" → "Job/Pipeline Name" (consistent with kpi214)
 *   <li>Renames "Environment" → "Deployed Environment" (consistent with kpi214)
 *   <li>Adds "Deployment Status" column
 *   <li>Renames "Start Date Time (UTC)" → "Deployment Date (UTC)" (consistent with kpi214)
 *   <li>Adds "Deployment End Date (UTC)" column
 * </ul>
 */
@ChangeUnit(
		id = "deployment_frequency_slingshot_column_refine",
		order = "17151",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class DeploymentFrequencySlingshotColumnRefineChangeUnit {

	private static final String KPI_ID = "kpi213";
	private static final String KPI_ID_FIELD = "kpiId";
	private static final String KPI_COLUMN_CONFIGS_COLLECTION = "kpi_column_configs";
	private static final String COLUMN_NAME = "columnName";
	private static final String ORDER = "order";
	private static final String IS_SHOWN = "isShown";
	private static final String IS_DEFAULT = "isDefault";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
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
												.append(COLUMN_NAME, "Repository Name")
												.append(ORDER, 3)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Job/Pipeline Name")
												.append(ORDER, 4)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Deployed Environment")
												.append(ORDER, 5)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Deployment Status")
												.append(ORDER, 6)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Deployment Date (UTC)")
												.append(ORDER, 7)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Deployment End Date (UTC)")
												.append(ORDER, 8)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true)));

		mongoTemplate.getCollection(KPI_COLUMN_CONFIGS_COLLECTION).insertOne(columnConfig);
	}

	@RollbackExecution
	public void rollback() {
		// No-op: the previous state is restored by re-running
		// DeploymentFrequencySlingshotChangeUnit
	}
}
