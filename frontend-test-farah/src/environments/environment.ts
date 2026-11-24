export const environment = {
  production: false,
  // URL de l'API Spring Boot (backend/). Les appels Gemini passent par le backend :
  // la clé GEMINI_API_KEY n'est jamais exposée dans le navigateur.
  apiUrl: 'http://localhost:8080/api',
};
