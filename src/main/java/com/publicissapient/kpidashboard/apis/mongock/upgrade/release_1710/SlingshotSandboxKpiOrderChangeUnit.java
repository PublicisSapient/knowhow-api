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
 * Moves Sandbox sub-category KPIs to defaultOrder values above all other Slingshot sub-categories
 * (Flow max=6, Speed max=7, Quality max=7) so the Sandbox tab always appears last.
 *
 * <p>The frontend builds sub-category tabs by iterating all Slingshot KPIs sorted by defaultOrder
 * using a JavaScript Set — the first time a sub-category is encountered determines its tab
 * position. Sandbox KPIs previously had defaultOrder=1/2, which caused them to race against Quality
 * KPIs (also starting at 1) with MongoDB document insertion order as the tiebreaker. Since kpi221
 * (Quality, order=1) is a newer document than kpi311 (Sandbox, order=1), Sandbox was seen first and
 * rendered before Quality. Setting Sandbox orders to 8/9 eliminates the race.
 *
 * <p>defaultOrder is set to match the KPI ID number (311, 312). This mirrors the existing
 * convention that Sandbox KPI IDs use a separate 3xx sequence to signal their experimental nature,
 * makes the intent self-documenting, and eliminates any realistic chance of collision with
 * production sub-category KPIs.
 *
 * <ol>
 *   <li>Story Hygiene (kpi311) — defaultOrder 1 → 311
 *   <li>Epic Hygiene (kpi312) — defaultOrder 2 → 312
 * </ol>
 */
@ChangeUnit(
		id = "slingshot_sandbox_kpi_order_update",
		order = "17181",
		author = "knowhow",
		systemVersion = "17.1.0")
public class SlingshotSandboxKpiOrderChangeUnit {

	private static final String KPI_MASTER = "kpi_master";

	private static final String[] KPI_IDS = {"kpi311", "kpi312"};
	private static final int[] NEW_ORDERS = {311, 312};
	private static final int[] OLD_ORDERS = {1, 2};

	@Execution
	public void execution(MongoTemplate mongoTemplate) {
		apply(mongoTemplate, NEW_ORDERS);
	}

	@RollbackExecution
	public void rollback(MongoTemplate mongoTemplate) {
		apply(mongoTemplate, OLD_ORDERS);
	}

	private void apply(MongoTemplate mongoTemplate, int[] orders) {
		for (int i = 0; i < KPI_IDS.length; i++) {
			mongoTemplate
					.getCollection(KPI_MASTER)
					.updateOne(
							new Document("kpiId", KPI_IDS[i]),
							new Document("$set", new Document("defaultOrder", orders[i])));
		}
	}
}
