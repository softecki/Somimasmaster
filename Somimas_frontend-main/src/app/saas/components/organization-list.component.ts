import { Component, OnInit, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { NgFor, NgIf } from '@angular/common';
import { MatCard, MatCardContent } from '@angular/material/card';
import { MatButton } from '@angular/material/button';
import {
  MatCell,
  MatCellDef,
  MatColumnDef,
  MatHeaderCell,
  MatHeaderCellDef,
  MatHeaderRow,
  MatHeaderRowDef,
  MatRow,
  MatRowDef,
  MatTable
} from '@angular/material/table';
import { ControlPlaneApiService, OrganizationSummary } from '../services/control-plane-api.service';
import { OrganizationContextService } from '../services/organization-context.service';
import { GlobalAuthService } from '../services/global-auth.service';
import { SettingsService } from 'app/settings/settings.service';

@Component({
  selector: 'mifosx-organization-list',
  standalone: true,
  template: `
    <div class="saas-page">
      <mat-card>
        <mat-card-content>
          <div class="header">
            <h2>Your organizations</h2>
            <button mat-stroked-button (click)="logout()">Logout</button>
          </div>
          <p *ngIf="errorMessage" class="error">{{ errorMessage }}</p>
          <table mat-table [dataSource]="organizations" *ngIf="organizations.length">
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef>Name</th>
              <td mat-cell *matCellDef="let org">{{ org.name }}</td>
            </ng-container>
            <ng-container matColumnDef="slug">
              <th mat-header-cell *matHeaderCellDef>Slug</th>
              <td mat-cell *matCellDef="let org">{{ org.slug }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let org">{{ org.status }}</td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>Actions</th>
              <td mat-cell *matCellDef="let org">
                <button mat-button (click)="selectOrg(org)">Open</button>
                <a mat-button [routerLink]="['/saas/organizations', org.id, 'billing']">Billing</a>
                <a mat-button [routerLink]="['/saas/organizations', org.id, 'provisioning']">Provisioning</a>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
          </table>
          <p *ngIf="!loading && !organizations.length">No organizations yet.</p>
          <a mat-flat-button color="primary" routerLink="/saas/signup">Create organization</a>
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
        max-width: 960px;
        margin: 0 auto;
      }
      .header {
        display: flex;
        justify-content: space-between;
        align-items: center;
      }
      table {
        width: 100%;
        margin: 16px 0;
      }
      .error {
        color: #c62828;
      }
    `
  ],
  imports: [
    NgFor,
    NgIf,
    RouterLink,
    MatCard,
    MatCardContent,
    MatButton,
    MatTable,
    MatColumnDef,
    MatHeaderCell,
    MatHeaderCellDef,
    MatCell,
    MatCellDef,
    MatHeaderRow,
    MatHeaderRowDef,
    MatRow,
    MatRowDef
  ]
})
export class OrganizationListComponent implements OnInit {
  private api = inject(ControlPlaneApiService);
  private orgContext = inject(OrganizationContextService);
  private auth = inject(GlobalAuthService);
  private settings = inject(SettingsService);
  private router = inject(Router);

  organizations: OrganizationSummary[] = [];
  displayedColumns = ['name', 'slug', 'status', 'actions'];
  loading = true;
  errorMessage = '';

  ngOnInit(): void {
    if (!this.auth.isAuthenticated()) {
      this.router.navigate(['/saas/login']);
      return;
    }
    this.api.getMyOrganizations().subscribe({
      next: (orgs) => {
        this.organizations = orgs;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Unable to load organizations.';
      }
    });
  }

  selectOrg(org: OrganizationSummary): void {
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
    // Control-plane auth is not a Fineract session — send the user to tenant login.
    this.router.navigate(['/login']);
  }

  logout(): void {
    this.auth.clearToken();
    this.orgContext.clearContext();
    this.router.navigate(['/saas/login']);
  }
}
