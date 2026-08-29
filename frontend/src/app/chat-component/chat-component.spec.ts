import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';

import { ChatComponent } from './chat-component';
import { AiChatService } from '../../shared/service/ai_chat.service';
import { AuthService } from '../../shared/service/auth.service';

describe('ChatComponent', () => {
  let component: ChatComponent;
  let fixture: ComponentFixture<ChatComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChatComponent],
      providers: [
        {
          provide: AiChatService,
          useValue: {
            messages: signal([]),
            loading: signal(false),
            carregarHistorico: vi.fn(),
            enviar: vi.fn(),
          },
        },
        {
          provide: AuthService,
          useValue: {
            userData: {
              sub: 'subject',
              nome: 'Usuário',
              avatar: '',
              email: 'usuario@example.com',
            },
            logout: vi.fn(),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ChatComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
