package com.redhat.lightblue.migrator.utils;

import net.sf.ehcache.CacheManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class SharedEntityIdStoreImplTest {

    SharedEntityIdStore store;

    @Before
    public void setup() {
        store = new SharedEntityIdStoreImpl();
    }

    @Test
    public void testSingle() {
        Class clazz = SharedEntityIdStoreImplTest.class;
        store.push(clazz, 101l);
        Assert.assertEquals((Long)101l, store.pop(clazz));
    }

    @Test
    public void testList() {
        Class clazz = SharedEntityIdStoreImplTest.class;
        store.push(clazz, 101l);
        store.push(clazz, 102l);
        store.push(clazz, 103l);
        Assert.assertEquals((Long)101l, store.pop(clazz));
        Assert.assertEquals((Long)102l, store.pop(clazz));
        Assert.assertEquals((Long)103l, store.pop(clazz));
    }

    @Test
    public void testDifferentCaches() {
        Class clazz1 = SharedEntityIdStoreImplTest.class;
        Class clazz2 = Country.class;

        store.push(clazz1, 101l);
        store.push(clazz1, 102l);
        store.push(clazz2, 104l);
        store.push(clazz2, 105l);
        Assert.assertEquals((Long)101l, store.pop(clazz1));
        Assert.assertEquals((Long)104l, store.pop(clazz2));
        Assert.assertEquals((Long)102l, store.pop(clazz1));
        Assert.assertEquals((Long)105l, store.pop(clazz2));
    }

    @Test(expected=RuntimeException.class)
    public void noId() {
        store.pop(EntityIdStoreImpl.class);
    }

    @Test(expected=RuntimeException.class)
    public void noId2() {
        store.push(EntityIdStoreImpl.class, 1l);
        store.pop(EntityIdStoreImpl.class);
        store.pop(EntityIdStoreImpl.class);
    }

    @After
    public void clearAll() {
        CacheManager.create().clearAll();
    }

}
