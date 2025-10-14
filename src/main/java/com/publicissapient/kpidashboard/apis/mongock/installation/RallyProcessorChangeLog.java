/*
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.mongock.installation;

import java.util.Collections;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(id = "insertRallyProcessor", order = "009", author = "girpatha")
public class RallyProcessorChangeLog {

	private static final String PROCESSOR_COLLECTION = "processor";
	private static final String CLASS_KEY = "_class";

	private final MongoTemplate mongoTemplate;

	public RallyProcessorChangeLog(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void insertRallyProcessor() {
		Document rallyProcessor =
				new Document()
						.append("processorName", "Rally")
						.append("processorType", "AGILE_TOOL")
						.append("isActive", true)
						.append("isOnline", true)
						.append("errors", Collections.emptyList())
						.append(CLASS_KEY, "com.publicissapient.kpidashboard.jira.model.RallyProcessor");
		mongoTemplate.getCollection(PROCESSOR_COLLECTION).insertOne(rallyProcessor);
	}

	@RollbackExecution
	public void rollback() {
		mongoTemplate
				.getCollection(PROCESSOR_COLLECTION)
				.deleteOne(new Document("processorName", "Rally"));
	}
}
