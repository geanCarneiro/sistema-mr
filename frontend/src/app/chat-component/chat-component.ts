import { AfterViewChecked, Component, ElementRef, OnDestroy, OnInit, signal, ViewChild, WritableSignal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AiChatService } from '../../shared/service/ai_chat.service';
import { IChatMessage } from '../../shared/interface/chat_message.interface';
import { AuthService, IUserData } from '../../shared/service/auth.service';
import { IChatFile } from '../../shared/interface/chat_file.interface';

@Component({
  selector: 'app-chat-component',
  imports: [CommonModule, FormsModule],
  templateUrl: './chat-component.html',
  styleUrl: './chat-component.scss',
})
export class ChatComponent implements OnInit, AfterViewChecked, OnDestroy {
  @ViewChild('scrollContainer') private scrollContainer!: ElementRef<HTMLElement>;

  messages: WritableSignal<IChatMessage[]>;
  prompt = signal<string>('');
  loading: WritableSignal<boolean>;
  files: WritableSignal<IChatFile[]>;
  uploading: WritableSignal<boolean>;
  uploadError: WritableSignal<string | null>;
  selectedFileIds = signal<string[]>([]);
  userData: IUserData | null;
  private renderedMessageCount = -1;
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

  ngOnInit() {
    this.aiChatService.carregarHistorico();
    this.aiChatService.carregarArquivos();
    this.filePolling = setInterval(() => {
      if (this.files().some((file) => this.isProcessing(file))) {
        this.aiChatService.carregarArquivos();
      }
    }, 2500);
  }

  ngOnDestroy(): void {
    if (this.filePolling) clearInterval(this.filePolling);
  }

  ngAfterViewChecked() {
    if (this.renderedMessageCount !== this.messages().length) {
      this.renderedMessageCount = this.messages().length;
      this.scrollToBottom();
    }
  }

  recarregarChat() {
    this.aiChatService.carregarHistorico();
  }

  enviar() {
    this.aiChatService.enviar(this.prompt(), this.selectedFileIds());
    this.prompt.set('');
    this.selectedFileIds.set([]);
  }

  selecionarArquivos(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.aiChatService.enviarArquivos(Array.from(input.files ?? []));
    input.value = '';
  }

  alternarAnexo(file: IChatFile): void {
    if (file.status !== 'READY') return;
    this.selectedFileIds.update((ids) =>
      ids.includes(file.id) ? ids.filter((id) => id !== file.id) : [...ids, file.id],
    );
  }

  anexoSelecionado(id: string): boolean {
    return this.selectedFileIds().includes(id);
  }

  baixarArquivo(file: IChatFile): void {
    this.aiChatService.baixarArquivo(file);
  }

  removerArquivo(file: IChatFile): void {
    this.selectedFileIds.update((ids) => ids.filter((id) => id !== file.id));
    this.aiChatService.removerArquivo(file.id);
  }

  isProcessing(file: IChatFile): boolean {
    return ['QUEUED', 'EXTRACTING', 'EMBEDDING'].includes(file.status);
  }

  statusLabel(status: IChatFile['status']): string {
    return {
      QUEUED: 'Na fila',
      EXTRACTING: 'Extraindo texto/OCR',
      EMBEDDING: 'Gerando embeddings',
      READY: 'Pronto',
      FAILED: 'Falhou',
    }[status];
  }

  logout(): void {
    this.authService.logout();
  }

  private scrollToBottom(): void {
    try {
      this.scrollContainer.nativeElement.scrollTop =
        this.scrollContainer.nativeElement.scrollHeight;
    } catch (err) {}
  }
}
