import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PublicationService } from '../../../../core/services/publication.service';
import { ChercheurService } from '../../../../core/services/chercheur.service';
import { DomaineService } from '../../../../core/services/domaine.service';
import { IaService } from '../../../../core/services/ia.service';
import { Chercheur, Domaine, Publication, PublicationPayload, SourceClassement, TYPES_PUBLICATION, TypePublication } from '../../../../core/models/chercheur.model';
import { DepotComponent } from '../../../../shared/depot.component';
import { ClassementBadgeComponent } from '../../../../shared/classement-badge.component';
import { SiteService } from '../../../../core/services/site.service';
import { messageErreur } from '../../../../shared/erreur';

@Component({
  selector: 'app-publications-admin',
  imports: [ReactiveFormsModule, FormsModule, RouterLink, DepotComponent, ClassementBadgeComponent],
  templateUrl: './publications.component.html',
})
export class PublicationsAdminComponent implements OnInit {
  private fb = inject(FormBuilder);
  private svc = inject(PublicationService);
  private chSvc = inject(ChercheurService);
  private domSvc = inject(DomaineService);
  ia = inject(IaService);
  site = inject(SiteService);
  typesLib = TYPES_PUBLICATION;

  /** ?edit=ID : ouvre directement le formulaire de modification (lien depuis la fiche publication). */
  edit = input<string>();

  publications = signal<Publication[]>([]);
  chercheurs = signal<Chercheur[]>([]);
  domaines = signal<Domaine[]>([]);
  q = signal('');
  error = signal('');
  info = signal('');

  formOuvert = signal(false);
  editId = signal<number | null>(null);
  saving = signal(false);
  iaKw = signal(false);
  iaErr = signal('');

  filtrees = computed(() => {
    const q = this.q().toLowerCase().trim();
    return this.publications().filter(p => !q || `${p.titre} ${p.motsCles.join(' ')}`.toLowerCase().includes(q));
  });

  form = this.fb.group({
    titre:        this.fb.nonNullable.control('', Validators.required),
    resume:       this.fb.nonNullable.control(''),
    annee:        this.fb.control<number | null>(new Date().getFullYear(), [Validators.min(1900), Validators.max(2100)]),
    doi:          this.fb.nonNullable.control(''),
    pdfUrl:       this.fb.nonNullable.control(''),
    motsCles:     this.fb.nonNullable.control(''),
    chercheurIds: this.fb.nonNullable.control<number[]>([]),
    domaineId:    this.fb.control<number | null>(null),
    type:         this.fb.nonNullable.control<TypePublication>('ARTICLE_JOURNAL'),
    venue:        this.fb.nonNullable.control(''),
    auteursTexte: this.fb.nonNullable.control(''),
    classement:   this.fb.nonNullable.control(''),
    sourceClassement: this.fb.control<SourceClassement | null>(null),
    facteurImpact: this.fb.control<number | null>(null, Validators.min(0)),
    snip:         this.fb.control<number | null>(null, Validators.min(0)),
    citations:    this.fb.control<number | null>(null, Validators.min(0)),
    volume: this.fb.nonNullable.control(''), numero: this.fb.nonNullable.control(''), pages: this.fb.nonNullable.control(''),
    lieu: this.fb.nonNullable.control(''), pays: this.fb.nonNullable.control(''), editeur: this.fb.nonNullable.control(''),
    isbn: this.fb.nonNullable.control(''), institution: this.fb.nonNullable.control(''), comiteOrganisation: this.fb.nonNullable.control(''),
    pdfMediaId:   this.fb.control<number | null>(null),
  });
  types = Object.entries(TYPES_PUBLICATION) as [TypePublication, string][];
  /** Liste de référence des classements (§4.3.3) ; une valeur libre reste possible. */
  classements: Record<string, string[]> = { SCIMAGO: ['Q1', 'Q2', 'Q3', 'Q4'], CORE: ['A*', 'A', 'B', 'C'], JCR: ['Q1', 'Q2', 'Q3', 'Q4'], SNIP: [], AUTRE: [] };
  sources = ['SCIMAGO', 'CORE', 'JCR', 'SNIP', 'AUTRE'] as SourceClassement[];

