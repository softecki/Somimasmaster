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

import com.somimas.saas.audit.AuditService;
import com.somimas.saas.billing.InvoiceService;
import com.somimas.saas.web.ApiException;
import com.somimas.saas.web.dto.request.BankDepositReviewRequest;
import com.somimas.saas.web.dto.response.BankDepositResponse;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankDepositReviewService {

    private final BankDepositRepository bankDepositRepository;
    private final BankDepositReviewRepository bankDepositReviewRepository;
    private final InvoiceService invoiceService;
    private final AuditService auditService;

    public BankDepositReviewService(BankDepositRepository bankDepositRepository,
            BankDepositReviewRepository bankDepositReviewRepository, InvoiceService invoiceService, AuditService auditService) {
        this.bankDepositRepository = bankDepositRepository;
        this.bankDepositReviewRepository = bankDepositReviewRepository;
        this.invoiceService = invoiceService;
        this.auditService = auditService;
    }

    @Transactional
    public BankDepositResponse review(Long depositId, Long reviewerIdentityId, BankDepositReviewRequest request) {
        BankDeposit deposit = bankDepositRepository.findById(depositId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Deposit not found"));
        if (!"PENDING_REVIEW".equals(deposit.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Deposit is not pending review");
        }
        if (reviewerIdentityId != null && reviewerIdentityId.equals(deposit.getSubmittedBy())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Submitter cannot review their own deposit");
        }
        BankDepositReview review = new BankDepositReview();
        review.setDepositId(depositId);
        review.setReviewerIdentityId(reviewerIdentityId);
        review.setDecision(request.decision());
        review.setNotes(request.notes());
        review.setReviewedAt(LocalDateTime.now());
        bankDepositReviewRepository.save(review);

        if ("APPROVED".equals(request.decision())) {
            deposit.setStatus("APPROVED");
            invoiceService.createInvoice(deposit.getOrganizationId(), "Bank deposit " + deposit.getReference(),
                    deposit.getAmount(), deposit.getCurrency());
        } else {
            deposit.setStatus("REJECTED");
        }
        bankDepositRepository.save(deposit);
        auditService.record(reviewerIdentityId, deposit.getOrganizationId(), "bank_deposit.review", "bank_deposit",
                depositId, request.decision());
        return new BankDepositResponse(deposit.getId(), deposit.getStatus(), deposit.getAmount(), deposit.getCurrency(),
                deposit.getReference(), deposit.getSubmittedAt());
    }
}
