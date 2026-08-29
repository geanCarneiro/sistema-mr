import { HttpInterceptorFn } from '@angular/common/http';

export const apiPrefixInterceptor: HttpInterceptorFn = (req, next) => {
  // Se a URL já for externa (ex: https://...) ou já tiver /api, deixa passar
  if (req.url.startsWith('http') || req.url === '/api' || req.url.startsWith('/api/')) {
    return next(req);
  }

  // Garante a barra no início e injeta o prefixo /api
  const urlComBarra = req.url.startsWith('/') ? req.url : `/${req.url}`;

  const apiReq = req.clone({
    url: `/api${urlComBarra}`,
  });

  return next(apiReq);
};
