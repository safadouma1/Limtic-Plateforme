import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ChatMessage, IaService } from '../../../../core/services/ia.service';
import { MarkdownPipe } from '../../../../shared/markdown.pipe';
import { messageErreur } from '../../../../shared/erreur';
import { IaStatusBannerComponent } from '../ia-status-banner.component';

interface Message extends ChatMessage { ts: Date; erreur?: boolean; }

@Component({
  selector: 'app-ia-chat',
  imports: [FormsModule, DatePipe, MarkdownPipe, IaStatusBannerComponent],
  templateUrl: './ia-chat.component.html',
  styleUrl: './ia-chat.component.scss',
})
export class IaChatComponent {
  ia = inject(IaService);
  private messagesEl = viewChild<ElementRef<HTMLElement>>('messagesEl');

  userInput = '';
  loading = signal(false);
  messages = signal<Message[]>([]);

  suggestions = [
    'Résume les travaux de la plateforme en traitement du langage',
    'Quels chercheurs travaillent sur la vision par ordinateur ?',
    'Quelles publications traitent de cybersécurité ?',
    "Explique simplement ce qu'est l'apprentissage fédéré",
  ];

  suggestion(s: string) { this.userInput = s; this.envoyer(); }

  nouvelleConversation() { this.messages.set([]); }

  envoyer() {
    const content = this.userInput.trim();
    if (!content || this.loading()) return;
    // Historique envoyé à Gemini : uniquement les échanges réussis
    const historique: ChatMessage[] = this.messages().filter(m => !m.erreur).map(({ role, content }) => ({ role, content }));
    this.messages.update(m => [...m, { role: 'user', content, ts: new Date() }]);
    this.userInput = '';
    this.loading.set(true);
    this.scroll();

    this.ia.chat(content, historique).subscribe({
      next: r => this.ajouter({ role: 'assistant', content: r, ts: new Date() }),
      error: e => this.ajouter({ role: 'assistant', content: messageErreur(e, "L'assistant n'a pas pu répondre."), ts: new Date(), erreur: true }),
    });
  }

  private ajouter(m: Message) {
    this.messages.update(list => [...list, m]);
    this.loading.set(false);
    this.scroll();
  }

  private scroll() {
    setTimeout(() => {
      const el = this.messagesEl()?.nativeElement;
      el?.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
    }, 50);
  }
}
