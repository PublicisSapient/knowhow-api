package com.publicissapient.kpidashboard.apis.githubaction.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "DTO representing a GitHub Action Repository")
public class GithubActionRepoDTO {

	@Schema(description = "Name of the GitHub Repository", example = "sample-repo")
	private String repositoryName;

	@Schema(
			description = "Connection ID associated with the repository",
			example = "60d21b4667d0d8992e610c85")
	private String connectionID;
}
