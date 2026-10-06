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

/**
 * Refines the DOR Acceptance Rate KPI (kpi228) after its initial rollout:
 *
 * <ul>
 *   <li>moves the widget to position 2 of the Intake sub-category ({@code defaultOrder} and {@code
 *       kpiSubCategoryOrder});
 *   <li>shortens the KPI definition;
 *   <li>makes Description the default for "Fields to count revisions on" and documents it in the
 *       tooltip;
 *   <li>fills the field mapping defaults (Description, 20%, 2 revisions) into project field
 *       mappings whose saved value is blank (empty string, empty list or null). Projects that never
 *       saved a value pick the same defaults up from the {@code FieldMapping} model, and values a
 *       project already set are kept.
 * </ul>
 */
@ChangeUnit(
		id = "dor_acceptance_rate_defaults_and_order_update",
		order = "17213",
		author = "knowhow",
		systemVersion = "17.1.0")
public class DorAcceptanceRateDefaultsChangeUnit {

	private static final String KPI_ID = "kpi228";
	private static final String KPI_ID_FIELD = "kpiId";
	private static final String KPI_MASTER_COLLECTION = "kpi_master";
	private static final String FIELD_MAPPING_STRUCTURE_COLLECTION = "field_mapping_structure";
	private static final String FIELD_MAPPING_COLLECTION = "field_mapping";
	private static final String FIELD_NAME = "fieldName";
	private static final String DEFAULT_ORDER = "defaultOrder";
	private static final String SUB_CATEGORY_ORDER = "kpiSubCategoryOrder";
	private static final String KPI_DEFINITION_PATH = "kpiInfo.definition";
	private static final String TOOLTIP_DEFINITION_PATH = "tooltip.definition";

	private static final String REVISION_FIELDS_FIELD = "dorRevisionFieldsKPI228";
	private static final String CHANGE_PERCENT_FIELD = "dorSubstantiveChangePercentKPI228";
	private static final String REWRITE_COUNT_FIELD = "dorMajorRewriteRevisionCountKPI228";

	private static final String SET = "$set";
	private static final String OR = "$or";
	private static final String TYPE = "$type";
	private static final String NULL_TYPE = "null";
	private static final String SIZE = "$size";

	private static final int NEW_ORDER = 2;
	private static final int PREVIOUS_ORDER = 7;
	private static final String DESCRIPTION_VALUE = "DESCRIPTION";
	private static final double DEFAULT_CHANGE_PERCENT = 20.0;
	private static final int DEFAULT_REWRITE_COUNT = 2;

	private static final String NEW_KPI_DEFINITION =
			"% of refined stories that pass through DOR without a major rewrite once development starts.";

	private static final String PREVIOUS_KPI_DEFINITION =
			"Percentage of refined stories that pass through Definition of Ready without a major rewrite. "
					+ "A story counts once it has been marked Ready and has entered development; it fails when its "
					+ "Description and/or Acceptance Criteria were substantively revised more than the configured "
					+ "number of times inside the revision window. 80%+ is healthy. Below 60% means DOR is theatre - "
					+ "stories are being marked Ready prematurely.";

	private static final String NEW_REVISION_FIELDS_TOOLTIP =
			"Which issue fields are inspected for rewrites after a story is marked Ready. Defaults to "
					+ "Description. Acceptance Criteria is read from the field configured as 'Custom field for "
					+ "Acceptance Criteria' - when that is not set, only Description revisions are available. <hr>";

	private static final String PREVIOUS_REVISION_FIELDS_TOOLTIP =
			"Which issue fields are inspected for rewrites after a story is marked Ready. Leave blank "
					+ "to inspect both. Acceptance Criteria is read from the field configured as 'Custom field for "
					+ "Acceptance Criteria' - when that is not set, only Description revisions are available. <hr>";

	@Execution
	public void execution(MongoTemplate mongoTemplate) {
		updateKpiMaster(mongoTemplate, NEW_ORDER, NEW_KPI_DEFINITION);
		updateRevisionFieldsTooltip(mongoTemplate, NEW_REVISION_FIELDS_TOOLTIP);
		fillBlankFieldMappingValues(mongoTemplate);
	}

	private void updateKpiMaster(MongoTemplate mongoTemplate, int order, String definition) {
		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.updateOne(
						new Document(KPI_ID_FIELD, KPI_ID),
						new Document(
								SET,
								new Document(DEFAULT_ORDER, order)
										.append(SUB_CATEGORY_ORDER, order)
										.append(KPI_DEFINITION_PATH, definition)));
	}

	private void updateRevisionFieldsTooltip(MongoTemplate mongoTemplate, String tooltip) {
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.updateOne(
						new Document(FIELD_NAME, REVISION_FIELDS_FIELD),
						new Document(SET, new Document(TOOLTIP_DEFINITION_PATH, tooltip)));
	}

	private void fillBlankFieldMappingValues(MongoTemplate mongoTemplate) {
		var fieldMappings = mongoTemplate.getCollection(FIELD_MAPPING_COLLECTION);
		fieldMappings.updateMany(
				blankValue(REVISION_FIELDS_FIELD, new Document(SIZE, 0)),
				new Document(SET, new Document(REVISION_FIELDS_FIELD, List.of(DESCRIPTION_VALUE))));
		fieldMappings.updateMany(
				blankValue(CHANGE_PERCENT_FIELD, ""),
				new Document(SET, new Document(CHANGE_PERCENT_FIELD, DEFAULT_CHANGE_PERCENT)));
		fieldMappings.updateMany(
				blankValue(REWRITE_COUNT_FIELD, ""),
				new Document(SET, new Document(REWRITE_COUNT_FIELD, DEFAULT_REWRITE_COUNT)));
	}

	/** Matches documents where the field is present but empty; absent fields are left alone. */
	private Document blankValue(String field, Object emptyValue) {
		return new Document(
				OR,
				Arrays.asList(
						new Document(field, emptyValue), new Document(field, new Document(TYPE, NULL_TYPE))));
	}

	/** The filled-in project values cannot be told apart from user input, so they stay. */
	@RollbackExecution
	public void rollback(MongoTemplate mongoTemplate) {
		updateKpiMaster(mongoTemplate, PREVIOUS_ORDER, PREVIOUS_KPI_DEFINITION);
		updateRevisionFieldsTooltip(mongoTemplate, PREVIOUS_REVISION_FIELDS_TOOLTIP);
	}
}
