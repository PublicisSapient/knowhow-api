/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1330;

import static com.mongodb.client.model.Filters.eq;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.MongoCollection;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * @author shi6
 */
@ChangeUnit(
		id = "r_update_time_kpi_column",
		order = "013300",
		author = "shi6",
		systemVersion = "13.3.0")
public class RollbackUpdateKPIColumnTime {
	private static final String COLUMN_NAME = "columnName";
	private static final String KPI_COLUMN_DETAILS = "kpiColumnDetails";
	public static final String REOPEN_DATE = "Reopen Date";
	public static final String REOPEN_TIME = "Reopen Time";
	public static final String CLOSED_DATE = "Closed Date";
	public static final String CLOSED_TIME = "Closed Time";
	private final MongoTemplate mongoTemplate;

	public RollbackUpdateKPIColumnTime(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void execution() {

		Map<String, Map<String, String>> kpiRenameMap = new HashMap<>();
		// Example for kpi156
		Map<String, String> kpi156Map = new HashMap<>();
		kpi156Map.put("Change Release Time [B]", "Change Release Date [B]");
		kpi156Map.put("Change Completion Time [A]", "Change Completion Date [A]");
		kpiRenameMap.put("kpi156", kpi156Map);

		// Example for another KPI (kpi789), add your own mappings
		Map<String, String> kpi166Map = new HashMap<>();
		kpi166Map.put("Created Time", "Created Date");
		kpi166Map.put("Completion Time", "Completion Date");
		kpiRenameMap.put("kpi166", kpi166Map);

		Map<String, String> kpi190Map = new HashMap<>();
		kpi190Map.put(REOPEN_TIME, REOPEN_DATE);
		kpi190Map.put(CLOSED_TIME, CLOSED_DATE);
		kpiRenameMap.put("kpi190", kpi190Map);

		Map<String, String> kpi137Map = new HashMap<>();
		kpi137Map.put(REOPEN_TIME, REOPEN_DATE);
		kpi137Map.put(CLOSED_TIME, CLOSED_DATE);
		kpiRenameMap.put("kpi137", kpi137Map);

		updateMultipleKpisColumns(mongoTemplate, kpiRenameMap);
	}

	public void updateMultipleKpisColumns(
			MongoTemplate mongoTemplate, Map<String, Map<String, String>> kpiRenameMap) {

		MongoCollection<Document> collection =
				mongoTemplate.getCollection("kpi_column_configs"); // Replace with your
		// collection name
		for (Map.Entry<String, Map<String, String>> entry : kpiRenameMap.entrySet()) {
			String kpiId = entry.getKey();
			Map<String, String> renameMap = entry.getValue();

			// Filter documents for this KPI with relevant old column names
			Document filter =
					new Document("kpiId", kpiId)
							.append(
									KPI_COLUMN_DETAILS,
									new Document(
											"$elemMatch",
											new Document(COLUMN_NAME, new Document("$in", renameMap.keySet()))));

			for (Document doc : collection.find(filter)) {
				boolean updated = false;
				List<Document> kpiColumnDetails = (List<Document>) doc.get(KPI_COLUMN_DETAILS);

				for (Document colDetail : kpiColumnDetails) {
					String oldName = colDetail.getString(COLUMN_NAME);
					if (renameMap.containsKey(oldName)) {
						colDetail.put(COLUMN_NAME, renameMap.get(oldName));
						updated = true;
					}
				}

				if (updated) {
					collection.updateOne(
							eq("_id", doc.getObjectId("_id")),
							new Document("$set", new Document(KPI_COLUMN_DETAILS, kpiColumnDetails)));
				}
			}
		}
	}

	@RollbackExecution
	public void rollback() {
		// Map of kpiId → map of old columnName to new columnName
		Map<String, Map<String, String>> kpiRenameMap = new HashMap<>();
		// Example for kpi156
		Map<String, String> kpi156Map = new HashMap<>();
		kpi156Map.put("Change Release Date [B]", "Change Release Time [B]");
		kpi156Map.put("Change Completion Date [A]", "Change Completion Time [A]");
		kpiRenameMap.put("kpi156", kpi156Map);

		// Example for another KPI (kpi789), add your own mappings
		Map<String, String> kpi166Map = new HashMap<>();
		kpi166Map.put("Created Date", "Created Time");
		kpi166Map.put("Completion Date", "Completion Time");
		kpiRenameMap.put("kpi166", kpi166Map);

		Map<String, String> kpi190Map = new HashMap<>();
		kpi190Map.put(REOPEN_DATE, REOPEN_TIME);
		kpi190Map.put(CLOSED_DATE, CLOSED_TIME);
		kpiRenameMap.put("kpi190", kpi190Map);

		Map<String, String> kpi137Map = new HashMap<>();
		kpi137Map.put(REOPEN_DATE, REOPEN_TIME);
		kpi137Map.put(CLOSED_DATE, CLOSED_TIME);
		kpiRenameMap.put("kpi137", kpi137Map);

		updateMultipleKpisColumns(mongoTemplate, kpiRenameMap);
	}
}
