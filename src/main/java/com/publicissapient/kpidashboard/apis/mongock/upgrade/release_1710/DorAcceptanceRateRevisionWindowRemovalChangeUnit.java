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

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.ReplaceOptions;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Removes the configurable revision window from the DOR Acceptance Rate KPI (kpi228).
 *
 * <p>Revisions are now always counted in the strict Definition-of-Ready window — between the first
 * transition into a Ready status and the first transition into a dev-start status. The {@code
 * AFTER_READY} alternative measured rewrites that land once development has already begun, which is
 * Mid-Sprint Re-Refinement Rate (kpi225) territory, not Definition of Ready.
 *
 * <p>Drops the {@code dorRevisionWindowKPI228} field mapping structure entry, unsets any value and
 * configuration history already captured against it, and closes the gap it leaves in the display
 * order of the remaining Custom Fields Mapping entries.
 */
@ChangeUnit(
		id = "dor_acceptance_rate_revision_window_removal",
		order = "17212",
		author = "knowhow",
		systemVersion = "17.1.0")
public class DorAcceptanceRateRevisionWindowRemovalChangeUnit {

	private static final String FIELD_MAPPING_STRUCTURE_COLLECTION = "field_mapping_structure";
	private static final String FIELD_MAPPING_COLLECTION = "field_mapping";
	private static final String FIELD_MAPPING_HISTORY_COLLECTION = "field_mapping_history";
	private static final String FIELD_NAME = "fieldName";
	private static final String FIELD_DISPLAY_ORDER = "fieldDisplayOrder";
	private static final String SECTION = "section";
	private static final String SECTION_ORDER = "sectionOrder";
	private static final String TOOLTIP = "tooltip";
	private static final String DEFINITION = "definition";
	private static final String MANDATORY = "mandatory";
	private static final String NODE_SPECIFIC = "nodeSpecific";
	private static final String PROCESSOR_COMMON = "processorCommon";
	private static final String FIELD_LABEL = "fieldLabel";
	private static final String FIELD_TYPE = "fieldType";
	private static final String NUMBER = "number";
	private static final String CUSTOM_FIELDS_SECTION = "Custom Fields Mapping";
	private static final String SET = "$set";
	private static final String UNSET = "$unset";

	private static final String REVISION_WINDOW_FIELD = "dorRevisionWindowKPI228";
	private static final String HISTORY_REVISION_WINDOW_FIELD = "historydorRevisionWindowKPI228";
	private static final String CHANGE_PERCENT_FIELD = "dorSubstantiveChangePercentKPI228";
	private static final String REWRITE_COUNT_FIELD = "dorMajorRewriteRevisionCountKPI228";

	@Execution
	public void execution(MongoTemplate mongoTemplate) {
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.deleteOne(new Document(FIELD_NAME, REVISION_WINDOW_FIELD));

		// Drop any value and history already captured against the removed field
		mongoTemplate
				.getCollection(FIELD_MAPPING_COLLECTION)
				.updateMany(
						new Document(REVISION_WINDOW_FIELD, new Document("$exists", true)),
						new Document(UNSET, new Document(REVISION_WINDOW_FIELD, "")));
		mongoTemplate
				.getCollection(FIELD_MAPPING_HISTORY_COLLECTION)
				.updateMany(
						new Document(HISTORY_REVISION_WINDOW_FIELD, new Document("$exists", true)),
						new Document(UNSET, new Document(HISTORY_REVISION_WINDOW_FIELD, "")));

		// Close the gap the removed field leaves behind: 9 -> 8, 10 -> 9
		setDisplayOrder(mongoTemplate, CHANGE_PERCENT_FIELD, 8);
		setDisplayOrder(mongoTemplate, REWRITE_COUNT_FIELD, 9);
	}

	private void setDisplayOrder(MongoTemplate mongoTemplate, String fieldName, int displayOrder) {
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.updateOne(
						new Document(FIELD_NAME, fieldName),
						new Document(SET, new Document(FIELD_DISPLAY_ORDER, displayOrder)));
	}

	@RollbackExecution
	public void rollback(MongoTemplate mongoTemplate) {
		Document revisionWindow =
				new Document()
						.append(FIELD_NAME, REVISION_WINDOW_FIELD)
						.append(FIELD_LABEL, "Revision window")
						.append(FIELD_TYPE, "radiobutton")
						.append(FIELD_DISPLAY_ORDER, 8)
						.append(SECTION_ORDER, 1)
						.append(SECTION, CUSTOM_FIELDS_SECTION)
						.append(PROCESSOR_COMMON, false)
						.append(MANDATORY, false)
						.append(NODE_SPECIFIC, false)
						.append(
								"options",
								Arrays.asList(
										new Document()
												.append("label", "Between Ready and In Progress")
												.append("value", "READY_TO_IN_PROGRESS"),
										new Document()
												.append("label", "Everything after Ready")
												.append("value", "AFTER_READY")))
						.append(
								TOOLTIP,
								new Document()
										.append(
												DEFINITION,
												"The slice of the story's life in which revisions are counted:"
														+ "<br>1. Between Ready and In Progress : only edits landing after the story was marked "
														+ "Ready and before development started. This is the strict Definition-of-Ready reading. "
														+ "Recommended.<br>2. Everything after Ready : also counts rewrites that land once "
														+ "development has already begun. <hr>"));

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.replaceOne(
						new Document(FIELD_NAME, REVISION_WINDOW_FIELD),
						revisionWindow,
						new ReplaceOptions().upsert(true));

		setDisplayOrder(mongoTemplate, CHANGE_PERCENT_FIELD, 9);
		setDisplayOrder(mongoTemplate, REWRITE_COUNT_FIELD, 10);
	}
}
