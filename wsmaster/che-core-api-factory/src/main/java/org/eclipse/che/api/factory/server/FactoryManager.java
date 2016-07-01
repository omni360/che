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
package org.eclipse.che.api.factory.server;

import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.factory.server.model.impl.FactoryImpl;
import org.eclipse.che.api.factory.server.snippet.SnippetGenerator;
import org.eclipse.che.api.factory.server.spi.FactoryDao;
import org.eclipse.che.api.factory.shared.model.Factory;
import org.eclipse.che.commons.lang.Pair;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.eclipse.che.api.factory.shared.Constants.HTML_SNIPPET_TYPE;
import static org.eclipse.che.api.factory.shared.Constants.IFRAME_SNIPPET_TYPE;
import static org.eclipse.che.api.factory.shared.Constants.MARKDOWN_SNIPPET_TYPE;
import static org.eclipse.che.api.factory.shared.Constants.URL_SNIPPET_TYPE;

/**
 * @author Anton Korneta
 */
@Singleton
public class FactoryManager {

    @Inject
    private FactoryDao factoryDao;

    /**
     * Stores {@link Factory} instance.
     *
     * @param factory
     *         instance of factory which would be stored
     * @return factory which has been stored
     * @throws NullPointerException
     *         when {@code factory} is null
     * @throws ConflictException
     *         when any conflict occurs (e.g Factory with given name already exists for {@code creator})
     * @throws ServerException
     *         when any server errors occurs
     */
    public Factory saveFactory(Factory factory) throws ConflictException, ServerException {
        requireNonNull(factory);
        return saveFactory(factory, null);
    }

    /**
     * Stores {@link Factory} instance and related set of {@link FactoryImage}.
     *
     * @param factory
     *         instance of factory which would be stored
     * @param images
     *         factory images which would be stored
     * @return factory which has been stored
     * @throws NullPointerException
     *         when {@code factory} is null
     * @throws ConflictException
     *         when any conflict occurs (e.g Factory with given name already exists for {@code creator})
     * @throws ServerException
     *         when any server errors occurs
     */
    public Factory saveFactory(Factory factory, Set<FactoryImage> images) throws ConflictException,
                                                                                 ServerException {
        requireNonNull(factory);
        return factoryDao.create(new FactoryImpl(factory, images));
    }

    /**
     * Updates factory accordance to the new configuration.
     *
     * <p>Note: Updating uses replace strategy for factory configuration but it won't affect existed factory images,
     * therefore existing factory configuration would be replaced with given update {@code update}
     *
     * @param update
     *         factory update
     * @return updated factory with links
     * @throws NullPointerException
     *         when factory id or factory {@code update} is null
     * @throws ConflictException
     *         when any conflict occurs (e.g Factory with given name already exists for {@code creator})
     * @throws NotFoundException
     *         when factory with given id not found
     * @throws ServerException
     *         when any server error occurs
     */
    public Factory updateFactory(Factory update) throws ConflictException,
                                                        NotFoundException,
                                                        ServerException {
        requireNonNull(update);
        return updateFactory(update, getFactoryImages(update.getId()));
    }

    /**
     * Updates factory and its images accordance to the new configuration.
     *
     * <p>Note: Updating uses replacement strategy,
     * therefore existing factory would be replaced with given update {@code update}
     *
     * @param update
     *         factory update
     * @return updated factory with links
     * @throws NullPointerException
     *         when {@code update} is null
     * @throws ConflictException
     *         when any conflict occurs (e.g Factory with given name already exists for {@code creator})
     * @throws NotFoundException
     *         when factory with given id not found
     * @throws ServerException
     *         when any server error occurs
     */
    public Factory updateFactory(Factory update, Set<FactoryImage> images) throws ConflictException,
                                                                                  NotFoundException,
                                                                                  ServerException {
        requireNonNull(update);
        return factoryDao.update(new FactoryImpl(update, images));
    }

    /**
     * Removes stored {@link Factory} by given id.
     *
     * @param id
     *         factory identifier
     * @throws NullPointerException
     *         when {@code id} is null
     * @throws NotFoundException
     *         when factory with given id not found
     * @throws ServerException
     *         when any server errors occurs
     */
    public void removeFactory(String id) throws NotFoundException,
                                                ServerException {
        requireNonNull(id);
        factoryDao.remove(id);
    }

