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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * Decides whether a single edit to a Jira text field is a <b>substantive revision</b> — a real
 * rewrite of the content — rather than a formatting tweak, a typo fix or a markup change.
 *
 * <p>Both texts are normalised (markup stripped, punctuation dropped, whitespace collapsed,
 * lower-cased) and reduced to a word multiset. The change magnitude is the symmetric difference of
 * those multisets expressed as a percentage of the larger text:
 *
 * <pre>
 * changePercent = (wordsAdded + wordsRemoved) / max(wordsBefore, wordsAfter) * 100
 * </pre>
 *
 * <p>An edit is substantive when that percentage reaches the configured threshold. Filling in a
 * previously empty field, or wiping one out, is always 100% and therefore always substantive.
 *
 * <p>Used by kpi228 (DOR Acceptance Rate) to separate stories that passed Definition of Ready
 * cleanly from stories that had to be rewritten after being marked Ready.
 */
public final class DorRevisionAnalyzer {

	/** Issue fields whose changelog can be inspected for revisions. */
	public enum RevisionField {
		/** The Jira {@code description} field. */
		DESCRIPTION,
		/** The custom field configured as the acceptance criteria. */
		ACCEPTANCE_CRITERIA
	}

	/** Default minimum share of the text that must change for an edit to count. */
	public static final double DEFAULT_SUBSTANTIVE_CHANGE_PERCENT = 20.0d;

	/** Default number of substantive revisions a story is allowed before it fails DOR. */
	public static final int DEFAULT_MAJOR_REWRITE_REVISION_COUNT = 2;

	/** Wiki markup, Markdown emphasis, headings, list markers, panel and code block wrappers. */
	private static final Pattern MARKUP =
			Pattern.compile(
					"\\{[a-z]+(:[^}]*)?\\}"
							+ "|<[^>]+>"
							+ "|^\\s*h[1-6]\\.\\s*"
							+ "|^\\s*[*#\\-+]+\\s+"
							+ "|^\\s*\\d+[.)]\\s+"
							+ "|[*_~`|\\[\\]]",
					Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

	private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{Nd}\\s]+");

	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	private DorRevisionAnalyzer() {
		// utility class
	}

	/**
	 * Resolves a configured revision field name onto the enum.
	 *
	 * @param configuredValue the raw project configuration value, may be {@code null}
	 * @return the matching field, or {@code null} when the value is blank or unknown
	 */
	public static RevisionField parseField(String configuredValue) {
		if (StringUtils.isBlank(configuredValue)) {
			return null;
		}
		try {
			return RevisionField.valueOf(configuredValue.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException unknownValue) { // NOSONAR - configuration is user supplied
			return null;
		}
	}

	/**
	 * Percentage of the text that changed between the two versions.
	 *
	 * @param before the field value before the edit, may be {@code null}
	 * @param after the field value after the edit, may be {@code null}
	 * @return a value between {@code 0.0} and {@code 100.0}
	 */
	public static double changePercent(String before, String after) {
		List<String> beforeWords = words(before);
		List<String> afterWords = words(after);

		if (beforeWords.isEmpty() && afterWords.isEmpty()) {
			return 0.0d;
		}
		if (beforeWords.isEmpty() || afterWords.isEmpty()) {
			return 100.0d;
		}

		Map<String, Integer> counts = new HashMap<>();
		beforeWords.forEach(word -> counts.merge(word, 1, Integer::sum));
		afterWords.forEach(word -> counts.merge(word, -1, Integer::sum));

		int changed = counts.values().stream().mapToInt(Math::abs).sum();
		int largest = Math.max(beforeWords.size(), afterWords.size());
		return Math.min(100.0d, changed * 100.0d / largest);
	}

	/**
	 * Whether an edit changed enough of the text to count as a substantive revision.
	 *
	 * @param before the field value before the edit, may be {@code null}
	 * @param after the field value after the edit, may be {@code null}
	 * @param minimumChangePercent the configured threshold; non-positive values fall back to {@link
	 *     #DEFAULT_SUBSTANTIVE_CHANGE_PERCENT}
	 * @return {@code true} when the edit is a real rewrite rather than a cosmetic change
	 */
	public static boolean isSubstantive(String before, String after, Double minimumChangePercent) {
		double threshold =
				minimumChangePercent == null || minimumChangePercent <= 0.0d
						? DEFAULT_SUBSTANTIVE_CHANGE_PERCENT
						: minimumChangePercent;
		return changePercent(before, after) >= threshold;
	}

	private static List<String> words(String text) {
		if (StringUtils.isBlank(text)) {
			return new ArrayList<>();
		}
		String normalised =
				WHITESPACE
						.matcher(NON_WORD.matcher(MARKUP.matcher(text).replaceAll(" ")).replaceAll(" "))
						.replaceAll(" ")
						.trim()
						.toLowerCase(Locale.ROOT);
		if (normalised.isEmpty()) {
			return new ArrayList<>();
		}
		return new ArrayList<>(List.of(normalised.split(" ")));
	}
}
