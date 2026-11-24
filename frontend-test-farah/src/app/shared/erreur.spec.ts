import { HttpErrorResponse } from '@angular/common/http';
import { messageErreur } from './erreur';

describe('messageErreur', () => {
  it('signale un backend arrêté', () => {
    expect(messageErreur(new HttpErrorResponse({ status: 0 }))).toContain('backend Spring Boot');
  });
  it('reprend le message renvoyé par l\'API', () => {
    expect(messageErreur(new HttpErrorResponse({ status: 400, error: { message: 'Réponse au captcha incorrecte.' } })))
      .toBe('Réponse au captcha incorrecte.');
  });
  it('traduit 403 et 404, sinon message par défaut', () => {
    expect(messageErreur(new HttpErrorResponse({ status: 403 }))).toContain('Accès refusé');
    expect(messageErreur(new HttpErrorResponse({ status: 404 }))).toContain('introuvable');
    expect(messageErreur(new Error('x'), 'Défaut')).toBe('Défaut');
  });
});
