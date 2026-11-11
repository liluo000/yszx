package com.yunshuzhixue;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * 模块边界校验（02.整体架构设计.md 4.3、决策 A09）：
 * 业务模块之间只能引用对方 api 子包中的类，且模块之间不能循环依赖。
 */
class ModuleBoundaryTest {

    private static final String MODULES = "com.yunshuzhixue.modules.";

    @Test
    void modulesOnlyDependOnEachOthersApi() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.yunshuzhixue.modules");

        List<String> violations = new ArrayList<>();
        for (JavaClass origin : classes) {
            String originModule = moduleOf(origin.getPackageName());
            for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                String targetPackage = dependency.getTargetClass().getPackageName();
                String targetModule = moduleOf(targetPackage);
                if (targetModule == null || targetModule.equals(originModule)) {
                    continue;
                }
                if (!targetPackage.startsWith(MODULES + targetModule + ".api")) {
                    violations.add(dependency.getDescription());
                }
            }
        }
        assertThat(violations).as("模块之间只能通过 api 包调用").isEmpty();
    }

    @Test
    void modulesHaveNoDependencyCycles() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.yunshuzhixue.modules");

        Map<String, Set<String>> graph = new TreeMap<>();
        for (JavaClass origin : classes) {
            String originModule = moduleOf(origin.getPackageName());
            for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                String targetModule = moduleOf(dependency.getTargetClass().getPackageName());
                if (targetModule != null && !targetModule.equals(originModule)) {
                    graph.computeIfAbsent(originModule, k -> new TreeSet<>()).add(targetModule);
                }
            }
        }
        List<String> cycles = new ArrayList<>();
        for (String module : graph.keySet()) {
            if (reaches(graph, module, module, new HashSet<>())) {
                cycles.add(module + " -> " + graph.get(module));
            }
        }
        assertThat(cycles).as("模块之间不能循环依赖").isEmpty();
    }

    private static boolean reaches(Map<String, Set<String>> graph, String from, String target, Set<String> visited) {
        for (String next : graph.getOrDefault(from, Set.of())) {
            if (next.equals(target) || (visited.add(next) && reaches(graph, next, target, visited))) {
                return true;
            }
        }
        return false;
    }

    private static String moduleOf(String packageName) {
        if (!packageName.startsWith(MODULES)) {
            return null;
        }
        String rest = packageName.substring(MODULES.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
