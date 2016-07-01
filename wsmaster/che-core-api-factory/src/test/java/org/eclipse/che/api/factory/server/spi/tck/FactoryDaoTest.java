/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.api.factory.server.spi.tck;

import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.factory.server.model.impl.AuthorImpl;
import org.eclipse.che.api.factory.server.model.impl.FactoryImpl;
import org.eclipse.che.api.factory.server.spi.FactoryDao;
import org.eclipse.che.commons.lang.NameGenerator;
import org.eclipse.che.commons.test.tck.TckModuleFactory;
import org.eclipse.che.commons.test.tck.repository.TckRepository;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.testng.Assert.assertEquals;

/**
 * Test {@link FactoryDao} contract.
 *
 * @author Anton Korneta
 */
@Guice(moduleFactory = TckModuleFactory.class)
@Test(suiteName = FactoryDaoTest.SUITE_NAME)
public class FactoryDaoTest {

    public static final String SUITE_NAME = "FactoryDaoTck";

    private static final int ENTRY_COUNT = 5;

    private List<FactoryImpl> factories;

    @Inject
    private FactoryDao factoryDao;

    @Inject
    private TckRepository<FactoryImpl> tckRepository;

    @BeforeMethod
    public void setup() throws Exception {
        factories = new ArrayList<>(ENTRY_COUNT);
        for (int i = 0; i < ENTRY_COUNT; i++) {
            factories.add(createFactory(i));
        }
        tckRepository.createAll(factories);
    }

    @AfterMethod
    public void cleanUp() throws Exception {
        tckRepository.removeAll();
    }

    @Test
    public void shouldCreateFactory() throws Exception {
        final FactoryImpl newFactory = createFactory(6);

        assertEquals(factoryDao.create(newFactory), newFactory);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void shouldThrowNpeWhenCreateFactoryNull() throws Exception {
        factoryDao.create(null);
    }

    @Test(expectedExceptions = ConflictException.class)
    public void shouldThrowConflictExceptionWhenCreateFactoryWithExistingId() throws Exception {
        final FactoryImpl newFactory = createFactory(7);
        newFactory.setId(factories.get(0).getId());

        factoryDao.create(newFactory);
    }

    @Test(expectedExceptions = ConflictException.class)
    public void shouldThrowConflictExceptionWhenCreateFactoryWithExistingNameAndCreator() throws Exception {
        final FactoryImpl newFactory = createFactory(8);
        newFactory.setName(factories.get(0).getName());
        newFactory.setCreator(factories.get(0).getCreator());

        factoryDao.create(newFactory);
    }

    @Test
    public void shouldUpdateFactory() throws Exception {
        final FactoryImpl update = factories.get(0);
        update.setName("awesome");

        assertEquals(factoryDao.update(update), update);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void shouldThrowNpeWhenUpdateFactoryNull() throws Exception {
        factoryDao.update(null);
    }

    @Test(expectedExceptions = NotFoundException.class)
    public void shouldThrowNotFoundExceptionWhenUpdateFactoryNotFound() throws Exception {
        final FactoryImpl update = factories.get(0);
        update.setId("non-existing");

        factoryDao.update(update);
    }

    @Test(expectedExceptions = ConflictException.class)
    public void shouldThrowConflictExceptionWhenUpdateFactoryWithExistingNameAndCreator() throws Exception {
        final FactoryImpl update = factories.get(0);
        update.setName(factories.get(1).getName());
        update.setCreator(factories.get(1).getCreator());

        factoryDao.update(update);
    }

    @Test(expectedExceptions = NotFoundException.class,
         dependsOnMethods = "shouldThrowNotFoundExceptionWhenGettingFactoryByNonExistingId")
    public void shouldRemoveFactory() throws Exception {
        final FactoryImpl remove = factories.get(0);

        factoryDao.remove(remove.getId());
        factoryDao.getById(remove.getId());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void shouldThrowNpeWhenRemoveFactoryIdNull() throws Exception {
        factoryDao.remove(null);
    }

    @Test(expectedExceptions = NotFoundException.class)
    public void shouldThrowNotFoundExceptionWhenRemoveFactoryWithNonExistingId() throws Exception {
        factoryDao.remove("non-existing");
    }

    @Test
    public void shouldGetFactoryById() throws Exception {
        final FactoryImpl factory = factories.get(0);

        assertEquals(factoryDao.getById(factory.getId()), factory);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void shouldThrowNpeWhenGettingFactoryByNullId() throws Exception {
        final FactoryImpl factory = factories.get(0);

        assertEquals(factoryDao.getById(factory.getId()), factory);
    }

    @Test(expectedExceptions = NotFoundException.class)
    public void shouldThrowNotFoundExceptionWhenGettingFactoryByNonExistingId() throws Exception {
        factoryDao.getById("non-existing");
    }

    @Test
    public void shouldGetFactoryByAttribute() throws Exception {
        assert false;
    }

    private FactoryImpl createFactory(int index) {
        return FactoryImpl.builder()
                          .setId(NameGenerator.generate("factoryId", 5))
                          .setVersion("4.0")
                          .setName("factoryName" + index)
                          .setCreator(new AuthorImpl(5L, "name" + index, "userId" + index, "userEmail"))
                          .setImages(Collections.emptySet())
                          .build();
    }
}
