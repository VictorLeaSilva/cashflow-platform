// Inicialização da Swagger UI (só no profile local). Fica num arquivo próprio para que a CSP das
// páginas de documentação não precise de 'unsafe-inline' em scripts (T-034, Q-078).
window.ui = SwaggerUIBundle({
  url: '/v3/api-docs',
  dom_id: '#swagger-ui',
  presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
  layout: 'StandaloneLayout'
});
