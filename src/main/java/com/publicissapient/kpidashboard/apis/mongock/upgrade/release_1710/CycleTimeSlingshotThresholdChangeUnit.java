package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeUnit(
        id = "cycle_time_slingshot_threshold_insert",
        order = "17108",
        author = "kunkambl",
        systemVersion = "17.1.0")
public class CycleTimeSlingshotThresholdChangeUnit {

    private final MongoTemplate mongoTemplate;

    public CycleTimeSlingshotThresholdChangeUnit(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void execution() {
        Document thresholdDocument =
                new Document("fieldName", "thresholdValueKPI202")
                        .append("fieldLabel", "Target KPI Value")
                        .append("fieldType", "number")
                        .append("section", "Project Level Threshold")
                        .append(
                                "tooltip",
                                new Document(
                                        "definition",
                                        "Target KPI value denotes the bare minimum a project should maintain for a KPI. User should just input the number and the unit like percentage, hours will automatically be considered. If the threshold is empty, then a common target KPI line will be shown"));

        mongoTemplate
                .getCollection("field_mapping_structure")
                .insertOne(thresholdDocument);
    }

    @RollbackExecution
    public void rollback() {
        Document filter = new Document("fieldName", "thresholdValueKPI202");
        mongoTemplate.getCollection("field_mapping_structure").deleteOne(filter);
    }
}
