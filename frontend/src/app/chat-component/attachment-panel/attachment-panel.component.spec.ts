import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AttachmentPanelComponent } from './attachment-panel.component';
import { IChatFile } from '../../../shared/interface/chat_file.interface';

describe('AttachmentPanelComponent', () => {
  let component: AttachmentPanelComponent;
  let fixture: ComponentFixture<AttachmentPanelComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AttachmentPanelComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(AttachmentPanelComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('files', [file('manual', 'READY'), file('failed', 'FAILED')]);
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('emits file selection only for ready files', () => {
    const toggled: IChatFile[] = [];
    component.fileToggled.subscribe((file) => toggled.push(file));

    component.alternarAnexo(file('failed', 'FAILED'));
    component.alternarAnexo(file('manual', 'READY'));

    expect(toggled.map((item) => item.id)).toEqual(['manual']);
  });

  it('emits related search and file actions', () => {
    const relatedFilesChanged = vi.fn();
    const downloaded = vi.fn();
    const removed = vi.fn();
    const retried = vi.fn();
    component.relatedFilesChanged.subscribe(relatedFilesChanged);
    component.fileDownloaded.subscribe(downloaded);
    component.fileRemoved.subscribe(removed);
    component.fileRetried.subscribe(retried);

    const ready = file('manual', 'READY');
    const failed = file('failed', 'FAILED');
    component.definirBuscaRelacionada(true);
    component.baixarArquivo(ready);
    component.removerArquivo(ready);
    component.reprocessarArquivo(failed);

    expect(relatedFilesChanged).toHaveBeenCalledWith(true);
    expect(downloaded).toHaveBeenCalledWith(ready);
    expect(removed).toHaveBeenCalledWith(ready);
    expect(retried).toHaveBeenCalledWith(failed);
  });

  it('renders file actions with accessible names and status', () => {
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('manual.pdf');
    expect(element.textContent).toContain('Pronto');
    expect(element.textContent).toContain('Falhou');
    expect(element.querySelector('button[aria-label="Baixar manual.pdf"]')).not.toBeNull();
    expect(element.querySelector('button[aria-label="Excluir manual.pdf"]')).not.toBeNull();
    expect(element.querySelector('button[aria-label="Reprocessar failed.pdf"]')).not.toBeNull();
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
