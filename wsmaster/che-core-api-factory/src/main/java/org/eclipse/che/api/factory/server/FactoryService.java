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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonSyntaxException;

import org.apache.commons.fileupload.FileItem;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.project.ProjectConfig;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.api.factory.server.builder.FactoryBuilder;
import org.eclipse.che.api.factory.shared.dto.AuthorDto;
import org.eclipse.che.api.factory.shared.dto.FactoryDto;
import org.eclipse.che.api.factory.shared.model.Factory;
import org.eclipse.che.api.user.server.UserManager;
import org.eclipse.che.api.workspace.server.WorkspaceManager;
import org.eclipse.che.api.workspace.server.model.impl.ProjectConfigImpl;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceImpl;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.lang.NameGenerator;
import org.eclipse.che.commons.lang.Pair;
import org.eclipse.che.commons.lang.URLEncodedUtils;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.dto.server.DtoFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.HttpHeaders.CONTENT_DISPOSITION;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.eclipse.che.api.factory.server.DtoConverter.asDto;
import static org.eclipse.che.api.factory.server.FactoryLinksHelper.createLinks;

/**
 * Defines Factory REST API.
 *
 * @author Anton Korneta
 * @author Florent Benoit
 */
@Api(value = "/factory",
     description = "Factory manager")
@Path("/factory")
public class FactoryService extends Service {
    private static final Logger LOG = LoggerFactory.getLogger(FactoryService.class);

    /**
     * Error message if there is no plugged resolver.
     */
    public static final String ERROR_NO_RESOLVER_AVAILABLE = "Cannot build factory with any of the provided parameters.";

    /**
     * Validate query parameter. If true, factory will be validated
     */
    public static final String VALIDATE_QUERY_PARAMETER = "validate";

    /**
     * Set of resolvers for factories. Injected through an holder.
     */
    private final Set<FactoryParametersResolver> factoryParametersResolvers;

    private final FactoryManager         factoryManager;
    private final UserManager            userManager;
    private final FactoryEditValidator   editValidator;
    private final FactoryCreateValidator createValidator;
    private final FactoryAcceptValidator acceptValidator;
    private final FactoryBuilder         factoryBuilder;
    private final WorkspaceManager       workspaceManager;

    @Inject
    public FactoryService(FactoryManager factoryManager,
                          UserManager userManager,
                          FactoryCreateValidator createValidator,
                          FactoryAcceptValidator acceptValidator,
                          FactoryEditValidator editValidator,
                          FactoryBuilder factoryBuilder,
                          WorkspaceManager workspaceManager,
                          FactoryParametersResolverHolder factoryParametersResolverHolder) {
        this.factoryManager = factoryManager;
        this.userManager = userManager;
        this.createValidator = createValidator;
        this.acceptValidator = acceptValidator;
        this.editValidator = editValidator;
        this.factoryBuilder = factoryBuilder;
        this.workspaceManager = workspaceManager;
        this.factoryParametersResolvers = factoryParametersResolverHolder.getFactoryParametersResolvers();
    }

