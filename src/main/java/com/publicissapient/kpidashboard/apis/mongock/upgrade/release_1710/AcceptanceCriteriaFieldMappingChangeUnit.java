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

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Adds the <b>Acceptance Criteria</b> custom field mapping.
 *
 * <p>Acceptance Criteria is not a built-in Jira field - every Jira instance stores it in a custom
 * field of its own (typically {@code customfield_1xxxx}), which is why the processor cannot guess
 * it. This change unit registers {@code jiraAcceptanceCriteriaCustomField} so a project can declare
 * the id once under <i>Custom Fields Mapping</i>; the Jira processor then collects the value into
 * {@code jira_issue.acceptanceCriteria} on the next run.
 *
 * <p>The mapping is flagged {@code processorCommon} because the collected value is not owned by a
 * single KPI - it feeds the issue itself and therefore every KPI that reads it.
 */
@Slf4j
@RequiredArgsConstructor
@ChangeUnit(
		id = "jira_acceptance_criteria_field_mapping",
		order = "17205",
		author = "kunkambl",
		systemVersion = "17.1.0")
public class AcceptanceCriteriaFieldMappingChangeUnit {

	private static final String FIELD_MAPPING_STRUCTURE_COLLECTION = "field_mapping_structure";

	private static final String FIELD_NAME = "fieldName";
	private static final String FIELD_LABEL = "fieldLabel";
	private static final String PLACEHOLDER_TEXT = "placeHolderText";
	private static final String FIELD_TYPE = "fieldType";
	private static final String FIELD_CATEGORY = "fieldCategory";
	private static final String FIELD_DISPLAY_ORDER = "fieldDisplayOrder";
	private static final String SECTION = "section";
	private static final String SECTION_ORDER = "sectionOrder";
	private static final String PROCESSOR_COMMON = "processorCommon";
	private static final String MANDATORY = "mandatory";
	private static final String NODE_SPECIFIC = "nodeSpecific";
	private static final String TOOLTIP = "tooltip";
	private static final String DEFINITION = "definition";

	private static final String ACCEPTANCE_CRITERIA_FIELD = "jiraAcceptanceCriteriaCustomField";

	private static final String TOOLTIP_DEFINITION =
			"Acceptance Criteria is not a built-in Jira field, it is always a custom field. Provide the id of the "
					+ "custom field that holds the Acceptance Criteria so that KnowHow can collect it together with the "
					+ "issue. The id is listed on <b>{your Jira base url}/rest/api/2/field</b>. <br> Example : "
					+ "customfield_11111 <hr>";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		log.info("Adding the {} field mapping structure", ACCEPTANCE_CRITERIA_FIELD);
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.insertOne(acceptanceCriteriaFieldMappingStructure());
	}

	@RollbackExecution
	public void rollback() {
		log.info("Removing the {} field mapping structure", ACCEPTANCE_CRITERIA_FIELD);
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.deleteOne(new Document(FIELD_NAME, ACCEPTANCE_CRITERIA_FIELD));
	}

	private static Document acceptanceCriteriaFieldMappingStructure() {
		return new Document()
				.append(FIELD_NAME, ACCEPTANCE_CRITERIA_FIELD)
				.append(FIELD_LABEL, "Custom field for Acceptance Criteria")
				.append(PLACEHOLDER_TEXT, "Example : customfield_11111")
				.append(FIELD_TYPE, "text")
				.append(FIELD_CATEGORY, "fields")
				.append(SECTION, "Custom Fields Mapping")
				.append(PROCESSOR_COMMON, true)
				.append(FIELD_DISPLAY_ORDER, 5)
				.append(SECTION_ORDER, 1)
				.append(MANDATORY, false)
				.append(NODE_SPECIFIC, false)
				.append(TOOLTIP, new Document().append(DEFINITION, TOOLTIP_DEFINITION));
	}
}
