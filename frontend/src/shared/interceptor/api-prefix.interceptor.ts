import { HttpInterceptorFn } from '@angular/common/http';

export const apiPrefixInterceptor: HttpInterceptorFn = (req, next) => {
  // URLs externas não passam pelo proxy local da aplicação.
  if (req.url.startsWith('http')) {
    return next(req);
  }

  // Este /api é o prefixo de roteamento do frontend. O proxy remove somente
  // essa primeira ocorrência; um /api já presente na URL pertence ao backend.
  const urlComBarra = req.url.startsWith('/') ? req.url : `/${req.url}`;

  const apiReq = req.clone({
    url: `/api${urlComBarra}`,
  });

  return next(apiReq);
};
