package br.com.fluxocaixa.consolidado.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Chaves {@code consolidado.consumer.*} (D-10, 03 §7.1, Q-047). Valores iniciais, revistos com a
 * medição da T-033.
 *
 * @param enabled liga o consumidor; desligado, o <i>container</i> do <i>listener</i> não sobe
 * @param groupId grupo de consumo do Kafka
 * @param concurrency consumidores por instância (até o número de partições)
 * @param poolSize máximo de conexões do pool JDBC dedicado do consumidor
 */
@ConfigurationProperties("consolidado.consumer")
public record ConsumidorProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("consolidado-projecao") String groupId,
    @DefaultValue("1") int concurrency,
    @DefaultValue("4") int poolSize) {}
