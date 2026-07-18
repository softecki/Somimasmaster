import { Component, Input } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { MatCard, MatCardContent } from '@angular/material/card';
import { Plan } from '../services/control-plane-api.service';

@Component({
  selector: 'mifosx-plan-list',
  standalone: true,
  template: `
    <div class="plans" *ngIf="plans?.length">
      <mat-card class="plan-card" *ngFor="let plan of plans">
        <mat-card-content>
          <h3>{{ plan.name }}</h3>
          <p>{{ plan.description }}</p>
          <ul *ngIf="plan.prices?.length">
            <li *ngFor="let price of plan.prices">
              {{ price.amount }} {{ price.currency }} / {{ price.billingInterval }}
            </li>
          </ul>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [
    `
      .plans {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
        gap: 16px;
        margin-bottom: 24px;
      }
      .plan-card h3 {
        margin-top: 0;
      }
    `
  ],
  imports: [NgFor, NgIf, MatCard, MatCardContent]
})
export class PlanListComponent {
  @Input() plans: Plan[] = [];
}
