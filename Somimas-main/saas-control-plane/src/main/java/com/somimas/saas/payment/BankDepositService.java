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

import com.somimas.saas.web.ApiException;
import com.somimas.saas.web.dto.request.BankDepositSubmitRequest;
import com.somimas.saas.web.dto.response.BankDepositResponse;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankDepositService {

    private final BankDepositRepository bankDepositRepository;
    private final BankAccountRepository bankAccountRepository;

    public BankDepositService(BankDepositRepository bankDepositRepository, BankAccountRepository bankAccountRepository) {
        this.bankDepositRepository = bankDepositRepository;
        this.bankAccountRepository = bankAccountRepository;
    }

    @Transactional
    public BankDepositResponse submit(Long organizationId, Long identityId, BankDepositSubmitRequest request) {
        BankAccount account = bankAccountRepository.findById(request.bankAccountId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bank account not found"));
        if (!organizationId.equals(account.getOrganizationId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bank account does not belong to organization");
        }
        BankDeposit deposit = new BankDeposit();
        deposit.setOrganizationId(organizationId);
        deposit.setBankAccountId(request.bankAccountId());
        deposit.setAmount(request.amount());
        deposit.setCurrency(request.currency());
        deposit.setReference(request.reference());
        deposit.setStatus("PENDING_REVIEW");
        deposit.setSubmittedBy(identityId);
        deposit.setSubmittedAt(LocalDateTime.now());
        deposit.setMetadata(request.metadata());
        deposit = bankDepositRepository.save(deposit);
        return new BankDepositResponse(deposit.getId(), deposit.getStatus(), deposit.getAmount(), deposit.getCurrency(),
                deposit.getReference(), deposit.getSubmittedAt());
    }
}
