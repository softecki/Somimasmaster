import { Injectable } from '@angular/core';

const SAAS_TOKEN_KEY = 'somimas_saas_token';
const SAAS_USER_KEY = 'somimas_saas_user';

export interface SaasUser {
  email: string;
  roles?: string[];
}

@Injectable({
  providedIn: 'root'
})
export class GlobalAuthService {
  getToken(): string | null {
    return localStorage.getItem(SAAS_TOKEN_KEY);
  }

  setToken(token: string): void {
    localStorage.setItem(SAAS_TOKEN_KEY, token);
  }

  clearToken(): void {
    localStorage.removeItem(SAAS_TOKEN_KEY);
    localStorage.removeItem(SAAS_USER_KEY);
  }

  isAuthenticated(): boolean {
    return !!this.getToken();
  }

  setUser(user: SaasUser): void {
    localStorage.setItem(SAAS_USER_KEY, JSON.stringify(user));
  }

  getUser(): SaasUser | null {
    const raw = localStorage.getItem(SAAS_USER_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as SaasUser;
    } catch {
      return null;
    }
  }

  isPlatformAdmin(): boolean {
    const user = this.getUser();
    return user?.roles?.includes('ROLE_PLATFORM_ADMIN') ?? false;
  }
}
