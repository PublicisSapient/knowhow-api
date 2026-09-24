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

package com.publicissapient.kpidashboard.apis.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * Counts how many individual acceptance criteria a story carries.
 *
 * <p>Jira has no structured acceptance criteria field — it is always a free-text custom field, and
 * every team writes it differently. This counter recognises the three shapes that cover virtually
 * all of them:
 *
 * <ul>
 *   <li><b>Gherkin</b> — {@code Scenario:} / {@code Scenario Outline:} / {@code Example:} headers,
 *       or bare {@code Given} steps when the scenarios are unnamed. {@code And} / {@code But}
 *       continuation steps are deliberately not counted, and the {@code Examples:} data table of a
 *       Scenario Outline is not mistaken for a scenario.
 *   <li><b>List</b> — bullets ({@code -}, {@code *}, {@code •}, …), checkboxes ({@code - [ ]}),
 *       numbered items ({@code 1.}, {@code 1)}, {@code (1)}) and {@code AC1:} style prefixes. Only
 *       items at the shallowest indentation level count, so nested sub-bullets elaborating a
 *       criterion do not inflate the number.
 *   <li><b>Line</b> — the fallback: every remaining non-empty line is one criterion.
 * </ul>
 *
 * <p>Headings ({@code Acceptance Criteria:}, {@code h3. AC}, {@code **Acceptance Criteria**}),
 * horizontal rules and Gherkin {@code Feature:} lines are stripped before counting so the wrapper
 * around the criteria is never counted as a criterion.
 *
 * <p>The input is expected to be the plain text produced by the Jira processor, which flattens
 * Atlassian Document Format (Jira Cloud) and wiki markup (Jira Server) into newline separated text.
 */
public final class AcceptanceCriteriaCounter {

	/** How the acceptance criteria text is split into individual criteria. */
	public enum Format {
		/** Detect the shape of the text: Gherkin, then list, then one criterion per line. */
		AUTO,
		/** Count {@code Scenario:} headers, falling back to {@code Given} steps. */
		GHERKIN,
		/** Count top level bullets, checkboxes, numbered items and {@code AC1:} prefixes. */
		LIST,
		/** Count every non-empty line. */
		LINE,
		/** No acceptance criteria at all — reported, never configured. */
		NONE
	}

	/** Outcome of counting: how many criteria, and which shape they were read as. */
	public record Result(int count, Format format) {

		/** The empty outcome used when a story has no acceptance criteria text. */
		static Result none() {
			return new Result(0, Format.NONE);
		}
	}

	private static final Pattern SCENARIO =
			Pattern.compile(
					"^(scenario outline|scenario template|scenario|example)\\s*[:\\-]",
					Pattern.CASE_INSENSITIVE);

	/** {@code Examples:} introduces a Scenario Outline data table — never a scenario of its own. */
	private static final Pattern EXAMPLES_TABLE =
			Pattern.compile("^examples?\\s*:?\\s*$", Pattern.CASE_INSENSITIVE);

	private static final Pattern GIVEN_STEP = Pattern.compile("^given\\b", Pattern.CASE_INSENSITIVE);

	private static final Pattern FEATURE = Pattern.compile("^feature\\s*:", Pattern.CASE_INSENSITIVE);

	private static final Pattern BULLET =
			Pattern.compile("^[-*+\u2022\u25E6\u25AA\u2023\u00B7\u2043]\\s+\\S");

	private static final Pattern CHECKBOX =
			Pattern.compile(
					"^(?:[-*+]\\s*)?(?:\\[\\s*[xX\u2713\u2714]?\\s*]|[\u2610\u2611\u2612])\\s*\\S");

	private static final Pattern NUMBERED =
			Pattern.compile("^\\(?\\d+\\s*[.)\\]:\\-]\\s+\\S|^\\(?[a-zA-Z]\\s*[.)]\\s+\\S");

	private static final Pattern AC_PREFIX =
			Pattern.compile(
					"^(ac|criteri(?:on|a)|rule)\\s*[-#]?\\s*\\d+\\s*[.):\\-]?\\s*\\S?",
					Pattern.CASE_INSENSITIVE);

	/** A line that only announces the block that follows — never a criterion itself. */
	private static final Set<String> HEADINGS =
			Set.of(
					"acceptance criteria",
					"acceptance criterion",
					"acceptancecriteria",
					"ac",
					"acs",
					"criteria",
					"criterion",
					"definition of done",
					"dod",
					"scenarios",
					"test scenarios",
					"gherkin");

	private static final Pattern HORIZONTAL_RULE = Pattern.compile("^[-=_*~#]{3,}$");

	/** Jira wiki heading ({@code h3. }) and markdown heading / emphasis noise. */
	private static final Pattern HEADING_DECORATION =
			Pattern.compile("^(?:h[1-6]\\.\\s*)?[#*_\\s]*|[#*_:\\s]*$", Pattern.CASE_INSENSITIVE);

