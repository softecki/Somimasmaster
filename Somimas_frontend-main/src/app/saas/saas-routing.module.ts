/** Angular Imports */
import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

/** Custom Components */
import { LandingComponent } from './components/landing.component';
import { SignupComponent } from './components/signup.component';
import { SaasLoginComponent } from './components/saas-login.component';
import { OrganizationListComponent } from './components/organization-list.component';
import { ProvisioningStatusComponent } from './components/provisioning-status.component';
import { BillingComponent } from './components/billing.component';
import { PlatformAdminComponent } from './components/platform-admin.component';
import { saasAuthGuard, saasPlatformAdminGuard } from './services/saas-auth.guard';

const saasChildRoutes: Routes = [
  {
    path: '',
    component: LandingComponent,
    data: { title: 'Somimas Cloud' }
  },
  {
    path: 'signup',
    component: SignupComponent,
    data: { title: 'Register' }
  },
  {
    path: 'login',
    component: SaasLoginComponent,
    data: { title: 'SaaS Login' }
  },
  {
    path: 'organizations',
    component: OrganizationListComponent,
    canActivate: [saasAuthGuard],
    data: { title: 'Organizations' }
  },
  {
    path: 'organizations/:id/billing',
    component: BillingComponent,
    canActivate: [saasAuthGuard],
    data: { title: 'Billing' }
  },
  {
    path: 'organizations/:id/provisioning',
    component: ProvisioningStatusComponent,
    canActivate: [saasAuthGuard],
    data: { title: 'Provisioning' }
  },
  {
    path: 'platform',
    component: PlatformAdminComponent,
    canActivate: [saasPlatformAdminGuard],
    data: { title: 'Platform Admin' }
  }
];

const routes: Routes = [
  {
    path: 'saas',
    children: saasChildRoutes
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class SaasRoutingModule {}
