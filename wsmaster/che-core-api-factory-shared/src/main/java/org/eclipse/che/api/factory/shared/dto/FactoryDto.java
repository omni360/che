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
package org.eclipse.che.api.factory.shared.dto;

import org.eclipse.che.api.core.factory.FactoryParameter;
import org.eclipse.che.api.core.rest.shared.dto.Hyperlinks;
import org.eclipse.che.api.core.rest.shared.dto.Link;
import org.eclipse.che.api.factory.shared.model.Factory;
import org.eclipse.che.api.workspace.shared.dto.WorkspaceConfigDto;
import org.eclipse.che.dto.shared.DTO;

import java.util.List;

import static org.eclipse.che.api.core.factory.FactoryParameter.Obligation.MANDATORY;
import static org.eclipse.che.api.core.factory.FactoryParameter.Obligation.OPTIONAL;

/**
 * Factory of version 4.0
 *
 * @author Max Shaposhnik
 */
@DTO
public interface FactoryDto extends Factory, Hyperlinks {

    @FactoryParameter(obligation = MANDATORY)
    String getV();

    void setV(String v);

    FactoryDto withV(String v);

    @FactoryParameter(obligation = MANDATORY)
    WorkspaceConfigDto getWorkspace();

    void setWorkspace(WorkspaceConfigDto workspace);

    FactoryDto withWorkspace(WorkspaceConfigDto workspace);

    @FactoryParameter(obligation = OPTIONAL, trackedOnly = true)
    PoliciesDto getPolicies();

    void setPolicies(PoliciesDto policies);

    FactoryDto withPolicies(PoliciesDto policies);

    @FactoryParameter(obligation = OPTIONAL)
    AuthorDto getCreator();

    void setCreator(AuthorDto creator);

    FactoryDto withCreator(AuthorDto creator);

    @FactoryParameter(obligation = OPTIONAL)
    ButtonDto getButton();

    void setButton(ButtonDto button);

    FactoryDto withButton(ButtonDto button);

    @FactoryParameter(obligation = OPTIONAL)
    IdeDto getIde();

    void setIde(IdeDto ide);

    FactoryDto withIde(IdeDto ide);

    @FactoryParameter(obligation = OPTIONAL, setByServer = true)
    String getId();

    void setId(String id);

    FactoryDto withId(String id);

    @FactoryParameter(obligation = OPTIONAL)
    String getName();

    void setName(String name);

    FactoryDto withName(String name);

    FactoryDto withLinks(List<Link> links);
}
