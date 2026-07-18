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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private InvoiceLineRepository invoiceLineRepository;

    @InjectMocks
    private InvoiceService invoiceService;

    @Test
    void createInvoicePersistsHeaderAndLine() {
        Invoice invoice = new Invoice();
        invoice.setId(1L);
        invoice.setOrganizationId(10L);
        invoice.setStatus("OPEN");
        invoice.setAmountTotal(new BigDecimal("49.00"));
        invoice.setCurrency("USD");
        invoice.setIssuedAt(LocalDateTime.now());
        when(invoiceRepository.save(any(Invoice.class))).thenReturn(invoice);

        Invoice created = invoiceService.createInvoice(10L, "Starter plan", new BigDecimal("49.00"), "USD");

        assertEquals("OPEN", created.getStatus());
        verify(invoiceLineRepository).save(any(InvoiceLine.class));
    }

    @Test
    void listForOrganizationReturnsInvoices() {
        Invoice invoice = new Invoice();
        invoice.setId(1L);
        invoice.setOrganizationId(10L);
        invoice.setStatus("OPEN");
        invoice.setAmountTotal(new BigDecimal("49.00"));
        invoice.setCurrency("USD");
        invoice.setIssuedAt(LocalDateTime.now());
        when(invoiceRepository.findByOrganizationIdOrderByIssuedAtDesc(10L)).thenReturn(List.of(invoice));
        when(invoiceLineRepository.findByInvoiceId(1L)).thenReturn(List.of());

        assertEquals(1, invoiceService.listForOrganization(10L).size());
    }
}
