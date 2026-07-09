package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1710;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

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
	public void execution() {
		// Restore original groupIds (undo the upgrade change unit)
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

	private void updateGroupId(String kpiId, int groupId) {
		mongoTemplate
				.getCollection(KPI_MASTER)
				.updateOne(
						new Document(KPI_ID, kpiId), new Document("$set", new Document(GROUP_ID, groupId)));
	}

	@RollbackExecution
	public void rollback() {
		// Reapply new groupIds if the rollback itself needs to be undone
		updateGroupId("kpi202", 50);
		updateGroupId("kpi203", 51);
		updateGroupId("kpi204", 50);
		updateGroupId("kpi205", 52);
		updateGroupId("kpi206", 53);
		updateGroupId("kpi207", 54);
		updateGroupId("kpi208", 60);
		updateGroupId("kpi209", 61);
		updateGroupId("kpi210", 62);
		updateGroupId("kpi211", 63);
		updateGroupId("kpi212", 64);
		updateGroupId("kpi213", 65);
		updateGroupId("kpi214", 66);
	}
}
