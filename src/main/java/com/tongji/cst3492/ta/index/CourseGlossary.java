package com.tongji.cst3492.ta.index;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small, maintainable Chinese-to-English glossary for course terminology.
 * The original question is always kept; these terms only extend the retrieval query.
 */
public final class CourseGlossary {
    private static final Map<String, String> TERMS = new LinkedHashMap<>();

    static {
        TERMS.put("受检异常", "checked exception");
        TERMS.put("运行时异常", "runtime exception");
        TERMS.put("未检查异常", "unchecked exception");
        TERMS.put("异常处理", "exception handling");
        TERMS.put("泛型擦除", "type erasure");
        TERMS.put("泛型", "generics generic type");
        TERMS.put("集合框架", "collections framework");
        TERMS.put("集合", "collections");
        TERMS.put("注解", "annotations");
        TERMS.put("反射", "reflection");
        TERMS.put("单元测试", "unit test JUnit");
        TERMS.put("抽象类", "abstract class");
        TERMS.put("接口", "interface");
        TERMS.put("继承", "inheritance");
        TERMS.put("多态", "polymorphism");
        TERMS.put("封装", "encapsulation");
        TERMS.put("构造函数", "constructor");
        TERMS.put("构造器", "constructor");
        TERMS.put("方法重载", "overloading");
        TERMS.put("方法重写", "overriding");
        TERMS.put("设计模式", "design pattern");
        TERMS.put("依赖注入", "dependency injection");
        TERMS.put("数组", "array");
        TERMS.put("字符串", "string");
        TERMS.put("包装类", "wrapper class");
        TERMS.put("线程", "thread");
        TERMS.put("静态方法", "static method");
        TERMS.put("访问修饰符", "access modifier");
        TERMS.put("面向对象", "object-oriented");
        TERMS.put("Lambda 表达式", "lambda expression");
        TERMS.put("流式处理", "streams");
        TERMS.put("事务", "transaction");
    }

    private CourseGlossary() {
    }

    public static String expand(String question) {
        if (question == null || question.isBlank()) {
            return "";
        }
        StringBuilder expansions = new StringBuilder();
        for (Map.Entry<String, String> entry : TERMS.entrySet()) {
            if (question.contains(entry.getKey())) {
                if (expansions.length() > 0) {
                    expansions.append(' ');
                }
                expansions.append(entry.getValue());
            }
        }
        return expansions.length() == 0
                ? question
                : question + "\n" + expansions;
    }
}
