import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ControlPlaneApiService } from './control-plane-api.service';
import { GlobalAuthService } from './global-auth.service';

describe('ControlPlaneApiService', () => {
  let service: ControlPlaneApiService;
  let httpMock: HttpTestingController;
  let auth: GlobalAuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ControlPlaneApiService, GlobalAuthService]
    });
    service = TestBed.inject(ControlPlaneApiService);
    httpMock = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(GlobalAuthService);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should post login to control plane API', () => {
    service.login({ email: 'user@example.com', password: 'secret' }).subscribe((res) => {
      expect(res.token).toBe('abc');
    });

    const req = httpMock.expectOne((request) => request.url.endsWith('/public/login'));
    expect(req.request.method).toBe('POST');
    req.flush({ token: 'abc' });
  });

  it('should attach bearer token for authenticated requests', () => {
    auth.setToken('saas-token');
    service.getMyOrganizations().subscribe();

    const req = httpMock.expectOne((request) => request.url.endsWith('/organizations'));
    expect(req.request.headers.get('Authorization')).toBe('Bearer saas-token');
    req.flush([]);
  });
});
