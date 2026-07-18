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
package com.somimas.saas.web;

import com.somimas.saas.billing.InvoiceService;
import com.somimas.saas.organization.OrganizationAuthorizationService;
import com.somimas.saas.web.dto.response.InvoiceResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/organizations/{id}/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final OrganizationAuthorizationService organizationAuthorizationService;

    public InvoiceController(InvoiceService invoiceService,
            OrganizationAuthorizationService organizationAuthorizationService) {
        this.invoiceService = invoiceService;
        this.organizationAuthorizationService = organizationAuthorizationService;
    }

    @GetMapping
    public List<InvoiceResponse> listInvoices(@PathVariable("id") Long organizationId) {
        organizationAuthorizationService.requireMembership(SecurityUtils.currentIdentityId(), organizationId);
        return invoiceService.listForOrganization(organizationId);
    }

    @GetMapping("/{invoiceId}")
    public InvoiceResponse getInvoice(@PathVariable("id") Long organizationId, @PathVariable Long invoiceId) {
        organizationAuthorizationService.requireMembership(SecurityUtils.currentIdentityId(), organizationId);
        return invoiceService.getForOrganization(organizationId, invoiceId);
    }
}
