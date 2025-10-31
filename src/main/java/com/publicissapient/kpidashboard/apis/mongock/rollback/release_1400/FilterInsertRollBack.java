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

package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1400;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.Filters;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(
		id = "r_insert_filter_data",
		order = "014002",
		author = "shi6",
		systemVersion = "14.0.0")
public class FilterInsertRollBack {
	private final MongoTemplate mongoTemplate;

	public FilterInsertRollBack(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void execution() {
		mongoTemplate.getCollection("filters").deleteOne(Filters.eq("boardId", 0));
	}

	@RollbackExecution
	public void rollback() {
		Document filterDocument =
				new Document()
						.append("boardId", 0)
						.append(
								"projectTypeSwitch", new Document().append("enabled", true).append("visible", true))
						.append(
								"primaryFilter",
								new Document()
										.append("type", "singleSelect")
										.append(
												"defaultLevel",
												new Document().append("labelName", "project").append("sortBy", null)))
						.append("parentFilter", new Document().append("labelName", "Organization Level"));

		mongoTemplate.getCollection("filters").insertOne(filterDocument);
	}
}
