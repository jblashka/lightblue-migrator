package com.redhat.lightblue.migrator.utils;

import com.redhat.lightblue.migrator.features.LightblueMigrationFeatures;
import com.redhat.lightblue.migrator.test.LightblueMigrationPhase;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.togglz.junit.TogglzRule;

import java.util.*;

@RunWith(MockitoJUnitRunner.class)
public class DAOProxyFacadeTest {

    @Rule
    public TogglzRule togglzRule = TogglzRule.allDisabled(LightblueMigrationFeatures.class);

    @Mock CountryDAO legacyDAO;
    @Mock CountryDAOLightblue lightblueDAO;
    @Mock SharedEntityIdStore sharedEntityIdStore;
    CountryDAO proxy;
    DAOProxyFacadeFactory<CountryDAO> factory;

    @Before
    public void setup() {
        factory = new DAOProxyFacadeFactory<CountryDAO>(CountryDAO.class, legacyDAO, lightblueDAO, sharedEntityIdStore);
        proxy = factory.getProxy();
    }

    @After
    public void verifyNoMoreInteractions() {
        Mockito.verifyNoMoreInteractions(lightblueDAO);
        Mockito.verifyNoMoreInteractions(legacyDAO);
    }

    /* Read tests */

    @Test
    public void initialPhaseRead() {
        LightblueMigrationPhase.initialPhase(togglzRule);

        proxy.getCountry("PL");

        Mockito.verifyNoMoreInteractions(lightblueDAO);
        Mockito.verify(legacyDAO).getCountry("PL");
    }

    @Test
    public void dualReadPhaseReadConsistentTest() {
        LightblueMigrationPhase.dualReadPhase(togglzRule);

        Country country = new Country();

        Mockito.when(legacyDAO.getCountry("PL")).thenReturn(country);
        Mockito.when(lightblueDAO.getCountry("PL")).thenReturn(country);

        proxy.getCountry("PL");

        Mockito.verify(legacyDAO).getCountry("PL");
        Mockito.verify(lightblueDAO).getCountry("PL");
    }

    @Test
    public void dualReadPhaseReadInconsistentTest() {
        LightblueMigrationPhase.dualReadPhase(togglzRule);

        Country pl = new Country("PL");
        Country ca = new Country("CA");

        Mockito.when(legacyDAO.getCountry("PL")).thenReturn(ca);
        Mockito.when(lightblueDAO.getCountry("PL")).thenReturn(pl);

        Country returnedCountry = proxy.getCountry("PL");

        Mockito.verify(legacyDAO).getCountry("PL");
        Mockito.verify(lightblueDAO).getCountry("PL");

        // when there is a conflict, proxy will return what legacy dao returned
        Assert.assertEquals(ca, returnedCountry);
    }

    @Test
    public void lightblueProxyTest() {
        LightblueMigrationPhase.lightblueProxyPhase(togglzRule);

        proxy.getCountry("PL");

        Mockito.verifyZeroInteractions(legacyDAO);
        Mockito.verify(lightblueDAO).getCountry("PL");
    }

    /* update tests */

    @Test
    public void initialPhaseUpdate() {
        LightblueMigrationPhase.initialPhase(togglzRule);

        Country pl = new Country("PL");

        proxy.updateCountry(pl);

        Mockito.verifyNoMoreInteractions(lightblueDAO);
        Mockito.verify(legacyDAO).updateCountry(pl);
    }

    @Test
    public void dualWritePhaseUpdateConsistentTest() {
        LightblueMigrationPhase.dualWritePhase(togglzRule);

        Country pl = new Country("PL");

        proxy.updateCountry(pl);

        Mockito.verify(legacyDAO).updateCountry(pl);
        Mockito.verify(lightblueDAO).updateCountry(pl);
    }

