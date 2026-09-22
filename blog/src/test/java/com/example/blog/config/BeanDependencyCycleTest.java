package com.example.blog.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守卫：Spring Bean 的构造依赖图不能出现循环。
 * <p>
 * 背景：循环依赖只在应用启动装配 Bean 时暴露，纯 Mockito 单测不会启动 Spring
 * 上下文，因此这类问题会绕过整个测试套件、直到部署才炸。本测试直接扫描组件并用
 * 反射重建构造依赖图，无需 MySQL/Redis/ES 等外部资源即可检出循环。
 */
class BeanDependencyCycleTest {

    private static final String BASE_PACKAGE = "com.example.blog";

    @Test
    void springComponentsHaveNoConstructorDependencyCycle() {
        Set<Class<?>> components = scanComponents();

        assertTrue(components.size() > 10,
                "组件扫描结果异常偏少，测试本身可能已失效: " + components.size());

        Map<Class<?>, Set<Class<?>>> graph = buildDependencyGraph(components);
        List<Class<?>> cycle = findCycle(graph);

        assertTrue(cycle.isEmpty(),
                () -> "检测到 Bean 构造依赖循环: " + renderCycle(cycle));
    }

    /** 扫描项目中所有 Spring 组件类。 */
    private Set<Class<?>> scanComponents() {
        ClassPathScanningCandidateComponentProvider provider =
                new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        provider.addIncludeFilter(new AnnotationTypeFilter(Service.class));
        provider.addIncludeFilter(new AnnotationTypeFilter(Repository.class));
        provider.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        provider.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        Set<Class<?>> components = new LinkedHashSet<>();
        for (BeanDefinition definition : provider.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> type = resolveClass(definition.getBeanClassName());
            if (type != null && !type.isInterface() && !Modifier.isAbstract(type.getModifiers())) {
                components.add(type);
            }
        }
        return components;
    }

    private Class<?> resolveClass(String className) {
        if (className == null) {
            return null;
        }
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * 建立「组件 → 它依赖的组件」的邻接表。
     * <p>注入类型常见是接口（如 {@code ArticleService}），需映射到其实现类才能形成闭环。
     */
    private Map<Class<?>, Set<Class<?>>> buildDependencyGraph(Set<Class<?>> components) {
        Map<Class<?>, Class<?>> interfaceToImpl = new HashMap<>();
        for (Class<?> component : components) {
            for (Class<?> itf : component.getInterfaces()) {
                interfaceToImpl.putIfAbsent(itf, component);
            }
        }

        Map<Class<?>, Set<Class<?>>> graph = new HashMap<>();
        for (Class<?> component : components) {
            Set<Class<?>> dependencies = new LinkedHashSet<>();
            for (Class<?> parameterType : constructorParameterTypes(component)) {
                Class<?> target = components.contains(parameterType) ? parameterType : null;
                if (target == null) {
                    target = interfaceToImpl.get(parameterType);
                }
                if (target != null && !target.equals(component)) {
                    dependencies.add(target);
                }
            }
            graph.put(component, dependencies);
        }
        return graph;
    }

    /**
     * 取 Spring 实际使用的构造器参数类型。
     * <p>Lombok 的 {@code @RequiredArgsConstructor} 会在编译期生成唯一构造器，因此按参数最多的
     * 构造器取值即可覆盖本项目全部组件。
     */
    private List<Class<?>> constructorParameterTypes(Class<?> component) {
        Constructor<?>[] constructors = component.getDeclaredConstructors();
        if (constructors.length == 0) {
            return List.of();
        }
        Constructor<?> effective = constructors[0];
        for (Constructor<?> candidate : constructors) {
            if (candidate.getParameterCount() > effective.getParameterCount()) {
                effective = candidate;
            }
        }
        return List.of(effective.getParameterTypes());
    }

    /** 深度优先搜索环；返回环上的节点序列，无环时返回空列表。 */
    private List<Class<?>> findCycle(Map<Class<?>, Set<Class<?>>> graph) {
        Set<Class<?>> visited = new HashSet<>();
        Set<Class<?>> onStack = new HashSet<>();
        Deque<Class<?>> path = new ArrayDeque<>();

        List<Class<?>> sorted = new ArrayList<>(graph.keySet());
        sorted.sort(Comparator.comparing(Class::getName));

        for (Class<?> node : sorted) {
            List<Class<?>> cycle = visit(node, graph, visited, onStack, path);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        return List.of();
    }

    private List<Class<?>> visit(
            Class<?> node,
            Map<Class<?>, Set<Class<?>>> graph,
            Set<Class<?>> visited,
            Set<Class<?>> onStack,
            Deque<Class<?>> path) {
        if (onStack.contains(node)) {
            List<Class<?>> cycle = new ArrayList<>();
            boolean collecting = false;
            for (Class<?> item : path) {
                if (item.equals(node)) {
                    collecting = true;
                }
                if (collecting) {
                    cycle.add(item);
                }
            }
            cycle.add(node);
            return cycle;
        }
        if (!visited.add(node)) {
            return List.of();
        }

        onStack.add(node);
        path.addLast(node);
        for (Class<?> next : graph.getOrDefault(node, Set.of())) {
            List<Class<?>> cycle = visit(next, graph, visited, onStack, path);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        path.removeLast();
        onStack.remove(node);
        return List.of();
    }

    private String renderCycle(List<Class<?>> cycle) {
        List<String> names = new ArrayList<>();
        for (Class<?> type : cycle) {
            names.add(type.getSimpleName());
        }
        return String.join(" -> ", names);
    }
}
