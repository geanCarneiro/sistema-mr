import {
  AfterViewChecked,
  Component,
  ElementRef,
  input,
  output,
  signal,
  ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { IChatMessage, IGroundingFile } from '../../../shared/interface/chat_message.interface';
import { IChatSubject } from '../../../shared/service/ai_chat.service';
import { ButtonDirective, ButtonIcon } from 'primeng/button';
import { Times } from '@primeicons/angular/times';
import { Paperclip } from '@primeicons/angular/paperclip';

export interface ChatSubmitEvent {
  prompt: string;
  attachmentIds: readonly string[];
  includeRelatedFiles: boolean;
}

@Component({
  selector: 'app-chat-conversation',
  imports: [CommonModule, FormsModule, ButtonDirective, ButtonIcon, Times, Paperclip],
  templateUrl: './chat-conversation.component.html',
  styleUrl: './chat-conversation.component.scss',
})
export class ChatConversationComponent implements AfterViewChecked {
  @ViewChild('scrollContainer') private scrollContainer?: ElementRef<HTMLElement>;

  messages = input.required<IChatMessage[]>();
  loading = input(false);
  userName = input<string | null>(null);
  subjects = input<IChatSubject[]>([]);
  activeSubjectId = input<string | null>(null);
  selectedFileIds = input<readonly string[]>([]);
  includeRelatedFiles = input(false);

  submit = output<ChatSubmitEvent>();
  subjectSelected = output<IChatSubject>();
  reloadHistory = output<void>();
  clearSelection = output<void>();
  logout = output<void>();

  prompt = signal('');
  private renderedMessageCount = -1;

  ngAfterViewChecked(): void {
    if (this.renderedMessageCount !== this.messages().length) {
      this.renderedMessageCount = this.messages().length;
      this.scrollToBottom();
    }
  }

  enviar(): void {
    const prompt = this.prompt().trim();
    if (!prompt || this.loading()) return;

    this.submit.emit({
      prompt,
      attachmentIds: [...this.selectedFileIds()],
      includeRelatedFiles: this.includeRelatedFiles(),
    });
    this.prompt.set('');
  }

  enviarComEnter(event: Event): void {
    event.preventDefault();
    this.enviar();
  }

  getGeneralSubject(): IChatSubject | null {
    const subjects = this.subjects();
    return subjects.find((subject) => subject.kind === 'GENERAL_CHAT') ?? subjects[0] ?? null;
  }

  sourceAccessibilityLabel(source: IGroundingFile): string {
    return source.available === false
      ? `Fonte ${source.name}. Arquivo não está mais disponível.`
      : `Fonte ${source.name}. Arquivo utilizado na resposta.`;
  }

  private scrollToBottom(): void {
    const container = this.scrollContainer?.nativeElement;
    if (container) container.scrollTop = container.scrollHeight;
  }
}
