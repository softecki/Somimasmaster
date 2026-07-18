import { Component, inject } from '@angular/core';
import { NgIf } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatCard, MatCardContent, MatCardActions } from '@angular/material/card';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatButton } from '@angular/material/button';
import { ControlPlaneApiService } from '../services/control-plane-api.service';
import { GlobalAuthService } from '../services/global-auth.service';

@Component({
  selector: 'mifosx-saas-login',
  standalone: true,
  template: `
    <div class="saas-page">
      <mat-card>
        <mat-card-content>
          <h2>Somimas Cloud Login</h2>
          <form [formGroup]="form" (ngSubmit)="submit()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Email</mat-label>
              <input matInput type="email" formControlName="email" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Password</mat-label>
              <input matInput type="password" formControlName="password" />
            </mat-form-field>
            <p class="error" *ngIf="errorMessage">{{ errorMessage }}</p>
            <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || loading">
              {{ loading ? 'Signing in...' : 'Sign in' }}
            </button>
          </form>
        </mat-card-content>
        <mat-card-actions>
          <a mat-button routerLink="/saas/signup">Create account</a>
          <a mat-button routerLink="/saas">Back</a>
        </mat-card-actions>
      </mat-card>
    </div>
  `,
  styles: [
    `
      .saas-page {
        min-height: 100vh;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 24px;
        background: #f5f7fa;
      }
      mat-card {
        width: 100%;
        max-width: 420px;
      }
      .full-width {
        width: 100%;
        display: block;
      }
      .error {
        color: #c62828;
      }
    `
  ],
  imports: [
    NgIf,
    ReactiveFormsModule,
    RouterLink,
    MatCard,
    MatCardContent,
    MatCardActions,
    MatFormField,
    MatLabel,
    MatInput,
    MatButton
  ]
})
export class SaasLoginComponent {
  private fb = inject(FormBuilder);
  private api = inject(ControlPlaneApiService);
  private auth = inject(GlobalAuthService);
  private router = inject(Router);

  loading = false;
  errorMessage = '';

  form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    const value = this.form.getRawValue();
    this.api.login({ email: value.email!, password: value.password! }).subscribe({
      next: (res) => {
        this.auth.setToken(res.token);
        this.auth.setUser({ email: res.email || value.email!, roles: res.roles });
        this.loading = false;
        if (this.auth.isPlatformAdmin()) {
          this.router.navigate(['/saas/platform']);
        } else {
          this.router.navigate(['/saas/organizations']);
        }
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Invalid email or password.';
      }
    });
  }
}
