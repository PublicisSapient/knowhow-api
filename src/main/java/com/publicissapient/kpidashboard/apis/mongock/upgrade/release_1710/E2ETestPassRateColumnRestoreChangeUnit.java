package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.ReplaceOptions;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "e2e_test_pass_rate_column_restore",
		order = "17169",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class E2ETestPassRateColumnRestoreChangeUnit {

	private static final String KPI_COLUMN_CONFIGS_COLLECTION = "kpi_column_configs";
	private static final String KPI_ID = "kpi218";
	private static final String KEY_BASIC_PROJECT_CONFIG_ID = "basicProjectConfigId";
	private static final String COLUMN_NAME = "columnName";
	private static final String ORDER = "order";
	private static final String IS_SHOWN = "isShown";
	private static final String IS_DEFAULT = "isDefault";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		Document filter = new Document(KEY_BASIC_PROJECT_CONFIG_ID, null).append("kpiId", KPI_ID);

		Document columnConfig =
				new Document(KEY_BASIC_PROJECT_CONFIG_ID, null)
						.append("kpiId", KPI_ID)
						.append(
								"kpiColumnDetails",
								Arrays.asList(
										col("Days/Weeks", 1, true),
										col("Workflow", 2, true),
										col("Suite Name", 3, true),
										col("Builds in Week", 4, true),
										col("Avg Tests/Build", 5, true),
										col("Avg Passed", 6, true),
										col("Avg Failed", 7, true),
										col("Pass Rate %", 8, true)));

		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS_COLLECTION)
				.replaceOne(filter, columnConfig, new ReplaceOptions().upsert(true));
	}

	@RollbackExecution
	public void rollback() {
		Document filter = new Document(KEY_BASIC_PROJECT_CONFIG_ID, null).append("kpiId", KPI_ID);

		Document columnConfig =
				new Document(KEY_BASIC_PROJECT_CONFIG_ID, null)
						.append("kpiId", KPI_ID)
						.append(
								"kpiColumnDetails",
								Arrays.asList(
										col("Days/Weeks", 1, true),
										col("Suite Name", 2, true),
										col("Total Tests", 3, true),
										col("Passed", 4, true),
										col("Failed", 5, true),
										col("Pass Rate %", 6, true)));

		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS_COLLECTION)
				.replaceOne(filter, columnConfig, new ReplaceOptions().upsert(true));
	}

	private Document col(String name, int order, boolean shown) {
		return new Document()
				.append(COLUMN_NAME, name)
				.append(ORDER, order)
				.append(IS_SHOWN, shown)
				.append(IS_DEFAULT, shown);
	}
}
