/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
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

package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1320;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;

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

@ChangeUnit(
		id = "r_align_kpi_maturity_range_kem",
		order = "013207",
		author = "kunkambl",
		systemVersion = "13.2.0")
public class AlignKpiMaturityRangeToKEM {

	private final MongoTemplate mongoTemplate;
	public static final String KPI_PI_PREDICTABILITY = "kpi153";

	public AlignKpiMaturityRangeToKEM(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@RollbackExecution
	public void execute() {
		Map<String, List<String>> maturityRangeMap =
				Map.of(
						"kpi72",
						List.of("-60", "60-79", "80-94", "95-105", "105-"),
						"kpi82",
						List.of("-60", "60-79", "80-89", "90-94", "95-"),
						"kpi111",
						List.of("-50", "50-40", "40-20", "20-10", "10-"),
						"kpi166",
						List.of("24-", "4-24", "1-4", "0.5-1", "-0.5"),
						KPI_PI_PREDICTABILITY,
						List.of("-50", "51-70", "71-80", "81-90", "91-"),
						"kpi118",
						List.of("-2", "2-4", "5-10", "11-20", "20-"));

		updateMaturityRanges(maturityRangeMap, true);
	}

	@Execution
	public void rollback() {
		Map<String, List<String>> maturityRangeMap =
				Map.of(
						"kpi72",
						List.of("-40", "40-60", "60-75", "75-90", "90-"),
						"kpi82",
						List.of("-25", "25-50", "50-75", "75-90", "90-"),
						"kpi111",
						List.of("-90", "90-60", "60-25", "25-10", "10-"),
						"kpi166",
						List.of("48-", "24-48", "12-24", "1-12", "-1"),
						KPI_PI_PREDICTABILITY,
						new ArrayList<>(),
						"kpi118",
						List.of("0-2", "2-4", "4-6", "6-8", "8-"));

		updateMaturityRanges(maturityRangeMap, false);
	}

	private void updateMaturityRanges(
			Map<String, List<String>> maturityRangeMap, boolean calculateMaturityValue) {
		MongoCollection<Document> collection = mongoTemplate.getCollection("kpi_master");
		List<WriteModel<Document>> bulkOperations = new ArrayList<>();

		for (Map.Entry<String, List<String>> entry : maturityRangeMap.entrySet()) {
			String kpiId = entry.getKey();
			Object newValue = entry.getValue();

			Bson filter = eq("kpiId", kpiId);
			Bson update = set("maturityRange", newValue);

			bulkOperations.add(new UpdateOneModel<>(filter, update));

			if (kpiId.equalsIgnoreCase(KPI_PI_PREDICTABILITY)) {
				bulkOperations.add(
						new UpdateOneModel<>(filter, set("calculateMaturity", calculateMaturityValue)));
			}
		}
		collection.bulkWrite(bulkOperations);
	}
}
