package br.com.fluxocaixa.lancamentos.domain;

import br.com.fluxocaixa.lancamentos.domain.LancamentoInvalidoException.ErroCampo;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Fato financeiro imutável (P4, A8). Só nasce por {@link #registrar}, {@link #estornar} ou {@link
 * #reconstituir}; não há métodos de alteração.
 */
public final class Lancamento {

  static final int MAX_DESCRICAO = 255;
  static final int MAX_IDENTIFICADOR_EXTERNO = 100;

  /** Dados de um novo crédito ou débito, como chegam do caso de uso. */
  public record Novo(
      UUID comercianteId,
      TipoLancamento tipo,
      BigDecimal valor,
      String descricao,
      Instant dataOcorrencia,
      String identificadorExterno) {

    /** Sem valor, descrição nem identificador externo (sem payload financeiro em log). */
    @Override
    public String toString() {
      return "Lancamento.Novo[tipo=" + tipo + "]";
    }
  }

  /** Estado completo de um lançamento (reidratação a partir da persistência). */
  public record Dados(
      UUID id,
      UUID comercianteId,
      TipoLancamento tipo,
      Dinheiro valor,
      String descricao,
      Instant dataOcorrencia,
      LocalDate dataReferencia,
      UUID referenciaLancamentoId,
      TipoLancamento tipoOriginal,
      String identificadorExterno,
      Instant criadoEm) {

    /** Sem valor, descrição nem identificador externo (sem payload financeiro em log). */
    @Override
    public String toString() {
      return "Lancamento.Dados[id="
          + id
          + ", tipo="
          + tipo
          + ", dataReferencia="
          + dataReferencia
          + "]";
    }
  }

  private final Dados dados;

  private Lancamento(Dados dados) {
    this.dados = validarEstrutura(dados);
  }

  /**
   * Registra um crédito ou débito. Débito não é limitado por saldo (A6).
   *
   * @throws LancamentoInvalidoException com todos os erros de campo encontrados
   * @throws RegraDeNegocioException se a data violar futuro ou retroatividade
   */
  public static Lancamento registrar(UUID id, Novo novo, PoliticaLancamento politica, Clock clock) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(novo, "novo");
    Objects.requireNonNull(novo.comercianteId(), "comercianteId");
    Dinheiro valor = validarCampos(novo, politica);
    DataReferencia dataReferencia =
        DataReferencia.paraRegistro(novo.dataOcorrencia(), politica, clock);
    Instant agora = clock.instant();
    Instant ocorrencia = novo.dataOcorrencia() != null ? novo.dataOcorrencia() : agora;
    return new Lancamento(
        new Dados(
            id,
            novo.comercianteId(),
            novo.tipo(),
            valor,
            novo.descricao(),
            ocorrencia,
            dataReferencia.data(),
            null,
            null,
            novo.identificadorExterno(),
            agora));
  }

  /**
   * Cria o estorno total de {@code original}: novo lançamento compensatório na data de hoje no fuso
   * de negócio (D-07). O original não é alterado. "Já estornado" (409) é garantido pela
   * persistência, não aqui.
   *
   * @throws RegraDeNegocioException {@code estorno-de-estorno} se o original já for um estorno
   */
  public static Lancamento estornar(
      UUID idEstorno, Lancamento original, PoliticaLancamento politica, Clock clock) {
    Objects.requireNonNull(idEstorno, "idEstorno");
    Objects.requireNonNull(original, "original");
    if (original.tipo() == TipoLancamento.ESTORNO) {
      throw new RegraDeNegocioException(
          RegraDeNegocioException.ESTORNO_DE_ESTORNO, "um estorno não pode ser estornado");
    }
    Instant agora = clock.instant();
    return new Lancamento(
        new Dados(
            idEstorno,
            original.comercianteId(),
            TipoLancamento.ESTORNO,
            original.valor(),
            null,
            agora,
            DataReferencia.hoje(politica, clock).data(),
            original.id(),
            original.tipo(),
            null,
            agora));
  }

  /**
   * Reidrata um lançamento já persistido. Valida só as invariantes estruturais (tipo × referência);
   * não reaplica as regras temporais de criação, então lançamentos antigos continuam legíveis.
   *
   * @throws IllegalArgumentException se as invariantes estruturais forem violadas
   */
  public static Lancamento reconstituir(Dados dados) {
    return new Lancamento(dados);
  }

  private static Dinheiro validarCampos(Novo novo, PoliticaLancamento politica) {
    List<ErroCampo> erros = new ArrayList<>();
    if (novo.tipo() == null) {
      erros.add(new ErroCampo("tipo", "obrigatorio", "tipo é obrigatório"));
    } else if (novo.tipo() == TipoLancamento.ESTORNO) {
      erros.add(new ErroCampo("tipo", "tipo-invalido", "use o endpoint de estorno para estornar"));
    }
    Dinheiro valor = null;
    try {
      valor = Dinheiro.de(novo.valor(), politica.valorMaximo());
    } catch (LancamentoInvalidoException e) {
      erros.addAll(e.erros());
    }
    validarTamanho(erros, "descricao", novo.descricao(), MAX_DESCRICAO);
    validarTamanho(
        erros, "identificadorExterno", novo.identificadorExterno(), MAX_IDENTIFICADOR_EXTERNO);
    if (!erros.isEmpty()) {
      throw new LancamentoInvalidoException(erros);
    }
    return valor;
  }

  private static void validarTamanho(List<ErroCampo> erros, String campo, String texto, int max) {
    if (texto != null && texto.codePointCount(0, texto.length()) > max) {
      erros.add(
          new ErroCampo(
              campo, "tamanho-maximo", campo + " deve ter no máximo " + max + " caracteres"));
    }
  }

  private static Dados validarEstrutura(Dados dados) {
    Objects.requireNonNull(dados, "dados");
    Objects.requireNonNull(dados.id(), "id");
    Objects.requireNonNull(dados.comercianteId(), "comercianteId");
    Objects.requireNonNull(dados.tipo(), "tipo");
    Objects.requireNonNull(dados.valor(), "valor");
    Objects.requireNonNull(dados.dataOcorrencia(), "dataOcorrencia");
    Objects.requireNonNull(dados.dataReferencia(), "dataReferencia");
    Objects.requireNonNull(dados.criadoEm(), "criadoEm");
    boolean estorno = dados.tipo() == TipoLancamento.ESTORNO;
    boolean temReferencia = dados.referenciaLancamentoId() != null;
    boolean temTipoOriginal = dados.tipoOriginal() != null;
    if (estorno != temReferencia || estorno != temTipoOriginal) {
      throw new IllegalArgumentException(
          "ESTORNO exige referência e tipo original; CREDITO/DEBITO não os admitem");
    }
    if (dados.tipoOriginal() == TipoLancamento.ESTORNO) {
      throw new IllegalArgumentException("tipo original de um estorno deve ser CREDITO ou DEBITO");
    }
    return dados;
  }

  /** Estado completo, para reidratação e persistência. */
  public Dados dados() {
    return dados;
  }

  public UUID id() {
    return dados.id();
  }

  public UUID comercianteId() {
    return dados.comercianteId();
  }

  public TipoLancamento tipo() {
    return dados.tipo();
  }

  public Dinheiro valor() {
    return dados.valor();
  }

  public String descricao() {
    return dados.descricao();
  }

  public Instant dataOcorrencia() {
    return dados.dataOcorrencia();
  }

  public LocalDate dataReferencia() {
    return dados.dataReferencia();
  }

  public UUID referenciaLancamentoId() {
    return dados.referenciaLancamentoId();
  }

  public TipoLancamento tipoOriginal() {
    return dados.tipoOriginal();
  }

  public String identificadorExterno() {
    return dados.identificadorExterno();
  }

  public Instant criadoEm() {
    return dados.criadoEm();
  }

  @Override
  public boolean equals(Object outro) {
    return outro instanceof Lancamento lancamento && dados.equals(lancamento.dados);
  }

  @Override
  public int hashCode() {
    return dados.hashCode();
  }

  /** Sem valor nem descrição: lançamentos não devem expor payload financeiro em log. */
  @Override
  public String toString() {
    return "Lancamento[id="
        + id()
        + ", tipo="
        + tipo()
        + ", dataReferencia="
        + dataReferencia()
        + "]";
  }
}
