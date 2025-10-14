/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *    http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.aiusage.enums;

import com.publicissapient.kpidashboard.apis.aiusage.model.AIUsage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
@Getter
public enum RequiredHeaders {
	EMAIL("email") {
		@Override
		public void apply(AIUsage aiUsage, String value) {
			aiUsage.setEmail(value);
		}
	},
	PROMPT_COUNT("promptCount") {
		@Override
		public void apply(AIUsage aiUsage, String value) throws NumberFormatException {
			Integer integerValue = Integer.parseInt(value);
			aiUsage.setPromptCount(integerValue);
		}
	},
	BUSINESS_UNIT("businessUnit") {
		@Override
		public void apply(AIUsage aiUsage, String value) {
			aiUsage.setBusinessUnit(value);
		}
	},
	ACCOUNT("account") {
		@Override
		public void apply(AIUsage aiUsage, String value) {
			aiUsage.setAccount(value);
		}
	},
	VERTICAL("vertical") {
		@Override
		public void apply(AIUsage aiUsage, String value) {
			aiUsage.setVertical(value);
		}
	};

	private final String name;

	public abstract void apply(AIUsage aiUsage, String value);

	public static RequiredHeaders fromString(String field) {
		for (RequiredHeaders mappedField : RequiredHeaders.values()) {
			if (mappedField.getName().equalsIgnoreCase(field)) {
				return mappedField;
			}
		}
		log.error("No mapping found for field: {}", field);
		return null;
	}
}
