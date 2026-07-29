package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;

import com.publicissapient.kpidashboard.common.constant.PromptKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ChangeUnit(
		id = "pr_throughput_kpi_insert",
		order = "17123",
		author = "kunkambl",
		systemVersion = "17.1.0")
public class PRThroughputKpiChangeUnit {

	private static final String KPI_ID = "kpi208";
	private static final String KPI_ID_FIELD = "kpiId";
	private static final String KPI_MASTER_COLLECTION = "kpi_master";
	private static final String KPI_COLUMN_CONFIGS_COLLECTION = "kpi_column_configs";
	private static final String FIELD_MAPPING_STRUCTURE_COLLECTION = "field_mapping_structure";
	private static final String COLUMN_NAME = "columnName";
	private static final String ORDER = "order";
	private static final String IS_SHOWN = "isShown";
	private static final String IS_DEFAULT = "isDefault";

	@Execution
	public void execution(MongoTemplate mongoTemplate) {
		insertKpiMaster(mongoTemplate);
		insertKpiColumnConfig(mongoTemplate);
		insertFieldMappingStructure(mongoTemplate);
	}

	public void insertKpiMaster(MongoTemplate mongoTemplate) {
		Document kpiMaster =
				new Document()
						.append(KPI_ID_FIELD, KPI_ID)
						.append("kpiName", "PR Throughput")
						.append("isDeleted", "False")
						.append("defaultOrder", 1)
						.append("kpiCategory", "Slingshot")
						.append("kpiSubCategory", "Speed")
						.append("kpiUnit", "PRs")
						.append("chartType", "line")
						.append("xAxisLabel", "Weeks")
						.append("yAxisLabel", "Count")
						.append("showTrend", true)
						.append("isPositiveTrend", true)
						.append("calculateMaturity", false)
						.append("hideOverallFilter", true)
						.append("kpiSource", "BitBucket")
						.append("maxValue", 15)
						.append("thresholdValue", 55.0)
						.append("kanban", false)
						.append("groupId", 6)
						.append(
								"kpiInfo",
								new Document()
										.append(
												"definition",
												"Merged pull requests per engineer per week, at team / org level only. "))
						.append("kpiFilter", "dropDown")
						.append("aggregationCriteria", "average")
						.append("isTrendCalculative", false)
						.append("isAdditionalFilterSupport", false)
						.append("isRepoToolKpi", true)
						.append("combinedKpiSource", "Bitbucket/AzureRepository/GitHub/GitLab")
						.append("forecastModel", "thetaMethod");

		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.replaceOne(
						new Document(KPI_ID_FIELD, KPI_ID),
						kpiMaster,
						new com.mongodb.client.model.ReplaceOptions().upsert(true));

		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.updateOne(
						new Document(KPI_ID, "kpi206"),
						new Document("$set", new Document("kpiFilter", "dropDown")));
	}

