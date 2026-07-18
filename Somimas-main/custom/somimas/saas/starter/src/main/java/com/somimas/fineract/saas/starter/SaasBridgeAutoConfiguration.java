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
package com.somimas.fineract.saas.starter;

import com.somimas.fineract.saas.access.TenantAccessFilter;
import com.somimas.fineract.saas.config.SaasBridgeProperties;
import com.somimas.fineract.saas.entitlement.EntitlementAuthorizationFilter;
import com.somimas.fineract.saas.provisioning.TenantAccessStateService;
import com.somimas.fineract.saas.security.SaasBridgeTokenFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.Ordered;

@AutoConfiguration
@ComponentScan("com.somimas.fineract.saas")
@EnableConfigurationProperties(SaasBridgeProperties.class)
@ConditionalOnProperty(name = "somimas.saas.enabled", havingValue = "true")
public class SaasBridgeAutoConfiguration {

    @Bean
    public FilterRegistrationBean<SaasBridgeTokenFilter> saasBridgeTokenFilter(SaasBridgeProperties properties) {
        FilterRegistrationBean<SaasBridgeTokenFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SaasBridgeTokenFilter(properties));
        registration.addUrlPatterns("/internal/saas/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<TenantAccessFilter> tenantAccessFilter(TenantAccessStateService tenantAccessStateService) {
        FilterRegistrationBean<TenantAccessFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TenantAccessFilter(tenantAccessStateService));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<EntitlementAuthorizationFilter> entitlementAuthorizationFilter(SaasBridgeProperties properties) {
        FilterRegistrationBean<EntitlementAuthorizationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new EntitlementAuthorizationFilter(properties));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 30);
        return registration;
    }
}
