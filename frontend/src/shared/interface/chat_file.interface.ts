export type ChatFileStatus = 'QUEUED' | 'EXTRACTING' | 'EMBEDDING' | 'READY' | 'FAILED';

export interface IChatFile {
  id: string;
  name: string;
  mimeType: string;
  size: number;
  status: ChatFileStatus;
  errorMessage?: string | null;
  contextTokenCount: number;
  createdAt: string;
  updatedAt: string;
}
