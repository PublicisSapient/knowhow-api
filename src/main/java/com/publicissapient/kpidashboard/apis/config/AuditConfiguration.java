/*
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.config;

import java.lang.reflect.Field;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import com.publicissapient.kpidashboard.apis.auth.service.AuthenticationService;

@Configuration
@EnableMongoAuditing(modifyOnCreate = false, setDates = true)
public class AuditConfiguration {

	private AuthenticationService authenticationService;

	@Autowired
	public AuditConfiguration(AuthenticationService authenticationService) {
		this.authenticationService = authenticationService;
	}

	@Bean
	public AuditorAware<String> auditorProvider() {
		return () -> Optional.empty();
	}

	@Component
	class ConditionalAuditListener extends AbstractMongoEventListener<Object> {
		@Override
		public void onBeforeConvert(BeforeConvertEvent<Object> event) {
			Object entity = event.getSource();
			String currentUser = authenticationService.getLoggedInUser();
			if (currentUser == null) return;

			Field createdByField = ReflectionUtils.findField(entity.getClass(), "createdBy");
			if (createdByField != null) {
				ReflectionUtils.makeAccessible(createdByField);
				Object createdBy = ReflectionUtils.getField(createdByField, entity);
				if (createdBy == null || ((String) createdBy).isEmpty()) {
					ReflectionUtils.setField(createdByField, entity, currentUser);
				}
			}

			Field updatedByField = ReflectionUtils.findField(entity.getClass(), "updatedBy");
			if (updatedByField != null) {
				ReflectionUtils.makeAccessible(updatedByField);
				Object updatedBy = ReflectionUtils.getField(updatedByField, entity);
				if (updatedBy == null || ((String) updatedBy).isEmpty()) {
					ReflectionUtils.setField(updatedByField, entity, currentUser);
				}
			}
		}
	}
}
