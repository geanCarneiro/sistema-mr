import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';

export const authGuard: CanActivateFn = (route, state) => {
  const router = inject(Router);

  const token = localStorage.getItem('token');

  if (token) {
    const {exp} = JSON.parse(atob(token.split('.')[1]));

    if(Date.now() / 1000 < exp) {
      return true;
    }

  }

  console.warn('Acesso negado! Redirecionando para /login...');
  return router.createUrlTree(['/login']);
}
