import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../shared/service/auth.service';
import { configEnv } from '../../shared/envirement/config.env';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class LoginComponent implements OnInit {
  private googleSdkAttempts = 0;

  public readonly loading;
  public readonly erro;

  constructor(
    private readonly authService: AuthService
  ) {
    this.loading = this.authService.loading;
    this.erro = this.authService.erro;

  }

  ngOnInit(): void {
    this.inicializarGoogleAuth();
  }

  private inicializarGoogleAuth(): void {
    if (!configEnv.clientId) {
      this.erro.set('GOOGLE_API_CLIENT_ID não foi configurado.');
      return;
    }

    if (typeof google === 'undefined') {
      if (this.googleSdkAttempts++ >= 20) {
        this.erro.set('Não foi possível carregar o login do Google.');
        return;
      }
      setTimeout(() => this.inicializarGoogleAuth(), 300);
      return;
    }

    // 1. Inicializa o SDK da Google
    google.accounts.id.initialize({
      client_id: configEnv.clientId,
      callback: (response: any) => this.tratarRetornoGoogle(response),
      auto_select: false, // Define se loga automaticamente se tiver só 1 conta
      cancel_on_tap_outside: true,
    });

    // 2. Exibe o prompt do One Tap (modal suspenso no canto superior direito)
    google.accounts.id.prompt((notification: any) => {
      if (notification.isNotDisplayed() || notification.isSkippedMoment()) {
        console.log(
          'One Tap não exibido ou fechado. Motivo:',
          notification.getNotDisplayedReason(),
        );
      }
    });

    // 3. Renderiza também o botão nativo do Google dentro da div
    const googleButton = document.getElementById('googleBtn');
    if (!googleButton) {
      this.erro.set('Não foi possível montar o botão de login.');
      return;
    }

    google.accounts.id.renderButton(googleButton, {
      theme: 'outline',
      size: 'large',
      shape: 'rectangular',
      text: 'signin_with',
    });
  }

  private tratarRetornoGoogle(response: any): void {
    // response.credential é a String do id_token enviada pelo Google!
    const idToken = response.credential;

    if (!idToken) {
      this.erro.set('Não foi possível obter o token do Google.');
      return;
    }

    this.loading.set(true);
    this.erro.set(undefined);

    this.authService.doLogin(idToken);
  }
}
