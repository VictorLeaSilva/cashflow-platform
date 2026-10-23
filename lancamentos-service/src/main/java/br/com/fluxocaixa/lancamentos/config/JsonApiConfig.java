package br.com.fluxocaixa.lancamentos.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * JSON da API estrito (03 §5, §5.2; API3): propriedade desconhecida ou duplicada é erro, e número
 * ou booleano nunca vira texto em silêncio (o {@code valor} é string, constituição §4). O payload
 * da outbox tem mapper próprio e não é afetado.
 */
@Configuration(proxyBeanMethods = false)
public class JsonApiConfig {

  @Bean
  JsonMapperBuilderCustomizer jsonEstrito() {
    return builder ->
        builder
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .withCoercionConfig(
                LogicalType.Textual,
                coercao ->
                    coercao
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
  }
}
