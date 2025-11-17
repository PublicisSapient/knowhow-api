package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1410;

import com.mongodb.client.MongoCollection;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeUnit(
        id = "r_pr_success_rate_chart_type",
        order = "14107",
        author = "gursingh49",
        systemVersion = "14.1.0")
public class PrSuccessRateChangeUnit {
    private final MongoTemplate mongoTemplate;

    private static final String KPI_CHART_TYPE="semi-circle-donut-chart";

    private static final String EXISTING_KPI_CHART_TYPE = "line";

    private static final String KPI_CHART_TYPE_FIELD ="chartType";

    private static final String KPI_SEARCH_FIELD ="kpiId";

    private static final String COLLECTION_NAME ="kpi_master";

    private static final String KPI_ID ="kpi182";

    public PrSuccessRateChangeUnit(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void execution() {
        MongoCollection<Document> kpiMaster = mongoTemplate.getCollection(COLLECTION_NAME);
        // Update documents
        updateDocument(kpiMaster, KPI_ID, EXISTING_KPI_CHART_TYPE);
    }

    private void updateDocument(
            MongoCollection<Document> kpiCategoryMapping, String kpiId, String kpiSource) {
        // Create the filter
        Document filter = new Document(KPI_SEARCH_FIELD, kpiId);
        // Create the update
        Document update = new Document("$set", new Document(KPI_CHART_TYPE_FIELD, kpiSource));
        // Perform the update
        kpiCategoryMapping.updateOne(filter, update);
    }

    @RollbackExecution
    public void rollback() {
        MongoCollection<Document> kpiMaster = mongoTemplate.getCollection(COLLECTION_NAME);
        // Update documents
        updateDocument(kpiMaster, KPI_ID, KPI_CHART_TYPE);
    }
}
