package com.redhat.lightblue.migrator.utils;

public interface CountryDAOLightblue extends CountryDAO, CanStoreIDs {

    public abstract void setEntityIdStore(EntityIdStore store);

}
