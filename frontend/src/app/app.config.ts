import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { apiPrefixInterceptor } from '../shared/interceptor/api-prefix.interceptor';
import { authInterceptor } from '../shared/interceptor/auth.interceptor';
import { providePrimeNG } from 'primeng/config';
import AuraBase from '@primeuix/themes/aura/base';
import AuraButton from '@primeuix/themes/aura/button';
import AuraCheckbox from '@primeuix/themes/aura/checkbox';

const sistemaMrPreset = {
  ...AuraBase,
  components: {
    button: AuraButton,
    checkbox: AuraCheckbox,
  },
};

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([apiPrefixInterceptor, authInterceptor])),
    providePrimeNG({
      theme: {
        preset: sistemaMrPreset,
        options: {
          darkModeSelector: '.app-dark',
        },
      },
    }),
  ],
};
