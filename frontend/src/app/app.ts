import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ChatComponent } from './chat-component/chat-component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ChatComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly title = signal('sistema-mr-web');
}
