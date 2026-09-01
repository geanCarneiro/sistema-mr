import { HttpInterceptorFn } from '@angular/common/http';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem('token');
  const isAuthEndpoint = req.url.includes('/auth/');

  if (token && !isAuthEndpoint && (req.url === '/api' || req.url.startsWith('/api/'))) {
    const authReq = req.clone({
      headers: req.headers.set('Authorization', `Bearer ${token}`),
    });

    return next(authReq);
  }

  return next(req);
};
