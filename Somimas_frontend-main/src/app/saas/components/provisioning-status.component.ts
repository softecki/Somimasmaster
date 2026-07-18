import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { NgFor, NgIf } from '@angular/common';
import { MatCard, MatCardContent } from '@angular/material/card';
import { MatButton } from '@angular/material/button';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { interval, Subscription, switchMap } from 'rxjs';
import { ControlPlaneApiService, ProvisioningStatus } from '../services/control-plane-api.service';

@Component({
  selector: 'mifosx-provisioning-status',
  standalone: true,
  template: `
    <div class="saas-page">
      <mat-card>
        <mat-card-content>
          <h2>Provisioning status</h2>
          <mat-spinner diameter="32" *ngIf="loading"></mat-spinner>
          <p *ngIf="status"><strong>Status:</strong> {{ status.status }}</p>
          <ul *ngIf="status?.steps?.length">
            <li *ngFor="let step of status?.steps">
              {{ step.stepName }} — {{ step.status }}<span *ngIf="step.message">: {{ step.message }}</span>
            </li>
          </ul>
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
      .error {
        color: #c62828;
      }
    `
  ],
  imports: [NgFor, NgIf, RouterLink, MatCard, MatCardContent, MatButton, MatProgressSpinner]
})
export class ProvisioningStatusComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private api = inject(ControlPlaneApiService);

  status: ProvisioningStatus | null = null;
  loading = true;
  errorMessage = '';
  private pollSub?: Subscription;

  ngOnInit(): void {
    const orgId = Number(this.route.snapshot.paramMap.get('id'));
    this.pollSub = interval(5000)
      .pipe(switchMap(() => this.api.getProvisioningStatus(orgId)))
      .subscribe({
        next: (status) => {
          this.status = status;
          this.loading = false;
          if (status.status === 'COMPLETED' || status.status === 'FAILED') {
            this.pollSub?.unsubscribe();
          }
        },
        error: () => {
          this.loading = false;
          this.errorMessage = 'Unable to load provisioning status.';
          this.pollSub?.unsubscribe();
        }
      });
    this.api.getProvisioningStatus(orgId).subscribe({
      next: (status) => {
        this.status = status;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Unable to load provisioning status.';
      }
    });
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
  }
}
