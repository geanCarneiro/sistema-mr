import { Routes } from '@angular/router';
import { LoginComponent } from './login/login';
import { ChatComponent } from './chat-component/chat-component';
import { authGuard } from '../shared/guard/auth.guard';
import { redirectIfAuthenticatedGuard } from '../shared/guard/redirectIfAuthenticated';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'chat',
  },
  {
    path: 'login',
    canActivate: [redirectIfAuthenticatedGuard],
    component: LoginComponent
  },
  {
    path: 'chat',
    canActivate: [authGuard],
    component: ChatComponent
  }

];
