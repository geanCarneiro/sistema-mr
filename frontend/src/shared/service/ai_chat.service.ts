import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { IChatMessage } from '../interface/chat_message.interface';
import { finalize } from 'rxjs';
import { IChatFile } from '../interface/chat_file.interface';

export interface IChatResponse {
  content: string;
  timestamp: string;
  messageType: 'ASSISTANT';
  groundingFiles?: Array<{ id: string; name: string }>;
}

@Injectable({ providedIn: 'root' })
export class AiChatService {
  private readonly urlBase = '/ai/chat';

  public messages = signal<IChatMessage[]>([]);
  public loading = signal<boolean>(false);
  public files = signal<IChatFile[]>([]);
  public uploading = signal<boolean>(false);
  public uploadError = signal<string | null>(null);

  constructor(private readonly http: HttpClient) {}

  public enviar(prompt: string, attachmentIds: string[] = []): void {
    prompt = prompt?.trim();
    if (!prompt || this.loading()) return;

    const now = new Date();

    // 1. Cria a mensagem do usuário já com o timestamp local do front
    const userMsg: IChatMessage = {
      messageType: 'USER',
      content: prompt,
      timestamp: now.toISOString(),
      notValid: false,
      attachments: this.files()
        .filter((file) => attachmentIds.includes(file.id))
        .map((file) => file.name),
    };

    // Adiciona a mensagem do usuário na tela
    this.messages.update((list) => [...list, userMsg]);
    this.loading.set(true);

    // Payload enviado ao back-end
    const payload = { prompt, attachmentIds };

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
          this.messages.update((list) => [...list, aiMsg]);
        },
        error: (err) => {
          console.error('Erro no envio:', err);

          // Remove a mensagem do usuário que falhou para que ele não tente "Tente de novo" sem contexto
          userMsg.notValid = true;

          // Adiciona um aviso amigável explicando que a mensagem não foi gravada
          const errorMsg: IChatMessage = {
            messageType: 'ASSISTANT',
            content: err?.error?.message
              ? `⚠️ ${err.error.message}`
              : '⚠️ Ocorreu um erro ao processar sua pergunta. Como ela não foi registrada no histórico, por favor, envie o prompt novamente por extenso.',
            timestamp: new Date().toISOString(),
          };
          this.messages.update((list) => [...list, errorMsg]);
        },
      });
  }

  public carregarHistorico(): void {
    this.http
      .get<IChatMessage[]>(`${this.urlBase}/history`)
      .subscribe({
        next: (data) => {
          this.messages.set(data);
        },
        error: (err) => console.error('Error ao carregar historico', err),
      });
  }

  public carregarArquivos(): void {
    this.http.get<IChatFile[]>(`${this.urlBase}/files`).subscribe({
      next: (files) => this.files.set(files),
      error: (err) => console.error('Erro ao carregar arquivos', err),
    });
  }

  public enviarArquivos(files: File[]): void {
    if (!files.length || this.uploading()) return;
    this.uploadError.set(null);
    const formData = new FormData();
    files.forEach((file) => formData.append('files', file));
    this.uploading.set(true);
    this.http
      .post<IChatFile[]>(`${this.urlBase}/files`, formData)
      .pipe(finalize(() => this.uploading.set(false)))
      .subscribe({
        next: (created) => {
          const createdIds = new Set(created.map((file) => file.id));
          this.files.update((current) => [
            ...created,
            ...current.filter((file) => !createdIds.has(file.id)),
          ]);
        },
        error: (err) => {
          console.error('Erro no upload', err);
          this.uploadError.set(err?.error?.message ?? 'Não foi possível enviar os arquivos.');
        },
      });
  }

  public removerArquivo(id: string): void {
    this.http.delete<void>(`${this.urlBase}/files/${id}`).subscribe({
      next: () => this.files.update((files) => files.filter((file) => file.id !== id)),
      error: (err) => console.error('Erro ao remover arquivo', err),
    });
  }

  public baixarArquivo(file: IChatFile): void {
    this.http.get(`${this.urlBase}/files/${file.id}/download`, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = file.name;
        anchor.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => console.error('Erro ao baixar arquivo', err),
    });
  }
}
