package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.ReplaceOptions;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "quality_kpi_column_consistency_fix",
		order = "17166",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class QualityKpiColumnConsistencyFixChangeUnit {

	private static final String KPI_COLUMN_CONFIGS_COLLECTION = "kpi_column_configs";
	private static final String KEY_BASIC_PROJECT_CONFIG_ID = "basicProjectConfigId";
	private static final String KPI_ID = "kpiId";
	private static final String KPI_215 = "kpi215";
	private static final String KEY_KPI_COLUMN_DETAILS = "kpiColumnDetails";
	private static final String COLUMN_NAME = "columnName";
	private static final String ORDER = "order";
	private static final String IS_SHOWN = "isShown";
	private static final String IS_DEFAULT = "isDefault";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		fixPrRevertRateColumns();
	}

	private void fixPrRevertRateColumns() {
		Document filter = new Document(KEY_BASIC_PROJECT_CONFIG_ID, null).append(KPI_ID, KPI_215);

		Document columnConfig =
				new Document(KEY_BASIC_PROJECT_CONFIG_ID, null)
						.append(KPI_ID, KPI_215)
						.append(
								KEY_KPI_COLUMN_DETAILS,
								List.of(
										col("Days/Weeks", 1, true),
										col("Repo", 2, true),
										col("Branch", 3, true),
										col("Developer", 4, true),
										col("Email/Username", 5, false),
										col("No. of PR", 6, true),
										col("No. of Revert PR", 7, true),
										col("Revert PR URL", 8, true),
										col("Revert Rate", 9, true)));

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

	@RollbackExecution
	public void rollback() {
		rollbackPrRevertRate();
	}

	private void rollbackPrRevertRate() {
		Document filter = new Document(KEY_BASIC_PROJECT_CONFIG_ID, null).append(KPI_ID, KPI_215);

		Document columnConfig =
				new Document(KEY_BASIC_PROJECT_CONFIG_ID, null)
						.append(KPI_ID, KPI_215)
						.append(
								KEY_KPI_COLUMN_DETAILS,
								List.of(
										col("Days/Weeks", 1, true),
										col("Project", 2, true),
										col("Repo", 3, true),
										col("Branch", 4, true),
										col("Developer", 5, true),
										col("Email/Username", 6, false),
										col("No. of PR", 7, true),
										col("No. of Revert PR", 8, true),
										col("Revert PR URL", 9, true),
										col("Revert Rate", 10, true)));

		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS_COLLECTION)
				.replaceOne(filter, columnConfig, new ReplaceOptions().upsert(true));
	}
}
