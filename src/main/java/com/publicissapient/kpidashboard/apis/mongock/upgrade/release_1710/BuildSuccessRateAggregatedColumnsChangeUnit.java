package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.ReplaceOptions;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

/**
 * Replaces kpi212 (Build Success Rate) column config with weekly-aggregate columns, aligning it
 * with kpi221 (Change Failure Rate). Drops per-build columns (Start Date, Build Url, Build Status)
 * and adds Total Builds, Successful Builds, Failed Builds, Build Success Rate %.
 */
@ChangeUnit(
		id = "build_success_rate_aggregated_columns",
		order = "17191",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class BuildSuccessRateAggregatedColumnsChangeUnit {

	private static final String KPI_ID = "kpi212";
	private static final String KPI_ID_FIELD = "kpiId";
	private static final String KPI_COLUMN_CONFIGS = "kpi_column_configs";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		Document columnConfig =
				new Document()
						.append("basicProjectConfigId", null)
						.append(KPI_ID_FIELD, KPI_ID)
						.append(
								"kpiColumnDetails",
								Arrays.asList(
										new Document()
												.append("columnName", "Days/Weeks")
												.append("order", 1)
												.append("isShown", true)
												.append("isDefault", true),
										new Document()
												.append("columnName", "Project")
												.append("order", 2)
												.append("isShown", true)
												.append("isDefault", true),
										new Document()
												.append("columnName", "Job / Pipeline / Workflow")
												.append("order", 3)
												.append("isShown", true)
												.append("isDefault", true),
										new Document()
												.append("columnName", "Branch")
												.append("order", 4)
												.append("isShown", true)
												.append("isDefault", true),
										new Document()
												.append("columnName", "Total Builds")
												.append("order", 5)
												.append("isShown", true)
												.append("isDefault", true),
										new Document()
												.append("columnName", "Successful Builds")
												.append("order", 6)
												.append("isShown", true)
												.append("isDefault", true),
										new Document()
												.append("columnName", "Failed Builds")
												.append("order", 7)
												.append("isShown", true)
												.append("isDefault", true),
										new Document()
												.append("columnName", "Build Success Rate %")
												.append("order", 8)
												.append("isShown", true)
												.append("isDefault", true)));

		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.replaceOne(
						new Document(KPI_ID_FIELD, KPI_ID).append("basicProjectConfigId", null),
						columnConfig,
						new ReplaceOptions().upsert(true));
	}

	@RollbackExecution
	public void rollback() {
		Document columnConfig =
				new Document()
						.append("basicProjectConfigId", null)
						.append(KPI_ID_FIELD, KPI_ID)
						.append(
								"kpiColumnDetails",
								Arrays.asList(
										new Document()
												.append("columnName", "Days/Weeks")
												.append("order", 1)
												.append("isShown", true)
												.append("isDefault", true),
										new Document()
												.append("columnName", "Project")
												.append("order", 2)
												.append("isShown", true)
												.append("isDefault", true),
										new Document()
												.append("columnName", "Job / Pipeline / Workflow")
												.append("order", 3)
												.append("isShown", true)
												.append("isDefault", true),
										new Document()
												.append("columnName", "Branch")
												.append("order", 4)
												.append("isShown", true)
												.append("isDefault", true),
										new Document()
												.append("columnName", "Start Date")
												.append("order", 5)
												.append("isShown", true)
												.append("isDefault", true),
										new Document()
												.append("columnName", "Build Url")
												.append("order", 6)
												.append("isShown", true)
												.append("isDefault", true),
										new Document()
												.append("columnName", "Build Status")
												.append("order", 7)
												.append("isShown", true)
												.append("isDefault", true)));

		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.replaceOne(
						new Document(KPI_ID_FIELD, KPI_ID).append("basicProjectConfigId", null),
						columnConfig,
						new ReplaceOptions().upsert(true));
	}
}
