import { AfterViewChecked, Component, ElementRef, OnInit, signal, ViewChild, WritableSignal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AiChatService } from '../../shared/service/ai_chat.service';
import { IChatMessage } from '../../shared/interface/chat_message.interface';

@Component({
  selector: 'app-chat-component',
  imports: [CommonModule, FormsModule],
  templateUrl: './chat-component.html',
  styleUrl: './chat-component.scss',
})
export class ChatComponent implements OnInit, AfterViewChecked {
  @ViewChild('scrollContainer') private scrollContainer!: ElementRef<HTMLElement>;

  messages: WritableSignal<IChatMessage[]>;
  prompt = signal<string>('');
  loading: WritableSignal<boolean>;
  conversationId: string;

  constructor(
    private aiChatService: AiChatService
  ) {
    this.messages = this.aiChatService.menssages;
    this.loading = this.aiChatService.loading;
    this.conversationId = this.aiChatService.CONVERSATION_ID;
  }

  ngOnInit() {
    this.aiChatService.carregarHistorico();
  }

  ngAfterViewChecked() {
    this.scrollToBottom();
  }

  recarregarChat() {
    this.aiChatService.carregarHistorico();
  }

  enviar() {
    this.aiChatService.enviar(this.prompt());
    this.prompt.set('');
  }

  private scrollToBottom(): void {
    try {
      this.scrollContainer.nativeElement.scrollTop = this.scrollContainer.nativeElement.scrollHeight;
    } catch (err) {}
  }
}
