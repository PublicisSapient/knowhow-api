package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1410;

import com.mongodb.client.MongoCollection;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.Date;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@ChangeUnit(
        id = "addCreatedOnUpdatedOnFields",
        order = "14103",
        author = "gursingh491",
        systemVersion = "14.1.0")
public class UserInfoChangeUnit {

    private final MongoTemplate mongoTemplate;

    private static final String FIELD_CREATED_ON = "createdOn";

    private static final String FIELD_CREATED_BY = "createdBy";

    private static final String FIELD_UPDATED_ON = "updatedOn";

    private static final String  FIELD_UPDATED_BY= "updatedBy";

    private static final String DEFAULT_USER ="SUPERADMIN";

    private static final String COLLECTION_NAME ="user_info";

    @Execution
    public void execution() {
        rollbackUserInfoCollection();
    }

    @RollbackExecution
    public void rollback() {
        log.error("Mongock upgrade failed");
        updateUserInfoCollection();
    }

    private void updateUserInfoCollection() {
        var collection = mongoTemplate.getCollection(COLLECTION_NAME);
        List<Document> docs = collection.find().into(new java.util.ArrayList<>());

        for (Document doc : docs) {
            Document updateFields = new Document();

            Object createdOn = doc.get(FIELD_CREATED_ON);
            Object updatedOn = doc.get(FIELD_UPDATED_ON);
            Object createdBy = doc.get(FIELD_CREATED_BY);
            Object updatedBy = doc.get(FIELD_UPDATED_BY);


            if (createdOn == null) {
                updateFields.put(FIELD_CREATED_ON, Date.from(Instant.now()));
            } else if (createdOn instanceof String) {
                updateFields.put(FIELD_CREATED_ON, parseDate((String) createdOn));
            }

            if (updatedOn == null || updatedOn.toString().isBlank()) {
                updateFields.put(FIELD_UPDATED_ON, Date.from(Instant.now()));
            }

            if (createdBy == null || createdBy.toString().isBlank()) {
                updateFields.put(FIELD_CREATED_BY, DEFAULT_USER);
            }
            if (updatedBy == null || updatedBy.toString().isBlank()) {
                updateFields.put(FIELD_UPDATED_BY, DEFAULT_USER);
            }

            if (!updateFields.isEmpty()) {
                collection.updateOne(
                        new Document("_id", doc.get("_id")),
                        new Document("$set", updateFields)
                );
            }
        }
    }


    private void rollbackUserInfoCollection() {
        MongoCollection<Document> collection = mongoTemplate.getCollection(COLLECTION_NAME);
        collection.find().forEach(doc -> {
            Document updateFields = new Document();
            Document unsetFields = new Document();
            Object createdOn = doc.get(FIELD_CREATED_ON);
            updateFields.put(FIELD_CREATED_ON, ((Date) createdOn).toInstant().toString());
            unsetFields.put(FIELD_UPDATED_ON, "");
            unsetFields.put(FIELD_UPDATED_BY, "");
            unsetFields.put(FIELD_CREATED_BY    , "");

            Document updateOps = new Document();
            if (!updateFields.isEmpty()) updateOps.put("$set", updateFields);
            if (!unsetFields.isEmpty()) updateOps.put("$unset", unsetFields);

            if (!updateOps.isEmpty()) {
                collection.updateOne(new Document("_id", doc.get("_id")), updateOps);
            }
        });
    }

    private Date parseDate(String value) {
        try {
            return Date.from(Instant.parse(value));
        } catch (Exception e) {
            return Date.from(Instant.now());
        }
    }
}
