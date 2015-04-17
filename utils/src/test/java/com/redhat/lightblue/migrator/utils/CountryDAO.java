package com.redhat.lightblue.migrator.utils;

import com.redhat.lightblue.migrator.utils.annotations.Create;
import com.redhat.lightblue.migrator.utils.annotations.Read;
import com.redhat.lightblue.migrator.utils.annotations.Update;

public interface CountryDAO {

    @Create
    public abstract Country createCountry(Country country);

    @Update
    public abstract Country updateCountry(Country country);

    @Read
    public abstract Country getCountry(String iso2Code);

}