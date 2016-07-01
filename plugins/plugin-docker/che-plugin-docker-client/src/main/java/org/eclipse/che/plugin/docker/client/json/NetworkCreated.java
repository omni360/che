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
package org.eclipse.che.plugin.docker.client.json;

import java.util.Objects;

/**
 * Represents response from docker API on successful creation of network.
 *
 * author Alexander Garagatyi
 */
public class NetworkCreated {
    private String id;
    private String warning;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public NetworkCreated withId(String id) {
        this.id = id;
        return this;
    }

    public String getWarning() {
        return warning;
    }

    public void setWarning(String warning) {
        this.warning = warning;
    }

    public NetworkCreated withWarning(String warning) {
        this.warning = warning;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NetworkCreated)) return false;
        NetworkCreated that = (NetworkCreated)o;
        return Objects.equals(id, that.id) &&
               Objects.equals(warning, that.warning);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, warning);
    }

    @Override
    public String toString() {
        return "NetworkCreated{" +
               "id='" + id + '\'' +
               ", warning='" + warning + '\'' +
               '}';
    }
}