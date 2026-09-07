import { HttpHandlerFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { firstValueFrom, of } from 'rxjs';
import { apiPrefixInterceptor } from './api-prefix.interceptor';

describe('apiPrefixInterceptor', () => {
  it('adds the routing prefix to a backend path', async () => {
    let forwardedRequest: HttpRequest<unknown> | undefined;
    const next: HttpHandlerFn = (request) => {
      forwardedRequest = request;
      return of(new HttpResponse());
    };

    await firstValueFrom(
      apiPrefixInterceptor(new HttpRequest('POST', '/v1/auth/google', null), next),
    );

    expect(forwardedRequest?.url).toBe('/api/v1/auth/google');
  });

  it('does not modify external urls', async () => {
    let forwardedRequest: HttpRequest<unknown> | undefined;
    const next: HttpHandlerFn = (request) => {
      forwardedRequest = request;
      return of(new HttpResponse());
    };

    await firstValueFrom(
      apiPrefixInterceptor(new HttpRequest('GET', 'https://example.com/resource'), next),
    );

    expect(forwardedRequest?.url).toBe('https://example.com/resource');
  });
});
