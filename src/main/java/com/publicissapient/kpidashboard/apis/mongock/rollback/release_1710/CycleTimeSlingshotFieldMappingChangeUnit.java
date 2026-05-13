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

package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1710;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "r_cycle_time_slingshot_field_mapping_insert",
		order = "17102",
		author = "kunkambl",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class CycleTimeSlingshotFieldMappingChangeUnit {

	private static final String FIELD_MAPPING_STRUCTURE = "field_mapping_structure";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		Query query =
				new Query(
						Criteria.where("fieldName")
								.in("jiraIssueTypeKPI202", "jiraIssueStatusGroupByCategoryKPI202"));
		mongoTemplate.remove(query, FIELD_MAPPING_STRUCTURE);
	}

	@RollbackExecution
	public void rollback() {
		// no-op: rollback of a rollback is not required
	}
}
