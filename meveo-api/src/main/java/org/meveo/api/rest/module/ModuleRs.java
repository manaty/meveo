/*
 * (C) Copyright 2018-2020 Webdrone SAS (https://www.webdrone.fr/) and contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * This program is not suitable for any direct or indirect application in MILITARY industry
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.meveo.api.rest.module;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import org.meveo.api.dto.ActionStatus;
import org.meveo.api.dto.module.MeveoModuleDto;
import org.meveo.api.dto.response.module.MeveoModuleDtoResponse;
import org.meveo.api.dto.response.module.MeveoModuleDtosResponse;
import org.meveo.api.rest.IBaseRs;

/**
 * JAX-RS interface for MeveoModule management
 * @author Clément Bareth
 * @lastModifiedVersion 6.3.0
 */
@Path("/module")
@Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
public interface ModuleRs extends IBaseRs {

    /**
     * Create a new meveo module
     * 
     * @param moduleDto The meveo module's data
     * @return Request processing status
     */
    @POST
    @Path("/")
    ActionStatus create(MeveoModuleDto moduleDto);

    /**
     * Update an existing Meveo module
     * 
     * @param moduleDto The Meveo module's data
     * @return Request processing status
     */
    @PUT
    @Path("/")
    ActionStatus update(MeveoModuleDto moduleDto);

    /**
     * Create new or update an existing Meveo module
     * 
     * @param moduleDto The Meveo module's data
     * @return Request processing status
     */
    @POST
    @Path("/createOrUpdate")
    ActionStatus createOrUpdate(MeveoModuleDto moduleDto);

    /**
     * Remove an existing module with a given code 
     * 
     * @param code The module's code
     * @return Request processing status
     */
    @DELETE
    @Path("/{code}")
    ActionStatus delete(@PathParam("code") String code);

    /**
     * List all Meveo's modules
     * 
     * @return A list of Meveo's modules
     */
    @GET
    @Path("/list")
    MeveoModuleDtosResponse list();

    /**
     * Install Meveo module
     * 
     * @return Request processing status
     */
    @PUT
    @Path("/install")
    ActionStatus install(MeveoModuleDto moduleDto);

    /**
     * Find a Meveo's module with a given code 
     * 
     * @param code The Meveo module's code
     */
    @GET
    @Path("/")
    MeveoModuleDtoResponse get(@QueryParam("code") String code);

    /**
     * Uninstall a Meveo's module with a given code
     *
     * @param code   The Meveo module's code
     * @param remove Whether to remove elements
     * @return Request processing status
     */
    @GET
    @Path("/uninstall")
    ActionStatus uninstall(@QueryParam("code") String code, @QueryParam("remove") @DefaultValue("false") boolean remove);

    /**
     * Enable a Meveo's module with a given code
     * 
     * @param code The Meveo module's code
     * @return Request processing status
     */
    @GET
    @Path("/enable")
    ActionStatus enable(@QueryParam("code") String code);

    /**
     * Disable a Meveo's module with a given code
     * 
     * @param code The Meveo module's code
     * @return Request processing status
     */
    @GET
    @Path("/disable")
    ActionStatus disable(@QueryParam("code") String code);
}