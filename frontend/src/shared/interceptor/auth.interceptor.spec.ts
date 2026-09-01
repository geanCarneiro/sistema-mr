import { HttpHandlerFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { firstValueFrom, of } from 'rxjs';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('anexa o cabeçalho Authorization quando existe token e a URL é uma rota de API genérica', async () => {
    localStorage.setItem('token', 'fake-jwt-token');
    let forwardedRequest: HttpRequest<unknown> | undefined;
    const next: HttpHandlerFn = (request) => {
      forwardedRequest = request;
      return of(new HttpResponse());
    };

    await firstValueFrom(
      authInterceptor(new HttpRequest('GET', '/api/v1/chat/files'), next)
    );

    expect(forwardedRequest?.headers.get('Authorization')).toBe('Bearer fake-jwt-token');
  });

  it('NÃO anexa o cabeçalho Authorization em rotas de autenticação mesmo existindo token em localStorage', async () => {
    localStorage.setItem('token', 'expired-jwt-token');
    let forwardedRequest: HttpRequest<unknown> | undefined;
    const next: HttpHandlerFn = (request) => {
      forwardedRequest = request;
      return of(new HttpResponse());
    };

    await firstValueFrom(
      authInterceptor(new HttpRequest('POST', '/api/api/v1/auth/google', null), next)
    );

    expect(forwardedRequest?.headers.has('Authorization')).toBe(false);
  });

  it('NÃO anexa cabeçalho Authorization quando não há token em localStorage', async () => {
    let forwardedRequest: HttpRequest<unknown> | undefined;
    const next: HttpHandlerFn = (request) => {
      forwardedRequest = request;
      return of(new HttpResponse());
    };

    await firstValueFrom(
      authInterceptor(new HttpRequest('GET', '/api/v1/chat/files'), next)
    );

    expect(forwardedRequest?.headers.has('Authorization')).toBe(false);
  });
});
