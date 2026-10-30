package ir.maziyar_gerami.otp.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.*;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;

@AnalyzeClasses(packages = "ir.maziyar_gerami.otp", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest
    static final ArchRule domainIsPure = classes().that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..domain..");

    @ArchTest
    static final ArchRule applicationUsesOnlyDomainAndPorts = classes().that().resideInAPackage("..application..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..domain..", "..application..");

    @ArchTest
    static final ArchRule controllersNeverAccessRedis = noClasses().that().resideInAPackage("..adapter.in.web..")
            .should().dependOnClassesThat().resideInAnyPackage("..adapter.out..", "org.springframework.data.redis..", "io.lettuce..");

    @ArchTest
    static final ArchRule adaptersNeverAccessBootstrap = noClasses().that().resideInAPackage("..adapter..")
            .should().dependOnClassesThat().resideInAPackage("..bootstrap..");

    @ArchTest
    static final ArchRule redisIsIndependent = isolated("..adapter.out.redis..");
    @ArchTest
    static final ArchRule smsIsIndependent = isolated("..adapter.out.sms..");
    @ArchTest
    static final ArchRule securityIsIndependent = isolated("..adapter.out.security..");

    private static ArchRule isolated(String ownPackage) {
        return noClasses().that().resideInAPackage(ownPackage)
                .should().dependOnClassesThat(resideInAPackage("..adapter..")
                        .and(resideOutsideOfPackage(ownPackage)));
    }
}
