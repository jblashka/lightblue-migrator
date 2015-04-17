package com.redhat.lightblue.migrator.utils;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.redhat.lightblue.migrator.features.LightblueMigration;
import com.redhat.lightblue.migrator.features.LightblueMigrationConfiguration;
import com.redhat.lightblue.migrator.utils.annotations.Create;
import com.redhat.lightblue.migrator.utils.annotations.DestinationOnly;
import com.redhat.lightblue.migrator.utils.annotations.Read;
import com.redhat.lightblue.migrator.utils.annotations.SourceOnly;
import com.redhat.lightblue.migrator.utils.annotations.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/**
 * Created by jblashka on 4/16/15.
 */
public class DAOProxyFacadeFactory<D> {
    private Class<D> clazz;
    private D sourceDAO, destinationDAO;
    private SharedEntityIdStore store;
    private static final Logger log = LoggerFactory.getLogger(LightblueMigrationConfiguration.class);

    public DAOProxyFacadeFactory (Class<D> clazz, D sourceDAO, D destinationDAO){
        this(clazz, sourceDAO, destinationDAO, new SharedEntityIdStoreImpl());

    }
    public DAOProxyFacadeFactory (Class<D> clazz, D sourceDAO, D destinationDAO, SharedEntityIdStore store){
        this.clazz = clazz;
        this.sourceDAO = sourceDAO;
        this.destinationDAO = destinationDAO;
        this.store = store;
    }

    public D getProxy() {
        InvocationHandler handler = new ProxyInvocationHandler();
        D proxy = (D) Proxy.newProxyInstance(
                clazz.getClassLoader(),
                new Class[]{clazz},
                handler);
        return proxy;
    }

