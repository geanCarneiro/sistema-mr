import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { IChatMessage } from '../interface/chat_message.interface';
import { Observable, of, map, catchError, finalize } from 'rxjs';
import { AuthService } from './auth.service';

export interface IChatResponse {
  content: string;
  timestamp: string;
  messageType: 'ASSISTANT';
}

@Injectable({ providedIn: 'root' })
export class AiChatService {
  public readonly CONVERSATION_ID: string;
  private readonly urlBase = '/ai/chat';

  public menssages = signal<IChatMessage[]>([]);
  public loading = signal<boolean>(false);

  constructor(
    private readonly http: HttpClient,
    private readonly authService: AuthService
  ) {
    this.CONVERSATION_ID = `chat-${this.authService.userData.sub}`;
  }

  public enviar(prompt: string): void {
    prompt = prompt?.trim();
    if (!prompt || this.loading()) return;

    const now = new Date();

    // 1. Cria a mensagem do usuário já com o timestamp local do front
    const userMsg: IChatMessage = {
      messageType: 'USER',
      content: prompt,
      timestamp: now.toISOString(),
      notValid: false,
    };

    // Adiciona a mensagem do usuário na tela
    this.menssages.update((list) => [...list, userMsg]);
    this.loading.set(true);

    // Payload enviado ao back-end
    const payload = { prompt, timestamp: now.toISOString(), conversationId: this.CONVERSATION_ID };

    this.http
      .post<IChatResponse>(this.urlBase, payload)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (res) => {
          // Sucesso: adiciona a resposta da IA com o timestamp devolvido pelo back-end
          const aiMsg: IChatMessage = {
            messageType: 'ASSISTANT',
            content: res.content,
            timestamp: res.timestamp,
          };
          this.menssages.update((list) => [...list, aiMsg]);
        },
        error: (err) => {
          console.error('Erro no envio:', err);

          // Remove a mensagem do usuário que falhou para que ele não tente "Tente de novo" sem contexto
          userMsg.notValid = true;

          // Adiciona um aviso amigável explicando que a mensagem não foi gravada
          const errorMsg: IChatMessage = {
            messageType: 'ASSISTANT',
            content:
              '⚠️ Ocorreu um erro ao processar sua pergunta. Como ela não foi registrada no histórico, por favor, envie o prompt novamente por extenso.',
            timestamp: new Date().toISOString(),
          };
          this.menssages.update((list) => [...list, errorMsg]);
        },
      });
  }

  public carregarHistorico(): void {
    this.http
      .get<IChatMessage[]>(`${this.urlBase}/history`, {
        params: {
          conversationId: this.CONVERSATION_ID,
        },
      })
      .subscribe({
        next: (data) => {
          this.menssages.set(data);
        },
        error: (err) => console.error('Error ao carregar historico', err),
      });
  }
}
