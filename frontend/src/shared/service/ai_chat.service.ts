import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { IChatMessage, IGroundingFile } from '../interface/chat_message.interface';
import { finalize } from 'rxjs';
import { IChatFile } from '../interface/chat_file.interface';

export interface IChatResponse {
  interactionId: string;
  userMessageId: string;
  assistantMessageId: string;
  content: string;
  timestamp: string;
  messageType: 'ASSISTANT';
  groundingFiles?: IGroundingFile[];
  privacyReviewResolved?: boolean;
}

interface IChatStartResponse {
  runId: string;
  acknowledgement: string;
  timestamp: string;
}

interface IRunEventResponse {
  runId: string;
  revision: number;
  status: string;
  phase: string;
  message?: string | null;
  result?: IChatResponse | null;
  failureReason?: string | null;
  terminal: boolean;
  changed: boolean;
  timestamp: string;
}

export interface IChatSubject {
  id: string;
  title: string;
  kind: string;
  createdAt?: string | null;
}

@Injectable({ providedIn: 'root' })
export class AiChatService {
  private readonly urlBase = '/ai/chat';

  public messages = signal<IChatMessage[]>([]);
  public loading = signal<boolean>(false);
  public processingMessage = signal<string | null>(null);
  public files = signal<IChatFile[]>([]);
  public uploading = signal<boolean>(false);
  public uploadError = signal<string | null>(null);
  public pendingPrivacyFileId = signal<string | null>(null);
  private readonly finishedRuns = new Set<string>();

  constructor(private readonly http: HttpClient) {}

  public enviar(
    prompt: string,
    attachmentIds: string[] = [],
    includeRelatedFiles = false,
    subjectId: string | null = null,
  ): void {
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
    const payload = { prompt, attachmentIds, includeRelatedFiles, subjectId };
    const requestPayload = {
      ...payload,
      privacyReviewFileId: this.pendingPrivacyFileId(),
    };

    this.http.post<IChatStartResponse>(this.urlBase, requestPayload).subscribe({
      next: (res) => {
        const acknowledgement: IChatMessage = {
          messageType: 'ASSISTANT',
          messageId: `ack-${res.runId}`,
          content: res.acknowledgement,
          timestamp: res.timestamp,
        };
        this.messages.update((list) => [...list, acknowledgement]);
        this.processingMessage.set('Estou preparando a próxima etapa…');
        this.pollRun(res.runId, 0, userMsg);
      },
      error: (err) => {
        this.handleInitialError(err, userMsg);
      },
    });
  }

  private pollRun(runId: string, after: number, userMsg: IChatMessage): void {
    this.http
      .get<IRunEventResponse>(`${this.urlBase}/runs/${runId}/events`, {
        params: { after, wait: 55 },
      })
      .subscribe({
        next: (event) => {
          if (event.message && event.status !== 'COMPLETED' && event.status !== 'FAILED') {
            this.processingMessage.set(event.message);
          }

          if (event.result && !this.finishedRuns.has(runId)) {
            this.finishedRuns.add(runId);
            this.appendResult(event.result, userMsg);
          }

          if (event.terminal || event.status === 'WAITING_FOR_USER') {
            this.loading.set(false);
            this.processingMessage.set(null);
            if (event.status === 'FAILED' && !event.result) {
              this.appendFailure(event.failureReason ?? event.message);
            }
            return;
          }

          this.pollRun(runId, event.revision, userMsg);
        },
        error: (err) => {
          this.loading.set(false);
          this.processingMessage.set(null);
          this.appendFailure(err?.error?.message ?? 'A espera pela execução foi interrompida.');
        },
      });
  }

  private appendResult(result: IChatResponse, userMsg: IChatMessage): void {
    if (result.privacyReviewResolved) {
      this.pendingPrivacyFileId.set(null);
      this.carregarArquivos();
    }
    this.messages.update((list) =>
      list.map((message) =>
        message === userMsg
          ? {
              ...message,
              messageId: result.userMessageId,
              interactionId: result.interactionId,
            }
          : message,
      ),
    );
    this.messages.update((list) => [
      ...list,
      {
        messageType: 'ASSISTANT',
        messageId: result.assistantMessageId,
        interactionId: result.interactionId,
        content: result.content,
        timestamp: result.timestamp,
        groundingFiles: result.groundingFiles ?? [],
      },
    ]);
  }

  private handleInitialError(err: { error?: { message?: string } }, userMsg: IChatMessage): void {
    this.loading.set(false);
    this.processingMessage.set(null);
    userMsg.notValid = true;
    this.appendFailure(
      err?.error?.message ?? 'Não foi possível iniciar a execução. Envie a mensagem novamente.',
    );
  }

  private appendFailure(message: string | null | undefined): void {
    this.messages.update((list) => [
      ...list,
      {
        messageType: 'ASSISTANT',
        content: `⚠️ ${message ?? 'Não foi possível concluir esta execução.'}`,
        timestamp: new Date().toISOString(),
      },
    ]);
  }

  public carregarHistorico(): void {
    this.http.get<IChatMessage[]>(`${this.urlBase}/history`).subscribe({
      next: (data) => {
        this.messages.set(data);
      },
      error: (err) => console.error('Error ao carregar historico', err),
    });
  }

  public solicitarRevisaoPrivacidade(file: IChatFile): void {
    this.http.post<IChatMessage>(`${this.urlBase}/privacy-review`, { fileId: file.id }).subscribe({
      next: (message) => {
        this.pendingPrivacyFileId.set(file.id);
        this.messages.update((list) => [...list, message]);
      },
      error: (err) => {
        this.appendFailure(
          err?.error?.message ?? 'Não foi possível iniciar a revisão de privacidade.',
        );
      },
    });
  }

  public carregarAssuntos() {
    return this.http.get<IChatSubject[]>(`${this.urlBase}/subjects`);
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

  public reprocessarArquivo(id: string): void {
    this.http.post<IChatFile>(`${this.urlBase}/files/${id}/retry`, {}).subscribe({
      next: (updatedFile) => {
        this.files.update((files) => files.map((file) => (file.id === id ? updatedFile : file)));
      },
      error: (err) => console.error('Erro ao reprocessar arquivo', err),
    });
  }
}
