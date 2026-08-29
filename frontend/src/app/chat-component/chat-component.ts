import { AfterViewChecked, Component, ElementRef, OnInit, signal, ViewChild, WritableSignal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AiChatService } from '../../shared/service/ai_chat.service';
import { IChatMessage } from '../../shared/interface/chat_message.interface';
import { AuthService, IUserData } from '../../shared/service/auth.service';

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
  userData: IUserData | null;
  private renderedMessageCount = -1;

  constructor(
    private aiChatService: AiChatService,
    private authService: AuthService,
  ) {
    this.messages = this.aiChatService.messages;
    this.loading = this.aiChatService.loading;
    this.userData = this.authService.userData;
  }

  ngOnInit() {
    this.aiChatService.carregarHistorico();
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
    this.aiChatService.enviar(this.prompt());
    this.prompt.set('');
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
