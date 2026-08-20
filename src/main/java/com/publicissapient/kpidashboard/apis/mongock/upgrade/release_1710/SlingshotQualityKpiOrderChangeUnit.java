/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Reorders the Slingshot Quality sub-category KPIs to the agreed display sequence and aligns
 * groupIds so they are sequential in display order (67-73, no gaps).
 *
 * <p>kpi221 (Change Failure Rate) is omitted — it is inserted with the correct values (1, 67) by
 * the preceding ChangeFailureRateSlingshotKpiChangeUnit (order 17179) and needs no update here.
 *
 * <ol>
 *   <li>Change Failure Rate (kpi221) — defaultOrder 1, groupId 67 (set by migration 17179)
 *   <li>Mean Time to Recover (kpi217) — defaultOrder 2, groupId 68
 *   <li>PR Revert Rate (kpi215) — defaultOrder 3, groupId 69
 *   <li>Defect Escape Rate (kpi216) — defaultOrder 4, groupId 70
 *   <li>E2E Test Pass Rate (kpi218) — defaultOrder 5, groupId 71
 *   <li>Flaky Test Rate (kpi220) — defaultOrder 6, groupId 72 (both unchanged)
 *   <li>Mean Time to Test Feedback (kpi219) — defaultOrder 7, groupId 73
 * </ol>
 */
@ChangeUnit(
		id = "slingshot_quality_kpi_order_update",
		order = "17180",
		author = "knowhow",
		systemVersion = "17.1.0")
public class SlingshotQualityKpiOrderChangeUnit {

	private static final String KPI_MASTER = "kpi_master";

	// kpi220 (Flaky Test Rate) omitted — already correct (6, 72).
	// kpi221 (Change Failure Rate) omitted — inserted with correct values (1, 67)
	// by migration 17179.
	private static final String[][] KPI_IDS = {
		{"kpi217"}, // Mean Time to Recover: defaultOrder 2, groupId 68
		{"kpi215"}, // PR Revert Rate: defaultOrder 3, groupId 69
		{"kpi216"}, // Defect Escape Rate: defaultOrder 4, groupId 70
		{"kpi218"}, // E2E Test Pass Rate: defaultOrder 5, groupId 71
		{"kpi219"}, // Mean Time to Test Feedback: defaultOrder 7, groupId 73
	};

	// { defaultOrder, groupId } aligned with KPI_IDS rows
	private static final int[][] NEW_VALUES = {
		{2, 68}, {3, 69}, {4, 70}, {5, 71}, {7, 73},
	};

	// Previous values for rollback
	private static final int[][] OLD_VALUES = {
		{3, 69}, // kpi217 was: defaultOrder 3, groupId 69
		{1, 67}, // kpi215 was: defaultOrder 1, groupId 67
		{2, 68}, // kpi216 was: defaultOrder 2, groupId 68
		{4, 70}, // kpi218 was: defaultOrder 4, groupId 70
		{5, 70}, // kpi219 was: defaultOrder 5, groupId 70
	};

	@Execution
	public void execution(MongoTemplate mongoTemplate) {
		apply(mongoTemplate, NEW_VALUES);
	}

	@RollbackExecution
	public void rollback(MongoTemplate mongoTemplate) {
		apply(mongoTemplate, OLD_VALUES);
	}

	private void apply(MongoTemplate mongoTemplate, int[][] values) {
		for (int i = 0; i < KPI_IDS.length; i++) {
			String kpiId = KPI_IDS[i][0];
			int defaultOrder = values[i][0];
			int groupId = values[i][1];
			mongoTemplate
					.getCollection(KPI_MASTER)
					.updateOne(
							new Document("kpiId", kpiId),
							new Document(
									"$set", new Document("defaultOrder", defaultOrder).append("groupId", groupId)));
		}
	}
}
