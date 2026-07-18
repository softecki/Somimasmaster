import { Component, inject } from '@angular/core';
import { NgIf } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatCard, MatCardContent, MatCardActions } from '@angular/material/card';
import { MatFormField, MatLabel, MatHint } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatButton } from '@angular/material/button';
import { switchMap } from 'rxjs';
import { ControlPlaneApiService } from '../services/control-plane-api.service';
import { GlobalAuthService } from '../services/global-auth.service';

@Component({
  selector: 'mifosx-saas-signup',
  standalone: true,
  template: `
    <div class="saas-page">
      <mat-card>
        <mat-card-content>
          <h2>Create your organization</h2>
          <form [formGroup]="form" (ngSubmit)="submit()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Email</mat-label>
              <input matInput type="email" formControlName="email" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Password</mat-label>
              <input matInput type="password" formControlName="password" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Organization name</mat-label>
              <input matInput formControlName="organizationName" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>URL slug</mat-label>
              <input matInput formControlName="slug" />
              <mat-hint>Preview: {{ slugPreview }}</mat-hint>
            </mat-form-field>
            <p class="error" *ngIf="errorMessage">{{ errorMessage }}</p>
            <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || loading">
              {{ loading ? 'Creating...' : 'Sign up' }}
            </button>
          </form>
        </mat-card-content>
        <mat-card-actions>
          <a mat-button routerLink="/saas/login">Already have an account?</a>
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
        max-width: 480px;
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
    MatHint,
    MatInput,
    MatButton
  ]
})
export class SignupComponent {
  private fb = inject(FormBuilder);
  private api = inject(ControlPlaneApiService);
  private auth = inject(GlobalAuthService);
  private router = inject(Router);

  loading = false;
  errorMessage = '';

  form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    organizationName: ['', Validators.required],
    slug: ['', [Validators.required, Validators.pattern(/^[a-z0-9][a-z0-9-]{1,38}[a-z0-9]$/)]]
  });

  get slugPreview(): string {
    const slug = this.form.get('slug')?.value || 'your-org';
    return `${window.location.origin}/#/saas/organizations (${slug})`;
  }

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    const value = this.form.getRawValue();
    const credentials = { email: value.email!, password: value.password! };
    let signupResult: any;
    this.api
      .signup({ ...credentials, organizationName: value.organizationName!, slug: value.slug! })
      .pipe(
        switchMap((result) => {
          signupResult = result;
          return this.api.login(credentials);
        })
      )
      .subscribe({
        next: (authResponse) => {
          this.loading = false;
          this.auth.setToken(authResponse.token);
          this.auth.setUser({ email: authResponse.email || credentials.email, roles: authResponse.roles });
          const orgId = signupResult?.organizationId ?? signupResult?.orgId;
          if (orgId) {
            this.router.navigate(['/saas/organizations', orgId, 'provisioning']);
          } else {
            this.router.navigate(['/saas/login']);
          }
        },
        error: (err) => {
          this.loading = false;
          this.errorMessage = err?.error?.message || 'Signup failed. Please try again.';
        }
      });
  }
}
