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
package com.somimas.saas.billing;

import com.somimas.saas.web.ApiException;
import com.somimas.saas.web.dto.response.InvoiceLineResponse;
import com.somimas.saas.web.dto.response.InvoiceResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;

    public InvoiceService(InvoiceRepository invoiceRepository, InvoiceLineRepository invoiceLineRepository) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> listForOrganization(Long organizationId) {
        return invoiceRepository.findByOrganizationIdOrderByIssuedAtDesc(organizationId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getForOrganization(Long organizationId, Long invoiceId) {
        Invoice invoice = invoiceRepository.findByIdAndOrganizationId(invoiceId, organizationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invoice not found"));
        return toResponse(invoice);
    }

    @Transactional
    public Invoice createInvoice(Long organizationId, String description, BigDecimal amount, String currency) {
        LocalDateTime now = LocalDateTime.now();
        Invoice invoice = new Invoice();
        invoice.setOrganizationId(organizationId);
        invoice.setStatus("OPEN");
        invoice.setAmountTotal(amount);
        invoice.setCurrency(currency);
        invoice.setDueDate(now.plusDays(14));
        invoice.setIssuedAt(now);
        invoice = invoiceRepository.save(invoice);

        InvoiceLine line = new InvoiceLine();
        line.setInvoiceId(invoice.getId());
        line.setDescription(description);
        line.setAmount(amount);
        line.setQuantity(1);
        invoiceLineRepository.save(line);
        return invoice;
    }

    @Transactional
    public void markPaid(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invoice not found"));
        invoice.setStatus("PAID");
        invoice.setPaidAt(LocalDateTime.now());
        invoiceRepository.save(invoice);
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        List<InvoiceLineResponse> lines = invoiceLineRepository.findByInvoiceId(invoice.getId()).stream()
                .map(line -> new InvoiceLineResponse(line.getDescription(), line.getAmount(), line.getQuantity()))
                .toList();
        return new InvoiceResponse(invoice.getId(), invoice.getStatus(), invoice.getAmountTotal(), invoice.getCurrency(),
                invoice.getDueDate(), invoice.getIssuedAt(), invoice.getPaidAt(), lines);
    }
}
