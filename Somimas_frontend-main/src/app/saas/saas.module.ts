/** Angular Imports */
import { NgModule } from '@angular/core';

/** Custom Modules */
import { SharedModule } from '../shared/shared.module';
import { SaasRoutingModule } from './saas-routing.module';

/** Custom Components */
import { LandingComponent } from './components/landing.component';
import { SignupComponent } from './components/signup.component';
import { SaasLoginComponent } from './components/saas-login.component';
import { OrganizationListComponent } from './components/organization-list.component';
import { ProvisioningStatusComponent } from './components/provisioning-status.component';
import { BillingComponent } from './components/billing.component';
import { PlatformAdminComponent } from './components/platform-admin.component';
import { PlanListComponent } from './components/plan-list.component';

/**
 * SaaS Module
 *
 * Public SaaS control-plane pages (signup, login, billing, provisioning).
 */
@NgModule({
  imports: [
    SharedModule,
    SaasRoutingModule,
    LandingComponent,
    SignupComponent,
    SaasLoginComponent,
    OrganizationListComponent,
    ProvisioningStatusComponent,
    BillingComponent,
    PlatformAdminComponent,
    PlanListComponent
  ]
})
export class SaasModule {}
