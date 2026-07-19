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
          <h2>Register your organization</h2>
          <p class="intro">
            Create your SOMIMAS Cloud account. We will generate a unique tenant slug, provision a dedicated Fineract
            database, and create your administrator login using the same email and password.
          </p>
          <form [formGroup]="form" (ngSubmit)="submit()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>First name</mat-label>
              <input matInput formControlName="firstName" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Last name</mat-label>
              <input matInput formControlName="lastName" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Email (also your tenant login username)</mat-label>
              <input matInput type="email" formControlName="email" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Password</mat-label>
              <input matInput type="password" formControlName="password" />
              <mat-hint>Used for both SOMIMAS Cloud and tenant banking login</mat-hint>
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Confirm password</mat-label>
              <input matInput type="password" formControlName="confirmPassword" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Organization name</mat-label>
              <input matInput formControlName="organizationName" />
              <mat-hint>Tenant slug preview: {{ slugPreview }}</mat-hint>
            </mat-form-field>
            <p class="error" *ngIf="errorMessage">{{ errorMessage }}</p>
            <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || loading">
              {{ loading ? 'Creating...' : 'Register organization' }}
            </button>
          </form>
        </mat-card-content>
        <mat-card-actions>
          <a mat-button routerLink="/saas/login">Already have an account?</a>
          <a mat-button routerLink="/login">Back to tenant login</a>
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
        max-width: 520px;
      }
      .intro {
        color: #546e7a;
        line-height: 1.5;
        margin-bottom: 1rem;
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
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', [Validators.required, Validators.minLength(8)]],
    organizationName: ['', [Validators.required, Validators.maxLength(200)]]
  });

  get slugPreview(): string {
    const name = this.form.get('organizationName')?.value || '';
    const slug = name
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 40);
    return slug.length >= 3 ? slug : 'your-organization';
  }

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    const value = this.form.getRawValue();
    if (value.password !== value.confirmPassword) {
      this.errorMessage = 'Passwords do not match.';
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    const credentials = { email: value.email!, password: value.password! };
    let signupResult: any;
    this.api
      .signup({
        ...credentials,
        firstName: value.firstName!,
        lastName: value.lastName!,
        organizationName: value.organizationName!
      })
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
            this.router.navigate(['/saas/organizations']);
          }
        },
        error: (err) => {
          this.loading = false;
          this.errorMessage = err?.error?.message || 'Signup failed. Please try again.';
        }
      });
  }
}
