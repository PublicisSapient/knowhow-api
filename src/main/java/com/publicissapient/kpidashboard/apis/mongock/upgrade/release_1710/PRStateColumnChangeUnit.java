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
		id = "pr_state_column_add",
		order = "17138",
		author = "kunkambl",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class PRStateColumnChangeUnit {

	private static final String KPI_ID = "kpiId";
	private static final String KPI_157 = "kpi157";
	private static final String KPI_209 = "kpi209";
	private static final String KPI_210 = "kpi210";
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
		upsertConfig(
				KPI_157,
				List.of(
						columnDetail("Days/Weeks", 1),
						columnDetail("Project", 2),
						columnDetail("Repo", 3),
						columnDetail("Branch", 4),
						columnDetail("Developer", 5),
						optionalColumnDetail("Email/Username", 6),
						columnDetail("Merge Request Url", 7),
						columnDetail("State", 8),
						columnDetail("No of Merge", 9)));

		upsertConfig(
				KPI_209,
				List.of(
						columnDetail("Days/Weeks", 1),
						columnDetail("Project", 2),
						columnDetail("Repo", 3),
						columnDetail("Branch", 4),
						columnDetail("Developer", 5),
						optionalColumnDetail("Email/Username", 6),
						columnDetail("Merge Request Url", 7),
						columnDetail("State", 8),
						columnDetail("PR Raised Time", 9),
						columnDetail("PR Merged Time", 10),
						columnDetail("Time Spent (in hours)", 11)));

		upsertConfig(
				KPI_210,
				List.of(
						columnDetail("Days/Weeks", 1),
						columnDetail("Project", 2),
						columnDetail("Repo", 3),
						columnDetail("Branch", 4),
						columnDetail("Developer", 5),
						optionalColumnDetail("Email/Username", 6),
						columnDetail("Merge Request Url", 7),
						columnDetail("State", 8),
						columnDetail("PR Raised Time", 9),
						columnDetail("PR Review Time", 10),
						columnDetail("Time to First Review (In Hours)", 11)));
	}

	private void upsertConfig(String kpiId, List<Document> columnDetails) {
		Document filter = new Document(KEY_BASIC_PROJECT_CONFIG_ID, null).append(KPI_ID, kpiId);
		Document config =
				new Document(KEY_BASIC_PROJECT_CONFIG_ID, null)
						.append(KPI_ID, kpiId)
						.append(KEY_KPI_COLUMN_DETAILS, columnDetails);
		mongoTemplate
				.getCollection(KPI_EXCEL_COLUMN_CONFIG)
				.replaceOne(filter, config, new ReplaceOptions().upsert(true));
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
		// Restore previous configs without State column
		upsertConfig(
				KPI_209,
				List.of(
						columnDetail("Days/Weeks", 1),
						columnDetail("Project", 2),
						columnDetail("Repo", 3),
						columnDetail("Branch", 4),
						columnDetail("Developer", 5),
						optionalColumnDetail("Email/Username", 6),
						columnDetail("Merge Request Url", 7),
						columnDetail("PR Raised Time", 8),
						columnDetail("PR Merged Time", 9),
						columnDetail("Time Spent (in hours)", 10)));

		upsertConfig(
				KPI_210,
				List.of(
						columnDetail("Days/Weeks", 1),
						columnDetail("Project", 2),
						columnDetail("Repo", 3),
						columnDetail("Branch", 4),
						columnDetail("Developer", 5),
						optionalColumnDetail("Email/Username", 6),
						columnDetail("Merge Request Url", 7),
						columnDetail("PR Raised Time", 8),
						columnDetail("PR Review Time", 9),
						columnDetail("Time to First Review (In Hours)", 10)));

		mongoTemplate
				.getCollection(KPI_EXCEL_COLUMN_CONFIG)
				.deleteOne(new Document(KEY_BASIC_PROJECT_CONFIG_ID, null).append(KPI_ID, KPI_157));
	}
}
