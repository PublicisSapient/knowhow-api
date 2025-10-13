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

package com.publicissapient.kpidashboard.apis.mongock.installation;

import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.publicissapient.kpidashboard.apis.ai.model.PromptDetails;
import com.publicissapient.kpidashboard.apis.mongock.data.PromptDetailsDataFactory;
import com.publicissapient.kpidashboard.apis.util.MongockUtil;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(id = "ddl10", order = "0010", author = "shunaray")
public class PromptDetailsChangeLog {

	private final MongoTemplate mongoTemplate;
	private List<PromptDetails> promptDetailsList;

	public PromptDetailsChangeLog(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
		PromptDetailsDataFactory dataFactory = PromptDetailsDataFactory.newInstance();
		promptDetailsList = dataFactory.getPromptDetailsList();
	}

	@Execution
	public boolean changeSet() {
		MongockUtil.saveListToDB(promptDetailsList, "prompt_details", mongoTemplate);
		return true;
	}

	@RollbackExecution
	public void rollback() {
		// We are inserting the documents through DDL, no rollback to any collections.
	}
}
