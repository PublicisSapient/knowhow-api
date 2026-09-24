/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

/**
 * Registers the Acceptance Criteria identifier in {@code metadata_identifier.customfield} so the
 * Jira/Azure/Rally metadata collector can discover the custom field automatically.
 *
 * <p>{@code CreateMetadataImpl.compareCustomField()} walks every entry of {@code
 * metadata_identifier.customfield} and resolves its {@code value} names against the field list the
 * board exposes, turning a display name such as "Acceptance Criteria" into an id like {@code
 * customfield_11101} and assigning it onto {@code FieldMapping.jiraAcceptanceCriteriaCustomField}.
 * Without an entry here that field is never auto discovered and every project has to look the id up
 * by hand.
 *
 * <p>The names are tried in order and the first one the board exposes wins. Casing is already
 * handled by the collector, so only genuinely different spellings are listed.
 *
 * <p>Two groups are treated differently:
 *
 * <ul>
 *   <li><b>Standard / Azure / Rally templates</b> get the candidate names, so the field resolves
 *       automatically.
 *   <li><b>Standard non-DOJO templates</b> deliberately ship every identifier with an empty value
 *       list — nothing is auto mapped there — so the entry is added empty to keep the document
 *       shape uniform without changing behaviour.
 * </ul>
 *
 * <p>Documents with no {@code customfield} block at all (the "Custom Template" ones) are left
 * untouched, and the update is idempotent: a document that already carries the identifier is
 * skipped.
 *
 * <p>This runs at order 17207, long after {@code KpiDefaultConfiguration} (order 004) seeds the
 * collection, so it covers fresh installations as well as upgrades.
 *
 * <p>Existing project configuration is never overwritten — {@code FieldMappingHelper.shouldMerge()}
 * only fills a field that is still null in the database.
 */
@Slf4j
@ChangeUnit(
		id = "acceptance_criteria_metadata_identifier",
		order = "17207",
		author = "knowhow",
		systemVersion = "17.1.0")
public class AcceptanceCriteriaMetadataIdentifierChangeUnit {

	private static final String METADATA_IDENTIFIER_COLLECTION = "metadata_identifier";
	private static final String CUSTOM_FIELD = "customfield";
	private static final String CUSTOM_FIELD_TYPE = "customfield.type";
	private static final String TEMPLATE_NAME = "templateName";
	private static final String TYPE = "type";
	private static final String VALUE = "value";
	private static final String EXISTS = "$exists";
	private static final String NE = "$ne";
	private static final String PUSH = "$push";
	private static final String PULL = "$pull";
	private static final String NON_DOJO_TEMPLATE = "Standard non-DOJO Template";

	/** Must match {@code CommonConstant.ACCEPTANCE_CRITERIA}. */
	private static final String ACCEPTANCE_CRITERIA_TYPE = "jiraAcceptanceCriteriaCustomField";

	private static final List<String> CANDIDATE_NAMES =
			Arrays.asList("Acceptance Criteria", "Acceptance Criteria (AC)", "AcceptanceCriteria");

	@Execution
	public void execution(MongoTemplate mongoTemplate) {
		long autoMapping =
				mongoTemplate
						.getCollection(METADATA_IDENTIFIER_COLLECTION)
						.updateMany(
								notYetMapped().append(TEMPLATE_NAME, new Document(NE, NON_DOJO_TEMPLATE)),
								push(CANDIDATE_NAMES))
						.getModifiedCount();

		long nonDojo =
				mongoTemplate
						.getCollection(METADATA_IDENTIFIER_COLLECTION)
						.updateMany(notYetMapped().append(TEMPLATE_NAME, NON_DOJO_TEMPLATE), push(List.of()))
						.getModifiedCount();

		log.info(
				"Added the {} metadata identifier to {} auto mapping template(s) and {} non-DOJO template(s)",
				ACCEPTANCE_CRITERIA_TYPE,
				autoMapping,
				nonDojo);
	}

	/** Only documents that already carry a customfield block and do not have the entry yet. */
	private Document notYetMapped() {
		return new Document(CUSTOM_FIELD, new Document(EXISTS, true))
				.append(CUSTOM_FIELD_TYPE, new Document(NE, ACCEPTANCE_CRITERIA_TYPE));
	}

	private Document push(List<String> values) {
		return new Document(
				PUSH,
				new Document(
						CUSTOM_FIELD, new Document(TYPE, ACCEPTANCE_CRITERIA_TYPE).append(VALUE, values)));
	}

	@RollbackExecution
	public void rollback(MongoTemplate mongoTemplate) {
		long reverted =
				mongoTemplate
						.getCollection(METADATA_IDENTIFIER_COLLECTION)
						.updateMany(
								new Document(CUSTOM_FIELD_TYPE, ACCEPTANCE_CRITERIA_TYPE),
								new Document(
										PULL, new Document(CUSTOM_FIELD, new Document(TYPE, ACCEPTANCE_CRITERIA_TYPE))))
						.getModifiedCount();
		log.info(
				"Removed the {} metadata identifier from {} document(s)",
				ACCEPTANCE_CRITERIA_TYPE,
				reverted);
	}
}
