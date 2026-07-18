import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { GlobalAuthService } from './global-auth.service';

export interface SignupRequest {
  email: string;
  password: string;
  organizationName: string;
  slug: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  email?: string;
  roles?: string[];
}

export interface OrganizationSummary {
  id: number;
  name: string;
  slug: string;
  status: string;
  tenantIdentifier?: string;
}

export interface ProvisioningStatus {
  jobId?: number;
  status: string;
  steps?: { stepName: string; status: string; message?: string }[];
}

export interface Plan {
  id: number;
  code: string;
  name: string;
  description?: string;
  prices?: { currency: string; amount: number; billingInterval: string }[];
}

export interface Invoice {
  id: number;
  status: string;
  amountTotal: number;
  currency?: string;
  dueDate?: string;
}

export interface BankDepositRequest {
  bankAccountId: number;
  amount: number;
  currency: string;
  reference: string;
  metadata?: string;
}

export interface BankDeposit {
  id: number;
  organizationId: number;
  amount: number;
  currency: string;
  reference: string;
  status: string;
  submittedAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class ControlPlaneApiService {
  private http = inject(HttpClient);
  private auth = inject(GlobalAuthService);

  private get baseUrl(): string {
    return environment.controlPlaneApiUrl.replace(/\/$/, '');
  }

  private authHeaders(): HttpHeaders {
    let headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    const token = this.auth.getToken();
    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }
    return headers;
  }

  signup(payload: SignupRequest): Observable<any> {
    return this.http.post(`${this.baseUrl}/public/signup`, payload, { headers: this.authHeaders() });
  }

  login(payload: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/public/login`, payload, { headers: this.authHeaders() });
  }

  getPlans(): Observable<Plan[]> {
    return this.http.get<Plan[]>(`${this.baseUrl}/public/plans`, { headers: this.authHeaders() });
  }

  getMyOrganizations(): Observable<OrganizationSummary[]> {
    return this.http.get<OrganizationSummary[]>(`${this.baseUrl}/organizations`, { headers: this.authHeaders() });
  }

  getOrganization(id: number): Observable<OrganizationSummary> {
    return this.http.get<OrganizationSummary>(`${this.baseUrl}/organizations/${id}`, { headers: this.authHeaders() });
  }

  getProvisioningStatus(orgId: number): Observable<ProvisioningStatus> {
    return this.http.get<ProvisioningStatus>(`${this.baseUrl}/organizations/${orgId}/provisioning`, {
      headers: this.authHeaders()
    });
  }

  getSubscription(orgId: number): Observable<any> {
    return this.http.get(`${this.baseUrl}/organizations/${orgId}/subscription`, { headers: this.authHeaders() });
  }

  getInvoices(orgId: number): Observable<Invoice[]> {
    return this.http.get<Invoice[]>(`${this.baseUrl}/organizations/${orgId}/invoices`, { headers: this.authHeaders() });
  }

  createCheckoutSession(orgId: number, invoiceId: number): Observable<{ checkoutUrl: string }> {
    return this.http.post<{ checkoutUrl: string }>(
      `${this.baseUrl}/organizations/${orgId}/payments/checkout-session`,
      { invoiceId },
      { headers: this.authHeaders() }
    );
  }

  submitBankDeposit(orgId: number, payload: BankDepositRequest): Observable<any> {
    return this.http.post(`${this.baseUrl}/organizations/${orgId}/bank-deposits`, payload, {
      headers: this.authHeaders()
    });
  }

  getPlatformOrganizations(): Observable<OrganizationSummary[]> {
    return this.http.get<OrganizationSummary[]>(`${this.baseUrl}/platform/organizations`, {
      headers: this.authHeaders()
    });
  }

  getPendingBankDeposits(): Observable<BankDeposit[]> {
    return this.http.get<BankDeposit[]>(`${this.baseUrl}/platform/bank-deposits/pending`, {
      headers: this.authHeaders()
    });
  }

  reviewBankDeposit(depositId: number, decision: 'APPROVED' | 'REJECTED', notes = ''): Observable<any> {
    return this.http.post(
      `${this.baseUrl}/platform/bank-deposits/${depositId}/review`,
      { decision, notes },
      { headers: this.authHeaders() }
    );
  }

  suspendOrganization(organizationId: number): Observable<OrganizationSummary> {
    return this.http.post<OrganizationSummary>(
      `${this.baseUrl}/platform/organizations/${organizationId}/suspend`,
      {},
      { headers: this.authHeaders() }
    );
  }

  reactivateOrganization(organizationId: number): Observable<OrganizationSummary> {
    return this.http.post<OrganizationSummary>(
      `${this.baseUrl}/platform/organizations/${organizationId}/reactivate`,
      {},
      { headers: this.authHeaders() }
    );
  }
}
