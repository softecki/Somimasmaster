import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NgFor, NgIf } from '@angular/common';
import { MatCard, MatCardContent } from '@angular/material/card';
import { MatButton } from '@angular/material/button';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { interval, Subscription, switchMap } from 'rxjs';
import { ControlPlaneApiService, OrganizationSummary, ProvisioningStatus } from '../services/control-plane-api.service';
import { OrganizationContextService } from '../services/organization-context.service';
import { SettingsService } from 'app/settings/settings.service';

@Component({
  selector: 'mifosx-provisioning-status',
  standalone: true,
  template: `
    <div class="saas-page">
      <mat-card>
        <mat-card-content>
          <h2>Provisioning your organization</h2>
          <p *ngIf="organization">
            <strong>{{ organization.name }}</strong> (tenant: <code>{{ organization.slug }}</code>)
          </p>
          <mat-spinner diameter="32" *ngIf="loading && !completed"></mat-spinner>
          <p *ngIf="status"><strong>Status:</strong> {{ status.status }}</p>
          <ul *ngIf="status?.steps?.length">
            <li *ngFor="let step of status?.steps">
              {{ step.stepName }} — {{ step.status }}<span *ngIf="step.message">: {{ step.message }}</span>
            </li>
          </ul>

          <div class="ready" *ngIf="completed">
            <p>
              Your tenant database is ready. Sign in to SOMIMAS using your registration <strong>email</strong> as the
              username and the same password. The tenant identifier is already selected.
            </p>
            <button mat-flat-button color="primary" (click)="continueToTenantLogin()">Continue to tenant login</button>
          </div>

          <p *ngIf="failed" class="error">
            Provisioning failed. Contact support or retry later. Your organization record is preserved.
          </p>
          <p *ngIf="errorMessage" class="error">{{ errorMessage }}</p>
          <a mat-button routerLink="/saas/organizations">Back to organizations</a>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [
    `
      .saas-page {
        padding: 24px;
        background: #f5f7fa;
        min-height: 100vh;
      }
      mat-card {
        max-width: 640px;
        margin: 0 auto;
      }
      .ready {
        margin: 1rem 0;
        padding: 1rem;
        background: #e8f5e9;
        border-radius: 8px;
      }
      .error {
        color: #c62828;
      }
      code {
        background: #eee;
        padding: 0.1rem 0.35rem;
        border-radius: 4px;
      }
    `
  ],
  imports: [NgFor, NgIf, RouterLink, MatCard, MatCardContent, MatButton, MatProgressSpinner]
})
export class ProvisioningStatusComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private api = inject(ControlPlaneApiService);
  private orgContext = inject(OrganizationContextService);
  private settings = inject(SettingsService);

  organization: OrganizationSummary | null = null;
  status: ProvisioningStatus | null = null;
  loading = true;
  errorMessage = '';
  private pollSub?: Subscription;
  private orgId = 0;

  get completed(): boolean {
    return this.status?.status === 'COMPLETED';
  }

  get failed(): boolean {
    return this.status?.status === 'FAILED';
  }

  ngOnInit(): void {
    this.orgId = Number(this.route.snapshot.paramMap.get('id'));
    this.api.getOrganization(this.orgId).subscribe({
      next: (org) => {
        this.organization = org;
      }
    });
    this.pollSub = interval(5000)
      .pipe(switchMap(() => this.api.getProvisioningStatus(this.orgId)))
      .subscribe({
        next: (status) => {
          this.status = status;
          this.loading = false;
          if (status.status === 'COMPLETED' || status.status === 'FAILED') {
            this.pollSub?.unsubscribe();
            if (status.status === 'COMPLETED') {
              this.prepareTenantContext();
            }
          }
        },
        error: () => {
          this.loading = false;
          this.errorMessage = 'Unable to load provisioning status.';
          this.pollSub?.unsubscribe();
        }
      });
    this.api.getProvisioningStatus(this.orgId).subscribe({
      next: (status) => {
        this.status = status;
        this.loading = false;
        if (status.status === 'COMPLETED') {
          this.prepareTenantContext();
        }
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Unable to load provisioning status.';
      }
    });
  }

  continueToTenantLogin(): void {
    this.prepareTenantContext();
    this.router.navigate(['/login']);
  }

  private prepareTenantContext(): void {
    const org = this.organization;
    if (!org) {
      return;
    }
    const tenant = org.tenantIdentifier || org.slug;
    this.orgContext.setContext({
      id: org.id,
      name: org.name,
      slug: org.slug,
      tenantIdentifier: tenant,
      status: org.status
    });
    this.settings.setTenantIdentifier(tenant);
    const existing = this.settings.tenantIdentifiers || [];
    if (!existing.includes(tenant)) {
      this.settings.setTenantIdentifiers([...existing, tenant]);
    }
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
  }
}