    /**
     * Gets factory by given id.
     *
     * @param id
     *         factory identifier
     * @return factory instance
     * @throws NullPointerException
     *         when {@code id} is null
     * @throws NotFoundException
     *         when factory with given id not found
     * @throws ServerException
     *         when any server errors occurs
     */
    public Factory getFactoryById(String id) throws NotFoundException,
                                                    ServerException {
        requireNonNull(id);
        return factoryDao.getById(id);
    }


    /**
     * Gets list of factories which conform given attributes.
     *
     * @param maxItems
     *         max number of items in response
     * @param skipCount
     *         skip items. Must be equals or greater then {@code 0}
     * @param attributes
     *         skip items. Must be equals or greater then {@code 0}
     * @return list of factories match the specified attributes
     * @throws ServerException
     *         when any server errors occurs
     */
    public List<? extends Factory> getByAttribute(int maxItems,
                                                  int skipCount,
                                                  List<Pair<String, String>> attributes) throws ServerException {
        return factoryDao.getByAttribute(maxItems, skipCount, attributes);
    }

    /**
     * Gets factory images.
     *
     * @param factoryId
     *         factory identifier
     * @return returns a list of factory images
     * @throws NullPointerException
     *         when {@code id} is null
     * @throws NotFoundException
     *         when the factory with specified id doesn't not found
     * @throws NotFoundException
     *         when could not find at least one factory image
     */
    public Set<FactoryImage> getFactoryImages(String factoryId) throws NotFoundException,
                                                                       ServerException {
        requireNonNull(factoryId);
        final Set<FactoryImage> images = factoryDao.getById(factoryId).getImages();
        if (images.isEmpty()) {
            throw new NotFoundException("Default image for factory " + factoryId + " is not found.");
        }
        return images;
    }

    /**
     * Gets factory images by given image id.
     *
     * @param factoryId
     *         factory identifier
     * @param imageId
     *         image identifier
     * @return image information if ids are correct. If imageId is not set, random image of the factory will be returned,
     * if factory has no images, exception will be thrown
     * @throws NotFoundException
     *         when the factory with specified id doesn't not found
     * @throws NotFoundException
     *         when image id is not specified and there is no default image for the specified factory
     * @throws NotFoundException
     *         when image with specified id doesn't exist
     */
    public FactoryImage getFactoryImage(String factoryId, String imageId) throws NotFoundException,
                                                                                 ServerException {
        requireNonNull(factoryId);
        requireNonNull(imageId);
        final Set<FactoryImage> images = getFactoryImages(factoryId).stream()
                                                                    .filter(image -> imageId.equals(image.getName()))
                                                                    .collect(Collectors.toSet());
        if (images.isEmpty()) {
            throw new NotFoundException("Image with name " + imageId + " is not found");
        }
        return images.iterator().next();
    }

    /**
     * Gets factory snippet by factory id and snippet type.
     * If snippet type is not set, "url" type will be used as default.
     *
     * @param factoryId
     *         id of factory
     * @param snippetType
     *         type of snippet
     * @param uriInfo
     *         url context
     * @return snippet content.
     * @throws NotFoundException
     *         when factory with specified id doesn't not found - with response code 400
     * @throws ServerException
     *         when any server error occurs during snippet creation
     */
    public String getFactorySnippet(String factoryId,
                                    String snippetType,
                                    UriInfo uriInfo) throws NotFoundException,
                                                            ServerException {
        requireNonNull(factoryId);
        final URI baseUri = uriInfo.getBaseUri();
        final String baseUrl = UriBuilder.fromUri(baseUri)
                                         .replacePath("")
                                         .build()
                                         .toString();
        switch (snippetType) {
            case URL_SNIPPET_TYPE:
                return UriBuilder.fromUri(baseUri)
                                 .replacePath("factory")
                                 .queryParam("id", factoryId)
                                 .build()
                                 .toString();
            case HTML_SNIPPET_TYPE:
                return SnippetGenerator.generateHtmlSnippet(baseUrl, factoryId);
            case IFRAME_SNIPPET_TYPE:
                return SnippetGenerator.generateiFrameSnippet(baseUrl, factoryId);
            case MARKDOWN_SNIPPET_TYPE:
                final Set<FactoryImage> images = getFactoryImages(factoryId);
                final String imageId = (images.size() > 0) ? images.iterator().next().getName()
                                                           : null;
                try {
                    return SnippetGenerator.generateMarkdownSnippet(baseUrl, getFactoryById(factoryId), imageId);
                } catch (IllegalArgumentException e) {
                    throw new ServerException(e.getLocalizedMessage());
                }
            default:
                // when the specified type is not supported
                return null;
        }
    }
}
