package br.com.fluxocaixa.lancamentos.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** AC-T012.3: hash canônico estável para comandos equivalentes e sensível a cada campo. */
@Tag("RF-007")
class RequestFingerprintTest {

  /** Semente fixa (Q-009); reproduzível com {@code -Dpropriedades.semente=<semente>}. */
  private static final long SEMENTE = Long.getLong("propriedades.semente", 20_261_023L);

  private static final int TENTATIVAS = 1_000;
  private static final UUID COMERCIANTE = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");

  @Test
  void eUmSha256HexDe64Caracteres() {
    assertThat(RequestFingerprint.deRegistro(comando("100.00")))
        .hasSize(64)
        .matches("[0-9a-f]{64}");
  }

  @Test
  void representacaoCanonicaSegueOFormatoDaTarefa() {
    var comando =
        new RegistrarLancamentoCommand(
            COMERCIANTE,
            "chave-1",
            TipoLancamento.DEBITO,
            new BigDecimal("100"),
            "Aluguel",
            Instant.parse("2026-10-22T02:30:00Z"),
            "nf-1",
            "req-1");

    assertThat(RequestFingerprint.canonico(comando))
        .isEqualTo(
            "17:POST /lancamentos|6:DEBITO|6:100.00|7:Aluguel|20:2026-10-22T02:30:00Z|4:nf-1");
  }

  @Test
  void camposOpcionaisAusentesViramHifen() {
    assertThat(RequestFingerprint.canonico(comando("10.00")))
        .isEqualTo("17:POST /lancamentos|7:CREDITO|5:10.00|-|-|-");
  }

  /** AC-T012.5 (Q-010): o texto "-" não se confunde com o campo ausente. */
  @Test
  void hifenComoTextoDifereDeCampoAusente() {
    assertThat(RequestFingerprint.deRegistro(comTextos("-", "-")))
        .isNotEqualTo(RequestFingerprint.deRegistro(comTextos(null, null)))
        .isNotEqualTo(RequestFingerprint.deRegistro(comTextos("-", null)))
        .isNotEqualTo(RequestFingerprint.deRegistro(comTextos(null, "-")));
  }

  /** AC-T012.5 (Q-010): um separador dentro do texto não imita outra combinação de campos. */
  @Test
  void separadorDentroDoTextoNaoImitaOutrosCampos() {
    // No formato sem tamanho, ambos seriam "...|a|-|-|-".
    var comSeparador = comTextos("a|-", null);
    var semSeparador = comTextos("a", "-|-");
    assertThat(RequestFingerprint.canonico(comSeparador))
        .isNotEqualTo(RequestFingerprint.canonico(semSeparador));
    assertThat(RequestFingerprint.deRegistro(comSeparador))
        .isNotEqualTo(RequestFingerprint.deRegistro(semSeparador));
  }

  @Test
  void tamanhoEmCodePointsAposNfc() {
    // "São" em NFD tem 4 code points; em NFC, 3. O emoji ocupa 2 chars Java e 1 code point.
    String nfd = Normalizer.normalize("São", Normalizer.Form.NFD);
    String emoji = "😀";
    assertThat(RequestFingerprint.canonico(comTextos(nfd, emoji)))
        .isEqualTo("17:POST /lancamentos|7:CREDITO|5:10.00|3:São|-|1:" + emoji);
  }

  @Test
  void valorComEscalasDiferentesGeraOMesmoHash() {
    assertThat(RequestFingerprint.deRegistro(comando("100")))
        .isEqualTo(RequestFingerprint.deRegistro(comando("100.00")))
        .isEqualTo(RequestFingerprint.deRegistro(comando("100.0")));
  }

  @Test
  void mesmoInstanteComOffsetsDiferentesGeraOMesmoHash() {
    Instant saoPaulo = OffsetDateTime.parse("2026-10-21T23:30:00-03:00").toInstant();
    Instant utc = OffsetDateTime.parse("2026-10-22T02:30:00Z").toInstant();
    Instant europa = OffsetDateTime.parse("2026-10-22T03:30:00+01:00").toInstant();

    assertThat(RequestFingerprint.deRegistro(comOcorrencia(saoPaulo)))
        .isEqualTo(RequestFingerprint.deRegistro(comOcorrencia(utc)))
        .isEqualTo(RequestFingerprint.deRegistro(comOcorrencia(europa)));
  }

  @Test
  void textoEmNfdENfcGeraOMesmoHash() {
    String nfc = Normalizer.normalize("São João", Normalizer.Form.NFC);
    String nfd = Normalizer.normalize("São João", Normalizer.Form.NFD);
    assertThat(nfc).isNotEqualTo(nfd);

    assertThat(RequestFingerprint.deRegistro(comTextos(nfd, nfd)))
        .isEqualTo(RequestFingerprint.deRegistro(comTextos(nfc, nfc)));
  }

  @Test
  void requestIdEChaveNaoEntramNoHash() {
    var base = comando("10.00");
    var outroRequest =
        new RegistrarLancamentoCommand(
            base.comercianteId(),
            "outra-chave",
            base.tipo(),
            base.valor(),
            base.descricao(),
            base.dataOcorrencia(),
            base.identificadorExterno(),
            "outro-request");

    assertThat(RequestFingerprint.deRegistro(outroRequest))
        .isEqualTo(RequestFingerprint.deRegistro(base));
  }

