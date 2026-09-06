import { Component, OnDestroy, OnInit, signal, WritableSignal } from '@angular/core';
import { AiChatService, IChatSubject } from '../../shared/service/ai_chat.service';
import { IChatFile } from '../../shared/interface/chat_file.interface';
import { IChatMessage } from '../../shared/interface/chat_message.interface';
import { AuthService, IUserData } from '../../shared/service/auth.service';
import {
  ChatConversationComponent,
  ChatSubmitEvent,
} from './chat-conversation/chat-conversation.component';
import { AttachmentPanelComponent } from './attachment-panel/attachment-panel.component';

@Component({
  selector: 'app-chat-component',
  imports: [ChatConversationComponent, AttachmentPanelComponent],
  templateUrl: './chat.component.html',
  styleUrl: './chat.component.scss',
})
export class ChatComponent implements OnInit, OnDestroy {
  private static readonly MAX_SELECTED_FILES = 10;

  messages: WritableSignal<IChatMessage[]>;
  loading: WritableSignal<boolean>;
  files: WritableSignal<IChatFile[]>;
  uploading: WritableSignal<boolean>;
  uploadError: WritableSignal<string | null>;
  selectedFileIds = signal<string[]>([]);
  includeRelatedFiles = signal(false);
  selectionError = signal<string | null>(null);
  subjects = signal<IChatSubject[]>([]);
  activeSubjectId = signal<string | null>(null);
  userData: IUserData | null;
  private filePolling?: ReturnType<typeof setInterval>;

  constructor(
    private aiChatService: AiChatService,
    private authService: AuthService,
  ) {
    this.messages = this.aiChatService.messages;
    this.loading = this.aiChatService.loading;
    this.files = this.aiChatService.files;
    this.uploading = this.aiChatService.uploading;
    this.uploadError = this.aiChatService.uploadError;
    this.userData = this.authService.userData;
  }

  ngOnInit(): void {
    this.carregarAssuntos();
    this.aiChatService.carregarHistorico();
    this.aiChatService.carregarArquivos();
    this.filePolling = setInterval(() => {
      if (this.files().some((file) => this.isProcessing(file))) {
        this.aiChatService.carregarArquivos();
      }
    }, 2500);
  }

  carregarAssuntos(): void {
    this.aiChatService.carregarAssuntos().subscribe({
      next: (subjects) => {
        this.subjects.set(subjects);
        if (!this.activeSubjectId() && subjects.length) {
          this.activeSubjectId.set(subjects[0].id);
        }
      },
      error: (err) => console.error('Erro ao carregar assuntos', err),
    });
  }

  selecionarAssunto(subject: IChatSubject): void {
    this.activeSubjectId.set(subject.id);
  }

  ngOnDestroy(): void {
    if (this.filePolling) clearInterval(this.filePolling);
  }

  recarregarChat(): void {
    this.aiChatService.carregarHistorico();
  }

  enviar(event: ChatSubmitEvent): void {
    if (!event.prompt || this.loading()) return;

    this.aiChatService.enviar(
      event.prompt,
      [...event.attachmentIds],
      event.includeRelatedFiles,
      this.activeSubjectId(),
    );
    this.limparSelecao();
  }

  selecionarArquivos(files: File[]): void {
    this.aiChatService.enviarArquivos(files);
  }

  alternarAnexo(file: IChatFile): void {
    if (file.status !== 'READY') return;

    const selectedIds = this.selectedFileIds();
    if (selectedIds.includes(file.id)) {
      const remainingIds = selectedIds.filter((id) => id !== file.id);
      this.selectedFileIds.set(remainingIds);
      this.selectionError.set(null);
      if (!remainingIds.length) this.includeRelatedFiles.set(false);
      return;
    }

    if (selectedIds.length >= ChatComponent.MAX_SELECTED_FILES) {
      this.selectionError.set(`Selecione no máximo ${ChatComponent.MAX_SELECTED_FILES} anexos.`);
      return;
    }

    this.selectedFileIds.set([...selectedIds, file.id]);
    this.selectionError.set(null);
  }

  definirBuscaRelacionada(checked: boolean): void {
    this.includeRelatedFiles.set(this.selectedFileIds().length > 0 && checked);
  }

  limparSelecao(): void {
    this.selectedFileIds.set([]);
    this.includeRelatedFiles.set(false);
    this.selectionError.set(null);
  }

  baixarArquivo(file: IChatFile): void {
    this.aiChatService.baixarArquivo(file);
  }

  removerArquivo(file: IChatFile): void {
    const remainingIds = this.selectedFileIds().filter((id) => id !== file.id);
    this.selectedFileIds.set(remainingIds);
    if (!remainingIds.length) this.includeRelatedFiles.set(false);
    this.aiChatService.removerArquivo(file.id);
  }

  reprocessarArquivo(file: IChatFile): void {
    if (file.status === 'FAILED') {
      this.aiChatService.reprocessarArquivo(file.id);
    }
  }

  isProcessing(file: IChatFile): boolean {
    return ['QUEUED', 'EXTRACTING', 'EMBEDDING'].includes(file.status);
  }

  logout(): void {
    this.authService.logout();
  }
}
