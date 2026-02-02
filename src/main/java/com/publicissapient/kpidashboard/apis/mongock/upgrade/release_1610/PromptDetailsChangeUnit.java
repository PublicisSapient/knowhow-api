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

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1610;

import com.mongodb.client.MongoCollection;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Arrays;

import static com.publicissapient.kpidashboard.common.constant.PromptKeys.KPI_CORRELATION_ANALYSIS_REPORT;
import static com.publicissapient.kpidashboard.common.constant.PromptKeys.KPI_RECOMMENDATION_PROMPT;

@RequiredArgsConstructor
@ChangeUnit(
        id = "prompt_details_change_unit",
        order = "16101",
        author = "kunkambl",
        systemVersion = "16.1.0")
public class PromptDetailsChangeUnit {

    private static final String PROMPT_DETAILS_COLLECTION = "prompt_details";
    public static final String CONTEXT = "context";
    public static final String INSTRUCTIONS = "instructions";
    public static final String INPUT = "input";
    public static final String OUTPUT_FORMAT = "outputFormat";
    public static final String PLACEHOLDER = "placeHolders";
    private final MongoTemplate mongoTemplate;

    @Execution
    public void execution() {
        kpirecommendationUpdate();
        anaylysisReportUpdate();
    }

    private void kpirecommendationUpdate() {
        MongoCollection<Document> collection = mongoTemplate.getCollection(PROMPT_DETAILS_COLLECTION);

        Document filter = new Document("key", "kpi-recommendation");

        Document update = new Document();
        update.append(
                "$set",
                new Document()
                        .append("key", "kpi-recommendation")
                        .append(
                                CONTEXT,
                                "You are an AI data scientist, your task is to analyze the KPI data for a project using the steps provided below:")
                        .append("task", "Analyze the KPI data and generate a comprehensive report.")
                        .append(
                                INSTRUCTIONS,
                                Arrays.asList(
                                        "1. Extract the correlation coefficients and weights from the given context: ANALYSIS_REPORT_PLACEHOLDER.",
                                        "2. Compute the pairwise correlation coefficients and weights for the project data",
                                        "3. Identify significant relationships in the benchmark:-correlation whose value are greater then 0.5 for positive and less than -0.5 for negative",
                                        "4. Calculate the pairwise scores based on the benchmark comparison using the formula: text(pair_score)_(i,j) = (1 - Delta r) cdot w_(benchmark) - (0.5 cdot Delta r text(if) Delta r > 0.)',Normalize between 0 and 1.",
                                        "5. Aggregate all pairwise scores and normalize across 676 pairs.",
                                        "6. Scale the normalized score to a percentage to get the project health.",
                                        "7. Identify the KPIs that are most out of alignment.",
                                        "8. Analyze **interdependencies** between KPIs using the benchmark correlation matrix - If improving one KPI is likely to improve others suggest it in recommendation,  Explain **which KPI(s)** should be prioritized to help multiple others improve indirectly.",
                                        "9. Severity of any recommendation should be according the deviation, if deviation is more keep it high"))
                        .append(
                                INPUT,
                                "Based on the KPIs – sprint_velocity, sprint_capacity_utilization, test_execution_pass_percentage, build_frequency, unit_test_coverage, code_build_time, tech_debt_sonar_maintainability, lead_time_for_change, defect_injection_rate, defect_rejection_rate, defect_removal_efficiency, defect_density, in_sprint_automation_coverage, regression_automation_coverage, sprint_predictability, first_time_pass_rate, happiness_index_rate, value_delivery, sonar_code_quality, release_frequency, issue_count, scope_churn, created_vs_resolved, defect_seepage_rate, commitment_reliability, code_violations – analyze the provided dataset KPI_DATA_BY_PROJECT_PLACEHOLDER, which contains kpi data of latest five sprints marked by date,analyze the performance of each kpi and generate a score for the project performance.")
                        .append(
                                OUTPUT_FORMAT,
                                "Provide a comprehensive report with:\n  1. The project health score as a percentage\n 2. Observations on significant deviations between the project and benchmark values\n 3. Provide any recommendations to address observed discrepancies and how can the project health be improved,\n  do not mention the size of dataset in this section\n 4. Translate deviations into understandable, business-friendly language.\n5.  No explanation or inline comments are required else than the required json format. \n  Format the comprehensive report in parsable json format,\n  the recommendations should be actionable and specific to the KPIs analyzed and specifically tailored\n  for USER_ROLE_PLACEHOLDER role\n  the key\n  for project health should be titled 'project_health_value'\n  and recommendation key should be 'project_recommendations',\n  in the project_recommendations key,\n  provide a list of recommendations with each recommendation should have keys kpi,correlated_kpis as a string and list of string respectively where kpi is main kpi and correlated kpis are the most dependent kpis, recommendation and observation are value as a string and another key severity and value as a string,\n  the severity can be one of the following: low,\n  medium,\n  high,\n  critical {\n\"project_health_value\": 0,\n\"project_recommendations\": [{\n\"kpi\": \"\",\n\"observation\": \"\",\n\"correlated_kpis\": [\"\"],\n\"recommendation\": \"\",\n\"severity\": \"\"\n}],\n\"observations\": []\n}")
                        .append(
                                PLACEHOLDER,
                                Arrays.asList(
                                        "ANALYSIS_REPORT_PLACEHOLDER",
                                        "KPI_DATA_BY_PROJECT_PLACEHOLDER",
                                        "USER_ROLE_PLACEHOLDER")));

        collection.updateOne(filter, update);
    }

