package com.publicissapient.kpidashboard.apis.ai.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Represents the hygiene evaluation returned by the LLM for a single Jira issue.
 *
 * <p>The LLM produces a JSON ARRAY — one element per input issue. Consumers should deserialize into
 * {@code List<HygieneKpiResponseDTO>}; each element of that list carries the per-rule verdict,
 * aggregated counts / score / grade, overall readiness, top failures, and pipe-separated
 * improvement recommendations.
 *
 * <p>String-typed enum-like fields are intentionally kept as {@link String} to be forgiving of
 * minor LLM formatting drift. The expected vocabulary is:
 *
 * <ul>
 *   <li>{@code results[].status} — {@code "Passed" | "Failed" | "Partial" | "N/A"}
 *   <li>{@code hygieneGrade} — {@code "GOOD" | "AVERAGE" | "POOR"}
 *   <li>{@code overallStatus} — {@code "READY" | "NOT READY"}
 * </ul>
 */
@Data
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class HygieneKpiResponseDTO {

	/** Jira issue key (mapped from {@code jiraIssue.number}). */
	private String issueKey;

	/** Jira issue type name, e.g. {@code Story} / {@code Bug} / {@code Task}. */
	private String issueType;

	/** Sprint id the issue is committed to (nullable for backlog items). */
	private String sprintId;

	/** Assignee display name, or {@code "Unassigned"} when the issue has no owner. */
	private String assignee;

	/** Per-rule evaluation results, in the order the rules were declared. */
	private List<RuleResult> results;

	/** Number of rules whose status is not {@code "N/A"}. */
	private Integer totalApplicableRules;

	/** Number of rules whose status is {@code "Passed"}. */
	private Integer passedRules;

	/** Number of rules whose status is {@code "Failed"}. */
	private Integer failedRules;

	/** Number of rules whose status is {@code "Partial"}. */
	private Integer partialRules;

	/**
	 * Hygiene score in the range {@code [0, 100]}, computed as {@code passedRules * 100 /
	 * totalApplicableRules} (falls back to {@code 100} when there are no applicable rules).
	 */
	private Integer hygieneScore;

	/** {@code "GOOD" | "AVERAGE" | "POOR"} — derived from {@link #hygieneScore}. */
	private String hygieneGrade;

	/**
	 * {@code "READY"} only when every applicable rule is {@code "Passed"}; otherwise {@code "NOT
	 * READY"}.
	 */
	private String overallStatus;

	/** Up to three rule names representing the most impactful non-Passed rules. */
	private List<String> topFailures;

	/**
	 * 3–5 short, actionable improvement suggestions joined by {@code " | "}. Stored as a single
	 * {@link String} to match the CSV/Excel-friendly contract declared in the hygiene analyzer
	 * prompt.
	 */
	private String recommendations;

	/** One row of the per-rule breakdown produced by the LLM. */
	@Data
	@Builder
	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@ToString
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class RuleResult {

		/** Rule name — matches the key from the {@code fieldMappingPrompts} map. */
		private String rule;

		/** The {@code JiraIssue} field(s) that were consulted for the verdict. */
		private String field;

		/** Actual field value observed in the issue (or {@code "null"} when absent). */
		private String observed;

		/** {@code "Passed" | "Failed" | "Partial" | "N/A"}. */
		private String status;

		/** One-line justification citing the observed value. */
		private String reason;
	}
}
