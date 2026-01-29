/*
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.publicissapient.kpidashboard.apis.mongock.installation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.publicissapient.kpidashboard.common.model.generic.Processor;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author bogolesw
 */
@Slf4j
@ChangeUnit(id = "ddl3", order = "003", author = "PSKnowHOW")
@RequiredArgsConstructor
public class GlobalConfigChangeLog {

	private static final String CLASS_KEY = "_class";
	private static final String BUILD = "BUILD";
	private static final String REPO_TOOL_PROVIDER = "repoToolProvider";
	private static final String TOOL_NAME = "toolName";
	private static final String TEST_API_URL = "testApiUrl";
	private static final String SONAR = "Sonar";

	private final MongoTemplate mongoTemplate;

	@Builder
	private record ProcessorConfig(String name, String type, String className) {}

	@Execution
	public void executeGlobalConfig() {
		insertGlobalConfigData();
		insertProcessorData();
		insertRepoToolProviderData();
	}

	@RollbackExecution
	public void rollback() {
		// We are inserting the documents through DDL, no rollback to any collections.
	}

	// repo tool related info used by repo tool processor
	public void insertRepoToolProviderData() {
		mongoTemplate
				.getCollection("repo_tools_provider")
				.insertMany(
						Arrays.asList(
								new Document(TOOL_NAME, "bitbucket")
										.append(TEST_API_URL, "https://api.bitbucket.org/2.0/workspaces/")
										.append("testServerApiUrl", "/bitbucket/rest/api/1.0/projects/")
										.append(REPO_TOOL_PROVIDER, "bitbucket_oauth2"),
								new Document(TOOL_NAME, "gitlab")
										.append(REPO_TOOL_PROVIDER, "gitlab")
										.append(TEST_API_URL, "/api/v4/projects/"),
								new Document(TOOL_NAME, "github")
										.append(TEST_API_URL, "https://api.github.com/users/")
										.append(REPO_TOOL_PROVIDER, "github")));
	}

	public void insertGlobalConfigData() {
		Document existingConfig =
				mongoTemplate
						.getCollection("global_config")
						.find(new Document("env", "production"))
						.first();

		if (existingConfig == null) {
			Document globalConfig =
					new Document()
							.append("env", "production")
							.append(
									"authTypeStatus",
									new Document().append("standardLogin", true).append("adLogin", false))
							.append(
									"emailServerDetail",
									new Document()
											.append("emailHost", "mail.example.com")
											.append("emailPort", 25)
											.append("fromEmail", "no-reply@example.com")
											.append(
													"feedbackEmailIds", Collections.singletonList("sampleemail@example.com")))
							.append("zephyrCloudBaseUrl", "https://api.zephyrscale.smartbear.com/v2/");

			mongoTemplate.getCollection("global_config").insertOne(globalConfig);
		}
	}

	public void insertProcessorData() {
		List<ProcessorConfig> processorConfigs = createRequiredProcessorConfigs();

		List<String> existingProcessorNames =
				mongoTemplate.findAll(Processor.class, "processor").stream()
						.filter(
								processor ->
										processor != null && StringUtils.isNotEmpty(processor.getProcessorName()))
						.map(processor -> processor.getProcessorName().toLowerCase())
						.toList();

		List<Document> processorData = new ArrayList<>();

		for (ProcessorConfig processorConfig : processorConfigs) {
			if (Boolean.FALSE.equals(
					existingProcessorNames.contains(processorConfig.name.toLowerCase()))) {
				if (SONAR.equalsIgnoreCase(processorConfig.name)) {
					processorData.add(createSonarProcessor());
				} else {
					processorData.add(
							createProcessor(
									processorConfig.name, processorConfig.type, processorConfig.className));
				}
			}
		}

		mongoTemplate.getCollection("processor").insertMany(processorData);
	}

	private static Document createProcessor(
			String processorName, String processorType, String className) {
		return new Document()
				.append("processorName", processorName)
				.append("processorType", processorType)
				.append("isActive", true)
				.append("isOnline", true)
				.append("errors", Collections.emptyList())
				.append("isLastSuccess", false)
				.append(CLASS_KEY, className);
	}

