package com.subtlesight.application;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.subtlesight", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest
    static final ArchRule domain_is_framework_free = noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "org.sqlite..", "org.apache.lucene..");

    @ArchTest
    static final ArchRule application_has_no_adapter_dependency = noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..storage..", "..provider..", "..server..");
}