	private AcceptanceCriteriaCounter() {
		// utility
	}

	/**
	 * Counts the acceptance criteria in the given text.
	 *
	 * @param acceptanceCriteria the plain text acceptance criteria, may be {@code null} or blank
	 * @param requested the configured format, {@code null} is treated as {@link Format#AUTO}
	 * @return the number of criteria found and the format they were read as, never {@code null}
	 */
	public static Result count(String acceptanceCriteria, Format requested) {
		List<Line> lines = meaningfulLines(acceptanceCriteria);
		if (lines.isEmpty()) {
			return Result.none();
		}

		Format format = requested == null || requested == Format.NONE ? Format.AUTO : requested;
		return switch (format) {
			case GHERKIN -> finalise(countGherkin(lines), Format.GHERKIN, lines);
			case LIST -> finalise(countList(lines), Format.LIST, lines);
			case LINE -> new Result(lines.size(), Format.LINE);
			default -> auto(lines);
		};
	}

	/**
	 * Resolves the configured format name onto the enum. Unknown or blank values fall back to {@link
	 * Format#AUTO} rather than failing the whole KPI over one mis-typed project setting.
	 */
	public static Format parseFormat(String configuredValue) {
		if (StringUtils.isBlank(configuredValue)) {
			return Format.AUTO;
		}
		try {
			Format format = Format.valueOf(configuredValue.trim().toUpperCase(Locale.ROOT));
			return format == Format.NONE ? Format.AUTO : format;
		} catch (IllegalArgumentException unknownValue) { // NOSONAR - configuration is user supplied
			return Format.AUTO;
		}
	}

	private static Result auto(List<Line> lines) {
		int gherkin = countGherkin(lines);
		if (gherkin > 0) {
			return new Result(gherkin, Format.GHERKIN);
		}
		int list = countList(lines);
		if (list > 0) {
			return new Result(list, Format.LIST);
		}
		return new Result(lines.size(), Format.LINE);
	}

	/**
	 * An explicitly requested format that matches nothing would otherwise report zero criteria for a
	 * story that clearly has some. Fall back to one criterion per line so the story is never counted
	 * as "no acceptance criteria" purely because of a format mismatch.
	 */
	private static Result finalise(int count, Format format, List<Line> lines) {
		return count > 0 ? new Result(count, format) : new Result(lines.size(), Format.LINE);
	}

	private static int countGherkin(List<Line> lines) {
		long scenarios =
				lines.stream()
						.filter(line -> !EXAMPLES_TABLE.matcher(line.text()).find())
						.filter(line -> SCENARIO.matcher(line.text()).find())
						.count();
		if (scenarios > 0) {
			return (int) scenarios;
		}
		return (int) lines.stream().filter(line -> GIVEN_STEP.matcher(line.text()).find()).count();
	}

	/**
	 * Counts list items at the shallowest indentation only: a nested bullet elaborating a criterion
	 * is part of that criterion, not a criterion of its own.
	 */
	private static int countList(List<Line> lines) {
		List<Line> items = lines.stream().filter(line -> isListItem(line.text())).toList();
		if (items.isEmpty()) {
			return 0;
		}
		int shallowest = items.stream().mapToInt(Line::indent).min().orElse(0);
		return (int) items.stream().filter(line -> line.indent() == shallowest).count();
	}

	private static boolean isListItem(String text) {
		return CHECKBOX.matcher(text).find()
				|| BULLET.matcher(text).find()
				|| NUMBERED.matcher(text).find()
				|| AC_PREFIX.matcher(text).find();
	}

	/** Splits the text into lines, dropping blanks, headings, rules and Gherkin feature lines. */
	private static List<Line> meaningfulLines(String acceptanceCriteria) {
		List<Line> lines = new ArrayList<>();
		if (StringUtils.isBlank(acceptanceCriteria)) {
			return lines;
		}
		for (String raw : acceptanceCriteria.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
			String text = raw.trim();
			if (text.isEmpty() || isNoise(text)) {
				continue;
			}
			lines.add(new Line(text, indentOf(raw)));
		}
		return lines;
	}

	private static boolean isNoise(String text) {
		if (HORIZONTAL_RULE.matcher(text).matches() || FEATURE.matcher(text).find()) {
			return true;
		}
		String stripped = HEADING_DECORATION.matcher(text).replaceAll("").trim();
		return stripped.isEmpty() || HEADINGS.contains(stripped.toLowerCase(Locale.ROOT));
	}

	/** Indentation width of the raw line, counting a tab as four spaces. */
	private static int indentOf(String raw) {
		int indent = 0;
		for (char character : raw.toCharArray()) {
			if (character == ' ') {
				indent++;
			} else if (character == '\t') {
				indent += 4;
			} else {
				break;
			}
		}
		return indent;
	}

	/** One meaningful line of the acceptance criteria together with how deeply it is indented. */
	private record Line(String text, int indent) {}
}
