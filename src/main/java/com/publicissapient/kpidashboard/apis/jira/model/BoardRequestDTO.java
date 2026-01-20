package com.publicissapient.kpidashboard.apis.jira.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Board Request Data Transfer Object")
public class BoardRequestDTO {

	@Schema(description = "Connection Identifier", example = "conn12345")
	private String connectionId;

	@Schema(description = "Project Key", example = "PROJKEY")
	private String projectKey;

	@Schema(description = "Board Type", example = "Scrum")
	private String boardType;
}
