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

package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1610;

import java.util.Arrays;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

/**
 * Rollback for Priority Mapping Migration
 *
 * @author shunaray
 */
@RequiredArgsConstructor
@ChangeUnit(
		id = "r_priority_mapping_change_unit",
		order = "016102",
		author = "shunaray",
		systemVersion = "16.1.0")
public class PriorityMappingChangeUnit {

	private final MongoTemplate mongoTemplate;
	private static final String FIELD_NAME = "fieldName";
	private static final String DEFINITION = "definition";
	private static final String FIELD_MAPPING_STRUCTURE = "field_mapping_structure";
	private static final String FIELD_MAPPING = "field_mapping";

	@Execution
	public void execution() {
		removePriorityFieldsFromFieldMapping();
		removePriorityFieldsFromFieldMappingStructure();
	}

	/** Remove priority fields from all FieldMapping documents */
	public void removePriorityFieldsFromFieldMapping() {
		MongoCollection<Document> fieldMappingCollection = mongoTemplate.getCollection(FIELD_MAPPING);

		fieldMappingCollection.updateMany(
				new Document(),
				Updates.combine(
						Updates.unset("priorityP1"),
						Updates.unset("priorityP2"),
						Updates.unset("priorityP3"),
						Updates.unset("priorityP4"),
						Updates.unset("priorityP5"),
						Updates.unset("priorityMisc")));
	}

	/** Remove FieldMappingStructure entries for priority fields */
	public void removePriorityFieldsFromFieldMappingStructure() {
		MongoCollection<Document> fieldMappingStructure =
				mongoTemplate.getCollection(FIELD_MAPPING_STRUCTURE);

		fieldMappingStructure.deleteMany(
				new Document(
						FIELD_NAME,
						new Document(
								"$in",
								Arrays.asList(
										"priorityP1",
										"priorityP2",
										"priorityP3",
										"priorityP4",
										"priorityP5",
										"priorityMisc"))));
	}

	@RollbackExecution
	public void rollback() {
		addPriorityFieldsToFieldMapping();
		addPriorityFieldsToFieldMappingStructure();
	}

	/** Update all existing FieldMapping documents with default priority values */
	public void addPriorityFieldsToFieldMapping() {
		MongoCollection<Document> fieldMappingCollection = mongoTemplate.getCollection(FIELD_MAPPING);

		// Update all documents that don't have priorityP1 field
		fieldMappingCollection.updateMany(
				Filters.exists("priorityP1", false),
				Updates.combine(
						Updates.set(
								"priorityP1",
								Arrays.asList("p1", "P1 - Blocker", "blocker", "1", "0", "p0", "Urgent")),
						Updates.set(
								"priorityP2", Arrays.asList("p2", "critical", "P2 - Critical", "2", "High")),
						Updates.set("priorityP3", Arrays.asList("p3", "P3 - Major", "major", "3", "Medium")),
						Updates.set("priorityP4", Arrays.asList("p4", "P4 - Minor", "minor", "4", "Low")),
						Updates.set(
								"priorityP5", Arrays.asList("p5", "P5 - Trivial", "trivial", "5", "Unprioritized")),
						Updates.set(
								"priorityMisc", Arrays.asList("MISC", "misc", "Unprioritized", "unprioritized"))));
	}

