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

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1330;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

import static com.mongodb.client.model.Filters.eq;

/**
 *
 * @author shi6
 */
@ChangeUnit(id = "update_dora_kpi_column", order = "13300", author = "shi6", systemVersion = "13.3.0")
public class UpdateDoraKPIColumnName {

	private final MongoTemplate mongoTemplate;

	public UpdateDoraKPIColumnName(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void execution() {
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

		updateMultipleKpisColumns(mongoTemplate, kpiRenameMap);
	}

	public void updateMultipleKpisColumns(MongoTemplate mongoTemplate, Map<String, Map<String, String>> kpiRenameMap) {

		MongoCollection<Document> collection = mongoTemplate.getCollection("kpi_column_configs"); // Replace with your
																								  // collection name
		for (Map.Entry<String, Map<String, String>> entry : kpiRenameMap.entrySet()) {
			String kpiId = entry.getKey();
			Map<String, String> renameMap = entry.getValue();

			// Filter documents for this KPI with relevant old column names
			Document filter = new Document("kpiId", kpiId).append("kpiColumnDetails",
					new Document("$elemMatch", new Document("columnName", new Document("$in", renameMap.keySet()))));

			for (Document doc : collection.find(filter)) {
				boolean updated = false;
				List<Document> kpiColumnDetails = (List<Document>) doc.get("kpiColumnDetails");

				for (Document colDetail : kpiColumnDetails) {
					String oldName = colDetail.getString("columnName");
					if (renameMap.containsKey(oldName)) {
						colDetail.put("columnName", renameMap.get(oldName));
						updated = true;
					}
				}

				if (updated) {
					collection.updateOne(eq("_id", doc.getObjectId("_id")),
							new Document("$set", new Document("kpiColumnDetails", kpiColumnDetails)));
				}
			}
		}
	}

	@RollbackExecution
	public void rollback() {
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

		updateMultipleKpisColumns(mongoTemplate, kpiRenameMap);
	}
}
