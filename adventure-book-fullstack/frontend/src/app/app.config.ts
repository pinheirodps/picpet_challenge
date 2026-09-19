import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { playerIdInterceptor } from './core/interceptors/player-id.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    // The player-id header is attached here rather than in each service, so a request added
    // later can't forget it.
    provideHttpClient(withInterceptors([playerIdInterceptor]))
  ]
};