	public void insertKpiColumnConfig(MongoTemplate mongoTemplate) {
		Document columnConfig =
				new Document()
						.append("basicProjectConfigId", null)
						.append(KPI_ID_FIELD, KPI_ID)
						.append(
								"kpiColumnDetails",
								Arrays.asList(
										new Document()
												.append(COLUMN_NAME, "Project")
												.append(ORDER, 1)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Repo")
												.append(ORDER, 2)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Branch")
												.append(ORDER, 3)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Days/Weeks")
												.append(ORDER, 4)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Developer")
												.append(ORDER, 5)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "No of Merge")
												.append(ORDER, 7)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true)));

		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS_COLLECTION)
				.replaceOne(
						new Document(KPI_ID_FIELD, KPI_ID),
						columnConfig,
						new com.mongodb.client.model.ReplaceOptions().upsert(true));
	}

	public void insertFieldMappingStructure(MongoTemplate mongoTemplate) {
		Document fieldMapping =
				new Document()
						.append("fieldName", "thresholdValueKPI208")
						.append("fieldLabel", "Target KPI Value")
						.append("fieldType", "number")
						.append("section", "Project Level Threshold")
						.append("processorCommon", false)
						.append(
								"tooltip",
								new Document()
										.append(
												"definition",
												"Target KPI value denotes the bare minimum a project should maintain for a KPI. User should just input the number and the unit like percentage, hours will automatically be considered. If the threshold is empty, then a common target KPI line will be shown"))
						.append("fieldDisplayOrder", 1)
						.append("sectionOrder", 6)
						.append("mandatory", false)
						.append("nodeSpecific", false);

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.replaceOne(
						new Document("fieldName", "thresholdValueKPI208"),
						fieldMapping,
						new com.mongodb.client.model.ReplaceOptions().upsert(true));
	}

	@RollbackExecution
	public void rollback(MongoTemplate mongoTemplate) {
		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.deleteOne(new Document(KPI_ID_FIELD, KPI_ID));
		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS_COLLECTION)
				.deleteOne(new Document(KPI_ID_FIELD, KPI_ID));
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.deleteOne(new Document("fieldName", "thresholdValueKPI208"));
	}

    /**
     * Inserts the {@code project-hygiene} prompt into the {@code prompt_details} collection so that the
     * Project Hygiene KPI (kpi311) reads its prompt template from the DB instead of a hardcoded
     * constant.
     *
     * <p>The document follows the {@code PromptDetails} schema - {@code key}, {@code context}, {@code
     * task}, {@code instructions}, {@code input}, {@code outputFormat} and {@code placeHolders}. The
     * two placeholders are resolved at runtime by {@code PromptService#getProjectHygienePrompt}.
     */
    @Slf4j
    @RequiredArgsConstructor
    @ChangeUnit(
            id = "project_hygiene_prompt_details",
            order = "17201",
            author = "knowhow",
            systemVersion = "17.2.0")
    public static class ProjectHygienePromptDetailsChangeUnit {

        private static final String PROMPT_DETAILS_COLLECTION = "prompt_details";
        private static final String PROJECT_HYGIENE_PROMPT = PromptKeys.PROJECT_HYGIENE_PROMPT;

        private static final String KEY = "key";
        private static final String CONTEXT = "context";
        private static final String TASK = "task";
        private static final String INSTRUCTIONS = "instructions";
        private static final String INPUT = "input";
        private static final String OUTPUT_FORMAT = "outputFormat";
        private static final String PLACEHOLDERS = "placeHolders";

        private static final String HYGIENE_RULES_PLACEHOLDER = "HYGIENE_RULES_PLACEHOLDER";
        private static final String JIRA_ISSUES_PLACEHOLDER = "JIRA_ISSUES_PLACEHOLDER";

        private final MongoTemplate mongoTemplate;

        @Execution
        public void execution() {
            insertProjectHygienePromptDetails();
        }

        @RollbackExecution
        public void rollback() {
            deleteProjectHygienePromptDetails();
        }

        /** Inserts / upserts the complete project hygiene prompt document. */
        private void insertProjectHygienePromptDetails() {
            log.info("Inserting {} prompt details", PROJECT_HYGIENE_PROMPT);

            String context =
                    "You are an Expert Project Hygiene Analyzer Agent. Your job is to evaluate Jira issues against a "
                            + "Definition-of-Ready (DoR) style hygiene checklist and produce a strict, evidence-based verdict "
                            + "for each issue.";

            String input =
                    "=== Hygiene Rules ===\n"
                            + "A numbered list of INDEPENDENT rule entries. Each entry declares:\n"
                            + "  - ruleName: the unique identifier of the rule - copy it VERBATIM into results[].rule\n"
                            + "  - field   : the Jira issue field this rule inspects\n"
                            + "  - criteria: the condition this rule evaluates\n"
                            + "Several entries MAY declare the SAME field (for example one 'acceptance criteria' rule and one "
                            + "'BDD definition' rule, both written against 'description'). Treat every entry as a completely "
                            + "separate rule: evaluate it using ONLY its own criteria and emit its own result element. Never "
                            + "merge, deduplicate or skip rules just because they share a field.\n"
                            + HYGIENE_RULES_PLACEHOLDER
                            + "\n\n=== Jira Issues (JSON array) ===\n"
                            + JIRA_ISSUES_PLACEHOLDER;

            String outputFormat =
                    "Return a JSON ARRAY - one element per input Jira issue, in the same order as the input. No markdown, "
                            + "no prose, no code fences, no trailing commentary. Schema per element:\n"
                            + "[\n"
                            + "  {\n"
                            + "    \"issueKey\": \"<jiraIssue.number>\",\n"
                            + "    \"issueType\": \"<jiraIssue.typeName>\",\n"
                            + "    \"sprintId\": \"<jiraIssue.sprintID>\",\n"
                            + "    \"assignee\": \"<jiraIssue.assigneeName or 'Unassigned'>\",\n"
                            + "    \"results\": [\n"
                            + "      {\n"
                            + "        \"rule\": \"<ruleName copied VERBATIM from the rule entry>\",\n"
                            + "        \"field\": \"<the field declared on that rule entry>\",\n"
                            + "        \"observed\": \"<actual field value or 'null'>\",\n"
                            + "        \"status\": \"Passed | Failed | Partial | N/A\",\n"
                            + "        \"reason\": \"<one-line justification citing the observed value>\"\n"
                            + "      }\n"
                            + "    ],\n"
                            + "    \"totalApplicableRules\": <int>,\n"
                            + "    \"passedRules\": <int>,\n"
                            + "    \"failedRules\": <int>,\n"
                            + "    \"partialRules\": <int>,\n"
                            + "    \"hygieneScore\": <int 0-100>,\n"
                            + "    \"hygieneGrade\": \"GOOD | AVERAGE | POOR\",\n"
                            + "    \"overallStatus\": \"READY | NOT READY\",\n"
                            + "    \"topFailures\": [\"<up to 3 ruleNames of most impactful non-Passed rules>\"],\n"
                            + "    \"recommendations\": \"<3-5 fixes joined by ' | '>\"\n"
                            + "  }\n"
                            + "]\n\n"
                            + "Hard Constraints:\n"
                            + "- Evaluate EVERY rule entry for EVERY issue; never skip a rule and never skip an issue.\n"
                            + "- results MUST contain EXACTLY one element per supplied rule entry - same count, same order - "
                            + "including when several rule entries share the same field.\n"
                            + "- results[].rule MUST be the ruleName copied verbatim; never rename, merge or invent rules.\n"
                            + "- status MUST be exactly one of \"Passed\", \"Failed\", \"Partial\", \"N/A\" (case sensitive, "
                            + "spelled exactly).\n"
                            + "- overallStatus MUST be exactly \"READY\" or \"NOT READY\".\n"
                            + "- reason MUST cite the exact field name and value observed.\n"
                            + "- Never invent field values that are not present in the input JSON.\n"
                            + "- Return the JSON array and nothing else.";

            Update update =
                    new Update()
                            .set(KEY, PROJECT_HYGIENE_PROMPT)
                            .set(CONTEXT, context)
                            .set(
                                    TASK,
                                    "Evaluate each Jira issue against every hygiene rule and produce a strict, evidence-based verdict per issue.")
                            .set(
                                    INSTRUCTIONS,
                                    Arrays.asList(
                                            "=== Non-Negotiable Principles === Rely STRICTLY on the fields provided in the Jira Issue "
                                                    + "JSON. NEVER assume, infer, or fabricate a value that is not present. If evidence is "
                                                    + "missing, mark the rule as \"Failed\" (never \"Passed\"). Differentiate REQUESTS from "
                                                    + "CONFIRMATIONS - a request for sign-off is NOT approval. Every verdict MUST cite the exact "
                                                    + "field name and observed value.",
                                            "=== Independent Rule Evaluation === Every numbered rule entry is INDEPENDENT, even when several "
                                                    + "entries declare the same field. Evaluate each entry strictly against its own criteria and "
                                                    + "ignore the criteria of every other entry. A field carrying two rule sets (e.g. an acceptance "
                                                    + "criteria check and a BDD definition check on 'description') MUST produce TWO separate result "
                                                    + "elements with two separate verdicts - one may pass while the other fails. The results array "
                                                    + "MUST contain exactly one element per rule entry, in the order the rules were supplied: if 7 "
                                                    + "rule entries are given, return 7 result elements.",
                                            "=== Per-Rule Verdict Vocabulary === \"Passed\" -> rule is fully satisfied by explicit evidence "
                                                    + "in the listed field. \"Failed\" -> rule is not met OR required evidence is missing. "
                                                    + "\"Partial\" -> rule is partially met - present but incomplete / unclear / unconfirmed. "
                                                    + "\"N/A\" -> rule does not apply to this issue type/status per its own criteria.",
                                            "=== Overall Status Rules === \"READY\" -> every applicable rule entry (i.e. excluding \"N/A\") "
                                                    + "has status \"Passed\". \"NOT READY\" -> any applicable rule entry is \"Failed\" or \"Partial\".",
                                            "=== Hygiene Score === totalApplicableRules = count of rule ENTRIES whose status is not \"N/A\" "
                                                    + "(rule entries sharing a field are counted separately). passedRules = count of rule entries "
                                                    + "whose status is \"Passed\". hygieneScore = passedRules * 100 / totalApplicableRules (if "
                                                    + "totalApplicableRules == 0 -> 100). hygieneGrade = \"GOOD\" when hygieneScore >= 80, "
                                                    + "\"AVERAGE\" when 50 <= hygieneScore < 80, \"POOR\" when hygieneScore < 50.",
                                            "=== Improvement Recommendations === Provide 3 to 5 short, actionable suggestions that would "
                                                    + "raise the hygiene score for this issue. Each suggestion must reference a specific rule or "
                                                    + "missing evidence. Return them as ONE string with items joined by \" | \"."))
                            .set(INPUT, input)
                            .set(OUTPUT_FORMAT, outputFormat)
                            .set(PLACEHOLDERS, Arrays.asList(HYGIENE_RULES_PLACEHOLDER, JIRA_ISSUES_PLACEHOLDER));

            mongoTemplate.upsert(
                    new Query(Criteria.where(KEY).is(PROJECT_HYGIENE_PROMPT)),
                    update,
                    PROMPT_DETAILS_COLLECTION);

            log.info("Successfully inserted {} prompt details", PROJECT_HYGIENE_PROMPT);
        }

        /** Deletes the project hygiene prompt document. */
        private void deleteProjectHygienePromptDetails() {
            log.info("Deleting {} prompt details", PROJECT_HYGIENE_PROMPT);

            mongoTemplate.remove(
                    new Query(Criteria.where(KEY).is(PROJECT_HYGIENE_PROMPT)), PROMPT_DETAILS_COLLECTION);

            log.info("Successfully deleted {} prompt details", PROJECT_HYGIENE_PROMPT);
        }
    }
}
