import {
  AfterViewChecked,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  signal,
  ViewChild,
  WritableSignal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AiChatService } from '../../shared/service/ai_chat.service';
import { IChatMessage } from '../../shared/interface/chat_message.interface';
import { AuthService, IUserData } from '../../shared/service/auth.service';
import { IChatFile } from '../../shared/interface/chat_file.interface';
import { ButtonDirective, ButtonIcon } from 'primeng/button';
import { Checkbox } from 'primeng/checkbox';
import { Plus } from '@primeicons/angular/plus';
import { Refresh } from '@primeicons/angular/refresh';
import { CloudDownload } from '@primeicons/angular/cloud-download';
import { Trash } from '@primeicons/angular/trash';
import { Times } from '@primeicons/angular/times';
import { Paperclip } from '@primeicons/angular/paperclip';

@Component({
  selector: 'app-chat-component',
  imports: [
    CommonModule,
    FormsModule,
    ButtonDirective,
    ButtonIcon,
    Checkbox,
    Plus,
    Refresh,
    CloudDownload,
    Trash,
    Times,
    Paperclip,
  ],
  templateUrl: './chat.component.html',
  styleUrl: './chat.component.scss',
})
export class ChatComponent implements OnInit, AfterViewChecked, OnDestroy {
  @ViewChild('scrollContainer') private scrollContainer!: ElementRef<HTMLElement>;
  @ViewChild('fileInput') private fileInput?: ElementRef<HTMLInputElement>;

  private static readonly MAX_SELECTED_FILES = 10;

  messages: WritableSignal<IChatMessage[]>;
  prompt = signal<string>('');
  loading: WritableSignal<boolean>;
  files: WritableSignal<IChatFile[]>;
  uploading: WritableSignal<boolean>;
  uploadError: WritableSignal<string | null>;
  selectedFileIds = signal<string[]>([]);
  includeRelatedFiles = signal(false);
  selectionError = signal<string | null>(null);
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
    const prompt = this.prompt().trim();
    if (!prompt || this.loading()) return;

    this.aiChatService.enviar(prompt, this.selectedFileIds(), this.includeRelatedFiles());
    this.prompt.set('');
    this.limparSelecao();
  }

  abrirSeletorDeArquivos(): void {
    if (!this.uploading()) this.fileInput?.nativeElement.click();
  }

  selecionarArquivos(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.aiChatService.enviarArquivos(Array.from(input.files ?? []));
    input.value = '';
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

  anexoSelecionado(id: string): boolean {
    return this.selectedFileIds().includes(id);
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
