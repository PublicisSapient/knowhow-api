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
		id = "pr_size_distribution_size_category_column",
		order = "17140",
		author = "aksshriv1",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class PrSizeDistributionSizeCategoryColumnChangeUnit {

	private static final String KPI_ID = "kpiId";
	private static final String KPI_211 = "kpi211";
	private static final String KEY_BASIC_PROJECT_CONFIG_ID = "basicProjectConfigId";
	private static final String KEY_KPI_COLUMN_DETAILS = "kpiColumnDetails";
	private static final String KPI_EXCEL_COLUMN_CONFIG = "kpi_column_configs";
	private static final String COLUMN_NAME = "columnName";
	private static final String ORDER = "order";
	private static final String IS_SHOWN = "isShown";
	private static final String IS_DEFAULT = "isDefault";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		Document filter = new Document(KEY_BASIC_PROJECT_CONFIG_ID, null).append(KPI_ID, KPI_211);

		Document kpiColumnConfig =
				new Document(KEY_BASIC_PROJECT_CONFIG_ID, null)
						.append(KPI_ID, KPI_211)
						.append(
								KEY_KPI_COLUMN_DETAILS,
								List.of(
										columnDetail("Days/Weeks", 1),
										columnDetail("Project", 2),
										columnDetail("Repo", 3),
										columnDetail("Branch", 4),
										columnDetail("Developer", 5),
										optionalColumnDetail("Email/Username", 6),
										columnDetail("Merge Request Url", 7),
										columnDetail("Total Lines Changed", 8),
										columnDetail("Size Category", 9)));

		mongoTemplate
				.getCollection(KPI_EXCEL_COLUMN_CONFIG)
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
		Document filter = new Document(KEY_BASIC_PROJECT_CONFIG_ID, null).append(KPI_ID, KPI_211);

		Document kpiColumnConfig =
				new Document(KEY_BASIC_PROJECT_CONFIG_ID, null)
						.append(KPI_ID, KPI_211)
						.append(
								KEY_KPI_COLUMN_DETAILS,
								List.of(
										columnDetail("Days/Weeks", 1),
										columnDetail("Project", 2),
										columnDetail("Repo", 3),
										columnDetail("Branch", 4),
										columnDetail("Developer", 5),
										optionalColumnDetail("Email/Username", 6),
										columnDetail("Merge Request Url", 7),
										columnDetail("Total Lines Changed", 8)));

		mongoTemplate
				.getCollection(KPI_EXCEL_COLUMN_CONFIG)
				.replaceOne(filter, kpiColumnConfig, new ReplaceOptions().upsert(true));
	}
}
