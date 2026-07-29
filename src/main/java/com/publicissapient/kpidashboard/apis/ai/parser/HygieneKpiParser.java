package com.publicissapient.kpidashboard.apis.ai.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.kpidashboard.common.model.jira.HygieneKpiResponseDTO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component("hygieneParser")
public class HygieneKpiParser implements ParserStategy<List<HygieneKpiResponseDTO>> {

	private static final ObjectMapper objectMapper = new ObjectMapper();

	private static final String STATUS_PASSED = "Passed";
	private static final String STATUS_FAILED = "Failed";
	private static final String STATUS_PARTIAL = "Partial";
	private static final String STATUS_NA = "N/A";

	@Override
	public List<HygieneKpiResponseDTO> parse(String chatResponse) {
		try {
			// Strip any pre/post prose or markdown fences the LLM may add
			// around the JSON array before handing it to Jackson.
			int start = chatResponse.indexOf('[');
			int end = chatResponse.lastIndexOf(']');
			if (start < 0 || end <= start) {
				log.error("Chat response did not contain a JSON array: {}", chatResponse);
				return new ArrayList<>();
			}
			String jsonArrayString = chatResponse.substring(start, end + 1);
			List<HygieneKpiResponseDTO> results =
					objectMapper.readValue(jsonArrayString, new TypeReference<>() {});
			normaliseRuleStatuses(results);
			recomputeCounts(results);
			return results;
		} catch (JsonProcessingException e) {
			log.error("Error parsing JSON: {}", e.getMessage());
			return new ArrayList<>();
		}
	}

	/**
	 * Normalises each {@code results[].status} value to one of the four canonical tokens the rest of
	 * the system expects: {@code "Passed"}, {@code "Failed"}, {@code "Partial"}, {@code "N/A"}. Any
	 * unrecognised value is mapped to {@code "Failed"} — ambiguous evidence is treated as not proven.
	 */
	private void normaliseRuleStatuses(List<HygieneKpiResponseDTO> dtos) {
		if (dtos == null) return;
		for (HygieneKpiResponseDTO dto : dtos) {
			if (dto == null || dto.getResults() == null) continue;
			for (HygieneKpiResponseDTO.RuleResult rr : dto.getResults()) {
				if (rr == null) continue;
				String normalised = normaliseStatus(rr.getStatus());
				if (!Objects.equals(normalised, rr.getStatus())) {
					log.warn(
							"Hygiene parser: normalised status '{}' → '{}' for rule '{}' on issue '{}'",
							rr.getStatus(),
							normalised,
							rr.getRule(),
							dto.getIssueKey());
				}
				rr.setStatus(normalised);
			}
		}
	}

	/**
	 * Recomputes all numeric fields on each DTO from its normalised {@code results} list so they are
	 * authoritative and consistent regardless of what the LLM returned. Also recomputes {@code
	 * hygieneGrade} and {@code overallStatus}.
	 */
	private void recomputeCounts(List<HygieneKpiResponseDTO> dtos) {
		if (dtos == null) return;
		for (HygieneKpiResponseDTO dto : dtos) {
			if (dto == null) continue;
			List<HygieneKpiResponseDTO.RuleResult> ruleResults =
					dto.getResults() == null ? List.of() : dto.getResults();

			int passed = 0, failed = 0, partial = 0;
			for (HygieneKpiResponseDTO.RuleResult rr : ruleResults) {
				if (rr == null) continue;
				switch (rr.getStatus() == null ? "" : rr.getStatus()) {
					case STATUS_PASSED -> passed++;
					case STATUS_FAILED -> failed++;
					case STATUS_PARTIAL -> partial++;
						// N/A excluded from all counts
				}
			}
			int totalApplicable = passed + failed + partial;
			int score = totalApplicable == 0 ? 100 : (passed * 100 / totalApplicable);

			dto.setPassedRules(passed);
			dto.setFailedRules(failed);
			dto.setPartialRules(partial);
			dto.setTotalApplicableRules(totalApplicable);
			dto.setHygieneScore(score);
			dto.setHygieneGrade(score >= 80 ? "GOOD" : score >= 50 ? "AVERAGE" : "POOR");
			dto.setOverallStatus(
					failed == 0 && partial == 0 && totalApplicable > 0 ? "READY" : "NOT READY");
		}
	}

	private String normaliseStatus(String raw) {
		if (raw == null) return STATUS_FAILED;
		switch (raw.trim().toLowerCase()) {
			case "passed", "pass", "yes", "true" -> {
				return STATUS_PASSED;
			}
			case "failed", "fail", "no", "false" -> {
				return STATUS_FAILED;
			}
			case "partial", "partially", "partial credit", "partially met" -> {
				return STATUS_PARTIAL;
			}
			case "n/a", "na", "not applicable", "not_applicable", "not-applicable" -> {
				return STATUS_NA;
			}
			default -> {
				log.warn(
						"Hygiene parser: unrecognised status '{}' — defaulting to '{}'", raw, STATUS_FAILED);
				return STATUS_FAILED;
			}
		}
	}
}
