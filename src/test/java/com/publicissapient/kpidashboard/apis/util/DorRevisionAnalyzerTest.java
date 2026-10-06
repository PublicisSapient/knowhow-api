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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.publicissapient.kpidashboard.apis.util.DorRevisionAnalyzer.RevisionField;

/** Tests for {@link DorRevisionAnalyzer} — the revision engine behind kpi228. */
class DorRevisionAnalyzerTest {

	@Test
	@DisplayName("two empty versions have not changed at all")
	void bothEmpty() {
		assertEquals(0.0d, DorRevisionAnalyzer.changePercent(null, null));
		assertEquals(0.0d, DorRevisionAnalyzer.changePercent("", "   \n  "));
		assertFalse(DorRevisionAnalyzer.isSubstantive(null, "", null));
	}

	@Test
	@DisplayName("filling in or wiping out a field is always a substantive revision")
	void emptyToPopulated() {
		assertEquals(100.0d, DorRevisionAnalyzer.changePercent(null, "As a user I want to log in"));
		assertEquals(100.0d, DorRevisionAnalyzer.changePercent("As a user I want to log in", ""));
		assertTrue(DorRevisionAnalyzer.isSubstantive("", "As a user I want to log in", null));
	}

	@Test
	@DisplayName("markup, punctuation and whitespace only edits are not substantive")
	void cosmeticEditsIgnored() {
		String before = "As a user I want to log in so that I can see my orders";
		String after =
				"""
				* As a user, I want to log in — *so that* I can see my orders.
				""";
		assertEquals(0.0d, DorRevisionAnalyzer.changePercent(before, after));
		assertFalse(DorRevisionAnalyzer.isSubstantive(before, after, null));
	}

	@Test
	@DisplayName("a typo fix in a long description falls below the default threshold")
	void typoFixIsNotSubstantive() {
		String before =
				"As a registered customer I want to reset my password from the login screen so that "
						+ "I can recover access without contacting support in any circumstance";
		String after =
				"As a registered customer I want to reset my passwrd from the login screen so that "
						+ "I can recover access without contacting support in any circumstance";
		assertFalse(DorRevisionAnalyzer.isSubstantive(before, after, null));
	}

	@Test
	@DisplayName("replacing the body of the text is a substantive revision")
	void rewriteIsSubstantive() {
		String before = "As a user I want to log in so that I can see my orders";
		String after = "As an admin I need bulk export of invoices filtered by tax region";
		assertTrue(DorRevisionAnalyzer.changePercent(before, after) >= 20.0d);
		assertTrue(DorRevisionAnalyzer.isSubstantive(before, after, null));
	}

	@Test
	@DisplayName("a stricter project threshold suppresses smaller rewrites")
	void configuredThresholdIsHonoured() {
		String before = "the payment service must retry three times before failing the order";
		String after = "the payment service must retry five times before failing the order";
		assertTrue(DorRevisionAnalyzer.isSubstantive(before, after, 10.0d));
		assertFalse(DorRevisionAnalyzer.isSubstantive(before, after, 90.0d));
	}

	@Test
	@DisplayName("a non-positive configured threshold falls back to the default")
	void invalidThresholdFallsBack() {
		// one word swapped out of twenty is a 10% change - below the 20% default
		String before =
				"one two three four five six seven eight nine ten "
						+ "eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty";
		String after =
				"one two three four five six seven eight nine ten "
						+ "eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen changed";
		assertFalse(DorRevisionAnalyzer.isSubstantive(before, after, 0.0d));
		assertFalse(DorRevisionAnalyzer.isSubstantive(before, after, -5.0d));
		assertTrue(DorRevisionAnalyzer.isSubstantive(before, after, 10.0d));
	}

	@Test
	@DisplayName("configured field names resolve onto the enum, unknown values are dropped")
	void parseField() {
		assertEquals(RevisionField.DESCRIPTION, DorRevisionAnalyzer.parseField("DESCRIPTION"));
		assertEquals(
				RevisionField.ACCEPTANCE_CRITERIA, DorRevisionAnalyzer.parseField(" acceptance_criteria "));
		assertNull(DorRevisionAnalyzer.parseField(null));
		assertNull(DorRevisionAnalyzer.parseField(""));
		assertNull(DorRevisionAnalyzer.parseField("SUMMARY"));
	}
}
