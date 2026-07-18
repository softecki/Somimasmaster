import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map, shareReplay } from 'rxjs/operators';
import { ControlPlaneApiService } from './control-plane-api.service';
import { OrganizationContextService } from './organization-context.service';

export interface Entitlement {
  code: string;
  name?: string;
  enabled: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class EntitlementService {
  private api = inject(ControlPlaneApiService);
  private orgContext = inject(OrganizationContextService);

  private cache = new Map<number, Observable<Entitlement[]>>();

  getEntitlements(orgId?: number): Observable<Entitlement[]> {
    const id = orgId ?? this.orgContext.current?.id;
    if (!id) {
      return of([]);
    }
    if (!this.cache.has(id)) {
      const stream = this.api.getSubscription(id).pipe(
        map((sub: any) => this.mapEntitlements(sub)),
        catchError(() => of([])),
        shareReplay(1)
      );
      this.cache.set(id, stream);
    }
    return this.cache.get(id)!;
  }

  hasEntitlement(code: string, orgId?: number): Observable<boolean> {
    return this.getEntitlements(orgId).pipe(map((items) => items.some((e) => e.code === code && e.enabled)));
  }

  clearCache(): void {
    this.cache.clear();
  }

  private mapEntitlements(subscription: any): Entitlement[] {
    const raw = subscription?.entitlements ?? subscription?.plan?.entitlements ?? [];
    if (!Array.isArray(raw)) {
      return [];
    }
    return raw.map((item: any) => ({
      code: item.code ?? item.entitlementCode ?? item,
      name: item.name,
      enabled: item.enabled !== false
    }));
  }
}
