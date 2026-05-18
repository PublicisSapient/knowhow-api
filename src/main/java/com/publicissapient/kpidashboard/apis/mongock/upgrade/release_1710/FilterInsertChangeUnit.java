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

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.Filters;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "insert_filter_data_board_20",
		order = "17103",
		author = "kunkambl",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class FilterInsertChangeUnit {

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		Document filterDocument =
				new Document()
						.append("boardId", 20)
						.append(
								"projectTypeSwitch", new Document().append("enabled", true).append("visible", true))
						.append(
								"primaryFilter",
								new Document()
										.append("type", "singleSelect")
										.append("defaultLevel", new Document().append("labelName", "project")))
						.append("parentFilter", new Document().append("labelName", "Organization Level"))
						.append("additionalFilters", null);

		mongoTemplate.getCollection("filters").insertOne(filterDocument);
	}

	@RollbackExecution
	public void rollback() {
		mongoTemplate.getCollection("filters").deleteOne(Filters.eq("boardId", 20));
	}
}
