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
package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1410;

import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.Filters;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(id = "r_code_quality_metrics_kpi", order = "014108", author = "valanil", systemVersion = "14.1.0")
public class CodeQualityMetricsChangeUnit {

	public static final String KPI_ID = "kpiId";
	public static final String KPI_201 = "kpi201";
	private final MongoTemplate mongoTemplate;

	public CodeQualityMetricsChangeUnit(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void execution() {
		rollbackKpi201();
	}

	@RollbackExecution
	public void rollback() {
		// No rollback needed for rollback script
	}

	public void rollbackKpi201() {
		Bson filter = Filters.eq(KPI_ID, KPI_201);
		mongoTemplate.getCollection("kpi_master").deleteOne(filter);
	}
}