/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;
import static com.mongodb.client.model.Updates.unset;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "slingshot_kpi_maturity_change_unit",
		order = "17152",
		author = "kunkambl",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class SlingshotKpiMaturityChangeUnit {

	private static final String KPI_MASTER = "kpi_master";
	private static final String KPI_ID = "kpiId";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		// kpi205 (Flow Velocity / Sprint Velocity) already has calculateMaturity:true
		// set
		// in SprintVelocitySlingshotChangeUnit — not repeated here.
		Map<String, List<String>> maturityRangeMap =
				Map.of(
						// Flow Efficiency — % active work; industry baseline 15-25%, >40% exceptional
						"kpi203", List.of("-15", "15-25", "25-35", "35-40", "40-"),
						// PR Throughput — total merged PRs per period; higher = better
						"kpi208", List.of("-5", "5-15", "15-25", "25-40", "40-"),
						// PR Cycle Time — hours from PR open to merge; lower = better (median <24h
						// healthy)
						"kpi209", List.of("96-", "72-96", "48-72", "24-48", "-24"),
						// Time to First Review — hours to first review; lower = better (<4h healthy)
						"kpi210", List.of("24-", "16-24", "8-16", "4-8", "-4"),
						// Build Success Rate — % builds passing on main; <90% = tax, <80% = critical
						"kpi212", List.of("-70", "70-80", "80-90", "90-95", "95-"),
						// Deployment Frequency Slingshot — same range as kpi118 after
						// AlignKpiMaturityRangeToKEM
						"kpi213", List.of("-2", "2-4", "5-10", "11-20", "20-"),
						// Lead Time for Change Slingshot — days; same range as kpi156 (DoraMaturity)
						"kpi214", List.of("90-", "30-90", "7-30", "1-7", "-1"));

		enableMaturity(maturityRangeMap);
	}

	@RollbackExecution
	public void rollback() {
		List<String> kpiIds =
				List.of("kpi203", "kpi208", "kpi209", "kpi210", "kpi212", "kpi213", "kpi214");
		MongoCollection<Document> collection = mongoTemplate.getCollection(KPI_MASTER);
		List<WriteModel<Document>> ops = new ArrayList<>();
		for (String kpiId : kpiIds) {
			Bson filter = eq(KPI_ID, kpiId);
			ops.add(
					new UpdateOneModel<>(
							filter, combine(set("calculateMaturity", false), unset("maturityRange"))));
		}
		collection.bulkWrite(ops);
	}

	private void enableMaturity(Map<String, List<String>> maturityRangeMap) {
		MongoCollection<Document> collection = mongoTemplate.getCollection(KPI_MASTER);
		List<WriteModel<Document>> ops = new ArrayList<>();
		for (Map.Entry<String, List<String>> entry : maturityRangeMap.entrySet()) {
			Bson filter = eq(KPI_ID, entry.getKey());
			ops.add(
					new UpdateOneModel<>(
							filter,
							combine(set("calculateMaturity", true), set("maturityRange", entry.getValue()))));
		}
		collection.bulkWrite(ops);
	}
}
