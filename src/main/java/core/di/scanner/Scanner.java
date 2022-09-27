package core.di.scanner;

import core.di.factory.BeanFactory;

import java.util.Set;

public interface Scanner {
    void scan(BeanFactory beanFactory, String... basePackage);
}
