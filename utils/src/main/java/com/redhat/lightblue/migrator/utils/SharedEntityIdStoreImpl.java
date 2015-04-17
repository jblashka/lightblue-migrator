package com.redhat.lightblue.migrator.utils;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.NoSuchElementException;

/**
 * EntityIdStore implementation using ehcache. Creates a cache object per entity and uses thread id as key to avoid conflicts.
 *
 * TODO: ehcache.xml will need to be optimized to minimize overhead.
 *
 * @author mpatercz
 *
 */
public class SharedEntityIdStoreImpl implements SharedEntityIdStore {

    private static final Logger log = LoggerFactory.getLogger(SharedEntityIdStoreImpl.class);

    // singleton
    private static CacheManager cacheManager = CacheManager.create();

    @Override
    public void push(Object entityId) {
        this.push(Object.class, entityId);
    }

    @Override
    public void push(Class<?> entity, Object entityId) {
        Ehcache cache = cacheManager.addCacheIfAbsent(entity.getCanonicalName());
        long threadId = Thread.currentThread().getId();
        log.debug("Storing id="+entityId+" for "+cache.getName()+", thread="+threadId);
        if (!cache.isKeyInCache(threadId)) {
            cache.put(new Element(threadId, new LinkedList()));
        }

        LinkedList list = (LinkedList)cache.get(threadId).getObjectValue();

        list.add(entityId);
    }

    @Override
    public Object pop() {
        return this.pop(Object.class);
    }

    @Override
    public Object pop(Class<?> entity) {
        Ehcache cache = cacheManager.getEhcache(entity.getCanonicalName());
        long threadId = Thread.currentThread().getId();
        log.debug("Restoring id for "+cache.getName()+" thread="+threadId);
        if (!cache.isKeyInCache(threadId)) {
            throw new RuntimeException("No ids found for "+cache.getName()+" thread="+threadId+"!");
        }

        LinkedList list = (LinkedList)cache.get(threadId).getObjectValue();

        try {
            return list.removeFirst();
        } catch (NoSuchElementException e) {
            throw new RuntimeException("No ids found for "+cache.getName()+" thread="+threadId+"!", e);
        }
    }
}
