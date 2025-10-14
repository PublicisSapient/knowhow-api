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

/**
 * @author shunaray
 */
@ChangeUnit(
		id = "r_drr_rejection_label",
		order = "013201",
		author = "shunaray",
		systemVersion = "13.2.0")
public class DRRChangeUnit {

	public static final String FIELD_NAME = "fieldName";
	public static final String FIELD_MAPPING_STRUCTURE = "field_mapping_structure";
	private final MongoTemplate mongoTemplate;

	public DRRChangeUnit(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void execution() {
		rollbackFieldMapping();
		changeMandatoryFlag(true);
	}

	private void insertDRRLabelFieldMapping() {

		Document fieldMapping =
				new Document()
						.append(FIELD_NAME, "defectRejectionLabelsKPI37")
						.append("fieldLabel", "Labels to filter issues in consideration")
						.append("fieldType", "chips")
						.append("section", "Custom Fields Mapping")
						.append("processorCommon", false)
						.append(
								"tooltip",
								new Document(
										"definition",
										"Specify labels to identify and filter issues considered as rejected defects."))
						.append("fieldDisplayOrder", 2)
						.append("sectionOrder", 2)
						.append("mandatory", false)
						.append("nodeSpecific", false);

		mongoTemplate.getCollection(FIELD_MAPPING_STRUCTURE).insertOne(fieldMapping);
	}

	@RollbackExecution
	public void rollback() {
		insertDRRLabelFieldMapping();
		changeMandatoryFlag(false);
	}

	private void changeMandatoryFlag(boolean value) {
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE)
				.updateOne(
						new Document(FIELD_NAME, "resolutionTypeForRejectionKPI37"),
						new Document("$set", new Document("mandatory", value)));
	}

	private void rollbackFieldMapping() {
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE)
				.deleteOne(new Document(FIELD_NAME, "defectRejectionLabelsKPI37"));
	}
}
