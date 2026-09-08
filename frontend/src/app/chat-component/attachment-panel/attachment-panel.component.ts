import { Component, ElementRef, input, output, signal, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { IChatFile } from '../../../shared/interface/chat_file.interface';
import { ButtonDirective, ButtonIcon } from 'primeng/button';
import { Checkbox } from 'primeng/checkbox';
import { Plus } from '@primeicons/angular/plus';
import { Refresh } from '@primeicons/angular/refresh';
import { CloudDownload } from '@primeicons/angular/cloud-download';
import { Trash } from '@primeicons/angular/trash';
import { ExclamationTriangle } from '@primeicons/angular/exclamation-triangle';

@Component({
  selector: 'app-attachment-panel',
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
    ExclamationTriangle,
  ],
  templateUrl: './attachment-panel.component.html',
  styleUrl: './attachment-panel.component.scss',
})
export class AttachmentPanelComponent {
  private readonly tamanhoFormatador = new Intl.NumberFormat('pt-BR', {
    maximumFractionDigits: 1,
  });

  @ViewChild('fileInput') private fileInput?: ElementRef<HTMLInputElement>;

  files = input.required<IChatFile[]>();
  uploading = input(false);
  uploadError = input<string | null>(null);
  selectedFileIds = input<readonly string[]>([]);
  includeRelatedFiles = input(false);
  selectionError = input<string | null>(null);

  filesSelected = output<File[]>();
  fileToggled = output<IChatFile>();
  relatedFilesChanged = output<boolean>();
  fileDownloaded = output<IChatFile>();
  fileRemoved = output<IChatFile>();
  fileRetried = output<IChatFile>();
  privacyReviewRequested = output<IChatFile>();

  abrirSeletorDeArquivos(): void {
    if (!this.uploading()) this.fileInput?.nativeElement.click();
  }

  selecionarArquivos(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.filesSelected.emit(Array.from(input.files ?? []));
    input.value = '';
  }

  alternarAnexo(file: IChatFile): void {
    if (file.status === 'READY') this.fileToggled.emit(file);
  }

  anexoSelecionado(id: string): boolean {
    return this.selectedFileIds().includes(id);
  }

  definirBuscaRelacionada(checked: boolean): void {
    this.relatedFilesChanged.emit(checked);
  }

  statusLabel(status: IChatFile['status']): string {
    return {
      QUEUED: 'Na fila',
      EXTRACTING: 'Extraindo texto/OCR',
      EMBEDDING: 'Gerando embeddings',
      NEEDS_REVIEW: 'Revisão necessária',
      READY: 'Pronto',
      FAILED: 'Falhou',
    }[status];
  }

  tamanhoFormatado(bytes: number): string {
    const unidades = ['B', 'KB', 'MB', 'GB'];
    let valor = Math.max(0, bytes);
    let unidade = 0;

    while (valor >= 1024 && unidade < unidades.length - 1) {
      valor /= 1024;
      unidade += 1;
    }

    return `${this.tamanhoFormatador.format(valor)} ${unidades[unidade]}`;
  }

  baixarArquivo(file: IChatFile): void {
    this.fileDownloaded.emit(file);
  }

  removerArquivo(file: IChatFile): void {
    this.fileRemoved.emit(file);
  }

  reprocessarArquivo(file: IChatFile): void {
    if (file.status === 'FAILED' || file.status === 'NEEDS_REVIEW') this.fileRetried.emit(file);
  }

  solicitarRevisaoPrivacidade(file: IChatFile): void {
    this.privacyReviewRequested.emit(file);
  }
}
