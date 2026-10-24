package br.com.fluxocaixa.consolidado.support;

import br.com.fluxocaixa.consolidado.application.port.out.ProjecaoRepository;
import br.com.fluxocaixa.consolidado.domain.Efeito;
import br.com.fluxocaixa.consolidado.domain.LancamentoRegistrado;
import br.com.fluxocaixa.consolidado.domain.TipoLancamento;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base dos ITs da consulta com banco real (T-023): a API completa (filtros, segurança e o adaptador
 * JDBC de leitura) sobre o {@link PostgresConsolidado}, com o consumidor desligado. A projeção é
 * montada pela porta de escrita da T-021, como o consumidor faria.
 */
@AutoConfigureMockMvc
@Import(RelogioFixo.class)
public abstract class IntegracaoApiPostgres extends IntegracaoPostgres {

  /** Instante gravado em {@code atualizado_em}, com microssegundos como o {@code TIMESTAMPTZ}. */
  protected static final Instant EM = Instant.parse("2026-10-21T14:03:12.123456Z");

  @Autowired protected MockMvc mvc;
  @Autowired protected ProjecaoRepository projecao;

  @DynamicPropertySource
  static void jwt(DynamicPropertyRegistry registro) {
    TokensDeTeste.registrarPropriedades(registro);
  }

  protected void credito(UUID comerciante, String data, String valor) {
    aplicar(comerciante, TipoLancamento.CREDITO, null, data, valor);
  }

  protected void debito(UUID comerciante, String data, String valor) {
    aplicar(comerciante, TipoLancamento.DEBITO, null, data, valor);
  }

  protected void estorno(UUID comerciante, TipoLancamento original, String data, String valor) {
    aplicar(comerciante, TipoLancamento.ESTORNO, original, data, valor);
  }

  private void aplicar(
      UUID comerciante, TipoLancamento tipo, TipoLancamento original, String data, String valor) {
    LancamentoRegistrado evento =
        new LancamentoRegistrado(
            UUID.randomUUID(),
            comerciante,
            UUID.randomUUID(),
            tipo,
            new BigDecimal(valor),
            LocalDate.parse(data),
            original,
            original == null ? null : UUID.randomUUID());
    projecao.aplicarSeNovo(evento.eventId(), Efeito.de(evento), EM);
  }
}