    @POST
    @Consumes(MULTIPART_FORM_DATA)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Create a new factory based on configuration and factory images",
                  notes = "The field 'factory' is required")
    @ApiResponses({@ApiResponse(code = 200, message = "Factory successfully created"),
                   @ApiResponse(code = 400, message = "Missed required parameters, parameters are not valid"),
                   @ApiResponse(code = 403, message = "The user does not have rights to create factory"),
                   @ApiResponse(code = 409, message = "When factory with given name and creator already exists"),
                   @ApiResponse(code = 500, message = "Internal server error occurred")})
    public FactoryDto saveFactory(Iterator<FileItem> formData) throws ForbiddenException,
                                                                      ConflictException,
                                                                      BadRequestException,
                                                                      ServerException {
        try {
            final Set<FactoryImage> images = new HashSet<>();
            FactoryDto factoryDto = null;
            while (formData.hasNext()) {
                final FileItem item = formData.next();
                switch (item.getFieldName()) {
                    case ("factory"): {
                        try (InputStream factoryData = item.getInputStream()) {
                            factoryDto = factoryBuilder.build(factoryData);
                        } catch (JsonSyntaxException ex) {
                            throw new BadRequestException("Invalid JSON value of the field 'factory' provided");
                        }
                        break;
                    }
                    case ("image"): {
                        try (InputStream imageData = item.getInputStream()) {
                            final FactoryImage image = FactoryImage.createImage(imageData,
                                                                                item.getContentType(),
                                                                                NameGenerator.generate(null, 16));
                            if (image.hasContent()) {
                                images.add(image);
                            }
                        }
                        break;
                    }
                    default:
                        //DO NOTHING
                }
            }
            requiredNotNull(factoryDto, "'factory' section of multipart/form-data");
            processDefaults(factoryDto);
            createValidator.validateOnCreate(factoryDto);
            return injectLinks(asDto(factoryManager.saveFactory(factoryDto, images)), images);
        } catch (IOException ioEx) {
            throw new ServerException(ioEx.getLocalizedMessage(), ioEx);
        }
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Create a new factory based on configuration",
                  notes = "Factory will be created without images")
    @ApiResponses({@ApiResponse(code = 200, message = "Factory successfully created"),
                   @ApiResponse(code = 400, message = "Missed required parameters, parameters are not valid"),
                   @ApiResponse(code = 403, message = "User does not have rights to create factory"),
                   @ApiResponse(code = 409, message = "When factory with given name and creator already exists"),
                   @ApiResponse(code = 500, message = "Internal server error occurred")})
    public FactoryDto saveFactory(FactoryDto factoryDto) throws BadRequestException,
                                                                ServerException,
                                                                ForbiddenException,
                                                                ConflictException {
        requiredNotNull(factoryDto, "Factory configuration");
        processDefaults(factoryDto);
        createValidator.validateOnCreate(factoryDto);
        return injectLinks(asDto(factoryManager.saveFactory(factoryDto)), null);
    }

    @GET
    @Path("/{id}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get factory by its identifier",
                  notes = "If validate parameter is not specified, retrieved factory wont be validated")
    @ApiResponses({@ApiResponse(code = 200, message = "Response contains requested factory entry"),
                   @ApiResponse(code = 400, message = "Missed required parameters, failed to validate factory"),
                   @ApiResponse(code = 404, message = "Factory with specified identifier does not exist"),
                   @ApiResponse(code = 500, message = "Internal server error occurred")})
    public FactoryDto getFactory(@ApiParam(value = "Factory identifier")
                                 @PathParam("id")
                                 String factoryId,
                                 @ApiParam(value = "Whether or not to validate values like it is done when accepting the factory",
                                           allowableValues = "true, false",
                                           defaultValue = "false")
                                 @DefaultValue("false")
                                 @QueryParam("validate")
                                 Boolean validate) throws BadRequestException,
                                                          NotFoundException,
                                                          ServerException {
        final FactoryDto factoryDto = asDto(factoryManager.getFactoryById(factoryId));
        if (validate) {
            acceptValidator.validateOnAccept(factoryDto);
        }
        return injectLinks(factoryDto, factoryManager.getFactoryImages(factoryId));
    }

    @GET
    @Path("/find")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get factory by attribute",
                  notes = "If specify more than one value for a single query parameter then will be taken the first one")
    @ApiResponses({@ApiResponse(code = 200, message = "Response contains requested factory entry"),
                   @ApiResponse(code = 400, message = "Failed to validate factory e.g. when factory expires"),
                   @ApiResponse(code = 500, message = "Internal server error")})
    public List<FactoryDto> getFactoryByAttribute(@DefaultValue("0")
                                                  @QueryParam("skipCount")
                                                  Integer skipCount,
                                                  @DefaultValue("30")
                                                  @QueryParam("maxItems")
                                                  Integer maxItems,
                                                  @Context
                                                  UriInfo uriInfo) throws BadRequestException,
                                                                          ServerException {
        final Set<String> skip = ImmutableSet.of("token", "skipCount", "maxItems");
        final List<Pair<String, String>> query = URLEncodedUtils.parse(uriInfo.getRequestUri())
                                                                .entrySet()
                                                                .stream()
                                                                .filter(param -> !skip.contains(param.getKey())
                                                                                 && !param.getValue().isEmpty())
                                                                .map(entry -> Pair.of(entry.getKey(),
                                                                                      entry.getValue()
                                                                                           .iterator()
                                                                                           .next()))
                                                                .collect(toList());
        if (query.isEmpty()) {
            throw new BadRequestException("Query must contain at least one attribute");
        }
        return factoryManager.getByAttribute(maxItems, skipCount, query)
                             .stream()
                             .map(factory -> injectLinks(asDto(factory), null))
                             .collect(Collectors.toList());
    }

    @PUT
    @Path("/{id}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Update factory information by configuration and specified identifier",
                  notes = "Update factory based on the factory id which is passed in a path parameter. " +
                          "For perform this operation user needs respective rights")
    @ApiResponses({@ApiResponse(code = 200, message = "Factory successfully updated"),
                   @ApiResponse(code = 400, message = "Missed required parameters, parameters are not valid"),
                   @ApiResponse(code = 403, message = "User does not have rights to update factory"),
                   @ApiResponse(code = 404, message = "Factory to update not found"),
                   @ApiResponse(code = 409, message = "Conflict error occurred during factory update" +
                                                      "(e.g. Factory with such name and creator already exists)"),
                   @ApiResponse(code = 500, message = "Internal server error")})
    public FactoryDto updateFactory(@ApiParam(value = "Factory identifier")
                                    @PathParam("id")
                                    String factoryId,
                                    FactoryDto update) throws BadRequestException,
                                                              NotFoundException,
                                                              ServerException,
                                                              ForbiddenException,
                                                              ConflictException {
        requiredNotNull(update, "Factory configuration");
        final Factory existingFactory = factoryManager.getFactoryById(factoryId);
        // check if the current user has enough access to edit the factory
        editValidator.validate(existingFactory);

        processDefaults(update);
        update.withId(factoryId)
              .getCreator()
              .setCreated(existingFactory.getCreator().getCreated());

        // validate the new content
        createValidator.validateOnCreate(update);
        return injectLinks(asDto(factoryManager.updateFactory(update)),
                           factoryManager.getFactoryImages(factoryId));
    }

    @DELETE
    @Path("/{id}")
    @ApiOperation(value = "Removes factory by its identifier",
                  notes = "Removes factory based on the factory id which is passed in a path parameter. " +
                          "For perform this operation user needs respective rights")
    @ApiResponses({@ApiResponse(code = 200, message = "Factory successfully removed"),
                   @ApiResponse(code = 403, message = "User not authorized to call this operation"),
                   @ApiResponse(code = 404, message = "Factory not found"),
                   @ApiResponse(code = 500, message = "Internal server error")})
    public void removeFactory(@ApiParam(value = "Factory identifier")
                              @PathParam("id")
                              String id) throws ForbiddenException,
                                                NotFoundException,
                                                ServerException {
        factoryManager.removeFactory(id);
    }

    @GET
    @Path("/{id}/image")
    @Produces("image/*")
    @ApiOperation(value = "Get factory image",
                  notes = "If image identifier is not specified then first found image will be returned")
    @ApiResponses({@ApiResponse(code = 200, message = "Response contains requested factory image"),
                   @ApiResponse(code = 400, message = "Missed required parameters, parameters are not valid"),
                   @ApiResponse(code = 404, message = "Factory or factory image not found"),
                   @ApiResponse(code = 500, message = "Internal server error")})
    public Response getImage(@ApiParam(value = "Factory identifier")
                             @PathParam("id")
                             String factoryId,
                             @ApiParam(value = "Image identifier")
                             @QueryParam("imgId")
                             String imageId) throws NotFoundException,
                                                    BadRequestException,
                                                    ServerException {
        final FactoryImage image = isNullOrEmpty(imageId) ? factoryManager.getFactoryImages(factoryId).iterator().next()
                                                          : factoryManager.getFactoryImage(factoryId, imageId);
        return Response.ok(image.getImageData(), image.getMediaType()).build();
    }

    @GET
    @Path("/{id}/snippet")
    @Produces(TEXT_PLAIN)
    @ApiOperation(value = "Get factory snippet",
                  notes = "If snippet type is not specified then default 'url' will be used")
    @ApiResponses({@ApiResponse(code = 200, message = "Response contains requested factory snippet"),
                   @ApiResponse(code = 400, message = "Missed required parameters, parameters are not valid"),
                   @ApiResponse(code = 404, message = "Factory or factory snippet not found"),
                   @ApiResponse(code = 500, message = "Internal server error")})
    public String getFactorySnippet(@ApiParam(value = "Factory identifier")
                                    @PathParam("id")
                                    String factoryId,
                                    @ApiParam(value = "Snippet type",
                                              required = true,
                                              allowableValues = "url, html, iframe, markdown",
                                              defaultValue = "url")
                                    @DefaultValue("url")
                                    @QueryParam("type")
                                    String type,
                                    @Context
                                    UriInfo uriInfo) throws NotFoundException,
                                                            BadRequestException,
                                                            ServerException {
        final String factorySnippet = factoryManager.getFactorySnippet(factoryId, type, uriInfo);
        if (factorySnippet != null) {
            return factorySnippet;
        }
        LOG.warn("Snippet type {} is unsupported", type);
        throw new BadRequestException("Snippet type \"" + type + "\" is unsupported.");
    }

    @GET
    @Path("/workspace/{ws-id}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Construct factory from workspace",
                  notes = "This call returns a Factory.json that is used to create a factory")
    @ApiResponses({@ApiResponse(code = 200, message = "Response contains requested factory JSON"),
                   @ApiResponse(code = 400, message = "Missed required parameters, parameters are not valid"),
                   @ApiResponse(code = 404, message = "Workspace not found"),
                   @ApiResponse(code = 500, message = "Internal server error")})
    public Response getFactoryJson(@ApiParam(value = "Workspace identifier")
                                   @PathParam("ws-id")
                                   String wsId,
                                   @ApiParam(value = "Project path")
                                   @QueryParam("path")
                                   String path) throws BadRequestException,
                                                       NotFoundException,
                                                       ServerException {
        final WorkspaceImpl workspace = workspaceManager.getWorkspace(wsId);
        excludeProjectsWithoutLocation(workspace, path);
        final FactoryDto factoryDto = DtoFactory.newDto(FactoryDto.class)
                                                .withV("4.0")
                                                .withWorkspace(org.eclipse.che.api.workspace.server.DtoConverter
                                                                       .asDto(workspace.getConfig()));
        return Response.ok(factoryDto, APPLICATION_JSON)
                       .header(CONTENT_DISPOSITION, "attachment; filename=factory.json")
                       .build();
    }

    @POST
    @Path("/resolver")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Create factory by providing map of parameters",
                  notes = "Get JSON with factory information")
    @ApiResponses({@ApiResponse(code = 200, message = "Factory successfully built from parameters"),
                   @ApiResponse(code = 400, message = "Missed required parameters, failed to validate factory"),
                   @ApiResponse(code = 500, message = "Internal server error")})
    public FactoryDto resolveFactory(@ApiParam(value = "Parameters provided to create factories")
                                     Map<String, String> parameters,
                                     @ApiParam(value = "Whether or not to validate values like it is done when accepting a Factory",
                                               allowableValues = "true,false",
                                               defaultValue = "false")
                                     @DefaultValue("false")
                                     @QueryParam(VALIDATE_QUERY_PARAMETER)
                                     Boolean validate) throws ServerException,
                                                              BadRequestException {

        // check parameter
        requiredNotNull(parameters, "Factory build parameters");

        // search matching resolver and create factory from matching resolver
        for (FactoryParametersResolver resolver : factoryParametersResolvers) {
            if (resolver.accept(parameters)) {
                final FactoryDto factory = resolver.createFactory(parameters);
                if (validate) {
                    acceptValidator.validateOnAccept(factory);
                }

                return injectLinks(factory, null);
            }
        }
        // no match
        throw new BadRequestException(ERROR_NO_RESOLVER_AVAILABLE);
    }

    /**
     * Injects factory links.
     *
     * If factory is named then accept named link will be injected,
     * if images set is not null and not empty then image links will be injected
     */
    private FactoryDto injectLinks(FactoryDto factory, Set<FactoryImage> images) {
        String username = null;
        try {
            username = userManager.getById(factory.getCreator().getUserId()).getName();
        } catch (ApiException ignored) {
            // when impossible to get username then named factory link won't be injected
        }
        return factory.withLinks(images != null && !images.isEmpty()
                                 ? createLinks(factory, images, getServiceContext(), username)
                                 : createLinks(factory, getServiceContext(), username));
    }

    /**
     * Filters workspace projects, removes projects which don't have location set.
     * If all workspace projects don't have location throws {@link BadRequestException}.
     */
    private void excludeProjectsWithoutLocation(WorkspaceImpl usersWorkspace, String projectPath) throws BadRequestException {
        final boolean notEmptyPath = projectPath != null;
        //Condition for sifting valid project in user's workspace
        Predicate<ProjectConfig> predicate = projectConfig -> {
            // if project is a subproject (it's path contains another project) , then location can be null
            final boolean isSubProject = projectConfig.getPath().indexOf('/', 1) != -1;
            final boolean hasNotEmptySource = projectConfig.getSource() != null
                                              && projectConfig.getSource().getType() != null
                                              && projectConfig.getSource().getLocation() != null;

            return !(notEmptyPath && !projectPath.equals(projectConfig.getPath()))
                   && (isSubProject || hasNotEmptySource);
        };

        //Filtered out projects by path and source storage presence.
        final List<ProjectConfigImpl> filtered = usersWorkspace.getConfig()
                                                               .getProjects()
                                                               .stream()
                                                               .filter(predicate)
                                                               .collect(toList());
        if (filtered.isEmpty()) {
            throw new BadRequestException("Unable to create factory from this workspace, " +
                                          "because it does not contains projects with source storage");
        }
        usersWorkspace.getConfig().setProjects(filtered);
    }

    /**
     * Adds to the factory information about creator and time of creation
     */
    private void processDefaults(FactoryDto factory) {
        final Subject currentSubject = EnvironmentContext.getCurrent().getSubject();
        final AuthorDto creator = factory.getCreator();
        if (creator == null) {
            factory.setCreator(DtoFactory.newDto(AuthorDto.class)
                                         .withUserId(currentSubject.getUserId())
                                         .withName(currentSubject.getUserName())
                                         .withCreated(System.currentTimeMillis()));
            return;
        }
        if (isNullOrEmpty(creator.getUserId())) {
            creator.setUserId(currentSubject.getUserId());
        }
        if (creator.getCreated() == null) {
            creator.setCreated(System.currentTimeMillis());
        }
    }

    /**
     * Usage of a dedicated class to manage the optional resolvers
     */
    protected static class FactoryParametersResolverHolder {

        /**
         * Optional inject for the resolvers.
         */
        @com.google.inject.Inject(optional = true)
        private Set<FactoryParametersResolver> factoryParametersResolvers;

        /**
         * Provides the set of resolvers if there are some else return an empty set.
         *
         * @return a non null set
         */
        public Set<FactoryParametersResolver> getFactoryParametersResolvers() {
            if (factoryParametersResolvers != null) {
                return factoryParametersResolvers;
            } else {
                return Collections.emptySet();
            }
        }
    }

    /**
     * Checks object reference is not {@code null}
     *
     * @param object
     *         object reference to check
     * @param subject
     *         used as subject of exception message "{subject} required"
     * @throws BadRequestException
     *         when object reference is {@code null}
     */
    private void requiredNotNull(Object object, String subject) throws BadRequestException {
        if (object == null) {
            throw new BadRequestException(subject + " required");
        }
    }
}
