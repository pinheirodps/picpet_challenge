/**
 * Production API location. Swapped in for environment.ts at build time via the
 * fileReplacements entry in angular.json's production configuration.
 *
 * <p>The relative path assumes the built frontend is served behind the same host as the
 * API (a reverse proxy, or Spring serving the static bundle). Point it at an absolute URL
 * if the two are deployed separately.
 */
export const environment = {
  apiBaseUrl: '/api',
};
