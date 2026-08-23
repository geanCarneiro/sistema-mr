import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../service/auth.service';

// Guard para proteger a rota /login
export const redirectIfAuthenticatedGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  if (authService.isAuthenticated()) {
    // Se já tá logado e tentou ir pro /login, joga pro /chat
    return router.createUrlTree(['/chat']);
  }

  return true;
};
