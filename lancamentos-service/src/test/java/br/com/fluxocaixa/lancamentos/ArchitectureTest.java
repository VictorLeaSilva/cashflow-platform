package br.com.fluxocaixa.lancamentos;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Garante que o código de produção respeita as regras de arquitetura. */
@Tag("RNF-008")
class ArchitectureTest {

  private static final JavaClasses PRODUCTION_CLASSES =
      new ClassFileImporter()
          .withImportOption(new ImportOption.DoNotIncludeTests())
          .importPackages(ArchitectureRules.SERVICE_PACKAGE);

  @Test
  void domainHasNoFrameworkDependencies() {
    ArchitectureRules.DOMAIN_WITHOUT_FRAMEWORKS.check(PRODUCTION_CLASSES);
  }

  @Test
  void applicationDoesNotDependOnAdapters() {
    ArchitectureRules.APPLICATION_WITHOUT_ADAPTERS.check(PRODUCTION_CLASSES);
  }

  @Test
  void applicationAndDomainDoNotDependOnObservabilityLibs() {
    ArchitectureRules.APPLICATION_AND_DOMAIN_WITHOUT_OBSERVABILITY_LIBS.check(PRODUCTION_CLASSES);
  }

  @Test
  void serviceDoesNotDependOnPeerService() {
    ArchitectureRules.NO_PEER_SERVICE_DEPENDENCY.check(PRODUCTION_CLASSES);
  }

  @Test
  void noFloatingPointInDomainAndApplication() {
    ArchitectureRules.NO_FLOATING_POINT_FIELDS.check(PRODUCTION_CLASSES);
    ArchitectureRules.NO_FLOATING_POINT_RETURNS.check(PRODUCTION_CLASSES);
  }

  @Test
  void noDirectSystemClockUsage() {
    ArchitectureRules.NO_SYSTEM_CLOCK.check(PRODUCTION_CLASSES);
  }

  @Test
  void noStandardStreams() {
    ArchitectureRules.NO_STANDARD_STREAMS.check(PRODUCTION_CLASSES);
  }

  @Test
  @Tag("RNF-005")
  void outboundHttpClientsAreNotUsed() {
    ArchitectureRules.NO_OUTBOUND_HTTP_CLIENTS.check(PRODUCTION_CLASSES);
  }

  @Test
  @Tag("RNF-004")
  void controllersReturnWebRecords() {
    ArchitectureRules.CONTROLLERS_RETURN_WEB_RECORDS.check(PRODUCTION_CLASSES);
  }
}