  /** Propriedade: variações equivalentes mantêm o hash; mudar qualquer campo o altera. */
  @Test
  void propriedadeEquivalenciaESensibilidade() {
    SplittableRandom aleatorio = new SplittableRandom(SEMENTE);
    for (int tentativa = 0; tentativa < TENTATIVAS; tentativa++) {
      RegistrarLancamentoCommand caso = gerar(aleatorio);
      try {
        verificarEquivalentes(caso);
        verificarSensivelACadaCampo(caso);
      } catch (AssertionError falha) {
        throw new AssertionError(
            "Propriedade falhou na tentativa "
                + tentativa
                + " (semente="
                + SEMENTE
                + "; reproduzir com -Dpropriedades.semente="
                + SEMENTE
                + "). Caso canônico: "
                + RequestFingerprint.canonico(caso),
            falha);
      }
    }
  }

  private static void verificarEquivalentes(RegistrarLancamentoCommand caso) {
    String hash = RequestFingerprint.deRegistro(caso);
    var outraEscala = copia(caso, caso.tipo(), caso.valor().setScale(4), caso.descricao());
    var outraChave =
        new RegistrarLancamentoCommand(
            caso.comercianteId(),
            caso.chave() + "-x",
            caso.tipo(),
            caso.valor(),
            caso.descricao(),
            caso.dataOcorrencia(),
            caso.identificadorExterno(),
            "req-" + caso.chave());
    assertThat(RequestFingerprint.deRegistro(outraEscala)).isEqualTo(hash);
    assertThat(RequestFingerprint.deRegistro(outraChave)).isEqualTo(hash);
  }

  private static void verificarSensivelACadaCampo(RegistrarLancamentoCommand caso) {
    String hash = RequestFingerprint.deRegistro(caso);
    TipoLancamento outroTipo =
        caso.tipo() == TipoLancamento.CREDITO ? TipoLancamento.DEBITO : TipoLancamento.CREDITO;
    List<RegistrarLancamentoCommand> alterados =
        List.of(
            copia(caso, outroTipo, caso.valor(), caso.descricao()),
            copia(caso, caso.tipo(), caso.valor().add(new BigDecimal("0.01")), caso.descricao()),
            copia(caso, caso.tipo(), caso.valor(), caso.descricao() + "!"),
            comOcorrencia(caso, caso.dataOcorrencia().plusSeconds(1)),
            comIdentificador(caso, caso.identificadorExterno() + "1"));
    for (RegistrarLancamentoCommand alterado : alterados) {
      assertThat(RequestFingerprint.deRegistro(alterado))
          .as("alterado: %s", RequestFingerprint.canonico(alterado))
          .isNotEqualTo(hash);
    }
  }

  private static RegistrarLancamentoCommand gerar(SplittableRandom aleatorio) {
    long centavos = aleatorio.nextLong(1, 1_000_000_001L);
    return new RegistrarLancamentoCommand(
        COMERCIANTE,
        "chave-" + aleatorio.nextInt(1_000_000),
        aleatorio.nextBoolean() ? TipoLancamento.CREDITO : TipoLancamento.DEBITO,
        BigDecimal.valueOf(centavos, 2),
        "descricao " + aleatorio.nextInt(1_000),
        Instant.parse("2026-10-01T00:00:00Z").plusSeconds(aleatorio.nextLong(0, 2_000_000)),
        "ext-" + aleatorio.nextInt(1_000),
        null);
  }

  private static RegistrarLancamentoCommand copia(
      RegistrarLancamentoCommand c, TipoLancamento tipo, BigDecimal valor, String descricao) {
    return new RegistrarLancamentoCommand(
        c.comercianteId(),
        c.chave(),
        tipo,
        valor,
        descricao,
        c.dataOcorrencia(),
        c.identificadorExterno(),
        c.requestId());
  }

  private static RegistrarLancamentoCommand comOcorrencia(
      RegistrarLancamentoCommand c, Instant ocorrencia) {
    return new RegistrarLancamentoCommand(
        c.comercianteId(),
        c.chave(),
        c.tipo(),
        c.valor(),
        c.descricao(),
        ocorrencia,
        c.identificadorExterno(),
        c.requestId());
  }

  private static RegistrarLancamentoCommand comIdentificador(
      RegistrarLancamentoCommand c, String identificador) {
    return new RegistrarLancamentoCommand(
        c.comercianteId(),
        c.chave(),
        c.tipo(),
        c.valor(),
        c.descricao(),
        c.dataOcorrencia(),
        identificador,
        c.requestId());
  }

  private static RegistrarLancamentoCommand comando(String valor) {
    return new RegistrarLancamentoCommand(
        COMERCIANTE,
        "chave-1",
        TipoLancamento.CREDITO,
        new BigDecimal(valor),
        null,
        null,
        null,
        null);
  }

  private static RegistrarLancamentoCommand comOcorrencia(Instant ocorrencia) {
    return comOcorrencia(comando("10.00"), ocorrencia);
  }

  private static RegistrarLancamentoCommand comTextos(String descricao, String identificador) {
    return comIdentificador(
        copia(comando("10.00"), TipoLancamento.CREDITO, new BigDecimal("10.00"), descricao),
        identificador);
  }
}
