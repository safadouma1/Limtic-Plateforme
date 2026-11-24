import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { messageErreur } from '../../../shared/erreur';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  loading = signal(false);
  error = signal('');
  showPass = signal(false);
  expire = this.route.snapshot.queryParamMap.has('expire');

  form = this.fb.nonNullable.group({
    email:    ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  remplirDemo(email: string) {
    this.form.setValue({ email, password: 'admin123' });
  }

  submit() {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading.set(true);
    this.error.set('');
    this.auth.login(this.form.getRawValue()).subscribe({
      next: u => {
        const retour = this.route.snapshot.queryParamMap.get('retour');
        // §7.3 : un chercheur est redirigé vers sa propre fiche
        const defaut = u.role === 'CHERCHEUR' && u.chercheurId ? '/utilisateur/ma-fiche' : '/dashboard';
        this.router.navigateByUrl(retour && retour.startsWith('/') && !retour.startsWith('//') ? retour : defaut);
      },
      error: err => { this.error.set(messageErreur(err, 'Email ou mot de passe incorrect.')); this.loading.set(false); },
    });
  }
}