    private void anaylysisReportUpdate() {

        Document filter = new Document("key", "kpi-correlation-analysis-report");

        Document update = new Document();
        update.append(
                "$set",
                new Document()
                        .append("key", "kpi-correlation-analysis-report")
                        .append(
                                CONTEXT,
                                "Below is the “analysis‐report” that summarizes the comprehensive pair–by–pair correlation analysis among the twenty-six key‐performance indicator (KPI) columns on our multi–sprint project dataset. \nThe KPI correlation is measured using a Pearson–based procedure and for each computed correlation a “weight” (an accuracy or reliability measure on a scale from 0 to 100uj, with values nearer 1 indicating very high confidence given the large number of sprints in the dataset) has been derived. (In our analysis no KPI was excluded and all 26×26 combinations – including the “self–correlations” – are shown.)' ")
                        .append(
                                "task",
                                "Analyze and interpret the correlation matrix of KPIs to identify strong relationships and potential areas of concern in project performance.")
                        .append(
                                INSTRUCTIONS,
                                Arrays.asList(
                                        "For reference, the twenty-six KPIs are:\n1. sprint_velocity\n2. sprint_capacity_utilization\n3. test_execution_pass_percentage\n4. build_frequency\n5. unit_test_coverage\n6. code_build_time\n7. tech_debt_sonar_maintainability\n8. lead_time_for_change\n9. defect_injection_rate\n10. defect_rejection_rate\n11. defect_removal_efficiency\n12. defect_density\n13. in_sprint_automation_coverage\n14. regression_automation_coverage\n15. sprint_predictability\n16. first_time_pass_rate\n17. happiness_index_rate\n18. value_delivery\n19. sonar_code_quality\n20. release_frequency\n21. issue_count\n22. scope_churn\n23. created_vs_resolved\n24. defect_seepage_rate\n25. commitment_reliability\n26. code_violations",
                                        "Note that the “weight” is our internal accuracy metric – obtained via a robust boot–strapping/confidence–interval procedure – and it tells us how “trustworthy” the estimated correlation is. In what follows every unique pair is shown (remember that A vs. B is the same as B vs. A, but we list each cell of the 26×26 matrix for completeness). \n" +
                                        "THE CORRELATION MATRIX (Each cell shows: r (coefficient) | weight), For our (illustrative) analysis, the computed Pearson correlation coefficients and their corresponding weights are as follows:,",
                                        "Below is the final 26‐by‐26 “correlation/weight” matrix for the 26 KPIs. (Each cell is reported as “r (w)” meaning that the Pearson correlation coefficient r was found and its “weight” (an effective accuracy percentage) is indicated in parentheses. All 26 KPI pairs were included – none were omitted.), ",
                                        "1: 1.00(100%) 0.62(92%) 0.15(85%) -0.05(60%) 0.70(96%) -0.40(75%) 0.32(88%) -0.12(70%) 0.25(85%) 0.55(89%) 0.20(81%) -0.23(76%) 0.40(91%) 0.37(88%) -0.18(73%) 0.09(68%) 0.50(86%) 0.67(94%) 0.44(85%) -0.15(67%) -0.30(74%) -0.22(72%) 0.12(69%) -0.10(60%) 0.60(92%) -0.27(70%)",
                                        "2: 0.62(92%) 1.00(100%) 0.30(88%) -0.08(68%) 0.63(91%) -0.33(78%) 0.35(87%) -0.09(70%) 0.18(83%) 0.48(86%) 0.26(82%) -0.20(75%) 0.39(89%) 0.41(87%) -0.14(72%) 0.12(71%) 0.55(87%) 0.60(90%) 0.50(83%) -0.18(69%) -0.29(75%) -0.24(70%) 0.15(72%) -0.11(63%) 0.57(91%) -0.29(72%)",
                                        "3: 0.15(85%) 0.30(88%) 1.00(100%) 0.22(75%) 0.28(82%) -0.05(67%) 0.10(76%) 0.14(70%) 0.40(86%) 0.42(85%) 0.33(80%) -0.18(74%) 0.21(78%) 0.25(79%) 0.02(60%) 0.08(69%) 0.20(73%) 0.32(81%) 0.24(77%) 0.09(65%) -0.07(66%) -0.10(62%) 0.05(65%) -0.04(60%) 0.29(80%) -0.12(64%)",
                                        "4: -0.05(60%) -0.08(68%) 0.22(75%) 1.00(100%) 0.12(69%) 0.56(90%) -0.30(74%) 0.09(68%) 0.06(70%) 0.03(66%) -0.02(65%) 0.01(63%) -0.10(67%) -0.05(64%) 0.12(69%) -0.07(66%) -0.03(62%) -0.12(68%) -0.09(66%) 0.02(60%) 0.25(73%) 0.30(78%) -0.01(62%) 0.05(61%) 0.00(65%) 0.03(63%)",
                                        "5: 0.70(96%) 0.63(91%) 0.28(82%) 0.12(69%) 1.00(100%) -0.20(71%) 0.36(89%) -0.05(65%) 0.22(80%) 0.60(88%) 0.29(82%) -0.15(72%) 0.33(84%) 0.35(83%) -0.12(70%) 0.17(75%) 0.57(89%) 0.62(91%) 0.53(85%) -0.10(64%) -0.20(70%) -0.18(69%) 0.10(68%) -0.08(63%) 0.65(92%) -0.25(71%)",
                                        "6: -0.40(75%) -0.33(78%) -0.05(67%) 0.56(90%) -0.20(71%) 1.00(100%) -0.48(83%) 0.11(70%) -0.09(69%) -0.10(67%) -0.20(70%) 0.18(71%) -0.12(69%) -0.15(67%) 0.08(66%) -0.09(68%) -0.05(63%) -0.15(67%) -0.11(66%) 0.22(74%) 0.30(79%) 0.28(77%) -0.02(64%) 0.06(63%) -0.10(68%) 0.08(66%)",
                                        "7: 0.32(88%) 0.35(87%) 0.10(76%) -0.30(74%) 0.36(89%) -0.48(83%) 1.00(100%) -0.15(70%) 0.20(82%) 0.27(83%) 0.22(80%) -0.12(72%) 0.38(85%) 0.40(84%) -0.22(70%) 0.16(73%) 0.42(86%) 0.50(88%) 0.37(81%) -0.08(64%) -0.18(71%) -0.14(69%) 0.12(68%) -0.06(64%) 0.45(89%) -0.18(68%)",
                                        "8: -0.12(70%) -0.09(70%) 0.14(70%) 0.09(68%) -0.05(65%) 0.11(70%) -0.15(70%) 1.00(100%) -0.06(69%) 0.03(68%) 0.05(70%) -0.02(66%) -0.04(68%) -0.07(65%) 0.10(68%) -0.05(65%) 0.00(63%) -0.08(66%) -0.07(66%) 0.05(63%) 0.10(68%) 0.12(70%) -0.02(63%) 0.04(62%) -0.03(65%) 0.06(64%)",
                                        "9: 0.25(85%) 0.18(83%) 0.40(86%) 0.06(70%) 0.22(80%) -0.09(69%) 0.20(82%) -0.06(69%) 1.00(100%) 0.55(88%) 0.45(85%) -0.20(74%) 0.28(82%) 0.30(81%) -0.10(70%) 0.12(72%) 0.42(83%) 0.50(86%) 0.40(80%) -0.12(67%) -0.28(73%) -0.20(72%) 0.18(70%) -0.09(66%) 0.47(86%) -0.22(70%)",
                                        "10: -0.30(78%) -0.25(72%) 0.45(85%) 0.05(60%) -0.40(75%) 0.30(75%) 0.25(70%) -0.10(65%) 0.80(90%) 1.00(100%) 0.35(75%) 0.20(70%) 0.70(96%) 0.65(92%) 0.55(90%) 0.30(75%) 0.25(70%) 0.20(70%) 0.75(95%) 0.40(80%) 0.35(75%) 0.20(70%) 0.15(68%) 0.10(65%) 0.70(96%) -0.35(74%)",
                                        "11: 0.55(90%) 0.50(85%) 0.20(70%) 0.10(65%) 0.65(94%) -0.50(80%) -0.55(85%) 0.30(75%) 0.40(80%) 0.35(75%) 1.00(100%) 0.15(68%) 0.60(90%) 0.55(88%) 0.45(88%) 0.50(90%) 0.40(85%) 0.35(85%) 0.70(94%) 0.45(85%) 0.30(75%) 0.20(70%) 0.15(68%) 0.10(65%) 0.75(95%) -0.40(75%)",
                                        "12: 0.05(60%) 0.00(50%) 0.55(90%) 0.00(50%) 0.10(65%) 0.40(80%) 0.50(88%) 0.20(70%) 0.50(88%) 0.20(70%) 0.15(68%) 1.00(100%) 0.40(80%) 0.35(82%) 0.30(75%) 0.15(70%) 0.10(65%) 0.05(60%) 0.55(90%) 0.20(70%) 0.25(75%) 0.10(65%) 0.05(60%) 0.00(55%) 0.35(82%) 0.10(65%)",
                                        "13: 0.65(94%) 0.60(90%) 0.30(80%) 0.10(65%) 0.70(96%) -0.30(70%) -0.45(80%) 0.55(88%) 0.55(88%) 0.70(96%) 0.60(90%) 0.40(80%) 1.00(100%) 0.95(97%) 0.80(95%) 0.85(96%) 0.75(94%) 0.65(92%) 0.90(98%) 0.55(88%) 0.45(88%) 0.35(82%) 0.25(75%) 0.15(68%) 0.95(97%) -0.50(80%)",
                                        "14: 0.60(92%) 0.55(88%) 0.35(82%) 0.05(60%) 0.68(95%) -0.25(68%) -0.40(75%) 0.50(85%) 0.50(85%) 0.65(92%) 0.55(88%) 0.35(82%) 0.95(97%) 1.00(100%) 0.82(93%) 0.87(94%) 0.77(93%) 0.67(92%) 0.90(98%) 0.60(90%) 0.50(88%) 0.40(85%) 0.30(80%) 0.20(70%) 0.90(98%) -0.45(80%)",
                                        "15: 0.45(88%) 0.40(85%) 0.40(85%) 0.15(70%) 0.50(90%) 0.10(65%) 0.05(60%) 0.25(75%) 0.45(85%) 0.55(90%) 0.45(88%) 0.30(75%) 0.80(95%) 0.82(93%) 1.00(100%) 0.80(95%) 0.70(92%) 0.60(90%) 0.85(96%) 0.50(88%) 0.45(85%) 0.35(82%) 0.25(75%) 0.15(68%) 0.80(95%) -0.35(74%)",
                                        "16: 0.50(90%) 0.45(86%) 0.15(70%) 0.10(65%) 0.55(90%) -0.20(70%) -0.30(70%) 0.30(75%) 0.20(70%) 0.30(75%) 0.50(90%) 0.15(70%) 0.85(96%) 0.87(94%) 0.80(95%) 1.00(100%) 0.90(96%) 0.80(95%) 0.85(96%) 0.55(90%) 0.40(85%) 0.30(75%) 0.20(70%) 0.10(65%) 0.88(96%) -0.40(75%)",
                                        "17: 0.40(85%) 0.35(82%) 0.10(65%) 0.05(60%) 0.45(88%) -0.30(70%) -0.40(75%) 0.20(70%) 0.15(68%) 0.25(70%) 0.40(85%) 0.10(65%) 0.75(94%) 0.77(93%) 0.70(92%) 0.90(96%) 1.00(100%) 0.80(95%) 0.85(96%) 0.50(88%) 0.35(82%) 0.25(75%) 0.15(68%) 0.05(60%) 0.80(95%) -0.35(74%)",
                                        "18: 0.30(80%) 0.20(75%) 0.05(60%) 0.00(55%) 0.35(85%) -0.40(75%) -0.50(80%) 0.15(68%) 0.10(65%) 0.20(70%) 0.35(85%) 0.05(60%) 0.65(92%) 0.67(92%) 0.60(90%) 0.80(95%) 0.80(95%) 1.00(100%) 0.70(94%) 0.40(80%) 0.30(75%) 0.20(70%) 0.10(65%) 0.00(55%) 0.75(95%) -0.30(75%)",
                                        "19: 0.68(95%) 0.65(92%) 0.50(88%) 0.20(70%) 0.75(95%) 0.35(75%) 0.40(80%) 0.60(90%) 0.65(92%) 0.75(95%) 0.70(94%) 0.55(90%) 0.90(98%) 0.90(98%) 0.85(96%) 0.85(96%) 0.85(96%) 0.70(94%) 1.00(100%) 0.65(92%) 0.50(88%) 0.40(85%) 0.30(80%) 0.20(70%) 0.95(97%) -0.50(80%)",
                                        "20: 0.25(75%) 0.30(80%) 0.20(70%) 0.10(65%) 0.40(80%) -0.50(80%) -0.55(85%) 0.10(65%) 0.30(80%) 0.40(80%) 0.45(85%) 0.20(70%) 0.55(88%) 0.60(90%) 0.50(88%) 0.55(90%) 0.50(88%) 0.40(80%) 0.65(92%) 1.00(100%) 0.75(92%) 0.60(90%) 0.45(85%) 0.30(80%) 0.70(96%) -0.40(75%)",
                                        "21: 0.10(65%) 0.15(68%) 0.25(75%) 0.05(60%) 0.30(75%) 0.10(65%) 0.00(55%) 0.05(60%) 0.25(75%) 0.35(75%) 0.30(75%) 0.25(75%) 0.45(88%) 0.50(88%) 0.45(85%) 0.40(85%) 0.35(82%) 0.30(75%) 0.50(88%) 0.75(92%) 1.00(100%) 0.80(92%) 0.65(88%) 0.50(88%) 0.60(90%) -0.35(74%)",
                                        "22: -0.20(70%) -0.10(65%) 0.10(65%) 0.00(50%) 0.20(70%) -0.05(60%) 0.05(60%) 0.00(55%) 0.15(68%) 0.20(70%) 0.20(70%) 0.10(65%) 0.35(82%) 0.40(85%) 0.35(82%) 0.30(75%) 0.25(75%) 0.20(70%) 0.40(85%) 0.60(90%) 0.80(92%) 1.00(100%) 0.85(92%) 0.70(90%) 0.40(85%) -0.20(70%)",
                                        "23: 0.05(60%) 0.00(60%) 0.05(60%) -0.05(60%) 0.10(65%) 0.20(70%) 0.15(65%) -0.05(60%) 0.10(65%) 0.15(68%) 0.15(68%) 0.05(60%) 0.25(75%) 0.30(80%) 0.25(75%) 0.20(70%) 0.15(68%) 0.10(65%) 0.30(80%) 0.45(85%) 0.65(88%) 0.85(92%) 1.00(100%) 0.75(92%) 0.35(82%) 0.10(65%)",
                                        "24: -0.15(68%) -0.20(70%) 0.00(55%) 0.10(65%) 0.00(55%) 0.30(75%) 0.20(70%) 0.10(65%) 0.05(60%) 0.10(65%) 0.10(65%) 0.00(55%) 0.15(68%) 0.20(70%) 0.15(68%) 0.10(65%) 0.05(60%) 0.00(55%) 0.20(70%) 0.30(80%) 0.50(88%) 0.70(90%) 0.75(92%) 1.00(100%) 0.55(88%) -0.10(65%)",
                                        "25: 0.70(96%) 0.60(90%) 0.35(82%) 0.15(70%) 0.80(97%) -0.60(85%) -0.65(88%) 0.55(88%) 0.50(88%) 0.70(96%) 0.75(95%) 0.35(82%) 0.95(97%) 0.90(98%) 0.80(95%) 0.88(96%) 0.80(95%) 0.75(95%) 0.95(97%) 0.70(96%) 0.60(90%) 0.40(85%) 0.35(82%) 0.55(88%) 1.00(100%) -0.45(80%)",
                                        "26: -0.25(72%) -0.30(74%) 0.10(65%) 0.05(60%) -0.45(80%) 0.45(80%) 0.50(80%) -0.10(65%) 0.15(68%) -0.35(74%) -0.40(75%) 0.10(65%) -0.50(80%) -0.45(80%) -0.35(74%) -0.40(75%) -0.35(74%) -0.30(75%) -0.50(80%) -0.40(75%) -0.35(74%) -0.20(70%) 0.10(65%) -0.10(65%) -0.45(80%) 1.00(100%)")));

        mongoTemplate.getCollection(PROMPT_DETAILS_COLLECTION).updateOne(filter, update);
    }

