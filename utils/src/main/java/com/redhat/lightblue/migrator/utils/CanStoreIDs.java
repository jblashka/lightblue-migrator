package com.redhat.lightblue.migrator.utils;

import java.lang.reflect.*;

/**
 * Created by jblashka on 4/16/15.
 */
public interface CanStoreIDs {
    void setEntityIdStore(EntityIdStore entityIdStore);
    EntityIdStore getEntityIdStore();
}
