/** Angular Imports */
import { Injectable, inject } from '@angular/core';
import { HttpEvent, HttpInterceptor, HttpHandler, HttpRequest } from '@angular/common/http';

/** rxjs Imports */
import { Observable } from 'rxjs';

/** Custom Imports */
import { environment } from '../../../environments/environment';
import { SettingsService } from 'app/settings/settings.service';

/** Http request (default) options headers. */
const httpOptions: { headers: { [key: string]: string } } = {
  headers: {
    'Fineract-Platform-TenantId': environment.fineractPlatformTenantId
  }
};

/** Authorization header. */
const authorizationHeader = 'Authorization';
const authorizationTenantHeader = 'Fineract-Platform-TenantId';
/** Two factor access token header. */
const twoFactorAccessTokenHeader = 'Fineract-Platform-TFA-Token';

const EXTERNAL_PAYMENT_HOSTS = ['api.flutterwave.com', 'checkout.flutterwave.com', 'ravemodal-dev.herokuapp.com'];

/**
 * Http Request interceptor to set the request headers.
 */
@Injectable()
export class AuthenticationInterceptor implements HttpInterceptor {
  private settingsService = inject(SettingsService);

  /**
   * Intercepts a Http request and sets the request headers.
   */
  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (!this.isFineractApiRequest(request)) {
      return next.handle(request);
    }

    const headers: { [key: string]: string } = { ...httpOptions.headers };
    if (this.settingsService.tenantIdentifier) {
      headers['Fineract-Platform-TenantId'] = this.settingsService.tenantIdentifier;
    }
    request = request.clone({ setHeaders: headers });
    return next.handle(request);
  }

  private isFineractApiRequest(request: HttpRequest<any>): boolean {
    const url = request.url;

    if (environment.controlPlaneApiUrl && url.startsWith(environment.controlPlaneApiUrl)) {
      return false;
    }

    if (EXTERNAL_PAYMENT_HOSTS.some((host) => url.includes(host))) {
      return false;
    }

    if (environment.vNextApiUrl && url.startsWith(environment.vNextApiUrl)) {
      return false;
    }

    if (environment.oauth?.serverUrl && url.startsWith(environment.oauth.serverUrl)) {
      return false;
    }

    if (environment.OIDC?.oidcApiUrl && url.startsWith(environment.OIDC.oidcApiUrl)) {
      return false;
    }

    if (!url.includes('http:') && !url.includes('https:')) {
      return true;
    }

    const fineractPrefixes = [
      this.settingsService.serverUrl,
      this.settingsService.baseServerUrl,
      this.settingsService.serverHost,
      `${environment.baseApiUrl}${environment.apiProvider}${environment.apiVersion}`,
      `${environment.baseApiUrl}${environment.apiProvider}`
    ].filter((prefix): prefix is string => !!prefix);

    return fineractPrefixes.some((prefix) => url.startsWith(prefix));
  }

  /**
   * Sets the basic/oauth authorization header depending on the configuration.
   * @param {string} authenticationKey Authentication key.
   */
  setAuthorizationToken(authenticationKey: string) {
    if (environment.oauth.enabled) {
      httpOptions.headers[authorizationHeader] = `Bearer ${authenticationKey}`;
    } else {
      httpOptions.headers[authorizationHeader] = `Basic ${authenticationKey}`;
    }
  }

  /**
   * Sets the two factor access token header.
   * @param {string} twoFactorAccessToken Two factor access token.
   */
  setTwoFactorAccessToken(twoFactorAccessToken: string) {
    httpOptions.headers[twoFactorAccessTokenHeader] = twoFactorAccessToken;
  }

  /**
   * Removes the authorization header.
   */
  removeAuthorization() {
    delete httpOptions.headers[authorizationHeader];
  }

  /**
   * Removes the authorization header.
   */
  removeAuthorizationTenant() {
    delete httpOptions.headers[authorizationHeader];
    delete httpOptions.headers[authorizationTenantHeader];
  }

  /**
   * Removes the two factor access token header.
   */
  removeTwoFactorAuthorization() {
    delete httpOptions.headers[twoFactorAccessTokenHeader];
  }
}
