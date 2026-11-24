import { HttpErrorResponse } from '@angular/common/http';

/** Transforme une erreur HTTP en message lisible pour l'utilisateur. */
export function messageErreur(err: unknown, defaut = 'Une erreur est survenue.'): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) return 'Serveur injoignable : vérifiez que le backend Spring Boot est démarré (port 8080).';
    const msg = (err.error && typeof err.error === 'object' && 'message' in err.error) ? String(err.error.message) : '';
    if (msg) return msg;
    if (err.status === 403) return 'Accès refusé : droits insuffisants.';
    if (err.status === 404) return 'Élément introuvable.';
  }
  return defaut;
}
