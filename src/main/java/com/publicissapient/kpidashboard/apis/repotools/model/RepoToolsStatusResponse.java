/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.repotools.model;

import java.io.Serial;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "RepoTools Status Response Model")
public class RepoToolsStatusResponse implements Serializable {

	@Serial private static final long serialVersionUID = 1L;

	@Schema(description = "Project Name", example = "Project Alpha")
	private String project;

	@Schema(description = "Repository Name", example = "repo-alpha")
	private String repository;

	@Schema(description = "Repository Provider", example = "GitHub")
	private String repositoryProvider;

	@Schema(description = "Source of the status", example = "RepoTools")
	private String source;

	@Schema(description = "Status of the repository analysis", example = "COMPLETED")
	private String status;

	@Schema(description = "Timestamp of the status update", example = "1627849923000")
	private Long timestamp;
}
