export interface IChatMessage {
  messageType?: 'USER' | 'ASSISTANT' | 'SYSTEM' | string;
  timestamp?: string;
  content?: string;
}
