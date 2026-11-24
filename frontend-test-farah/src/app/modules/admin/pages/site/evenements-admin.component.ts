import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { RapportService } from '../../../../core/services/rapport.service';
import { SiteService } from '../../../../core/services/site.service';
import { Fiche, STATUT_FICHE } from '../../../../core/models/rapport.model';
import { ETATS_EVENEMENT, Intervenant, MediaDto, TYPES_EVENEMENT, TypeEvenement } from '../../../../core/models/site.model';
import { DepotComponent } from '../../../../shared/depot.component';
import { EditeurRicheComponent } from '../../../../shared/editeur-riche.component';
import { messageErreur } from '../../../../shared/erreur';

interface PhotoEdit { mediaId: number; legende: string | null; }
interface EvenementEdit {
  id?: number; titre: string; type: TypeEvenement; dateDebut: string | null; dateFin: string | null; lieu: string;
  organisateurs: string; organiseParLabo: boolean; description: string | null; programme: string | null;
  programmeMediaId: number | null; intervenants: Intervenant[]; photos: PhotoEdit[];
}

/** Gestion des événements (§4.3.4) : texte riche, programme PDF, intervenants, galerie (dépôt multiple, glisser-déposer, légendes). */
@Component({
  selector: 'app-evenements-admin',
  imports: [FormsModule, DatePipe, RouterLink, DepotComponent, EditeurRicheComponent],
  template: `
    <div class="page-head">
      <div><h1 class="page-title">Événements</h1><p class="page-subtitle">Conférences, journées, séminaires et ateliers publiés sur le site</p></div>
      @if (!e()) { <button class="btn btn-primary" (click)="nouveau()"><span class="mi sm">add</span> Nouvel événement</button> }
    </div>
    @if (ok()) { <div class="alert alert-success"><span class="mi sm">check_circle</span> {{ ok() }}</div> }
    @if (erreur()) { <div class="alert alert-error"><span class="mi sm">error</span> {{ erreur() }}</div> }

    @if (e(); as e) {
      <form class="card card-body mb-24 fade-up" (ngSubmit)="enregistrer(e)">
        <div class="row mb-16" style="justify-content:space-between"><div class="card-title">{{ e.id ? 'Modifier' : 'Nouvel' }} événement</div>
          <button type="button" class="btn btn-ghost btn-icon" (click)="e$.set(null)" aria-label="Fermer"><span class="mi sm">close</span></button></div>
        <div class="form-group"><label class="form-label" for="t">Titre *</label><input id="t" class="form-control" name="t" [(ngModel)]="e.titre" required /></div>
        <div class="form-row">
          <div class="form-group"><label class="form-label" for="ty">Type *</label>
            <select id="ty" class="form-control" name="ty" [(ngModel)]="e.type">@for (t of typesListe; track t[0]) { <option [value]="t[0]">{{ t[1] }}</option> }</select></div>
          <div class="form-group"><label class="form-label" for="li">Lieu *</label><input id="li" class="form-control" name="li" [(ngModel)]="e.lieu" /></div>
        </div>
        <div class="form-row">
          <div class="form-group"><label class="form-label" for="d1">Date de début *</label><input id="d1" type="date" class="form-control" name="d1" [(ngModel)]="e.dateDebut" /></div>
          <div class="form-group"><label class="form-label" for="d2">Date de fin</label><input id="d2" type="date" class="form-control" name="d2" [(ngModel)]="e.dateFin" />
            <span class="form-hint">Le statut (à venir, en cours, terminé) se met à jour automatiquement selon ces dates.</span></div>
        </div>
        <div class="form-row">
          <div class="form-group"><label class="form-label" for="or">Organisateurs *</label><input id="or" class="form-control" name="or" [(ngModel)]="e.organisateurs" /></div>
          <div class="form-group" style="align-self:end"><label class="row small" style="gap:8px;cursor:pointer"><input type="checkbox" name="lab" [(ngModel)]="e.organiseParLabo" /> Organisé par le laboratoire (rapport d'activité, III-1)</label></div>
        </div>
        <div class="form-group"><label class="form-label" for="de">Description</label><textarea id="de" class="form-control" rows="4" name="de" [(ngModel)]="e.description"></textarea></div>
        <div class="form-group"><label class="form-label">Programme scientifique</label>
          <app-editeur-riche label="Programme scientifique" [(valeur)]="e.programme" />
          <div class="row" style="margin-top:8px">
            <app-depot libelle="Joindre le programme (PDF)" icone="picture_as_pdf" accept="application/pdf" (deposes)="e.programmeMediaId = $event[0].id" />
            @if (e.programmeMediaId) { <a class="small" [href]="site.url('/api/media/' + e.programmeMediaId)" target="_blank" style="color:var(--accent)">PDF joint</a>
              <button type="button" class="btn btn-ghost btn-sm" (click)="e.programmeMediaId = null">Retirer</button> }
          </div></div>

        <div class="form-group"><div class="row" style="justify-content:space-between"><label class="form-label" style="margin:0">Intervenants invités</label>
          <button type="button" class="btn btn-ghost btn-sm" (click)="e.intervenants.push({ nom: '', affiliation: '', titreIntervention: '' })"><span class="mi sm">person_add</span> Ajouter</button></div>
          @for (i of e.intervenants; track $index; let n = $index) {
            <div class="int-row">
              <input class="form-control" placeholder="Nom" [name]="'in' + n" [(ngModel)]="i.nom" [attr.aria-label]="'Nom de l\\'intervenant ' + (n + 1)" />
              <input class="form-control" placeholder="Affiliation" [name]="'ia' + n" [(ngModel)]="i.affiliation" aria-label="Affiliation" />
              <input class="form-control" placeholder="Titre de l'intervention" [name]="'it' + n" [(ngModel)]="i.titreIntervention" aria-label="Titre de l'intervention" />
              <button type="button" class="btn btn-ghost btn-icon" (click)="e.intervenants.splice(n, 1)" aria-label="Retirer l'intervenant"><span class="mi sm">close</span></button>
            </div>
          }</div>

        <div class="form-group"><div class="row" style="justify-content:space-between"><label class="form-label" style="margin:0">Galerie photos ({{ e.photos.length }})</label>
          <app-depot libelle="Ajouter des photos" icone="add_photo_alternate" accept="image/jpeg,image/png,image/webp,image/gif" [multiple]="true" (deposes)="ajouterPhotos(e, $event)" /></div>
          <p class="form-hint">Glissez-déposez les vignettes pour changer l'ordre (ou utilisez les flèches). La première photo sert de couverture.</p>
          <div class="gal">
            @for (p of e.photos; track p.mediaId; let n = $index) {
              <div class="ph" draggable="true" (dragstart)="glisse = n" (dragover)="$event.preventDefault()" (drop)="deposer(e, n)" [class.cover]="n === 0">
                <img [src]="site.url('/api/media/' + p.mediaId)" [alt]="p.legende || 'Photo ' + (n + 1)" />
                <input class="form-control" placeholder="Légende" [name]="'pl' + n" [(ngModel)]="p.legende" [attr.aria-label]="'Légende de la photo ' + (n + 1)" />
                <div class="row" style="justify-content:space-between">
                  <div class="row" style="gap:2px">
                    <button type="button" class="btn btn-ghost btn-sm" [disabled]="n === 0" (click)="deplacer(e, n, -1)" aria-label="Avancer"><span class="mi sm">arrow_back</span></button>
                    <button type="button" class="btn btn-ghost btn-sm" [disabled]="n === e.photos.length - 1" (click)="deplacer(e, n, 1)" aria-label="Reculer"><span class="mi sm">arrow_forward</span></button>
                  </div>
                  <button type="button" class="btn btn-danger btn-sm" (click)="e.photos.splice(n, 1)" aria-label="Retirer la photo"><span class="mi sm">delete</span></button>
                </div>
              </div>
            }
          </div></div>
        <div class="row"><button class="btn btn-primary" type="submit" [disabled]="envoi()"><span class="mi sm">save</span> Enregistrer</button>
          <button type="button" class="btn btn-ghost" (click)="e$.set(null)">Annuler</button></div>
      </form>
    }

    <div class="card"><div class="table-wrap"><table class="data-table">
      <thead><tr><th>Événement</th><th>Dates</th><th>État</th><th>Photos</th><th>Statut</th><th></th></tr></thead>
      <tbody>
        @for (x of liste(); track x.id) {
          <tr>
            <td><a [routerLink]="['/evenements', x.id]" target="_blank" style="font-weight:500">{{ x['titre'] }}</a><div class="small muted">{{ types[$any(x['type'])] }} · {{ x['lieu'] }}</div></td>
            <td class="small">@if (x['dateDebut']) { {{ x['dateDebut'] | date:'d/MM/y' }}@if (x['dateFin']) { – {{ x['dateFin'] | date:'d/MM/y' }} } } @else { {{ x['annee'] }} }</td>
            <td><span class="chip" [class]="'chip ' + etats[$any(x['etat'])].chip">{{ etats[$any(x['etat'])].l }}</span></td>
            <td>{{ x['photos']?.length ?? 0 }}</td>
            <td><span class="chip" [class]="'chip ' + statuts[x.statut].chip">{{ statuts[x.statut].label }}</span></td>
            <td><div class="actions">
              <button class="btn btn-ghost btn-icon" (click)="modifier(x)" title="Modifier" [disabled]="x.statut === 'VERROUILLE'"><span class="mi sm">edit</span></button>
              <button class="btn btn-danger btn-icon" (click)="supprimer(x)" title="Supprimer" [disabled]="x.statut === 'VERROUILLE'"><span class="mi sm">delete</span></button></div></td>
          </tr>
        } @empty { <tr><td colspan="6" class="muted small">Aucun événement.</td></tr> }
      </tbody>
    </table></div></div>
  `,
  styles: [`
    .int-row { display:grid; grid-template-columns:1fr 1fr 1.4fr auto; gap:8px; margin-top:8px; }
    .gal { display:grid; grid-template-columns:repeat(auto-fill,minmax(190px,1fr)); gap:12px; margin-top:8px; }
    .ph { background:var(--surface2); border:1px solid var(--border); border-radius:10px; padding:8px; display:flex; flex-direction:column; gap:6px; cursor:grab; }
    .ph.cover { border-color:var(--accent); } .ph img { width:100%; aspect-ratio:4/3; object-fit:cover; border-radius:6px; }
    .ph .form-control { padding:5px 8px; font-size:12.5px; }
    @media (max-width:720px) { .int-row { grid-template-columns:1fr; } }
  `],
})
export class EvenementsAdminComponent {
  private svc = inject(RapportService);
  site = inject(SiteService);
  liste = signal<Fiche[]>([]);
  e$ = signal<EvenementEdit | null>(null);
  e = this.e$.asReadonly();
  ok = signal(''); erreur = signal(''); envoi = signal(false);
  glisse = -1;
  types: Record<string, string> = TYPES_EVENEMENT; etats: Record<string, { l: string; chip: string }> = ETATS_EVENEMENT; statuts = STATUT_FICHE;
  typesListe = Object.entries(TYPES_EVENEMENT) as [TypeEvenement, string][];