  ngOnInit() {
    this.charger();
    this.chSvc.getAll().subscribe({ next: c => this.chercheurs.set(c), error: () => {} });
    this.domSvc.getAll().subscribe({ next: d => this.domaines.set(d), error: () => {} });
    const id = Number(this.edit());
    if (id) this.svc.getById(id).subscribe({ next: p => this.ouvrir(p), error: () => {} });
  }

  charger() {
    this.svc.getAll().subscribe({ next: p => this.publications.set(p), error: e => this.error.set(messageErreur(e)) });
  }

  ouvrir(p?: Publication) {
    this.iaErr.set('');
    this.editId.set(p?.id ?? null);
    this.form.reset({
      titre: p?.titre ?? '', resume: p?.resume ?? '', annee: p?.annee ?? new Date().getFullYear(),
      doi: p?.doi ?? '', pdfUrl: p?.pdfUrl ?? '', motsCles: p?.motsCles.join(', ') ?? '',
      chercheurIds: p?.chercheurs.map(c => c.id) ?? [], domaineId: p?.domaine?.id ?? null,
      type: p?.type ?? 'ARTICLE_JOURNAL', venue: p?.venue ?? '', auteursTexte: p?.auteursTexte ?? '',
      classement: p?.classement ?? '', sourceClassement: p?.sourceClassement ?? null, facteurImpact: p?.facteurImpact ?? null,
      snip: p?.snip ?? null, citations: p?.citations ?? null, volume: p?.volume ?? '', numero: p?.numero ?? '', pages: p?.pages ?? '',
      lieu: p?.lieu ?? '', pays: p?.pays ?? '', editeur: p?.editeur ?? '', isbn: p?.isbn ?? '', institution: p?.institution ?? '',
      comiteOrganisation: p?.comiteOrganisation ?? '', pdfMediaId: p?.pdfMediaId ?? null,
    });
    this.formOuvert.set(true);
  }

  fermer() { this.formOuvert.set(false); this.editId.set(null); }

  /** Remplit les mots-clés à partir du titre + résumé grâce à Gemini. */
  genererMotsCles() {
    const v = this.form.getRawValue();
    const texte = `${v.titre}\n\n${v.resume}`.trim();
    if (texte.length < 20) { this.iaErr.set("Saisissez d'abord un titre et un résumé."); return; }
    this.iaKw.set(true); this.iaErr.set('');
    this.ia.extraireMotsCles(texte).subscribe({
      next: mots => { this.form.controls.motsCles.setValue(mots.join(', ')); this.iaKw.set(false); },
      error: e => { this.iaErr.set(messageErreur(e)); this.iaKw.set(false); },
    });
  }

  submit() {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.saving.set(true); this.error.set('');
    const v = this.form.getRawValue();
    const payload: PublicationPayload = {
      ...v,
      motsCles: v.motsCles.split(',').map(s => s.trim()).filter(Boolean),
    };
    const id = this.editId();
    (id ? this.svc.update(id, payload) : this.svc.create(payload)).subscribe({
      next: () => {
        this.saving.set(false); this.fermer(); this.charger();
        this.info.set(id ? 'Publication mise à jour.' : 'Publication créée.');
        setTimeout(() => this.info.set(''), 3000);
      },
      error: e => { this.error.set(messageErreur(e, 'Erreur lors de la sauvegarde.')); this.saving.set(false); },
    });
  }

  supprimer(p: Publication) {
    if (!confirm(`Supprimer « ${p.titre} » ?`)) return;
    this.svc.delete(p.id).subscribe({
      next: () => this.publications.update(l => l.filter(x => x.id !== p.id)),
      error: e => this.error.set(messageErreur(e)),
    });
  }

  auteurs(p: Publication) { return p.chercheurs.map(c => `${c.prenom} ${c.nom}`).join(', '); }
}
