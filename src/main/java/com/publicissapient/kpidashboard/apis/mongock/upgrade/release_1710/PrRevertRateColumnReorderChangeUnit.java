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
		id = "pr_revert_rate_column_reorder",
		order = "17156",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class PrRevertRateColumnReorderChangeUnit {

	private static final String KPI_ID = "kpiId";
	private static final String KPI_215 = "kpi215";
	private static final String KEY_BASIC_PROJECT_CONFIG_ID = "basicProjectConfigId";
	private static final String KEY_KPI_COLUMN_DETAILS = "kpiColumnDetails";
	private static final String KPI_COLUMN_CONFIGS_COLLECTION = "kpi_column_configs";
	private static final String COLUMN_NAME = "columnName";
	private static final String ORDER = "order";
	private static final String IS_SHOWN = "isShown";
	private static final String IS_DEFAULT = "isDefault";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		Document filter = new Document(KEY_BASIC_PROJECT_CONFIG_ID, null).append(KPI_ID, KPI_215);

		Document kpiColumnConfig =
				new Document(KEY_BASIC_PROJECT_CONFIG_ID, null)
						.append(KPI_ID, KPI_215)
						.append(
								KEY_KPI_COLUMN_DETAILS,
								List.of(
										columnDetail("Days/Weeks", 1),
										columnDetail("Project", 2),
										columnDetail("Repo", 3),
										columnDetail("Branch", 4),
										columnDetail("Developer", 5),
										optionalColumnDetail("Email/Username", 6),
										columnDetail("No of Merge", 7),
										columnDetail("Revert PR", 8),
										columnDetail("Revert Rate", 9)));

		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS_COLLECTION)
				.replaceOne(filter, kpiColumnConfig, new ReplaceOptions().upsert(true));
	}

	private Document columnDetail(String name, int order) {
		return new Document()
				.append(COLUMN_NAME, name)
				.append(ORDER, order)
				.append(IS_SHOWN, true)
				.append(IS_DEFAULT, true);
	}

	private Document optionalColumnDetail(String name, int order) {
		return new Document()
				.append(COLUMN_NAME, name)
				.append(ORDER, order)
				.append(IS_SHOWN, false)
				.append(IS_DEFAULT, false);
	}

	@RollbackExecution
	public void rollback() {
		// no-op: the document is owned by PrRevertRateKpiChangeUnit
	}
}
