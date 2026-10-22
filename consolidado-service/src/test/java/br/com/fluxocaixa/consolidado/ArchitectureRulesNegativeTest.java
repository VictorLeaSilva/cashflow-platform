package br.com.fluxocaixa.consolidado;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Prova que cada regra realmente falha quando violada, usando as classes de {@code
 * archfixtures.violations} (que só existem em teste). A regra de serviços sem referência cruzada
 * não tem caso negativo aqui: o enforcer do Maven já a barra no classpath.
 */
@Tag("RNF-008")
class ArchitectureRulesNegativeTest {

  private static final JavaClasses VIOLATIONS =
      new ClassFileImporter()
          .importPackages(ArchitectureRules.SERVICE_PACKAGE + ".archfixtures.violations");

  private static void assertViolated(ArchRule rule) {
    assertThatThrownBy(() -> rule.check(VIOLATIONS)).isInstanceOf(AssertionError.class);
  }

  @Test
  void domainUsingFrameworkViolatesRule() {
    assertViolated(ArchitectureRules.DOMAIN_WITHOUT_FRAMEWORKS);
  }

  @Test
  void applicationUsingAdapterViolatesRule() {
    assertViolated(ArchitectureRules.APPLICATION_WITHOUT_ADAPTERS);
  }

  @Test
  void floatingPointInDomainViolatesRules() {
    assertViolated(ArchitectureRules.NO_FLOATING_POINT_FIELDS);
    assertViolated(ArchitectureRules.NO_FLOATING_POINT_RETURNS);
  }

  @Test
  void systemClockUsageViolatesRule() {
    assertViolated(ArchitectureRules.NO_SYSTEM_CLOCK);
  }

  @Test
  void standardStreamUsageViolatesRule() {
    assertViolated(ArchitectureRules.NO_STANDARD_STREAMS);
  }
}
