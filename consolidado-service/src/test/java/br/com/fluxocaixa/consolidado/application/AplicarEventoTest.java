package br.com.fluxocaixa.consolidado.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.consolidado.application.AplicarEvento.Resultado;
import br.com.fluxocaixa.consolidado.application.port.out.ProjecaoRepository;
import br.com.fluxocaixa.consolidado.domain.Efeito;
import br.com.fluxocaixa.consolidado.domain.LancamentoRegistrado;
import br.com.fluxocaixa.consolidado.domain.TipoLancamento;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Caso de uso: efeito do domínio, instante do {@code Clock} (Q-038) e resultado. */
@Tag("RF-008")
class AplicarEventoTest {

  private static final Instant AGORA = Instant.parse("2026-10-23T15:00:00.123456Z");

  /** Porta em memória: deduplica por {@code eventId} e guarda o que recebeu. */
  static final class ProjecaoEmMemoria implements ProjecaoRepository {

    final Set<UUID> processados = new HashSet<>();
    final List<Efeito> efeitos = new ArrayList<>();
    final List<Instant> instantes = new ArrayList<>();

    @Override
    public boolean aplicarSeNovo(UUID eventId, Efeito efeito, Instant instante) {
      instantes.add(instante);
      if (!processados.add(eventId)) {
        return false;
      }
      efeitos.add(efeito);
      return true;
    }
  }

  private final ProjecaoEmMemoria projecao = new ProjecaoEmMemoria();
  private final AplicarEvento aplicarEvento =
      new AplicarEvento(projecao, Clock.fixed(AGORA, ZoneOffset.UTC));

  @Test
  void eventoNovoEAplicadoComOEfeitoDoDominioEOInstanteDoRelogio() {
    LancamentoRegistrado evento = debito(new UUID(0L, 1L));

    Resultado resultado = aplicarEvento.aplicar(evento);

    assertThat(resultado).isEqualTo(Resultado.APLICADO);
    assertThat(projecao.efeitos).containsExactly(Efeito.de(evento));
    assertThat(projecao.instantes).containsExactly(AGORA);
  }

  @Test
  void mesmoEventIdEDuplicado() {
    LancamentoRegistrado evento = debito(new UUID(0L, 1L));
    aplicarEvento.aplicar(evento);

    assertThat(aplicarEvento.aplicar(evento)).isEqualTo(Resultado.DUPLICADO);
    assertThat(projecao.efeitos).hasSize(1);
  }

  private static LancamentoRegistrado debito(UUID eventId) {
    return new LancamentoRegistrado(
        eventId,
        new UUID(0L, 0xaaL),
        new UUID(0L, 2L),
        TipoLancamento.DEBITO,
        new BigDecimal("12.34"),
        LocalDate.parse("2026-10-23"),
        null,
        null);
  }
}
