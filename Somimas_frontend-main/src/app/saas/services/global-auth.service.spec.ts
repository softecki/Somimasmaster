import { GlobalAuthService } from './global-auth.service';

describe('GlobalAuthService', () => {
  let service: GlobalAuthService;

  beforeEach(() => {
    localStorage.clear();
    service = new GlobalAuthService();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should store and retrieve token', () => {
    service.setToken('test-token');
    expect(service.getToken()).toBe('test-token');
    expect(service.isAuthenticated()).toBe(true);
  });

  it('should clear token and user on logout', () => {
    service.setToken('test-token');
    service.setUser({ email: 'user@example.com', roles: ['ROLE_USER'] });
    service.clearToken();
    expect(service.getToken()).toBeNull();
    expect(service.getUser()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
  });

  it('should detect platform admin role', () => {
    service.setUser({ email: 'admin@example.com', roles: ['ROLE_PLATFORM_ADMIN'] });
    expect(service.isPlatformAdmin()).toBe(true);
  });
});
