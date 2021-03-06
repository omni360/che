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
package org.eclipse.che.api.core.rest.shared.dto;

import org.eclipse.che.dto.shared.DTO;

import java.util.List;

/**
 * Describes capabilities of the Service.
 *
 * @author andrew00x
 * @see org.eclipse.che.api.core.rest.Service
 */
@DTO
public interface ServiceDescriptor extends Hyperlinks {

    /**
     * Get location to get this service descriptor in JSON format.
     *
     * @return location to get this service descriptor in JSON format
     */
    String getHref();

    ServiceDescriptor withHref(String href);

    /**
     * Set location to get this service descriptor in JSON format.
     *
     * @param href
     *         location to get this service descriptor in JSON format
     */
    void setHref(String href);

    /**
     * Get description of the Service.
     *
     * @return description of the Service
     */
    String getDescription();

    ServiceDescriptor withDescription(String description);

    /**
     * Set description of the Service.
     *
     * @param description
     *         description of the Service
     */
    void setDescription(String description);

    /**
     * Get API version.
     *
     * @return API version
     */
    String getVersion();

    ServiceDescriptor withVersion(String version);

    /**
     * Get API version.
     *
     * @param version
     *         API version
     */
    void setVersion(String version);

    ServiceDescriptor withLinks(List<Link> links);
}
