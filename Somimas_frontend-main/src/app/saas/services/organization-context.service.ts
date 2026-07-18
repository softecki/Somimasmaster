import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

const ORG_CONTEXT_KEY = 'somimas_org_context';
const ORG_TENANT_KEY = 'somimas_org_tenant_slug';

export interface OrganizationContext {
  id: number;
  name: string;
  slug: string;
  tenantIdentifier?: string;
  status?: string;
}

@Injectable({
  providedIn: 'root'
})
export class OrganizationContextService {
  private readonly contextSubject = new BehaviorSubject<OrganizationContext | null>(this.loadFromStorage());

  readonly context$ = this.contextSubject.asObservable();

  get current(): OrganizationContext | null {
    return this.contextSubject.value;
  }

  setContext(org: OrganizationContext): void {
    localStorage.setItem(ORG_CONTEXT_KEY, JSON.stringify(org));
    if (org.tenantIdentifier || org.slug) {
      localStorage.setItem(ORG_TENANT_KEY, org.tenantIdentifier || org.slug);
    }
    this.contextSubject.next(org);
  }

  clearContext(): void {
    localStorage.removeItem(ORG_CONTEXT_KEY);
    localStorage.removeItem(ORG_TENANT_KEY);
    this.contextSubject.next(null);
  }

  getTenantIdentifier(): string | null {
    return localStorage.getItem(ORG_TENANT_KEY);
  }

  private loadFromStorage(): OrganizationContext | null {
    const raw = localStorage.getItem(ORG_CONTEXT_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as OrganizationContext;
    } catch {
      return null;
    }
  }
}