	/** Add FieldMappingStructure entries for priority fields */
	public void addPriorityFieldsToFieldMappingStructure() {
		MongoCollection<Document> fieldMappingStructure =
				mongoTemplate.getCollection(FIELD_MAPPING_STRUCTURE);

		// PriorityP1
		Document priorityP1 =
				new Document(FIELD_NAME, "priorityP1")
						.append("fieldLabel", "Priority Mapping for P1")
						.append("fieldType", "chips")
						.append("fieldCategory", "workflow")
						.append("section", "WorkFlow Status Mapping")
						.append("processorCommon", false)
						.append(
								"tooltip",
								new Document(
										DEFINITION,
										"Map Jira priorities to P1 (Critical/Blocker). Issues with these priorities will be categorized as P1 in KnowHOW KPIs."))
						.append("fieldDisplayOrder", 51)
						.append("sectionOrder", 4)
						.append("mandatory", false)
						.append("nodeSpecific", false);

		// PriorityP2
		Document priorityP2 =
				new Document(FIELD_NAME, "priorityP2")
						.append("fieldLabel", "Priority Mapping for P2")
						.append("fieldType", "chips")
						.append("fieldCategory", "workflow")
						.append("section", "WorkFlow Status Mapping")
						.append("processorCommon", false)
						.append(
								"tooltip",
								new Document(
										DEFINITION,
										"Map Jira priorities to P2 (High/Critical). Issues with these priorities will be categorized as P2 in KnowHOW KPIs."))
						.append("fieldDisplayOrder", 52)
						.append("sectionOrder", 4)
						.append("mandatory", false)
						.append("nodeSpecific", false);

		// PriorityP3
		Document priorityP3 =
				new Document(FIELD_NAME, "priorityP3")
						.append("fieldLabel", "Priority Mapping for P3")
						.append("fieldType", "chips")
						.append("fieldCategory", "workflow")
						.append("section", "WorkFlow Status Mapping")
						.append("processorCommon", false)
						.append(
								"tooltip",
								new Document(
										DEFINITION,
										"Map Jira priorities to P3 (Medium/Major). Issues with these priorities will be categorized as P3 in KnowHOW KPIs."))
						.append("fieldDisplayOrder", 53)
						.append("sectionOrder", 4)
						.append("mandatory", false)
						.append("nodeSpecific", false);

		// PriorityP4
		Document priorityP4 =
				new Document(FIELD_NAME, "priorityP4")
						.append("fieldLabel", "Priority Mapping for P4")
						.append("fieldType", "chips")
						.append("fieldCategory", "workflow")
						.append("section", "WorkFlow Status Mapping")
						.append("processorCommon", false)
						.append(
								"tooltip",
								new Document(
										DEFINITION,
										"Map Jira priorities to P4 (Low/Minor). Issues with these priorities will be categorized as P4 in KnowHOW KPIs."))
						.append("fieldDisplayOrder", 54)
						.append("sectionOrder", 4)
						.append("mandatory", false)
						.append("nodeSpecific", false);

		// PriorityP5
		Document priorityP5 =
				new Document(FIELD_NAME, "priorityP5")
						.append("fieldLabel", "Priority Mapping for P5")
						.append("fieldType", "chips")
						.append("fieldCategory", "workflow")
						.append("section", "WorkFlow Status Mapping")
						.append("processorCommon", false)
						.append(
								"tooltip",
								new Document(
										DEFINITION,
										"Map Jira priorities to P5 (Trivial/Lowest). Issues with these priorities will be categorized as P5 in KnowHOW KPIs."))
						.append("fieldDisplayOrder", 55)
						.append("sectionOrder", 4)
						.append("mandatory", false)
						.append("nodeSpecific", false);

		// PriorityMisc
		Document priorityMisc =
				new Document(FIELD_NAME, "priorityMisc")
						.append("fieldLabel", "Priority Mapping for Miscellaneous")
						.append("fieldType", "chips")
						.append("fieldCategory", "workflow")
						.append("section", "WorkFlow Status Mapping")
						.append("processorCommon", false)
						.append(
								"tooltip",
								new Document(
										DEFINITION,
										"Map Jira priorities to Miscellaneous category. Issues with these priorities will be categorized as MISC in KnowHOW KPIs."))
						.append("fieldDisplayOrder", 56)
						.append("sectionOrder", 4)
						.append("mandatory", false)
						.append("nodeSpecific", false);

		fieldMappingStructure.insertMany(
				Arrays.asList(priorityP1, priorityP2, priorityP3, priorityP4, priorityP5, priorityMisc));
	}
}
