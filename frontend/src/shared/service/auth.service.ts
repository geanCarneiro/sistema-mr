import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';

export interface IUserData {
  sub: string;
  nome: string;
  avatar: string;
  email: string;
}

interface IJwtPayload extends IUserData {
  exp: number;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  public loading = signal(false);
  public erro = signal<string | undefined>(undefined);

  public constructor(
    private readonly http: HttpClient,
    private readonly router: Router,
  ) {}

  public doLogin(idToken: string): void {
    this.http.post('/api/v1/auth/google', idToken, { responseType: 'text' }).subscribe({
      next: (res) => {
        this.loading.set(false);

        if (res) {
          localStorage.setItem('token', res);
          this.router.navigateByUrl('/chat');
        } else {
          console.error('não foi possivel receber o token');
        }
      },
      error: (err) => {
        this.loading.set(false);
        console.error('Erro na resposta do back:', err);
        this.erro.set(
          typeof err.error === 'string' && err.error
            ? err.error
            : 'Erro ao autenticar com o servidor.',
        );
      },
    });
  }


  isAuthenticated(): boolean {
    const token = localStorage.getItem('token');

    if (!token) {
      return false;
    }

    try {
      const payload = this.decodeToken(token);
      const isExpired = Math.floor(Date.now() / 1000) >= payload.exp;
      return !isExpired;
    } catch {
      localStorage.removeItem('token');
      return false;
    }
  }

  get userData(): IUserData | null {
    const token = localStorage.getItem('token');

    if (!token) {
      return null;
    }

    try {
      return this.decodeToken(token);
    } catch {
      localStorage.removeItem('token');
      return null;
    }
  }

  logout(): void {
    localStorage.removeItem('token');
    this.router.navigateByUrl('/login');
  }

  private decodeToken(token: string): IJwtPayload {
    const encodedPayload = token.split('.')[1];
    if (!encodedPayload) {
      throw new Error('JWT inválido');
    }

    const base64 = encodedPayload.replace(/-/g, '+').replace(/_/g, '/');
    const paddedBase64 = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=');
    const bytes = Uint8Array.from(atob(paddedBase64), (character) => character.charCodeAt(0));
    const payload: unknown = JSON.parse(new TextDecoder().decode(bytes));
    if (
      typeof payload !== 'object' ||
      payload === null ||
      typeof (payload as Partial<IJwtPayload>).exp !== 'number' ||
      typeof (payload as Partial<IJwtPayload>).sub !== 'string'
    ) {
      throw new Error('Payload JWT inválido');
    }
    return payload as IJwtPayload;
  }
}
