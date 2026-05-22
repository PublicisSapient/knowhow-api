//package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;
//
//import io.mongock.api.annotations.ChangeUnit;
//import io.mongock.api.annotations.Execution;
//import lombok.RequiredArgsConstructor;
//import org.springframework.data.mongodb.core.MongoTemplate;
//import org.springframework.data.mongodb.core.query.Criteria;
//import org.springframework.data.mongodb.core.query.Query;
//import org.springframework.data.mongodb.core.query.Update;
//
//@ChangeUnit(
//        id = "update_slingshot_flow_efficiency",
//        order = "17107",
//        author = "kunkambl",
//        systemVersion = "17.1.0")
//@RequiredArgsConstructor
//public class FlowEfficiencyUpdate {
//
//    private final MongoTemplate mongoTemplate;
//
//    @Execution
//    public void execute() {
//        Query query =
//                new Query(
//                        Criteria.where("kpiId")
//                                .is("kpi203"));
//        Update update = new Update().set("kpiInfo.$.definition", "ID");
//        mongoTemplate.updateMulti(query, update, "kpi_column_configs");
//    }
//}
