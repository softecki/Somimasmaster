import { Injectable, inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { GlobalAuthService } from './global-auth.service';

@Injectable({ providedIn: 'root' })
export class SaasAuthGuardService {
  private auth = inject(GlobalAuthService);
  private router = inject(Router);

  canActivate(): boolean {
    if (this.auth.isAuthenticated()) {
      return true;
    }
    this.router.navigate(['/saas/login']);
    return false;
  }

  canActivatePlatformAdmin(): boolean {
    if (this.auth.isAuthenticated() && this.auth.isPlatformAdmin()) {
      return true;
    }
    if (!this.auth.isAuthenticated()) {
      this.router.navigate(['/saas/login']);
    } else {
      this.router.navigate(['/saas/organizations']);
    }
    return false;
  }
}

export const saasAuthGuard: CanActivateFn = () => inject(SaasAuthGuardService).canActivate();

export const saasPlatformAdminGuard: CanActivateFn = () => inject(SaasAuthGuardService).canActivatePlatformAdmin();
