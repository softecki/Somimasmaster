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

import com.somimas.saas.billing.Invoice;
import com.somimas.saas.billing.InvoiceRepository;
import com.somimas.saas.config.SomimasProperties;
import com.somimas.saas.web.ApiException;
import com.somimas.saas.web.dto.request.CheckoutSessionRequest;
import com.somimas.saas.web.dto.response.CheckoutSessionResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final InvoiceRepository invoiceRepository;
    private final SomimasProperties somimasProperties;

    public PaymentService(PaymentRepository paymentRepository, PaymentAttemptRepository paymentAttemptRepository,
            InvoiceRepository invoiceRepository, SomimasProperties somimasProperties) {
        this.paymentRepository = paymentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.invoiceRepository = invoiceRepository;
        this.somimasProperties = somimasProperties;
    }

    @Transactional
    public CheckoutSessionResponse createCheckoutSession(Long organizationId, CheckoutSessionRequest request) {
        Invoice invoice = invoiceRepository.findByIdAndOrganizationId(request.invoiceId(), organizationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invoice not found"));
        if (!"OPEN".equals(invoice.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invoice is not open for payment");
        }
        LocalDateTime now = LocalDateTime.now();
        Payment payment = new Payment();
        payment.setOrganizationId(organizationId);
        payment.setInvoiceId(invoice.getId());
        payment.setAmount(invoice.getAmountTotal());
        payment.setCurrency(invoice.getCurrency());
        payment.setStatus("PENDING");
        payment.setGatewayRef("flw-" + UUID.randomUUID());
        payment.setCreatedAt(now);
        payment = paymentRepository.save(payment);

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setPaymentId(payment.getId());
        attempt.setStatus("INITIATED");
        attempt.setGatewayRef(payment.getGatewayRef());
        attempt.setCreatedAt(now);
        paymentAttemptRepository.save(attempt);

        String checkoutUrl = "https://checkout.flutterwave.com/v3/hosted/pay/" + payment.getGatewayRef()
                + "?public_key=" + somimasProperties.getFlutterwave().getPublicKey();
        return new CheckoutSessionResponse(payment.getId(), payment.getGatewayRef(), checkoutUrl);
    }

    @Transactional
    public void recordSuccessfulPayment(String gatewayRef, BigDecimal amount, String rawPayload) {
        Payment payment = paymentRepository.findAll().stream()
                .filter(p -> gatewayRef.equals(p.getGatewayRef()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found"));
        payment.setStatus("SUCCEEDED");
        paymentRepository.save(payment);

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setPaymentId(payment.getId());
        attempt.setStatus("SUCCEEDED");
        attempt.setGatewayRef(gatewayRef);
        attempt.setRawResponse(rawPayload);
        attempt.setCreatedAt(LocalDateTime.now());
        paymentAttemptRepository.save(attempt);

        if (payment.getInvoiceId() != null) {
            Invoice invoice = invoiceRepository.findById(payment.getInvoiceId()).orElse(null);
            if (invoice != null) {
                invoice.setStatus("PAID");
                invoice.setPaidAt(LocalDateTime.now());
                invoiceRepository.save(invoice);
            }
        }
    }
}