    class ProxyInvocationHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] values) throws Throwable {
            if (method.isAnnotationPresent(SourceOnly.class)) {
                //TODO
            } else if (method.isAnnotationPresent(DestinationOnly.class)) {
                //TODO
            }
            if (method.isAnnotationPresent(Read.class)) {
                return callDAOReadMethod(method, values);
                //return facadeBase.callDAOReadMethod(method.getReturnType(), method.getName(), values);
            } else if (method.isAnnotationPresent(Update.class)) {
                return callDAOUpdateMethod(method, values);
                //return facadeBase.callDAOUpdateMethod(method.getReturnType(), method.getName(), values);
            } else if (method.isAnnotationPresent(Create.class)) {
                return callDAOCreateSingleMethod(method, values);
                //return facadeBase.callDAOCreateSingleMethod((Class<IdExtractible<X>>) method.getReturnType(), method.getName(), values);
            }
            throw new Exception("Method must have one of Read, Update, Create annotations");
        }

        /**
         * Call dao method which reads data.
         *
         * @param method method to call
         * @param values List of parameters
         * @return Object returned by dao
         * @throws Exception
         */
        private <T> Object callDAOReadMethod(final Method method, final Object ... values) throws Exception {
            //This isn't quite the same return type
            Class returnedType = method.getReturnType();
            log.debug("Reading "+returnedType.getName() + " " + methodCallToString(method, values));

            T legacyEntity = null, lightblueEntity = null;
            ListenableFuture<T> listenableFuture = null;

            if (LightblueMigration.shouldReadDestinationEntity()) {
                // fetch from lightblue using future (asynchronously)
                log.debug("."+method.getName()+" reading from lightblue");
                listenableFuture = getExecutor().submit(new Callable<T>(){
                    @Override
                    public T call() throws Exception {
                        return (T) method.invoke(destinationDAO, values);
                    }
                });
            }

            if (LightblueMigration.shouldReadSourceEntity()) {
                // fetch from oracle, synchronously
                log.debug("."+method.getName()+" reading from legacy");
                legacyEntity = (T) method.invoke(sourceDAO, values);
            }

            if (LightblueMigration.shouldReadDestinationEntity()) {
                // make sure asnyc call to lightblue has completed
                lightblueEntity = listenableFuture.get();
            }

            if (LightblueMigration.shouldCheckReadConsistency() && LightblueMigration.shouldReadSourceEntity()) {
                // make sure that response from lightblue and oracle are the same
                log.debug("."+method.getName()+" checking returned entity's consistency");
                if (Objects.equals(legacyEntity, lightblueEntity)) {
                    // return lightblue data if they are
                    return lightblueEntity;
                } else {
                    // return oracle data if they aren't and log data inconsistency
                    logInconsistency(returnedType.getName(), method, values);
                    return legacyEntity;
                }
            }

            return lightblueEntity != null ? lightblueEntity : legacyEntity;
        }

        /**
         * Call dao method which updates data. Updating makes sense only for entities with known ID. If ID is not specified, it will be generated
         * by both legacy and lightblue datastores independently, creating a data incosistency. If you don't know the ID, use callDAOUpdateMethod method.
         *
         * @param method method to call
         * @param values List of parameters
         * @return Object returned by dao
         * @throws Exception
         */
        private <T> T callDAOUpdateMethod(final Method method, final Object ... values) throws Exception {
            Class returnedType = method.getReturnType();
            log.debug("Writing "+(returnedType!=null?returnedType.getName():"")+" "+methodCallToString(method, values));

            T legacyEntity = null, lightblueEntity = null;
            ListenableFuture<T> listenableFuture = null;

            if (LightblueMigration.shouldWriteDestinationEntity()) {
                // fetch from lightblue using future (asynchronously)
                log.debug("."+method.getName()+" writing to lightblue");
                listenableFuture = getExecutor().submit(new Callable<T>(){
                    @Override
                    public T call() throws Exception {
                        return (T) method.invoke(destinationDAO, values);
                    }
                });
            }

            if (LightblueMigration.shouldWriteSourceEntity()) {
                // fetch from oracle, synchronously
                log.debug("."+method.getName()+" writing to legacy");
                legacyEntity = (T) method.invoke(sourceDAO, values);
            }

            if (LightblueMigration.shouldWriteDestinationEntity()) {
                // make sure asnyc call to lightblue has completed
                lightblueEntity = listenableFuture.get();
            }

            if (LightblueMigration.shouldCheckWriteConsistency() && LightblueMigration.shouldWriteSourceEntity()) {
                // make sure that response from lightblue and oracle are the same
                log.debug("."+method.getName()+" checking returned entity's consistency");
                if (Objects.equals(legacyEntity, lightblueEntity)) {
                    // return lightblue data if they are
                    return lightblueEntity;
                } else {
                    // return oracle data if they aren't and log data inconsistency
                    logInconsistency(returnedType.getName(), method, values);
                    return legacyEntity;
                }
            }

            return lightblueEntity != null ? lightblueEntity : legacyEntity;
        }

        /**
         * Call dao method which creates a single entity. It will ensure that entities in both legacy and lightblue datastores are the same, including IDs.
         *
         * @param method method to call
         * @param values List of parameters
         * @return Object returned by dao
         * @throws Exception
         */
        public <T extends IdExtractable> T callDAOCreateSingleMethod(final Method method, final Object ... values) throws Exception {
            Class returnedType = method.getReturnType();
            log.debug("Creating "+(returnedType!=null?returnedType.getName():"")+" "+methodCallToString(method, values));

            T legacyEntity = null, lightblueEntity = null;

            if (LightblueMigration.shouldWriteSourceEntity()) {
                // insert to oracle, synchronously
                log.debug("."+method.getName()+" creating in legacy");
                legacyEntity = (T) method.invoke(sourceDAO, values);
            }

            if (LightblueMigration.shouldWriteDestinationEntity()) {
                log.debug("." + method.getName() + " creating in lightblue");


                if (destinationDAO instanceof CanStoreIDs) {
                    if (store != null) {
                        if (((CanStoreIDs) destinationDAO).getEntityIdStore() == null) {
                            ((CanStoreIDs) destinationDAO).setEntityIdStore(store);
                        }
                        Object entityId = legacyEntity.extractId();
                        store.push(legacyEntity.getClass(), entityId);
                    }
                }

                // it's expected that this method in lightblueDAO will extract id from idStore
                lightblueEntity = (T) method.invoke(destinationDAO, values);

            }

            if (LightblueMigration.shouldCheckWriteConsistency() && LightblueMigration.shouldWriteSourceEntity()) {
                // make sure that response from lightblue and oracle are the same
                log.debug("."+method.getName()+" checking returned entity's consistency");

                // check if entities match
                if (Objects.equals(lightblueEntity, legacyEntity)) {
                    // return lightblue data if they are
                    return lightblueEntity;
                } else {
                    // return oracle data if they aren't and log data inconsistency
                    logInconsistency(returnedType.getName(), method, values);
                    return legacyEntity;
                }
            }

            return lightblueEntity != null ? lightblueEntity : legacyEntity;
        }

        private ListeningExecutorService getExecutor() {
            return MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
        }

        private void logInconsistency(String entityName, Method method, Object[] values) {
            log.error(entityName+" inconsistency in "+methodCallToString(method, values));
        }

        private String methodCallToString(Method method, Object[] values) {
            StringBuffer str = new StringBuffer();
            str.append(method.getName()+"(");
            Iterator<Object> it = Arrays.asList(values).iterator();
            while(it.hasNext()) {
                Object value = it.next();
                str.append(value.toString());
                if (it.hasNext()) {
                    str.append(", ");
                }
            }
            str.append(")");
            return str.toString();
        }
    }
}
