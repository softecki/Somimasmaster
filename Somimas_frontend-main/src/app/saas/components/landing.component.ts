import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatCard, MatCardContent, MatCardActions } from '@angular/material/card';
import { MatButton } from '@angular/material/button';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'mifosx-saas-landing',
  standalone: true,
  template: `
    <div class="saas-page">
      <mat-card class="hero-card">
        <mat-card-content>
          <h1>Somimas Cloud</h1>
          <p class="subtitle">
            Microfinance platform for institutions that need secure tenant isolation, billing, and provisioning.
          </p>
          <ul class="features">
            <li>Multi-tenant Fineract deployments</li>
            <li>Subscription plans with trial periods</li>
            <li>Bank deposit and online payment options</li>
          </ul>
        </mat-card-content>
        <mat-card-actions align="end">
          <a mat-stroked-button routerLink="/saas/signup">Register</a>
          <a mat-stroked-button routerLink="/saas/login">Login</a>
          <a mat-flat-button color="primary" [routerLink]="appRoute">Go to App</a>
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
      .hero-card {
        max-width: 720px;
        width: 100%;
      }
      h1 {
        margin: 0 0 8px;
        font-weight: 500;
      }
      .subtitle {
        color: rgba(0, 0, 0, 0.67);
        margin-bottom: 16px;
      }
      .features {
        margin: 0;
        padding-left: 20px;
        color: rgba(0, 0, 0, 0.75);
      }
    `
  ],
  imports: [RouterLink, MatCard, MatCardContent, MatCardActions, MatButton]
})
export class LandingComponent {
  appRoute = environment.saasMode ? '/saas/organizations' : '/login';
}
