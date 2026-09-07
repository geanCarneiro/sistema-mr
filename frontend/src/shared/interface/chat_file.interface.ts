export type ChatFileStatus =
  'QUEUED' | 'EXTRACTING' | 'EMBEDDING' | 'NEEDS_REVIEW' | 'READY' | 'FAILED';

export type ChatFileSensitivity = 'NORMAL' | 'PERSONAL' | 'SENSITIVE' | 'RESTRICTED' | 'UNKNOWN';

export interface IChatFile {
  id: string;
  name: string;
  mimeType: string;
  size: number;
  status: ChatFileStatus;
  errorMessage?: string | null;
  contextTokenCount: number;
  sensitivity?: ChatFileSensitivity;
  createdAt: string;
  updatedAt: string;
}
