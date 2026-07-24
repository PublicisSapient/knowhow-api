package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "e2e_branch_field_type_chips",
		order = "17170",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class E2EBranchFieldTypeChangeUnit {

	private static final String FIELD_MAPPING_STRUCTURE = "field_mapping_structure";
	private static final String FIELD_MAPPING = "field_mapping";
	private static final List<String> BRANCH_FIELDS =
			List.of("e2eTestBranchKPI218", "e2eTestBranchKPI219");

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		updateFieldMappingStructure("chips");
		convertBranchStringToArray();
	}

	private void updateFieldMappingStructure(String fieldType) {
		for (String fieldName : BRANCH_FIELDS) {
			mongoTemplate
					.getCollection(FIELD_MAPPING_STRUCTURE)
					.updateOne(
							new Document("fieldName", fieldName),
							new Document("$set", new Document("fieldType", fieldType)));
		}
	}

	/**
	 * Converts any existing String value of e2eTestBranchKPI218 / e2eTestBranchKPI219
	 * in field_mapping to a single-element array so the List<String> model can deserialize it.
	 * Documents that already hold an array or have no value are left untouched.
	 */
	private void convertBranchStringToArray() {
		for (String fieldName : BRANCH_FIELDS) {
			List<WriteModel<Document>> bulk = new java.util.ArrayList<>();
			try (MongoCursor<Document> cursor =
					mongoTemplate
							.getCollection(FIELD_MAPPING)
							.find(new Document(fieldName, new Document("$type", "string")))
							.cursor()) {
				while (cursor.hasNext()) {
					Document doc = cursor.next();
					String val = doc.getString(fieldName);
					if (val == null || val.isBlank()) continue;
					List<String> asList = Arrays.asList(val.split(","));
					asList.replaceAll(String::trim);
					asList.removeIf(String::isBlank);
					if (asList.isEmpty()) continue;
					bulk.add(new UpdateManyModel<>(
							new Document("_id", doc.get("_id")),
							Updates.set(fieldName, asList)));
				}
			}
			if (!bulk.isEmpty()) {
				mongoTemplate.getCollection(FIELD_MAPPING).bulkWrite(bulk);
			}
		}
	}

	@RollbackExecution
	public void rollback() {
		updateFieldMappingStructure("text");
	}
}