  constructor() { this.charger(); }
  charger() {
    this.svc.lister('evenements').subscribe({ next: l => this.liste.set([...l].sort((a, b) => (b['dateDebut'] ?? '').localeCompare(a['dateDebut'] ?? ''))),
      error: e => this.erreur.set(messageErreur(e)) });
  }
  nouveau() {
    this.ok.set('');
    this.e$.set({ titre: '', type: 'SEMINAIRE', dateDebut: null, dateFin: null, lieu: 'ISI, Ariana', organisateurs: 'LIMTIC', organiseParLabo: true,
      description: '', programme: '', programmeMediaId: null, intervenants: [], photos: [] });
  }
  modifier(x: Fiche) {
    this.ok.set('');
    this.e$.set({ id: x.id, titre: x['titre'], type: x['type'], dateDebut: x['dateDebut'], dateFin: x['dateFin'], lieu: x['lieu'] ?? '',
      organisateurs: x['organisateurs'] ?? '', organiseParLabo: !!x['organiseParLabo'], description: x['description'] ?? '',
      programme: x['programme'] ?? '', programmeMediaId: x['programmeMediaId'] ?? null,
      intervenants: (x['intervenants'] ?? []).map((i: Intervenant) => ({ ...i })), photos: (x['photos'] ?? []).map((p: PhotoEdit) => ({ ...p })) });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
  ajouterPhotos(e: EvenementEdit, m: MediaDto[]) { for (const x of m) e.photos.push({ mediaId: x.id, legende: '' }); }
  deplacer(e: EvenementEdit, n: number, d: number) { const [p] = e.photos.splice(n, 1); e.photos.splice(n + d, 0, p); }
  deposer(e: EvenementEdit, n: number) { if (this.glisse < 0 || this.glisse === n) return; const [p] = e.photos.splice(this.glisse, 1); e.photos.splice(n, 0, p); this.glisse = -1; }

  enregistrer(e: EvenementEdit) {
    if (!e.titre.trim() || !e.dateDebut) { this.erreur.set('Le titre et la date de début sont obligatoires.'); return; }
    if (e.dateFin && e.dateFin < e.dateDebut) { this.erreur.set('La date de fin précède la date de début.'); return; }
    this.envoi.set(true); this.erreur.set('');
    const corps = { ...e, annee: Number(e.dateDebut.slice(0, 4)), intervenants: e.intervenants.filter(i => i.nom?.trim()) };
    (e.id ? this.svc.modifier('evenements', e.id, corps) : this.svc.creer('evenements', corps)).subscribe({
      next: () => { this.envoi.set(false); this.e$.set(null); this.ok.set('Événement enregistré.'); this.charger(); },
      error: x => { this.envoi.set(false); this.erreur.set(messageErreur(x)); },
    });
  }
  supprimer(x: Fiche) {
    if (!confirm(`Supprimer « ${x['titre']} » ?`)) return;
    this.svc.supprimer('evenements', x.id).subscribe({ next: () => this.charger(), error: e => this.erreur.set(messageErreur(e)) });
  }
}
