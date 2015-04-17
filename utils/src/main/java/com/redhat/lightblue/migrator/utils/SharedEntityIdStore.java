package com.redhat.lightblue.migrator.utils;

/**
 * A FIFO queue. Used to pass IDs to create apis in Lightblue DAO without changing the signatures.
 * A single shared entity id store should be able to be used by multiple DAOs simultaneously
 *
 * @author mpatercz
 *
 */
public interface SharedEntityIdStore extends EntityIdStore {

    public void push(Class<?> entity, Object entityId);

    public Object pop(Class<?> entity);

}
