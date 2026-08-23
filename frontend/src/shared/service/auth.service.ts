import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';

export interface IUserData {
  sub: string;
  nome: string;
  avatar: string;
  email: string;
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
        console.log('Resposta do Back-end:', res);

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
        this.erro.set('Erro ao autenticar com o servidor.');
      },
    });
  }


  isAuthenticated(): boolean {
    const token = localStorage.getItem('token'); // ou o nome da sua chave

    if (!token) {
      return false;
    }

    // Opcional: Checar se o JWT não está expirado
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const isExpired = Math.floor(Date.now() / 1000) >= payload.exp;
      return !isExpired;
    } catch (e) {
      return false;
    }
  }

  get userData() {
    const token = localStorage.getItem('token');

    return token && JSON.parse(atob(token.split('.')[1]))
  }
}
