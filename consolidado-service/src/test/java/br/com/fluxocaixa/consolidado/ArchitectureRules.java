package br.com.fluxocaixa.consolidado;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import java.time.Clock;

/** Regras de arquitetura (04-quality-standards §2.2), usadas pelos testes positivos e negativos. */
final class ArchitectureRules {

  static final String SERVICE_PACKAGE = "br.com.fluxocaixa.consolidado";

  private static final String PEER_PACKAGE = "br.com.fluxocaixa.lancamentos..";
  private static final String[] DOMAIN_AND_APPLICATION = {"..domain..", "..application.."};

  private static final DescribedPredicate<JavaClass> FLOATING_POINT =
      equivalentTo(double.class)
          .or(equivalentTo(float.class))
          .or(equivalentTo(Double.class))
          .or(equivalentTo(Float.class));

  private static final DescribedPredicate<JavaMethodCall> SYSTEM_CLOCK_CALL =
      new DescribedPredicate<>(
          "chamar now() do java.time sem Clock ou System.currentTimeMillis()") {
        @Override
        public boolean test(JavaMethodCall call) {
          String name = call.getTarget().getName();
          boolean javaTimeNow =
              name.equals("now")
                  && call.getTargetOwner().getPackageName().equals("java.time")
                  && call.getTarget().getRawParameterTypes().stream()
                      .noneMatch(type -> type.isEquivalentTo(Clock.class));
          boolean currentTimeMillis =
              call.getTargetOwner().isEquivalentTo(System.class)
                  && name.equals("currentTimeMillis");
          return javaTimeNow || currentTimeMillis;
        }
      };

  static final ArchRule DOMAIN_WITHOUT_FRAMEWORKS =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "com.fasterxml.jackson..",
              "tools.jackson..",
              "java.sql..",
              "javax.sql..",
              "org.apache.kafka..",
              "org.postgresql..")
          .allowEmptyShould(true);

  static final ArchRule APPLICATION_WITHOUT_ADAPTERS =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..adapter..")
          .allowEmptyShould(true);

  static final ArchRule NO_PEER_SERVICE_DEPENDENCY =
      noClasses()
          .that()
          .resideInAPackage(SERVICE_PACKAGE + "..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage(PEER_PACKAGE)
          .allowEmptyShould(true);

  static final ArchRule NO_FLOATING_POINT_FIELDS =
      noFields()
          .that()
          .areDeclaredInClassesThat()
          .resideInAnyPackage(DOMAIN_AND_APPLICATION)
          .should()
          .haveRawType(FLOATING_POINT)
          .allowEmptyShould(true);

  static final ArchRule NO_FLOATING_POINT_RETURNS =
      noMethods()
          .that()
          .areDeclaredInClassesThat()
          .resideInAnyPackage(DOMAIN_AND_APPLICATION)
          .should()
          .haveRawReturnType(FLOATING_POINT)
          .allowEmptyShould(true);

  static final ArchRule NO_SYSTEM_CLOCK =
      noClasses().should().callMethodWhere(SYSTEM_CLOCK_CALL).allowEmptyShould(true);

  static final ArchRule NO_STANDARD_STREAMS =
      GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

  private ArchitectureRules() {}
}
