package core.di;

import core.di.factory.BeanFactory;
import core.di.scanner.ClasspathBeanScanner;
import core.di.scanner.ConfigurationBeanScanner;

import java.lang.reflect.InvocationTargetException;

public class ApplicationContext {

    private final BeanFactory beanFactory = new BeanFactory();

    public void initialize(Class<?> configurationClass) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        ClasspathBeanScanner classpathBeanScanner = new ClasspathBeanScanner();
        ConfigurationBeanScanner configurationBeanScanner = new ConfigurationBeanScanner();

        String[] basePackages = configurationBeanScanner.getBasePackages(configurationClass);
        classpathBeanScanner.scan(beanFactory, basePackages);
        configurationBeanScanner.scan(beanFactory, basePackages);

        beanFactory.initialize();
    }

    public BeanFactory getBeanFactory() {
        return beanFactory;
    }

}
