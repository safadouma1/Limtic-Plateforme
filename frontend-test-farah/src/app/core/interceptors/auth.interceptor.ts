import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { environment } from '../../../environments/environment';

/** Ajoute le JWT aux appels vers notre API et déconnecte proprement si le token est expiré (401). */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.currentUser()?.token;
  const versApi = req.url.startsWith(environment.apiUrl);

  if (token && versApi) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && token && versApi && !req.url.includes('/auth/')) {
        auth.logout();
        router.navigate(['/auth/login'], { queryParams: { expire: 1 } });
      }
      return throwError(() => err);
    }),
  );
};
