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

import java.util.Collections;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.UpdateOptions;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

/**
 * @author shunaray .
 */
@RequiredArgsConstructor
@ChangeUnit(
		id = "update_kpi135_link",
		order = "13306",
		author = "shunaray",
		systemVersion = "13.3.0")
public class UpdateKpiLinkChangeUnit {

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		Document filter =
				new Document("kpiId", "kpi135")
						.append("kpiInfo.details", new Document("$elemMatch", new Document("type", "link")));
		Document update =
				new Document(
						"$set",
						new Document(
								"kpiInfo.details.$[elem].kpiLinkDetail.link",
								"https://knowhow.tools.publicis.sapient.com/wiki/kpi135-First+Time+Pass+Rate"));
		UpdateOptions options =
				new UpdateOptions()
						.arrayFilters(Collections.singletonList(new Document("elem.type", "link")));

		mongoTemplate.getCollection("kpi_master").updateOne(filter, update, options);
	}

	@RollbackExecution
	public void rollback() {
		// rollback is not required for this change unit
	}
}
