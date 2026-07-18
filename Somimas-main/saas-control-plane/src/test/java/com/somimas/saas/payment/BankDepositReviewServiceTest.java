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
package com.somimas.saas.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.somimas.saas.audit.AuditService;
import com.somimas.saas.billing.InvoiceService;
import com.somimas.saas.web.ApiException;
import com.somimas.saas.web.dto.request.BankDepositReviewRequest;
import com.somimas.saas.web.dto.response.BankDepositResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BankDepositReviewServiceTest {

    @Mock
    private BankDepositRepository bankDepositRepository;
    @Mock
    private BankDepositReviewRepository bankDepositReviewRepository;
    @Mock
    private InvoiceService invoiceService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private BankDepositReviewService bankDepositReviewService;

    @Test
    void approveDepositCreatesInvoiceAndMarksApproved() {
        BankDeposit deposit = new BankDeposit();
        deposit.setId(1L);
        deposit.setOrganizationId(10L);
        deposit.setStatus("PENDING_REVIEW");
        deposit.setAmount(new BigDecimal("100.00"));
        deposit.setCurrency("USD");
        deposit.setReference("REF-001");
        deposit.setSubmittedAt(LocalDateTime.now());
        when(bankDepositRepository.findById(1L)).thenReturn(Optional.of(deposit));
        when(bankDepositRepository.save(any(BankDeposit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BankDepositResponse response = bankDepositReviewService.review(1L, 99L,
                new BankDepositReviewRequest("APPROVED", "Looks good"));

        assertEquals("APPROVED", response.status());
        verify(invoiceService).createInvoice(10L, "Bank deposit REF-001", new BigDecimal("100.00"), "USD");
        verify(auditService).record(99L, 10L, "bank_deposit.review", "bank_deposit", 1L, "APPROVED");
    }

    @Test
    void submitterCannotApproveOwnDeposit() {
        BankDeposit deposit = new BankDeposit();
        deposit.setId(1L);
        deposit.setOrganizationId(10L);
        deposit.setStatus("PENDING_REVIEW");
        deposit.setSubmittedBy(99L);
        when(bankDepositRepository.findById(1L)).thenReturn(Optional.of(deposit));

        assertThrows(ApiException.class,
                () -> bankDepositReviewService.review(1L, 99L, new BankDepositReviewRequest("APPROVED", "self")));
    }
}
