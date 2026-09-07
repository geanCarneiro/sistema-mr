import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';

import { ChatComponent } from './chat.component';
import { AiChatService } from '../../shared/service/ai_chat.service';
import { AuthService } from '../../shared/service/auth.service';
import { IChatFile } from '../../shared/interface/chat_file.interface';

describe('ChatComponent', () => {
  let component: ChatComponent;
  let fixture: ComponentFixture<ChatComponent>;
  let aiChatService: ReturnType<typeof createAiChatServiceMock>;

  beforeEach(async () => {
    aiChatService = createAiChatServiceMock();

    await TestBed.configureTestingModule({
      imports: [ChatComponent],
      providers: [
        {
          provide: AiChatService,
          useValue: aiChatService,
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

  function createAiChatServiceMock() {
    return {
      messages: signal([]),
      loading: signal(false),
      files: signal<IChatFile[]>([]),
      uploading: signal(false),
      uploadError: signal<string | null>(null),
      carregarHistorico: vi.fn(),
      carregarArquivos: vi.fn(),
      enviar: vi.fn(),
      enviarArquivos: vi.fn(),
      removerArquivo: vi.fn(),
      baixarArquivo: vi.fn(),
      reprocessarArquivo: vi.fn(),
      solicitarRevisaoPrivacidade: vi.fn(),
      carregarAssuntos: vi.fn(() => ({
        subscribe: ({ next }: { next: (subjects: never[]) => void }) => next([]),
      })),
    };
  }

  it('should create and load the conversation resources', () => {
    expect(component).toBeTruthy();
    expect(aiChatService.carregarHistorico).toHaveBeenCalledOnce();
    expect(aiChatService.carregarArquivos).toHaveBeenCalledOnce();
  });

  it('selects only ready files and clears the hybrid option with the selection', () => {
    const ready = file('ready', 'READY');
    const failed = file('failed', 'FAILED');

    component.alternarAnexo(failed);
    component.alternarAnexo(ready);
    component.definirBuscaRelacionada(true);

    expect(component.selectedFileIds()).toEqual(['ready']);
    expect(component.includeRelatedFiles()).toBe(true);

    component.limparSelecao();

    expect(component.selectedFileIds()).toEqual([]);
    expect(component.includeRelatedFiles()).toBe(false);
  });

  it('sends the explicit and related-file choices from the conversation event', () => {
    component.selectedFileIds.set(['ready']);
    component.includeRelatedFiles.set(true);

    component.enviar({
      prompt: 'Compare os documentos',
      attachmentIds: component.selectedFileIds(),
      includeRelatedFiles: component.includeRelatedFiles(),
    });

    expect(aiChatService.enviar).toHaveBeenCalledWith(
      'Compare os documentos',
      ['ready'],
      true,
      null,
    );
    expect(component.selectedFileIds()).toEqual([]);
    expect(component.includeRelatedFiles()).toBe(false);
  });

  it('limits explicit selection to the backend contract', () => {
    Array.from({ length: 11 }, (_, index) => file(`ready-${index}`, 'READY')).forEach((readyFile) =>
      component.alternarAnexo(readyFile),
    );

    expect(component.selectedFileIds()).toHaveLength(10);
    expect(component.selectionError()).toBe('Selecione no máximo 10 anexos.');
  });

  it('coordinates file actions through the chat service', () => {
    const ready = file('ready', 'READY');
    const failed = file('failed', 'FAILED');

    component.selecionarArquivos([new File(['conteúdo'], 'manual.pdf')]);
    component.baixarArquivo(ready);
    component.removerArquivo(ready);
    component.reprocessarArquivo(failed);

    expect(aiChatService.enviarArquivos).toHaveBeenCalled();
    expect(aiChatService.baixarArquivo).toHaveBeenCalledWith(ready);
    expect(aiChatService.removerArquivo).toHaveBeenCalledWith('ready');
    expect(aiChatService.reprocessarArquivo).toHaveBeenCalledWith('failed');
  });

  function file(id: string, status: IChatFile['status']): IChatFile {
    return {
      id,
      name: `${id}.pdf`,
      mimeType: 'application/pdf',
      size: 1024,
      status,
      contextTokenCount: 10,
      createdAt: '2026-08-30T00:00:00Z',
      updatedAt: '2026-08-30T00:00:00Z',
    };
  }
});
