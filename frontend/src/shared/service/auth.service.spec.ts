import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { throwError } from 'rxjs';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  it('does not expose an HTML error document in the login message', () => {
    const http = {
      post: vi.fn(() =>
        throwError(() => ({
          error: '<!DOCTYPE html><html><body><h1>Error response</h1></body></html>',
        })),
      ),
    };
    const router = { navigateByUrl: vi.fn() };
    const service = new AuthService(http as unknown as HttpClient, router as unknown as Router);

    service.doLogin('google-id-token');

    expect(service.erro()).toBe('Erro ao autenticar com o servidor.');
  });

  it('preserves a plain backend error message', () => {
    const http = {
      post: vi.fn(() => throwError(() => ({ error: 'Credencial inválida.' }))),
    };
    const router = { navigateByUrl: vi.fn() };
    const service = new AuthService(http as unknown as HttpClient, router as unknown as Router);

    service.doLogin('google-id-token');

    expect(service.erro()).toBe('Credencial inválida.');
  });
});
