/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.portfolio.savings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.utils.ColumnValidator;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class SavingsAccountReadPlatformServiceImplTest {

    static Stream<Arguments> birthdayFilterCases() {
        return Stream.of(
                Arguments.of(null, null, new Object[] { ".1.%", "test-account" }),
                Arguments.of(11, null, new Object[] { ".1.%", "test-account", 11 }),
                Arguments.of(null, 3, new Object[] { ".1.%", "test-account", 3 }),
                Arguments.of(11, 3, new Object[] { ".1.%", "test-account", 11, 3 }));
    }

    @ParameterizedTest(name = "birthMonth={0}, birthDay={1}")
    @MethodSource("birthdayFilterCases")
    @SuppressWarnings("unchecked")
    void retrieveAll_appliesOptionalBirthdayFilters(
            Integer birthMonth, Integer birthDay, Object[] expectedQueryParams) {

        final PlatformSecurityContext context = mock(PlatformSecurityContext.class);
        final AppUser appUser = mock(AppUser.class);
        final Office office = mock(Office.class);
        final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        final PaginationHelper paginationHelper = mock(PaginationHelper.class);
        final DatabaseSpecificSQLGenerator sqlGenerator = mock(DatabaseSpecificSQLGenerator.class);
        final ColumnValidator columnValidator = mock(ColumnValidator.class);

        when(context.authenticatedUser()).thenReturn(appUser);
        when(appUser.getOffice()).thenReturn(office);
        when(office.getHierarchy()).thenReturn(".1.");
        when(sqlGenerator.calcFoundRows()).thenReturn("");

        final SavingsAccountReadPlatformServiceImpl service = new SavingsAccountReadPlatformServiceImpl(
                context, jdbcTemplate, null, null, null, null, null,
                null, null, columnValidator, null, paginationHelper, sqlGenerator);

        final SearchParameters searchParameters = SearchParameters.forSavings(
                "sa.status_enum = 300", "test-account", null, 0,
                null, null, birthMonth, birthDay);

        service.retrieveAll(searchParameters);

        final ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Object[]> queryParams = ArgumentCaptor.forClass(Object[].class);

        verify(paginationHelper).fetchPage(
                eq(jdbcTemplate), sql.capture(), queryParams.capture(), any());

        assertThat(sql.getValue()).contains(
                "where o.hierarchy like ?",
                "and (sa.status_enum = 300)",
                "and sa.external_id = ?");

        if (birthMonth == null) {
            assertThat(sql.getValue()).doesNotContain("month(c.date_of_birth)");
        } else {
            assertThat(sql.getValue()).contains("and month(c.date_of_birth) = ?");
        }

        if (birthDay == null) {
            assertThat(sql.getValue()).doesNotContain("day(c.date_of_birth)");
        } else {
            assertThat(sql.getValue()).contains("and day(c.date_of_birth) = ?");
        }

        assertThat(queryParams.getValue()).containsExactly(expectedQueryParams);
    }
}