    @Test
    public void dualWritePhaseUpdateInconsistentTest() {
        LightblueMigrationPhase.dualWritePhase(togglzRule);

        Country pl = new Country("PL");
        Country ca = new Country("CA");

        Mockito.when(legacyDAO.updateCountry(pl)).thenReturn(ca);
        Mockito.when(lightblueDAO.updateCountry(pl)).thenReturn(pl);

        Country updatedEntity = proxy.updateCountry(pl);

        Mockito.verify(legacyDAO).updateCountry(pl);
        Mockito.verify(lightblueDAO).updateCountry(pl);

        // when there is a conflict, proxy will return what legacy dao returned
        Assert.assertEquals(ca, updatedEntity);
    }

    @Test
    public void ligtblueProxyPhaseUpdateTest() {
        LightblueMigrationPhase.lightblueProxyPhase(togglzRule);

        Country pl = new Country("PL");

        proxy.updateCountry(pl);

        Mockito.verifyZeroInteractions(legacyDAO);
        Mockito.verify(lightblueDAO).updateCountry(pl);
    }

    /* insert tests */

    @Test
    public void initialPhaseCreate() {
        LightblueMigrationPhase.initialPhase(togglzRule);

        Country pl = new Country("PL");

        proxy.createCountry(pl);

        Mockito.verifyZeroInteractions(lightblueDAO);
        Mockito.verify(legacyDAO).createCountry(pl);
    }

    @Test
    public void dualWritePhaseCreateConsistentTest() {
        LightblueMigrationPhase.dualWritePhase(togglzRule);

        Country pl = new Country("PL");
        Country createdByLegacy = new Country(101l, "PL"); // has id set

        Mockito.when(legacyDAO.createCountry(pl)).thenReturn(createdByLegacy);

        Country createdCountry = proxy.createCountry(pl);
        Assert.assertEquals(101l, createdCountry.getId());


        Mockito.verify(legacyDAO).createCountry(pl);
        Mockito.verify(lightblueDAO).createCountry(pl);

        //Make sure the Proxy checked for and set the entityIdStore
        Mockito.verify(lightblueDAO).getEntityIdStore();
        Mockito.verify(lightblueDAO).setEntityIdStore(sharedEntityIdStore);
        //Assert.assertTrue(101l == (Long)((DAOFacadeBase) proxy).getEntityIdStore().pop());
    }

    @Test
    public void dualWritePhaseCreateInconsistentTest() {
        LightblueMigrationPhase.dualWritePhase(togglzRule);

        Country pl = new Country("PL");
        Country createdByLegacy = new Country(101l, "PL");

        Mockito.when(legacyDAO.createCountry(pl)).thenReturn(createdByLegacy);
        Mockito.when(lightblueDAO.createCountry(pl)).thenReturn(pl);

        Country createdCountry = proxy.createCountry(pl);

        Mockito.verify(legacyDAO).createCountry(pl);
        Mockito.verify(lightblueDAO).createCountry(pl);

        //Make sure the Proxy checked for and set the entityIdStore
        Mockito.verify(lightblueDAO).getEntityIdStore();
        Mockito.verify(lightblueDAO).setEntityIdStore(sharedEntityIdStore);

        // CountryDAOLightblue should set the id. Since it's just a mock, I'm checking what's in the cache.
        //Assert.assertTrue(101l == (Long) ((DAOFacadeBase) proxy).getEntityIdStore().pop());

        // when there is a conflict, proxy will return what legacy dao returned
        Assert.assertEquals(createdByLegacy, createdCountry);
    }

    @Test
    public void ligtblueProxyPhaseCreateTest() {
        LightblueMigrationPhase.lightblueProxyPhase(togglzRule);
        factory = new DAOProxyFacadeFactory<CountryDAO>(CountryDAO.class, legacyDAO, lightblueDAO, null);
        proxy = factory.getProxy();

        Country pl = new Country("PL");

        proxy.createCountry(pl);

        Mockito.verifyZeroInteractions(legacyDAO);
        Mockito.verify(lightblueDAO).createCountry(pl);
        //Mockito.verify(lightblueDAO).getEntityIdStore();
    }

}
