package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.ReplaceOptions;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

/**
 * Sets the definitive column config for kpi208 (PR Throughput) via replaceOne with upsert.
 *
 * <p>Corrects two cumulative issues:
 *
 * <ul>
 *   <li>PRThroughputKpiChangeUnit (17123) placed "Days/Weeks" at order 4; the checklist requires it
 *       at order 1.
 *   <li>SlingshotColumnStandardizationChangeUnit (17189) renames "Repo" → "Repository" via
 *       updateMany, but a prod-dump restore that reverts the DB after the earlier reorder
 *       migrations ran will still leave the wrong order. This replaceOne is authoritative and
 *       resolves both.
 * </ul>
 *
 * <p>The rollback is a no-op: reverting to "Repo at order 4" would be worse than leaving this in
 * place, and no clean prior state exists across all environments.
 */
@ChangeUnit(
		id = "pr_throughput_column_final_state",
		order = "17190",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class PRThroughputColumnFinalStateChangeUnit {

	private static final String KPI_ID = "kpi208";
	private static final String KPI_ID_FIELD = "kpiId";
	private static final String KPI_COLUMN_CONFIGS = "kpi_column_configs";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		Document filter = new Document("basicProjectConfigId", null).append(KPI_ID_FIELD, KPI_ID);

		Document config =
				new Document("basicProjectConfigId", null)
						.append(KPI_ID_FIELD, KPI_ID)
						.append(
								"kpiColumnDetails",
								List.of(
										col("Days/Weeks", 1, true, true),
										col("Project", 2, true, true),
										col("Repository", 3, true, true),
										col("Branch", 4, true, true),
										col("Developer", 5, true, true),
										col("Email/Username", 6, false, false),
										col("Merge Request Url", 7, true, true),
										col("No of Merge", 8, true, true)));

		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.replaceOne(filter, config, new ReplaceOptions().upsert(true));
	}

	private Document col(String name, int order, boolean isShown, boolean isDefault) {
		return new Document()
				.append("columnName", name)
				.append("order", order)
				.append("isShown", isShown)
				.append("isDefault", isDefault);
	}

	@RollbackExecution
	public void rollback() {
		// Intentional no-op: prior state is inconsistent across environments.
	}
}
