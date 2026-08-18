export interface IChatMessage {
  messageType?: 'USER' | 'ASSISTANT' | 'SYSTEM' | string;
  type?: string;
  content?: string;
}
