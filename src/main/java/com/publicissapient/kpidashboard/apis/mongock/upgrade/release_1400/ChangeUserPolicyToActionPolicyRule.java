/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1400;

import java.util.Date;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.MongoCollection;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * @author girpatha
 */
@ChangeUnit(
		id = "change_user_policy_to_action_policy_rule",
		order = "14002",
		author = "girpatha",
		systemVersion = "14.0.0")
public class ChangeUserPolicyToActionPolicyRule {

	private final MongoTemplate mongoTemplate;

	public ChangeUserPolicyToActionPolicyRule(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void execution() {
		MongoCollection<Document> actionPolicyRule = mongoTemplate.getCollection("action_policy_rule");

		// Create new policy document
		Document newPolicy =
				new Document("name", "ADD_USER")
						.append("roleAllowed", "")
						.append(
								"description",
								"User with role ROLE_SUPERADMIN or ROLE_PROJECT_ADMIN can add the users if granted access")
						.append("roleActionCheck", "action == 'ADD_USER'")
						.append(
								"condition",
								"subject.authorities.contains('ROLE_SUPERADMIN') || subject.authorities.contains('ROLE_PROJECT_ADMIN')")
						.append("createdDate", new Date())
						.append("lastModifiedDate", new Date());

		// Insert the new policy
		actionPolicyRule.insertOne(newPolicy);
	}

	@RollbackExecution
	public void rollback() {
		MongoCollection<Document> actionPolicyRule = mongoTemplate.getCollection("action_policy_rule");

		// Create filter to remove the policy
		Document filter = new Document("name", "ADD_USER");

		// Remove the policy
		actionPolicyRule.deleteOne(filter);
	}
}
