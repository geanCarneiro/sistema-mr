import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChatConversationComponent, ChatSubmitEvent } from './chat-conversation.component';
import { IChatMessage } from '../../../shared/interface/chat_message.interface';

describe('ChatConversationComponent', () => {
  let component: ChatConversationComponent;
  let fixture: ComponentFixture<ChatConversationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChatConversationComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ChatConversationComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('messages', []);
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('emits a trimmed submission with the current attachment context', () => {
    const submissions: ChatSubmitEvent[] = [];
    component.submit.subscribe((event) => submissions.push(event));
    fixture.componentRef.setInput('selectedFileIds', ['file-1']);
    fixture.componentRef.setInput('includeRelatedFiles', true);
    component.prompt.set('  Resuma o arquivo  ');

    component.enviar();

    expect(submissions).toEqual([
      {
        prompt: 'Resuma o arquivo',
        attachmentIds: ['file-1'],
        includeRelatedFiles: true,
      },
    ]);
    expect(component.prompt()).toBe('');
  });

  it('does not submit an empty prompt or while loading', () => {
    const submit = vi.fn();
    component.submit.subscribe(submit);

    component.prompt.set('   ');
    component.enviar();
    component.prompt.set('Pergunta');
    fixture.componentRef.setInput('loading', true);
    component.enviar();

    expect(submit).not.toHaveBeenCalled();
  });

  it('renders accessible source information and conversation messages', () => {
    const messages: IChatMessage[] = [
      {
        messageType: 'ASSISTANT',
        content: 'Resposta',
        timestamp: '2026-08-30T00:00:00Z',
        groundingFiles: [
          { id: 'available', name: 'manual.pdf', available: true },
          { id: 'missing', name: 'apagado.pdf', available: false },
        ],
      },
    ];
    fixture.componentRef.setInput('messages', messages);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Resposta');
    expect(
      element.querySelector('[aria-label="Fonte manual.pdf. Arquivo utilizado na resposta."]'),
    ).not.toBeNull();
    expect(
      element.querySelector('[aria-label="Fonte apagado.pdf. Arquivo não está mais disponível."]'),
    ).not.toBeNull();
  });

  it('renders privacy review as part of the conversation', () => {
    fixture.componentRef.setInput('messages', [
      {
        messageId: 'privacy-review',
        messageType: 'ASSISTANT',
        messageKind: 'PRIVACY_REVIEW',
        privacyFileId: 'review',
        content:
          'Encontrei um problema ao analisar o arquivo captura.png. A análise local não foi concluída. Responda com suas próprias palavras.',
        timestamp: '2026-08-30T00:00:00Z',
      },
    ]);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[role="dialog"]')).toBeNull();
    expect(element.textContent).toContain('captura.png');
    expect(element.textContent).toContain('A análise local não foi concluída');
    expect(element.textContent).toContain('Responda com suas próprias palavras');
    expect(element.textContent).not.toContain('Manter bloqueado');
  });
});
