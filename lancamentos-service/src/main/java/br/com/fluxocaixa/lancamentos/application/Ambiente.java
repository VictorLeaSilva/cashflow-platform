package br.com.fluxocaixa.lancamentos.application;

import br.com.fluxocaixa.lancamentos.application.port.out.IdGenerator;
import br.com.fluxocaixa.lancamentos.domain.PoliticaLancamento;
import java.time.Clock;

/**
 * O que os casos de uso de escrita pedem ao ambiente para decidir: identificadores, política de
 * lançamento (A4, A7, A11) e relógio (constituição §4). Agrupados para o construtor ficar dentro do
 * limite de parâmetros do Checkstyle.
 */
public record Ambiente(IdGenerator ids, PoliticaLancamento politica, Clock clock) {}
