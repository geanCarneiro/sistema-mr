import { Component, OnInit, signal, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
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
  private http = inject(HttpClient);

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
    if (typeof google === 'undefined') {
      setTimeout(() => this.inicializarGoogleAuth(), 300); // Aguarda script carregar
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
    google.accounts.id.renderButton(document.getElementById('googleBtn'), {
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

    console.log('id_token obtido no front! Enviando pro back...');

    // Envia pro Controller do Spring
    this.authService.doLogin(idToken);
  }
}
