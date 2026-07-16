package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

/**
 * Assigns a structured groupId block to all Slingshot KPIs:
 *
 * <pre>
 * 50–59  Slingshot / Flow subcategory
 *   50 – Flow Time (kpi202) + Flow Time Trend (kpi204)
 *   51 – Flow Efficiency (kpi203)
 *   52 – Flow Velocity (kpi205)
 *   53 – Flow Load (kpi206)
 *   54 – Flow Distribution (kpi207)
 *
 * 60–69  Slingshot / Speed subcategory
 *   60 – PR Throughput (kpi208)
 *   61 – PR Cycle Time (kpi209)
 *   62 – Time to First Review (kpi210)
 *   63 – PR Size Distribution (kpi211)
 *   64 – Build Success Rate (kpi212)
 *   65 – Deployment Frequency (kpi213)
 *   66 – Lead Time For Change (kpi214)
 * </pre>
 *
 * Add groupIds 50-54 and 60-66 to groupIdsToExcludeFromCache in application.properties.
 */
@ChangeUnit(
		id = "slingshot_kpi_group_id_assignment",
		order = "17149",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class SlingshotKpiGroupIdChangeUnit {

	private static final String KPI_MASTER = "kpi_master";
	private static final String KPI_ID = "kpiId";
	private static final String GROUP_ID = "groupId";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		// Flow subcategory
		updateGroupId("kpi202", 50); // Flow Time
		updateGroupId("kpi203", 51); // Flow Efficiency
		updateGroupId("kpi204", 50); // Flow Time Trend — shares group with Flow Time
		updateGroupId("kpi205", 52); // Flow Velocity
		updateGroupId("kpi206", 53); // Flow Load
		updateGroupId("kpi207", 54); // Flow Distribution

		// Speed subcategory
		updateGroupId("kpi208", 60); // PR Throughput
		updateGroupId("kpi209", 61); // PR Cycle Time
		updateGroupId("kpi210", 62); // Time to First Review
		updateGroupId("kpi211", 63); // PR Size Distribution
		updateGroupId("kpi212", 64); // Build Success Rate
		updateGroupId("kpi213", 65); // Deployment Frequency
		updateGroupId("kpi214", 66); // Lead Time For Change
	}

	private void updateGroupId(String kpiId, int newGroupId) {
		mongoTemplate
				.getCollection(KPI_MASTER)
				.updateOne(
						new Document(KPI_ID, kpiId), new Document("$set", new Document(GROUP_ID, newGroupId)));
	}

	@RollbackExecution
	public void rollback() {
		// Restore original groupIds that existed before this change unit
		updateGroupId("kpi202", 45);
		updateGroupId("kpi203", 45);
		updateGroupId("kpi204", 34);
		updateGroupId("kpi205", 45);
		updateGroupId("kpi206", 46);
		updateGroupId("kpi207", 46);
		updateGroupId("kpi208", 6);
		updateGroupId("kpi209", 7);
		updateGroupId("kpi210", 6);
		updateGroupId("kpi211", 6);
		updateGroupId("kpi212", 8);
		updateGroupId("kpi213", 47);
		updateGroupId("kpi214", 9);
	}
}
