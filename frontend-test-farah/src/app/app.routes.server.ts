import { RenderMode, ServerRoute } from '@angular/ssr';

// L'application dépend de la session stockée dans le navigateur (localStorage) :
// le rendu se fait côté client pour éviter les redirections incorrectes pendant le SSR.
export const serverRoutes: ServerRoute[] = [
  { path: '**', renderMode: RenderMode.Client },
];
