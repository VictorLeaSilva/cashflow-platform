package br.com.fluxocaixa.consolidado.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.consolidado.application.port.out.ProjecaoRepository;
import br.com.fluxocaixa.consolidado.domain.Efeito;
import br.com.fluxocaixa.consolidado.domain.GeradorDeEventos;
import br.com.fluxocaixa.consolidado.domain.LancamentoRegistrado;
import br.com.fluxocaixa.consolidado.domain.ProjecaoDeReferencia;
import br.com.fluxocaixa.consolidado.domain.Propriedades;
import br.com.fluxocaixa.consolidado.support.IntegracaoPostgres;
import br.com.fluxocaixa.consolidado.support.PostgresConsolidado;
import br.com.fluxocaixa.consolidado.support.TabelasDoConsolidado;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * AC-008.3 (parte de banco): a propriedade de ordem e duplicatas da T-020, agora pelo adaptador e
 * contra o PostgreSQL (sugestão (c) da Q-033). Cada caso começa com as tabelas vazias e compara a
 * tabela inteira com o modelo de referência (Q-042).
 */
@Tag("RF-008")
@Tag("RNF-004")
class ProjecaoPostgresPropertiesIT extends IntegracaoPostgres {

  private static final int TENTATIVAS = 100;
  private static final Instant AGORA = Instant.parse("2026-10-22T13:30:00Z");

  @Autowired private ProjecaoRepository projecao;

  @Test
  void ordemEDuplicatasNaoMudamAProjecaoNoBanco() {
    Propriedades.paraTodo(
        "a entrega com ordem e duplicatas dá no banco a projeção dos distintos",
        TENTATIVAS,
        GeradorDeEventos.fronteiras(),
        GeradorDeEventos::cenario,
        caso -> {
          PostgresConsolidado.esvaziar();

          int aplicados = 0;
          for (LancamentoRegistrado evento : caso.entrega1()) {
            if (projecao.aplicarSeNovo(evento.eventId(), Efeito.de(evento), AGORA)) {
              aplicados++;
            }
          }

          assertThat(TabelasDoConsolidado.dias())
              .isEqualTo(ProjecaoDeReferencia.aplicandoTodos(caso.distintos()).dias());
          assertThat(aplicados).isEqualTo(caso.distintos().size());
          assertThat(TabelasDoConsolidado.eventosProcessados()).isEqualTo(caso.distintos().size());
        });
  }
}
