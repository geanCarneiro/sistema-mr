export interface IGroundingFile {
  id: string;
  name: string;
  sourceType?: 'EXPLICIT' | 'SEMANTIC' | string;
  similarity?: number | null;
  available?: boolean;
}

export interface IChatMessage {
  messageType?: 'USER' | 'ASSISTANT' | 'SYSTEM' | string;
  messageId?: string;
  interactionId?: string;
  timestamp?: string;
  content?: string;
  notValid?: boolean;
  attachments?: string[];
  groundingFiles?: IGroundingFile[];
}