    @RollbackExecution
    public void rollback() {
        MongoCollection<Document> collection = mongoTemplate.getCollection(PROMPT_DETAILS_COLLECTION);
        collection.deleteMany(
                new Document(
                        "key",
                        new Document(
                                "$in", Arrays.asList(KPI_CORRELATION_ANALYSIS_REPORT, KPI_RECOMMENDATION_PROMPT))));

        Document kpiCorrelationAnalysis =
                new Document()
                        .append("key", KPI_CORRELATION_ANALYSIS_REPORT)
                        .append(
                                CONTEXT,
                                "Below is the “analysis‐report” that summarizes the comprehensive pair–by–pair correlation analysis among the twenty-six key‐performance indicator (KPI) columns on our multi–sprint project dataset. In this example we assume that all KPI values were measured using a Pearson–based procedure and that for each computed correlation a “weight” (an accuracy or reliability measure on a scale from 0 to 1, with values nearer 1 indicating very high confidence given the large number of sprints in the dataset) has been derived. (In our analysis no KPI was excluded and all 26×26 combinations – including the “self–correlations” – are shown.)\n")
                        .append(
                                "task",
                                "Analyze and interpret the correlation matrix of KPIs to identify strong relationships and potential areas of concern in project performance.")
                        .append(
                                INSTRUCTIONS,
                                Arrays.asList(
                                        "For reference, the twenty-six KPIs are:\n1. sprint_velocity\n2. sprint_capacity_utilization\n3. test_execution_pass_percentage\n4. build_frequency\n5. unit_test_coverage\n6. code_build_time\n7. tech_debt_sonar_maintainability\n8. lead_time_for_change\n9. defect_injection_rate\n10. defect_rejection_rate\n11. defect_removal_efficiency\n12. defect_density\n13. in_sprint_automation_coverage\n14. regression_automation_coverage\n15. sprint_predictability\n16. first_time_pass_rate\n17. happiness_index_rate\n18. value_delivery\n19. sonar_code_quality\n20. release_frequency\n21. issue_count\n22. scope_churn\n23. created_vs_resolved\n24. defect_seepage_rate\n25. commitment_reliability\n26. code_violations",
                                        "Note that the “weight” is our internal accuracy metric – obtained via a robust boot–strapping/confidence–interval procedure – and it tells us how “trustworthy” the estimated correlation is. In what follows every unique pair is shown (remember that A vs. B is the same as B vs. A, but we list each cell of the 26×26 matrix for completeness). Should you ever have a smaller dataset (say, only five sprints for one project), the same reporting format may be applied to “diagnose” overall project health by comparing the observed pairwise correlations and their reliability weights with these benchmark numbers.",
                                        "THE CORRELATION MATRIX (Each cell shows: r (coefficient) | weight)",
                                        "For our (illustrative) analysis, the computed Pearson correlation coefficients and their corresponding weights are as follows:",
                                        "Below is the final 26‐by‐26 “correlation/weight” matrix for the 26 KPIs. (Each cell is reported as “r (w)” meaning that the Pearson correlation coefficient r was found and its “weight” (an effective accuracy percentage) is indicated in parentheses. All 26 KPI pairs were included – none were omitted.)",
                                        "1. 1.00 (100%) 0.65 (92%) 0.72 (93%) 0.50 (90%) 0.40 (88%) -0.30 (85%) 0.55 (90%) 0.33 (87%) 0.28 (85%) -0.15 (80%) 0.10 (78%) 0.05 (77%) 0.60 (91%) 0.70 (92%) 0.50 (90%) 0.45 (89%) 0.55 (90%) 0.40 (88%) 0.75 (94%) 0.20 (80%) 0.10 (78%) -0.05 (77%) 0.35 (87%) 0.30 (85%) 0.80 (95%) -0.20 (80%)",
                                        "2 .0.65 (92%) 1.00 (100%) 0.68 (92%) 0.52 (90%) 0.42 (88%) -0.25 (85%) 0.50 (90%) 0.30 (87%) 0.26 (85%) -0.10 (80%) 0.15 (78%) 0.08 (77%) 0.58 (91%) 0.66 (92%) 0.48 (90%) 0.43 (89%) 0.50 (90%) 0.38 (88%) 0.70 (94%) 0.25 (80%) 0.12 (78%) -0.02 (77%) 0.40 (87%) 0.33 (85%) 0.75 (95%) -0.18 (80%)",
                                        "3. 0.72 (93%) 0.68 (92%) 1.00 (100%) 0.55 (90%) 0.45 (88%) -0.20 (85%) 0.60 (90%) 0.38 (87%) 0.32 (85%) -0.12 (80%) 0.18 (78%) 0.10 (77%) 0.63 (91%) 0.72 (92%) 0.53 (90%) 0.47 (89%) 0.57 (90%) 0.42 (88%) 0.78 (94%) 0.28 (80%) 0.14 (78%) -0.03 (77%) 0.38 (87%) 0.33 (85%) 0.82 (95%) -0.22 (80%)",
                                        "4. 0.50 (90%) 0.52 (90%) 0.55 (90%) 1.00 (100%) 0.50 (88%) -0.10 (85%) 0.45 (90%) 0.40 (87%) 0.30 (85%) -0.05 (80%) 0.20 (78%) 0.15 (77%) 0.55 (91%) 0.60 (92%) 0.50 (90%) 0.40 (89%) 0.50 (90%) 0.35 (88%) 0.65 (94%) 0.22 (80%) 0.18 (78%) 0.00 (77%) 0.30 (87%) 0.25 (85%) 0.68 (95%) -0.10 (80%)",
                                        "5. 0.40 (88%) 0.42 (88%) 0.45 (88%) 0.50 (88%) 1.00 (100%) -0.20 (85%) 0.40 (90%) 0.35 (87%) 0.30 (85%) -0.05 (80%) 0.25 (78%) 0.12 (77%) 0.50 (91%) 0.58 (92%) 0.45 (90%) 0.42 (89%) 0.48 (90%) 0.34 (88%) 0.60 (94%) 0.18 (80%) 0.15 (78%) -0.08 (77%) 0.32 (87%) 0.28 (85%) 0.70 (95%) -0.12 (80%)",
                                        "6. -0.30 (85%) -0.25 (85%) -0.20 (85%) -0.10 (85%) -0.20 (85%) 1.00 (100%) -0.15 (85%) -0.10 (87%) -0.08 (85%) 0.05 (80%) -0.12 (78%) -0.15 (77%) -0.20 (91%) -0.18 (92%) -0.25 (90%) -0.22 (89%) -0.28 (90%) -0.30 (88%) -0.35 (94%) -0.10 (80%) -0.15 (78%) -0.05 (77%) -0.20 (87%) -0.25 (85%) -0.40 (95%) 0.10 (80%)",
                                        "7. 0.55 (90%) 0.50 (90%) 0.60 (90%) 0.45 (90%) 0.40 (90%) -0.15 (85%) 1.00 (100%) 0.38 (87%) 0.42 (85%) -0.10 (80%) 0.30 (78%) 0.20 (77%) 0.65 (91%) 0.70 (92%) 0.55 (90%) 0.50 (89%) 0.60 (90%) 0.45 (88%) 0.75 (94%) 0.28 (80%) 0.20 (78%) 0.05 (77%) 0.40 (87%) 0.35 (85%) 0.78 (95%) -0.15 (80%)",
                                        "8. 0.33 (87%) 0.30 (87%) 0.38 (87%) 0.40 (87%) 0.35 (87%) -0.10 (87%) 0.38 (87%) 1.00 (100%) 0.28 (85%) -0.05 (80%) 0.12 (78%) 0.10 (77%) 0.40 (91%) 0.45 (92%) 0.35 (90%) 0.30 (89%) 0.38 (90%) 0.28 (88%) 0.55 (94%) 0.15 (80%) 0.10 (78%) 0.00 (77%) 0.25 (87%) 0.20 (85%) 0.50 (95%) -0.05 (80%)",
                                        "9. 0.28 (85%) 0.26 (85%) 0.32 (85%) 0.30 (85%) 0.30 (85%) -0.08 (85%) 0.42 (85%) 0.28 (85%) 1.00 (100%) -0.05 (80%) 0.20 (78%) 0.10 (77%) 0.35 (91%) 0.40 (92%) 0.30 (90%) 0.25 (89%) 0.32 (90%) 0.28 (88%) 0.50 (94%) 0.12 (80%) 0.10 (78%) 0.00 (77%) 0.28 (87%) 0.25 (85%) 0.55 (95%) -0.05 (80%)",
                                        "10. -0.15 (80%) -0.10 (80%) -0.12 (80%) -0.05 (80%) -0.05 (80%) 0.05 (80%) -0.10 (80%) -0.05 (80%) -0.05 (80%) 1.00 (100%) -0.08 (78%) -0.10 (77%) -0.12 (91%) -0.15 (92%) -0.10 (90%) -0.08 (89%) -0.10 (90%) -0.12 (88%) -0.18 (94%) 0.05 (80%) -0.10 (78%) 0.00 (77%) -0.12 (87%) -0.15 (85%) -0.20 (95%) 0.05 (80%)",
                                        "11. 0.10 (78%) 0.15 (78%) 0.18 (78%) 0.20 (78%) 0.25 (78%) -0.12 (78%) 0.30 (78%) 0.12 (78%) 0.20 (78%) -0.08 (78%) 1.00 (100%) 0.05 (77%) 0.28 (91%) 0.30 (92%) 0.25 (90%) 0.20 (89%) 0.30 (90%) 0.25 (88%) 0.45 (94%) 0.10 (80%) 0.08 (78%) 0.00 (77%) 0.22 (87%) 0.20 (85%) 0.35 (95%) -0.05 (80%)",
                                        "12. 0.05 (77%) 0.08 (77%) 0.10 (77%) 0.15 (77%) 0.12 (77%) -0.15 (77%) 0.20 (77%) 0.10 (77%) 0.10 (77%) -0.10 (77%) 0.05 (77%) 1.00 (100%) 0.33 (91%) 0.38 (92%) 0.30 (90%) 0.28 (89%) 0.32 (90%) 0.22 (88%) 0.40 (94%) 0.08 (80%) 0.05 (78%) 0.00 (77%) 0.18 (87%) 0.15 (85%) 0.30 (95%) -0.08 (80%)",
                                        "13. 0.60 (91%) 0.58 (91%) 0.63 (91%) 0.55 (91%) 0.50 (91%) -0.20 (91%) 0.65 (91%) 0.40 (91%) 0.35 (91%) -0.12 (91%) 0.28 (91%) 0.33 (91%) 1.00 (100%) 0.80 (92%) 0.58 (90%) 0.55 (89%) 0.60 (90%) 0.50 (88%) 0.70 (94%) 0.30 (80%) 0.25 (78%) 0.10 (77%) 0.45 (87%) 0.40 (85%) 0.75 (95%) -0.18 (80%)",
                                        "14. 0.70 (92%) 0.66 (92%) 0.72 (92%) 0.60 (92%) 0.58 (92%) -0.18 (92%) 0.70 (92%) 0.45 (92%) 0.40 (92%) -0.15 (92%) 0.35 (92%) 0.38 (92%) 0.80 (92%) 1.00 (100%) 0.62 (90%) 0.58 (89%) 0.65 (90%) 0.50 (88%) 0.78 (94%) 0.32 (80%) 0.28 (78%) 0.12 (77%) 0.50 (87%) 0.45 (85%) 0.82 (95%) -0.20 (80%)",
                                        "15. 0.50 (90%) 0.48 (90%) 0.53 (90%) 0.50 (90%) 0.45 (90%) -0.25 (90%) 0.55 (90%) 0.35 (90%) 0.30 (90%) -0.10 (90%) 0.25 (90%) 0.30 (90%) 0.58 (90%) 0.62 (90%) 1.00 (100%) 0.50 (89%) 0.55 (90%) 0.42 (88%) 0.68 (94%) 0.20 (80%) 0.15 (78%) 0.05 (77%) 0.35 (87%) 0.30 (85%) 0.68 (95%) -0.15 (80%)",
                                        "16. 0.45 (89%) 0.43 (89%) 0.47 (89%) 0.40 (89%) 0.42 (89%) -0.22 (89%) 0.50 (89%) 0.30 (89%) 0.25 (89%) -0.08 (89%) 0.20 (89%) 0.28 (89%) 0.55 (89%) 0.58 (89%) 0.50 (89%) 1.00 (100%) 0.45 (90%) 0.35 (88%) 0.60 (94%) 0.18 (80%) 0.12 (78%) 0.05 (77%) 0.30 (87%) 0.25 (85%) 0.70 (95%) -0.10 (80%)",
                                        "17. 0.55 (90%) 0.50 (90%) 0.57 (90%) 0.50 (90%) 0.48 (90%) -0.28 (90%) 0.60 (90%) 0.38 (90%) 0.32 (90%) -0.10 (90%) 0.30 (90%) 0.32 (90%) 0.60 (90%) 0.65 (90%) 0.55 (90%) 0.45 (90%) 1.00 (100%) 0.40 (88%) 0.70 (94%) 0.25 (80%) 0.20 (78%) 0.10 (77%) 0.35 (87%) 0.30 (85%) 0.75 (95%) -0.15 (80%)",
                                        "18. 0.40 (88%) 0.38 (88%) 0.42 (88%) 0.35 (88%) 0.34 (88%) -0.30 (88%) 0.45 (88%) 0.28 (88%) 0.28 (88%) -0.12 (88%) 0.25 (88%) 0.22 (88%) 0.50 (88%) 0.50 (88%) 0.42 (88%) 0.35 (88%) 0.40 (88%) 1.00 (100%) 0.60 (94%) 0.15 (80%) 0.12 (78%) 0.05 (77%) 0.30 (87%) 0.28 (85%) 0.65 (95%) -0.10 (80%)",
                                        "19. 0.75 (94%) 0.70 (94%) 0.78 (94%) 0.65 (94%) 0.60 (94%) -0.35 (94%) 0.75 (94%) 0.55 (94%) 0.50 (94%) -0.18 (94%) 0.45 (94%) 0.40 (94%) 0.70 (94%) 0.78 (94%) 0.68 (94%) 0.60 (94%) 0.70 (94%) 0.60 (94%) 1.00 (100%) 0.30 (80%) 0.25 (78%) 0.10 (77%) 0.50 (87%) 0.45 (85%) 0.80 (95%) -0.20 (80%)",
                                        "20. 0.20 (80%) 0.25 (80%) 0.28 (80%) 0.22 (80%) 0.18 (80%) -0.10 (80%) 0.28 (80%) 0.15 (80%) 0.12 (80%) 0.05 (80%) 0.10 (80%) 0.08 (80%) 0.30 (80%) 0.32 (80%) 0.20 (80%) 0.18 (80%) 0.25 (80%) 0.15 (80%) 0.30 (80%) 1.00 (100%) 0.15 (78%) 0.05 (77%) 0.20 (87%) 0.18 (85%) 0.40 (95%) -0.05 (80%)",
                                        "21. 0.10 (78%) 0.12 (78%) 0.14 (78%) 0.18 (78%) 0.15 (78%) -0.15 (78%) 0.20 (78%) 0.10 (78%) 0.10 (78%) -0.10 (78%) 0.08 (78%) 0.05 (78%) 0.25 (78%) 0.28 (78%) 0.15 (78%) 0.12 (78%) 0.20 (78%) 0.12 (78%) 0.25 (78%) 0.15 (78%) 1.00 (100%) 0.05 (77%) 0.18 (87%) 0.15 (85%) 0.30 (95%) -0.08 (80%)",
                                        "22. -0.05 (77%) -0.02 (77%) -0.03 (77%) 0.00 (77%) -0.08 (77%) -0.05 (77%) 0.05 (77%) 0.00 (77%) 0.00 (77%) 0.00 (77%) 0.00 (77%) 0.00 (77%) 0.10 (77%) 0.12 (77%) 0.05 (77%) 0.05 (77%) 0.10 (77%) 0.05 (77%) 0.10 (77%) 0.05 (77%) 0.05 (77%) 1.00 (100%) 0.10 (87%) 0.08 (85%) 0.15 (95%) 0.00 (80%)",
                                        "23. 0.35 (87%) 0.40 (87%) 0.38 (87%) 0.30 (87%) 0.32 (87%) -0.20 (87%) 0.40 (87%) 0.25 (87%) 0.28 (87%) -0.12 (87%) 0.22 (87%) 0.18 (87%) 0.45 (87%) 0.50 (87%) 0.35 (87%) 0.30 (87%) 0.35 (87%) 0.30 (87%) 0.50 (87%) 0.20 (87%) 0.18 (87%) 0.10 (87%) 1.00 (100%) 0.80 (85%) 0.60 (95%) -0.10 (80%)",
                                        "24. 0.30 (85%) 0.33 (85%) 0.33 (85%) 0.25 (85%) 0.28 (85%) -0.25 (85%) 0.35 (85%) 0.20 (85%) 0.25 (85%) -0.15 (85%) 0.20 (85%) 0.15 (85%) 0.40 (85%) 0.45 (85%) 0.30 (85%) 0.25 (85%) 0.30 (85%) 0.28 (85%) 0.45 (85%) 0.18 (85%) 0.15 (85%) 0.08 (85%) 0.80 (85%) 1.00 (100%) 0.68 (95%) -0.12 (80%)",
                                        "25. 0.80 (95%) 0.75 (95%) 0.82 (95%) 0.68 (95%) 0.70 (95%) -0.40 (95%) 0.78 (95%) 0.50 (95%) 0.55 (95%) -0.20 (95%) 0.35 (95%) 0.30 (95%) 0.75 (95%) 0.82 (95%) 0.68 (95%) 0.70 (95%) 0.75 (95%) 0.65 (95%) 0.80 (95%) 0.40 (95%) 0.30 (95%) 0.15 (95%) 0.60 (95%) 0.68 (95%) 1.00 (100%) -0.25 (80%)",
                                        "26. -0.20 (80%) -0.18 (80%) -0.22 (80%) -0.10 (80%) -0.12 (80%) 0.10 (80%) -0.15 (80%) -0.05 (80%) -0.05 (80%) 0.05 (80%) -0.05 (80%) -0.08 (80%) -0.18 (80%) -0.20 (80%) -0.15 (80%) -0.10 (80%) -0.15 (80%) -0.10 (80%) -0.20 (80%) -0.05 (80%) -0.08 (80%) 0.00 (80%) -0.10 (80%) -0.12 (80%) -0.25 (80%) 1.00 (100%)"));

        Document kpiRecommendationPrompt =
                new Document()
                        .append("key", KPI_RECOMMENDATION_PROMPT)
                        .append(
                                CONTEXT,
                                "You are an AI data scientist, your task is to analyze the KPI data for a project using the steps provided below:")
                        .append("task", "Analyze the KPI data and generate a comprehensive report.")
                        .append(
                                INSTRUCTIONS,
                                Arrays.asList(
                                        "1. Extract the correlation coefficients and weights for the benchmark matrix from analysis report generated through large kpi dataset: ANALYSIS_REPORT_PLACEHOLDER.",
                                        "2. Compute the pairwise correlation coefficients and weights for the project data",
                                        "3. Identify significant relationships in the benchmark:-correlation whose value are greater then 0.5 for positive and less than -0.5 for negative",
                                        "4. Calculate the pairwise scores based on the benchmark comparison using the formula: text(pair_score)_(i,j) = (1 - Delta r) cdot w_(benchmark) - (0.5 cdot Delta r text(if) Delta r > 0.)',Normalize between 0 and 1.",
                                        "5. Aggregate all pairwise scores and normalize across 676 pairs.",
                                        "6. Scale the normalized score to a percentage to get the project health.",
                                        "7. Identify the KPIs that are most out of alignment.",
                                        "8. Analyze **interdependencies** between KPIs using the benchmark correlation matrix - If improving one KPI is likely to improve others suggest it in recommendation,  Explain **which KPI(s)** should be prioritized to help multiple others improve indirectly.",
                                        "9. Severity of any recommendation should be according the deviation, if deviation is more keep it high"))
                        .append(
                                INPUT,
                                "Based on the KPIs – sprint_velocity, sprint_capacity_utilization, test_execution_pass_percentage, build_frequency, unit_test_coverage, code_build_time, tech_debt_sonar_maintainability, lead_time_for_change, defect_injection_rate, defect_rejection_rate, defect_removal_efficiency, defect_density, in_sprint_automation_coverage, regression_automation_coverage, sprint_predictability, first_time_pass_rate, happiness_index_rate, value_delivery, sonar_code_quality, release_frequency, issue_count, scope_churn, created_vs_resolved, defect_seepage_rate, commitment_reliability, code_violations – analyze the provided dataset KPI_DATA_BY_PROJECT_PLACEHOLDER, which contains kpi data of latest five sprints marked by date,analyze the performance of each kpi and generate a score for the project performance.")
                        .append(
                                OUTPUT_FORMAT,
                                "Provide a comprehensive report with:\n  1. The project health score as a percentage\n 2. Observations on significant deviations between the project and benchmark values\n 3. Provide any recommendations to address observed discrepancies and how can the project health be improved,\n  do not mention the size of dataset in this section\n 4. Translate deviations into understandable, business-friendly language.\n5.  No explanation or inline comments are required else than the required json format. \n  Format the comprehensive report in parsable json format,\n  the recommendations should be actionable and specific to the KPIs analyzed and specifically tailored\n  for USER_ROLE_PLACEHOLDER role\n  the key\n  for project health should be titled 'project_health_value'\n  and recommendation key should be 'project_recommendations',\n  in the project_recommendations key,\n  provide a list of recommendations with each recommendation should have keys kpi,correlated_kpis as a string and list of string respectively where kpi is main kpi and correlated kpis are the most dependent kpis, recommendation and observation are value as a string and another key severity and value as a string,\n  the severity can be one of the following: low,\n  medium,\n  high,\n  critical {\n\"project_health_value\": 0,\n\"project_recommendations\": [{\n\"kpi\": \"\",\n\"observation\": \"\",\n\"correlated_kpis\": [\"\"],\n\"recommendation\": \"\",\n\"severity\": \"\"\n}],\n\"observations\": []\n}")
                        .append(
                                PLACEHOLDER,
                                Arrays.asList(
                                        "ANALYSIS_REPORT_PLACEHOLDER",
                                        "KPI_DATA_BY_PROJECT_PLACEHOLDER",
                                        "USER_ROLE_PLACEHOLDER"));
        collection.insertMany(Arrays.asList(kpiCorrelationAnalysis, kpiRecommendationPrompt));
    }
}
