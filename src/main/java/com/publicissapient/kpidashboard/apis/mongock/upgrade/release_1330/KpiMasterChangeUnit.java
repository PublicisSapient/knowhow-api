package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1330;

import com.mongodb.client.MongoCollection;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeUnit(id = "yaxis_sonar_label", order = "13303", author = "aksshriv1", systemVersion = "13.3.0")
public class KpiMasterChangeUnit {

    private final MongoTemplate mongoTemplate;

    public KpiMasterChangeUnit(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void execution() {
        updatekpi168();
    }

    public void updatekpi168() {
        MongoCollection<Document> kpiMaster = mongoTemplate.getCollection("kpi_master");
        Document filter = new Document("kpiId", "kpi168");

        Document update = new Document("$set", new Document("yaxisLabel", "Code Quality"));

        // Perform the update
        kpiMaster.updateOne(filter, update);
    }

    @RollbackExecution
    public void rollback() {
        rollbackkpi168();
    }

    public void rollbackkpi168() {
        MongoCollection<Document> kpiMaster = mongoTemplate.getCollection("kpi_master");
        Document filter = new Document("kpiId", "kpi168");
        Document update = new Document("$set", new Document("yaxisLabel", null));

        // Perform the update
        kpiMaster.updateOne(filter, update);
    }
}
