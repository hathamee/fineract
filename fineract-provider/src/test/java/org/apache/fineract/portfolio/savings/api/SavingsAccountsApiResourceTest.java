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
package org.apache.fineract.portfolio.savings.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.savings.SavingsApiConstants;
import org.apache.fineract.portfolio.savings.data.SavingsAccountData;
import org.apache.fineract.portfolio.savings.service.SavingsAccountReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsAccountsApiResourceTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppUser appUser;
    @Mock
    private SavingsAccountReadPlatformService savingsAccountReadPlatformService;
    @Mock
    private ApiRequestParameterHelper apiRequestParameterHelper;
    @Mock
    private DefaultToApiJsonSerializer<SavingsAccountData> toApiJsonSerializer;
    @Mock
    private UriInfo uriInfo;

    @InjectMocks
    private SavingsAccountsApiResource underTest;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(appUser);
    }

    @ParameterizedTest(name = "birthMonth={0}, birthDay={1}: reject {2}")
    @CsvSource(value = { "0, null, birthMonth", "13, null, birthMonth", "null, 0, birthDay", "null, 32, birthDay",
            "2, 30, birthDay",
            "4, 31, birthDay", "6, 31, birthDay", "9, 31, birthDay", "11, 31, birthDay", "0, 1, birthMonth",
            "13, 1, birthMonth" }, nullValues = "null")
    void retrieveAll_rejectsInvalidBirthdayFilters(Integer birthMonth, Integer birthDay, String invalidParameter) {
        final PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.retrieveAll(uriInfo, null, null, birthMonth, birthDay, null, null, null, null));

        assertThat(exception.getErrors()).extracting(ApiParameterError::getParameterName).contains(invalidParameter);
        verifyNoInteractions(savingsAccountReadPlatformService);
    }

    @ParameterizedTest(name = "birthMonth={0}, birthDay={1}: accept and forward")
    @CsvSource(value = { "null, null", "11, null", "null, 31", "11, 3", "2, 29", "4, 30", "1, 1",
            "12, 31" }, nullValues = "null")
    void retrieveAll_acceptsValidBirthdayFilters(Integer birthMonth, Integer birthDay) {
        final Page<SavingsAccountData> page = new Page<>(List.of(), 0);
        final ApiRequestJsonSerializationSettings settings = mock(ApiRequestJsonSerializationSettings.class);
        when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());
        when(apiRequestParameterHelper.process(any())).thenReturn(settings);
        when(savingsAccountReadPlatformService.retrieveAll(any(SearchParameters.class))).thenReturn(page);
        when(toApiJsonSerializer.serialize(settings, page,
                SavingsApiSetConstants.SAVINGS_ACCOUNT_RESPONSE_DATA_PARAMETERS))
                .thenReturn("serialized");

        final String response = underTest.retrieveAll(uriInfo, null, "test-account", birthMonth, birthDay, 5, 10,
                "sa.id",
                "ASC");

        final ArgumentCaptor<SearchParameters> parameters = ArgumentCaptor.forClass(SearchParameters.class);
        verify(savingsAccountReadPlatformService).retrieveAll(parameters.capture());
        assertThat(parameters.getValue().getBirthMonth()).isEqualTo(birthMonth);
        assertThat(parameters.getValue().getBirthDay()).isEqualTo(birthDay);
        assertThat(parameters.getValue().getExternalId()).isEqualTo("test-account");
        assertThat(parameters.getValue().getOffset()).isEqualTo(5);
        assertThat(parameters.getValue().getLimit()).isEqualTo(10);
        assertThat(parameters.getValue().getOrderBy()).isEqualTo("sa.id");
        assertThat(parameters.getValue().getSortOrder()).isEqualTo("ASC");
        assertThat(response).isEqualTo("serialized");
        verify(appUser).validateHasReadPermission(SavingsApiConstants.SAVINGS_ACCOUNT_RESOURCE_NAME);
    }
}