	private static Document createSonarProcessor() {
		return new Document()
				.append("processorName", SONAR)
				.append("processorType", "SONAR_ANALYSIS")
				.append("isActive", true)
				.append("isOnline", true)
				.append("errors", Collections.emptyList())
				.append("isLastSuccess", false)
				.append(CLASS_KEY, "com.publicissapient.kpidashboard.sonar.model.SonarProcessor")
				.append("sonarKpiMetrics", createSonarKpiMetrics());
	}

	private static List<String> createSonarKpiMetrics() {
		return Arrays.asList(
				"lines",
				"ncloc",
				"violations",
				"new_vulnerabilities",
				"critical_violations",
				"major_violations",
				"blocker_violations",
				"minor_violations",
				"info_violations",
				"tests",
				"test_success_density",
				"test_errors",
				"test_failures",
				"coverage",
				"line_coverage",
				"sqale_index",
				"alert_status",
				"quality_gate_details",
				"sqale_rating");
	}

	@SuppressWarnings({"java:S138"})
	private static List<ProcessorConfig> createRequiredProcessorConfigs() {
		return List.of(
				ProcessorConfig.builder()
						.name("Jira")
						.type("AGILE_TOOL")
						.className("com.publicissapient.kpidashboard.jira.model.JiraProcessor")
						.build(),
				ProcessorConfig.builder()
						.name("Zephyr")
						.type("TESTING_TOOLS")
						.className("com.publicissapient.kpidashboard.jira.model.ZephyrProcessor")
						.build(),
				ProcessorConfig.builder()
						.name("GitHub")
						.type("SCM")
						.className("com.publicissapient.kpidashboard.jira.model.GitHubProcessor")
						.build(),
				ProcessorConfig.builder()
						.name("Teamcity")
						.type(BUILD)
						.className("com.publicissapient.kpidashboard.jira.model.TeamcityProcessor")
						.build(),
				ProcessorConfig.builder()
						.name("Bitbucket")
						.type("SCM")
						.className("com.publicissapient.kpidashboard.jira.model.BitbucketProcessor")
						.build(),
				ProcessorConfig.builder()
						.name("GitLab")
						.type("SCM")
						.className("com.publicissapient.kpidashboard.jira.model.GitLabProcessor")
						.build(),
				ProcessorConfig.builder()
						.name("Jenkins")
						.type(BUILD)
						.className("com.publicissapient.kpidashboard.jira.model.JenkinsProcessor")
						.build(),
				ProcessorConfig.builder()
						.name("Bamboo")
						.type(BUILD)
						.className("com.publicissapient.kpidashboard.jira.model.BambooProcessor")
						.build(),
				ProcessorConfig.builder()
						.name("Azure")
						.type("AGILE_TOOL")
						.className("com.publicissapient.kpidashboard.jira.model.AzureProcessor")
						.build(),
				ProcessorConfig.builder()
						.name("AzureRepository")
						.type("SCM")
						.className("com.publicissapient.kpidashboard.jira.model.AzureRepoProcessor")
						.build(),
				ProcessorConfig.builder()
						.name("AzurePipeline")
						.type(BUILD)
						.className("com.publicissapient.kpidashboard.jira.model.AzurePipelineProcessor")
						.build(),
				ProcessorConfig.builder()
						.name("JiraTest")
						.type("TESTING_TOOLS")
						.className("com.publicissapient.kpidashboard.jira.model.JiraTestProcessor")
						.build(),
				ProcessorConfig.builder()
						.name("GitHubAction")
						.type(BUILD)
						.className("com.publicissapient.kpidashboard.jira.model.GitHubActionProcessor")
						.build(),
				ProcessorConfig.builder()
						.name("RepoTool")
						.type("SCM")
						.className("com.publicissapient.kpidashboard.jira.model.RepoDbProcessor")
						.build(),
				ProcessorConfig.builder()
						.name("ArgoCD")
						.type(BUILD)
						.className("com.publicissapient.kpidashboard.jira.model.ArgoCDProcessor")
						.build(),
				ProcessorConfig.builder()
						.name(SONAR)
						.type("SONAR_ANALYSIS")
						.className("com.publicissapient.kpidashboard.jira.model.SonarProcessor")
						.build());
	}
}
