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

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1610;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.mongodb.client.result.UpdateResult;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(
		id = "update_kpi_category_mapping",
		order = "16101",
		author = "kunkambl",
		systemVersion = "16.1.0")
public class UpdateKpiCategoryMappingChangeUnit {

	@Execution
	public void execution(MongoTemplate mongoTemplate) {
		updateCategory(mongoTemplate, "categoryThree", "value");
		updateCategory(mongoTemplate, "categoryTwo", "quality");
		updateCategory(mongoTemplate, "categoryOne", "speed");
		log.info("Successfully updated KPI categories");
	}

	@RollbackExecution
	public void rollback(MongoTemplate mongoTemplate) {
		updateCategory(mongoTemplate, "value", "categoryThree");
		updateCategory(mongoTemplate, "quality", "categoryTwo");
		updateCategory(mongoTemplate, "speed", "categoryOne");
		log.info("Successfully rolled back KPI categories");
	}

	private void updateCategory(MongoTemplate mongoTemplate, String oldId, String newId) {
		Query query = new Query(Criteria.where("categoryId").is(oldId));
		Update update = new Update().set("categoryId", newId);

		UpdateResult result1 = mongoTemplate.updateMulti(query, update, "kpi_category");
		log.info(
				"Updated kpi_category: {} -> {} (matched: {}, modified: {})",
				oldId,
				newId,
				result1.getMatchedCount(),
				result1.getModifiedCount());

		UpdateResult result2 = mongoTemplate.updateMulti(query, update, "kpi_category_mapping");
		log.info(
				"Updated kpi_category_mapping: {} -> {} (matched: {}, modified: {})",
				oldId,
				newId,
				result2.getMatchedCount(),
				result2.getModifiedCount());
	}
}
