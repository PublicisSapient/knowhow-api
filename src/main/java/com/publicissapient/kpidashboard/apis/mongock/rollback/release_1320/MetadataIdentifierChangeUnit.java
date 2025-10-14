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

package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1320;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(
		id = "r_insertMetadataIdentifier",
		order = "013208",
		author = "girpatha",
		systemVersion = "13.2.0")
public class MetadataIdentifierChangeUnit {
	private static final String METADATA_IDENTIFIER_COLLECTION = "metadata_identifier";
	private final MongoTemplate mongoTemplate;

	public MetadataIdentifierChangeUnit(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void execution() {
		deleteMetadataIdentifier();
	}

	@RollbackExecution
	public void rollback() {
		// No execution logic as this is a rollback script
	}

	private void deleteMetadataIdentifier() {
		mongoTemplate
				.getCollection(METADATA_IDENTIFIER_COLLECTION)
				.deleteOne(new Document("tool", "Rally").append("templateName", "Standard Template"));
	}
}
