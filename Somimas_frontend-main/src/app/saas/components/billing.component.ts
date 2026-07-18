import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { NgFor, NgIf } from '@angular/common';
import { MatCard, MatCardContent } from '@angular/material/card';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
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
import { ControlPlaneApiService, Invoice, Plan } from '../services/control-plane-api.service';
import { PlanListComponent } from './plan-list.component';

@Component({
  selector: 'mifosx-billing',
  standalone: true,
  template: `
    <div class="saas-page">
      <mat-card>
        <mat-card-content>
          <h2>Billing</h2>
          <mifosx-plan-list [plans]="plans"></mifosx-plan-list>

          <h3>Invoices</h3>
          <table mat-table [dataSource]="invoices" *ngIf="invoices.length">
            <ng-container matColumnDef="number">
              <th mat-header-cell *matHeaderCellDef>Number</th>
              <td mat-cell *matCellDef="let inv">{{ inv.id }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let inv">{{ inv.status }}</td>
            </ng-container>
            <ng-container matColumnDef="total">
              <th mat-header-cell *matHeaderCellDef>Total</th>
              <td mat-cell *matCellDef="let inv">{{ inv.amountTotal }} {{ inv.currency || 'USD' }}</td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>Actions</th>
              <td mat-cell *matCellDef="let inv">
                <button mat-button (click)="payOnline(inv)">Pay online</button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="invoiceColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: invoiceColumns"></tr>
          </table>

          <h3>Bank deposit</h3>
          <form [formGroup]="depositForm" (ngSubmit)="submitDeposit()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Bank account ID</mat-label>
              <input matInput type="number" formControlName="bankAccountId" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Amount</mat-label>
              <input matInput type="number" formControlName="amount" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Currency</mat-label>
              <input matInput formControlName="currency" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Reference</mat-label>
              <input matInput formControlName="reference" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Payment notes</mat-label>
              <input matInput formControlName="metadata" />
            </mat-form-field>
            <button mat-flat-button color="primary" type="submit" [disabled]="depositForm.invalid">Submit deposit</button>
          </form>
          <p *ngIf="message" class="message">{{ message }}</p>
          <a mat-button routerLink="/saas/organizations">Back</a>
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
      .full-width {
        width: 100%;
        display: block;
      }
      table {
        width: 100%;
        margin-bottom: 24px;
      }
      .message {
        color: #2e7d32;
      }
    `
  ],
  imports: [
    NgFor,
    NgIf,
    ReactiveFormsModule,
    RouterLink,
    MatCard,
    MatCardContent,
    MatFormField,
    MatLabel,
    MatInput,
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
    MatRowDef,
    PlanListComponent
  ]
})
export class BillingComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private api = inject(ControlPlaneApiService);
  private fb = inject(FormBuilder);

  orgId = 0;
  plans: Plan[] = [];
  invoices: Invoice[] = [];
  invoiceColumns = ['number', 'status', 'total', 'actions'];
  message = '';

  depositForm = this.fb.group({
    bankAccountId: [0, [Validators.required, Validators.min(1)]],
    amount: [0, [Validators.required, Validators.min(1)]],
    currency: ['USD', Validators.required],
    reference: ['', Validators.required],
    metadata: ['']
  });

  ngOnInit(): void {
    this.orgId = Number(this.route.snapshot.paramMap.get('id'));
    this.api.getPlans().subscribe({ next: (plans) => (this.plans = plans) });
    this.api.getInvoices(this.orgId).subscribe({ next: (invoices) => (this.invoices = invoices) });
  }

  payOnline(invoice: Invoice): void {
    this.api.createCheckoutSession(this.orgId, invoice.id).subscribe({
      next: (res) => {
        if (res.checkoutUrl) {
          window.open(res.checkoutUrl, '_blank');
        }
      },
      error: () => {
        this.message = 'Unable to start online payment.';
      }
    });
  }

  submitDeposit(): void {
    if (this.depositForm.invalid) {
      return;
    }
    const value = this.depositForm.getRawValue();
    this.api
      .submitBankDeposit(this.orgId, {
        bankAccountId: value.bankAccountId!,
        amount: value.amount!,
        currency: value.currency!,
        reference: value.reference!,
        metadata: value.metadata || undefined
      })
      .subscribe({
        next: () => {
          this.message = 'Deposit submitted for review.';
          this.depositForm.reset({ bankAccountId: 0, currency: 'USD', amount: 0, reference: '', metadata: '' });
        },
        error: () => {
          this.message = 'Unable to submit deposit.';
        }
      });
  }
}
