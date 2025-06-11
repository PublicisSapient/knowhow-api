package com.publicissapient.kpidashboard.apis.ai.model;

import com.publicissapient.kpidashboard.common.model.generic.BasicModel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@AllArgsConstructor
@Document(collection = "prompt_details")
public class PromptDetails extends BasicModel {
	private String key;
	private String prompt;
}