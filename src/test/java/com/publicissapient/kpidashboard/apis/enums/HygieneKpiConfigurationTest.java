/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 ******************************************************************************/
package com.publicissapient.kpidashboard.apis.enums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.Test;

import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.util.FieldMappingHelper;

/**
 * Guards the configuration wiring of the AI hygiene KPIs.
 *
 * <p>Several of these registrations fail <em>at runtime</em> rather than at compile time, which is
 * how they get missed:
 *
 * <ul>
 *   <li>{@code FieldMappingServiceImpl} resolves the config screen with {@code
 *       FieldMappingEnum.valueOf(kpiId.toUpperCase())} — a missing constant throws {@link
 *       IllegalArgumentException} when a user opens the KPI's field mapping.
 *   <li>{@code FieldMappingHelper.getAccessibleFieldHistory} reflects on {@code "history" +
 *       fieldName} against {@link FieldMapping}'s superclass — a missing history field throws
 *       {@link NoSuchFieldException} when the mapping is saved or its history viewed.
 *   <li>A typo in a {@code FieldMappingEnum} field name silently yields an empty config section.
 * </ul>
 *
 * <p>The checks are run against both hygiene KPIs so the invariant, not just one KPI, is protected.
 */
public class HygieneKpiConfigurationTest {

	@Test
	public void kpiCodesAreRegisteredWithTheExpectedIdAndSource() {
		assertEquals("kpi311", KPICode.STORY_HYGIENE.getKpiId());
		assertEquals(KPISource.JIRA.name(), KPICode.STORY_HYGIENE.getSource());

		assertEquals("kpi312", KPICode.EPIC_HYGIENE.getKpiId());
		assertEquals(KPISource.JIRA.name(), KPICode.EPIC_HYGIENE.getSource());
	}

	@Test
	public void kpiCodeLookupResolvesEpicHygiene() {
		assertEquals(KPICode.EPIC_HYGIENE, KPICode.getKPI("kpi312"));
	}

	/** Mirrors exactly what {@code FieldMappingServiceImpl} does when the config screen opens. */
	@Test
	public void fieldMappingEnumResolvesFromKpiIdForBothHygieneKpis() {
		assertNotNull(FieldMappingEnum.valueOf(KPICode.STORY_HYGIENE.getKpiId().toUpperCase()));
		assertNotNull(FieldMappingEnum.valueOf(KPICode.EPIC_HYGIENE.getKpiId().toUpperCase()));
	}

	@Test
	public void epicHygieneFieldMappingEnumDeclaresBothConfigurableFields() {
		List<String> fields = FieldMappingEnum.KPI312.getFields();

		assertEquals("Epic Hygiene", FieldMappingEnum.KPI312.getKpiName());
		assertEquals(KPISource.JIRA.name(), FieldMappingEnum.KPI312.getKpiSource());
		assertTrue(fields.contains("jiraFieldsSelectionKPI312"));
		assertTrue(fields.contains("thresholdValueKPI312"));
	}

	/** A typo here silently renders an empty config section, so every name must really exist. */
	@Test
	public void everyHygieneFieldMappingNameExistsOnFieldMapping() throws Exception {
		for (FieldMappingEnum kpi : List.of(FieldMappingEnum.KPI311, FieldMappingEnum.KPI312)) {
			for (String field : kpi.getFields()) {
				assertNotNull(
						kpi.name() + " declares '" + field + "' which does not exist on FieldMapping",
						FieldMapping.class.getDeclaredField(field));
			}
		}
	}

	/**
	 * {@code FieldMappingHelper.getAccessibleFieldHistory} reflects on {@code "history" + fieldName}
	 * against {@code FieldMapping}'s superclass, throwing when the audit field is missing.
	 */
	@Test
	public void everyHygieneFieldMappingNameHasAMatchingHistoryField() throws Exception {
		Class<?> historyClass = FieldMapping.class.getSuperclass();

		for (FieldMappingEnum kpi : List.of(FieldMappingEnum.KPI311, FieldMappingEnum.KPI312)) {
			for (String field : kpi.getFields()) {
				String historyField = FieldMappingHelper.HISTORY + field;
				Field declared = historyClass.getDeclaredField(historyField);
				assertNotNull(kpi.name() + " is missing audit field '" + historyField + "'", declared);
			}
		}
	}
}
