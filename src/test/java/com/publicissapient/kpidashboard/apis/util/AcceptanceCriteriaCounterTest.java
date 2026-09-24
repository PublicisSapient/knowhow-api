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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.publicissapient.kpidashboard.apis.util.AcceptanceCriteriaCounter.Format;
import com.publicissapient.kpidashboard.apis.util.AcceptanceCriteriaCounter.Result;

/** Tests for {@link AcceptanceCriteriaCounter} — the counting engine behind kpi227. */
class AcceptanceCriteriaCounterTest {

	@Test
	@DisplayName("null, blank and whitespace-only criteria report zero with format NONE")
	void emptyInput() {
		assertEquals(Result.none(), AcceptanceCriteriaCounter.count(null, Format.AUTO));
		assertEquals(Result.none(), AcceptanceCriteriaCounter.count("", Format.AUTO));
		assertEquals(Result.none(), AcceptanceCriteriaCounter.count("   \n\t\n  ", Format.AUTO));
	}

	@Test
	@DisplayName("a heading with no criteria under it still reports zero")
	void headingOnly() {
		assertEquals(
				Result.none(), AcceptanceCriteriaCounter.count("Acceptance Criteria:", Format.AUTO));
		assertEquals(
				Result.none(), AcceptanceCriteriaCounter.count("h3. Acceptance Criteria", Format.AUTO));
		assertEquals(
				Result.none(),
				AcceptanceCriteriaCounter.count("**Acceptance Criteria**\n---", Format.AUTO));
	}

	@Test
	@DisplayName("named Gherkin scenarios are counted, Examples data tables are not")
	void gherkinScenarios() {
		String text =
				"""
				Feature: Checkout
				Scenario: Card payment succeeds
					Given a valid card
					When the customer pays
					Then the order is confirmed
				Scenario Outline: Declined cards
					Given a <type> card
					Then the payment is declined
				Examples:
					| type |
					| expired |
				""";

		Result result = AcceptanceCriteriaCounter.count(text, Format.AUTO);
		assertEquals(2, result.count());
		assertEquals(Format.GHERKIN, result.format());
	}

	@Test
	@DisplayName("unnamed Gherkin falls back to counting Given steps, never And/But")
	void gherkinGivenSteps() {
		String text =
				"""
				Given the user is logged in
				When they open the cart
				And the cart has items
				Then the total is shown
				Given the user is anonymous
				But the cart is empty
				Then a prompt is shown
				""";

		Result result = AcceptanceCriteriaCounter.count(text, Format.AUTO);
		assertEquals(2, result.count());
		assertEquals(Format.GHERKIN, result.format());
	}

	@Test
	@DisplayName("bullets, checkboxes and numbered items are all recognised as list items")
	void listShapes() {
		assertEquals(3, AcceptanceCriteriaCounter.count("- one\n- two\n- three", Format.AUTO).count());
		assertEquals(
				3, AcceptanceCriteriaCounter.count("* one\n\u2022 two\n+ three", Format.AUTO).count());
		assertEquals(
				3,
				AcceptanceCriteriaCounter.count("- [ ] one\n- [x] two\n- [ ] three", Format.AUTO).count());
		assertEquals(
				3, AcceptanceCriteriaCounter.count("1. one\n2) two\n(3) three", Format.AUTO).count());
		assertEquals(
				2,
				AcceptanceCriteriaCounter.count("AC1: must log in\nAC 2 - must log out", Format.AUTO)
						.count());
	}

	@Test
	@DisplayName("nested sub-bullets belong to their parent criterion and are not counted separately")
	void nestedListItemsAreNotCounted() {
		String text =
				"""
				Acceptance Criteria:
				- The user can log in
						- with email
						- with SSO
				- The user can log out
				""";

		Result result = AcceptanceCriteriaCounter.count(text, Format.AUTO);
		assertEquals(2, result.count());
		assertEquals(Format.LIST, result.format());
	}

	@Test
	@DisplayName("plain prose falls back to one criterion per line")
	void plainLines() {
		String text =
				"""
				Acceptance Criteria
				The report must export to Excel
				The export must include the header row
				""";

		Result result = AcceptanceCriteriaCounter.count(text, Format.AUTO);
		assertEquals(2, result.count());
		assertEquals(Format.LINE, result.format());
	}

	@Test
	@DisplayName("an explicitly configured format that matches nothing falls back to line counting")
	void explicitFormatFallsBack() {
		Result result = AcceptanceCriteriaCounter.count("- one\n- two", Format.GHERKIN);
		assertEquals(2, result.count());
		assertEquals(Format.LINE, result.format());
	}

	@Test
	@DisplayName("an explicitly configured format wins over auto-detection")
	void explicitFormatWins() {
		String text = "Scenario: pays by card\n- bullet that is not a scenario";

		assertEquals(1, AcceptanceCriteriaCounter.count(text, Format.GHERKIN).count());
		assertEquals(1, AcceptanceCriteriaCounter.count(text, Format.LIST).count());
		assertEquals(2, AcceptanceCriteriaCounter.count(text, Format.LINE).count());
	}

	@Test
	@DisplayName("windows and old-mac line endings are handled")
	void lineEndings() {
		assertEquals(3, AcceptanceCriteriaCounter.count("- a\r\n- b\r\n- c", Format.AUTO).count());
		assertEquals(3, AcceptanceCriteriaCounter.count("- a\r- b\r- c", Format.AUTO).count());
	}

	@Test
	@DisplayName("configured format names are resolved leniently, unknown values fall back to AUTO")
	void formatParsing() {
		assertEquals(Format.AUTO, AcceptanceCriteriaCounter.parseFormat(null));
		assertEquals(Format.AUTO, AcceptanceCriteriaCounter.parseFormat("   "));
		assertEquals(Format.AUTO, AcceptanceCriteriaCounter.parseFormat("not-a-format"));
		assertEquals(Format.AUTO, AcceptanceCriteriaCounter.parseFormat("NONE"));
		assertEquals(Format.GHERKIN, AcceptanceCriteriaCounter.parseFormat(" gherkin "));
		assertEquals(Format.LIST, AcceptanceCriteriaCounter.parseFormat("List"));
		assertEquals(Format.LINE, AcceptanceCriteriaCounter.parseFormat("LINE"));
	}
}
