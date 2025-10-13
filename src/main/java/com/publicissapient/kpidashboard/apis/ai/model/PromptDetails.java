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

package com.publicissapient.kpidashboard.apis.ai.model;

import java.util.List;

import org.springframework.data.mongodb.core.mapping.Document;

import com.publicissapient.kpidashboard.common.model.generic.BasicModel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author shunaray
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "prompt_details")
public class PromptDetails extends BasicModel {
	private String key;
	private String context;
	private String task;
	private List<String> instructions;
	private String input;
	private String outputFormat;
	private List<String> placeHolders;

	@Override
	public String toString() {
		return "{"
				+ "context='"
				+ context
				+ '\''
				+ ", task='"
				+ task
				+ '\''
				+ ", instructions="
				+ instructions
				+ ", input='"
				+ input
				+ '\''
				+ ", outputFormat='"
				+ outputFormat
				+ '\''
				+ '}';
	}
}
