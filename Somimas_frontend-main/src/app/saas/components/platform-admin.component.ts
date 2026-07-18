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
import { BankDeposit, ControlPlaneApiService, OrganizationSummary } from '../services/control-plane-api.service';
import { GlobalAuthService } from '../services/global-auth.service';

@Component({
  selector: 'mifosx-platform-admin',
  standalone: true,
  template: `
    <div class="saas-page">
      <mat-card>
        <mat-card-content>
          <div class="header">
            <h2>Platform administration</h2>
            <button mat-stroked-button (click)="logout()">Logout</button>
          </div>
          <p *ngIf="errorMessage" class="error">{{ errorMessage }}</p>
          <table mat-table [dataSource]="organizations" *ngIf="organizations.length">
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef>Organization</th>
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
                <button mat-button *ngIf="org.status !== 'SUSPENDED'" (click)="suspend(org)">Suspend</button>
                <button mat-button *ngIf="org.status === 'SUSPENDED'" (click)="reactivate(org)">Reactivate</button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
          </table>
          <h3>Pending bank deposits</h3>
          <p *ngIf="!pendingDeposits.length">No deposits awaiting review.</p>
          <div *ngFor="let deposit of pendingDeposits" class="deposit-row">
            <span>
              #{{ deposit.id }} — {{ deposit.amount }} {{ deposit.currency }} — {{ deposit.reference }}
            </span>
            <button mat-button (click)="reviewDeposit(deposit, 'APPROVED')">Approve</button>
            <button mat-button (click)="reviewDeposit(deposit, 'REJECTED')">Reject</button>
          </div>
          <a mat-button routerLink="/saas">Back</a>
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
      .deposit-row {
        display: flex;
        gap: 12px;
        align-items: center;
        padding: 8px 0;
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
export class PlatformAdminComponent implements OnInit {
  private api = inject(ControlPlaneApiService);
  private auth = inject(GlobalAuthService);
  private router = inject(Router);

  organizations: OrganizationSummary[] = [];
  pendingDeposits: BankDeposit[] = [];
  displayedColumns = ['name', 'slug', 'status', 'actions'];
  errorMessage = '';

  ngOnInit(): void {
    if (!this.auth.isPlatformAdmin()) {
      this.router.navigate(['/saas/login']);
      return;
    }
    this.api.getPlatformOrganizations().subscribe({
      next: (orgs) => (this.organizations = orgs),
      error: () => (this.errorMessage = 'Unable to load platform organizations.')
    });
    this.loadPendingDeposits();
  }

  reviewDeposit(deposit: BankDeposit, decision: 'APPROVED' | 'REJECTED'): void {
    this.api.reviewBankDeposit(deposit.id, decision).subscribe({
      next: () => this.loadPendingDeposits(),
      error: () => (this.errorMessage = 'Deposit approval failed.')
    });
  }

  suspend(org: OrganizationSummary): void {
    this.api.suspendOrganization(org.id).subscribe({
      next: (updated) => Object.assign(org, updated),
      error: () => (this.errorMessage = 'Unable to suspend organization.')
    });
  }

  reactivate(org: OrganizationSummary): void {
    this.api.reactivateOrganization(org.id).subscribe({
      next: (updated) => Object.assign(org, updated),
      error: () => (this.errorMessage = 'Unable to reactivate organization.')
    });
  }

  private loadPendingDeposits(): void {
    this.api.getPendingBankDeposits().subscribe({
      next: (deposits) => (this.pendingDeposits = deposits),
      error: () => (this.errorMessage = 'Unable to load pending deposits.')
    });
  }

  logout(): void {
    this.auth.clearToken();
    this.router.navigate(['/saas/login']);
  }
}
