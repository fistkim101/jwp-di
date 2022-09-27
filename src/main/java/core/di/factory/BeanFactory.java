package core.di.factory;

import com.google.common.collect.Maps;
import core.annotation.Bean;
import core.util.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class BeanFactory {
    private static final Logger logger = LoggerFactory.getLogger(BeanFactory.class);
    private final Set<Class<?>> preInstantiatedBeans = new HashSet<>();

    private final Set<Class<?>> preInstantiatedBeansFromConfiguration = new HashSet<>();

    private final Map<Class<?>, Object> beans = Maps.newHashMap();

    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> requiredType) {
        return (T) beans.get(requiredType);
    }

    public void addPreInstantiatedBeans(Class<?> target) {
        this.preInstantiatedBeans.add(target);
    }

    public void addPreInstantiatedBeansFromConfiguration(Class<?> target) {
        this.preInstantiatedBeansFromConfiguration.add(target);
    }

    public void addAllPreInstantiatedBeansFromConfiguration(List<Class<?>> targets) {
        this.preInstantiatedBeansFromConfiguration.addAll(targets);
    }

    public void initialize() throws InvocationTargetException, InstantiationException, IllegalAccessException {

        for (Class<?> preInstantiatedBean : this.preInstantiatedBeans) {
            Object instantiatedBean = this.getInstantiatedBeanFromClassPath(preInstantiatedBean);
            beans.put(preInstantiatedBean, instantiatedBean);
        }

        this.preInstantiatedBeans.addAll(this.preInstantiatedBeansFromConfiguration);
        for (Class<?> configurationBean : this.preInstantiatedBeansFromConfiguration) {
            this.addInstantiatedBeansFromConfigurationBeans(configurationBean);
        }
    }

    public Set<Class<?>> getPreInstantiatedBeans() {
        return preInstantiatedBeans;
    }

    public Set<Class<?>> getPreInstantiatedBeansFromConfiguration() {
        return preInstantiatedBeansFromConfiguration;
    }

    private Object getNewInstance(Class<?> preInstantiatedBean, List<Object> parameterBeans) {
        Object[] parameters = new Object[parameterBeans.size()];
        int index = 0;
        for (Object parameterBean : parameterBeans) {
            parameters[index++] = parameterBean;
        }
        return ReflectionUtils.newInstance(preInstantiatedBean, parameters);
    }

    private List<Class<?>> getConcreteParameterClasses(Class<?>[] parameters) {
        List<Class<?>> concreteParameters = new ArrayList<>();
        for (Class<?> parameter : parameters) {
            Class<?> concreteParameter = BeanFactoryUtils.findConcreteClass(parameter, this.preInstantiatedBeans);
            concreteParameters.add(concreteParameter);
        }

        return concreteParameters;
    }

    private Object getInstantiatedBeanFromClassPath(Class<?> preInstantiatedBean) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        logger.debug("[beanName : {}] try to create Bean from classPath ...", preInstantiatedBean.getName());
        Object instantiateTargetBean = this.beans.get(preInstantiatedBean);
        if (instantiateTargetBean != null) {
            logger.debug("[beanName : {}] already created Bean from classPath...", preInstantiatedBean.getName());
            return instantiateTargetBean;
        }

        Constructor<?> injectedConstructor = BeanFactoryUtils.getInjectedConstructor(preInstantiatedBean);
        if (injectedConstructor == null) {
            logger.debug("[beanName : {}] created Bean from classPath...", preInstantiatedBean.getName());
            return ReflectionUtils.getNoArgsConstructor(preInstantiatedBean).newInstance();
        }

        List<Object> parameterBeans = new ArrayList<>();
        List<Class<?>> concreteParameterClasses = this.getConcreteParameterClasses(injectedConstructor.getParameterTypes());
        for (Class<?> parameterClass : concreteParameterClasses) {
            Object parameterBean = this.beans.get(parameterClass);
            if (parameterBean != null) {
                parameterBeans.add(parameterBean);
                continue;
            }

            parameterBeans.add(this.getInstantiatedBeanFromClassPath(parameterClass));
        }

        logger.debug("[beanName : {}] created Bean from classPath...", preInstantiatedBean.getName());
        return this.getNewInstance(preInstantiatedBean, parameterBeans);
    }

    private boolean isBean(Method method) {
        return Arrays.stream(method.getDeclaredAnnotations()).anyMatch(annotation -> annotation.annotationType().equals(Bean.class));
    }

    private Object[] getParameterBeans(Class<?>[] parameterClasses) {
        List<Object> parameterBeans = Arrays.stream(parameterClasses)
                .map(this.beans::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (parameterClasses.length != parameterBeans.size()) {
            throw new IllegalArgumentException();
        }

        Object[] parameters = new Object[parameterBeans.size()];
        int index = 0;
        for (Object bean : parameterBeans) {
            parameters[index++] = bean;
        }

        return parameters;
    }

    private void addInstantiatedBeansFromConfigurationBeans(Class<?> configurationBeanClass) throws InvocationTargetException, IllegalAccessException {
        List<Method> declaredBeans = Arrays.stream(configurationBeanClass.getDeclaredMethods())
                .filter(this::isBean)
                .collect(Collectors.toList());

        for (Method declaredBean : declaredBeans) {
            logger.debug("[beanName : {}] try to create Bean from configuration ...", declaredBean.getReturnType().getName());
            Object bean = this.getBeanFromBeanAnnotationMethod(declaredBean.getParameterTypes(), configurationBeanClass, declaredBean);

            logger.debug("[beanName : {}] created Bean from configuration...", bean.getClass().getName());
            this.beans.put(declaredBean.getReturnType(), bean);
        }
    }

    private Object getBeanFromBeanAnnotationMethod(Class<?>[] parameterClasses, Class<?> configurationBean, Method declaredBean) throws InvocationTargetException, IllegalAccessException {
        Object configurationClass = this.getBean(configurationBean);
        if (parameterClasses.length == 0) {
            return declaredBean.invoke(configurationClass);
        }

        Object[] parameters = this.getParameterBeans(parameterClasses);
        return declaredBean.invoke(configurationClass, parameters);
    }

}
