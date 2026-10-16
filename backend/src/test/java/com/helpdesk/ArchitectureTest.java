package com.helpdesk;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.helpdesk.comment.CommentRepository;
import com.helpdesk.history.TicketHistoryRepository;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.user.UserRepository;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

/** Layering and tenant-isolation guard rails (docs/07 §6, docs/05 §2.2). */
@AnalyzeClasses(packages = "com.helpdesk", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final List<Class<?>> TENANT_REPOSITORIES = List.of(
            TicketRepository.class, UserRepository.class, CommentRepository.class, TicketHistoryRepository.class);

    private static final Set<String> UNSCOPED_METHODS = Set.of("findById", "findAll", "findAllById",
            "getReferenceById", "getById", "getOne", "existsById", "count", "deleteById", "deleteAll", "delete");

    /** Rule 1: controllers never touch repositories or entities (they speak DTOs only). */
    @ArchTest
    static final ArchRule controllers_doNotDependOnRepositoriesOrEntities = noClasses()
            .that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat().areAssignableTo(Repository.class)
            .orShould().dependOnClassesThat().areAnnotatedWith(Entity.class)
            .because("controllers depend only on services and DTOs (docs/02 §4.1)");

    /** Rule 2: nobody may bypass org scoping through the inherited unscoped repository methods. */
    @ArchTest
    static final ArchRule tenantRepositories_onlyOrgScopedMethods = noClasses()
            .should(callUnscopedMethodOnTenantRepository())
            .because("tenant-owned data must always be loaded with organizationId (docs/05 §2.2)");

    /** Rule 3: no REST endpoint returns a JPA entity. */
    @ArchTest
    static final ArchRule noEndpointReturnsEntity = noMethods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .should().haveRawReturnType(annotatedWithEntity())
            .because("entities never leave the service layer");

    /** Rule 4: request DTOs never carry an organization id (BR-1, T2). */
    @ArchTest
    static final ArchRule requestDtos_haveNoOrganizationId = fields()
            .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("Request")
            .should().notHaveName("organizationId")
            .because("the organization always comes from the token, never from the request");

    /** Rule 5: feature packages are free of cycles. */
    @ArchTest
    static final ArchRule features_areFreeOfCycles = slices().matching("com.helpdesk.(*)..").should().beFreeOfCycles();

    /** Security is infrastructure: it never depends on a feature package (features plug in via PrincipalLoader). */
    @ArchTest
    static final ArchRule securityDoesNotDependOnFeatures = classes()
            .that().resideInAPackage("com.helpdesk.security..")
            .should().onlyDependOnClassesThat().resideOutsideOfPackages(
                    "com.helpdesk.user..", "com.helpdesk.ticket..", "com.helpdesk.comment..",
                    "com.helpdesk.history..", "com.helpdesk.organization..", "com.helpdesk.auth..")
            .because("security is infrastructure; features plug into it through PrincipalLoader");

    private static DescribedPredicate<JavaClass> annotatedWithEntity() {
        return DescribedPredicate.describe("annotated with @Entity", c -> c.isAnnotatedWith(Entity.class));
    }

    private static ArchCondition<JavaClass> callUnscopedMethodOnTenantRepository() {
        return new ArchCondition<>("call an unscoped findById/findAll/getReferenceById/existsById on a tenant repository") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                for (JavaMethodCall call : clazz.getMethodCallsFromSelf()) {
                    JavaClass owner = call.getTargetOwner();
                    boolean tenantRepo = TENANT_REPOSITORIES.stream().anyMatch(owner::isAssignableTo);
                    if (!tenantRepo || !UNSCOPED_METHODS.contains(call.getName())) {
                        continue;
                    }
                    // findAll(Specification, ...) is allowed: the specifications always start with the org predicate.
                    boolean withSpecification = call.getTarget().getRawParameterTypes().stream()
                            .anyMatch(p -> p.isAssignableTo(Specification.class));
                    if (!withSpecification) {
                        events.add(SimpleConditionEvent.satisfied(call, call.getDescription()));
                    }
                }
            }
        };
    }
}
