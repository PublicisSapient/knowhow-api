package com.publicissapient.kpidashboard.apis.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author sanbhand1
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "DTO for submitting user feedback")
public class FeedbackSubmitDTO {
	@Schema(description = "Username of the person submitting feedback", example = "john.doe")
	private String username;

	@Schema(description = "Feedback content", example = "Great application! Very user-friendly.")
	private String feedback;

	@Override
	public String toString() {
		return "FeedbackSubmitDTO [username=" + this.username + ", feedback=" + this.feedback + "]";
	}
}
