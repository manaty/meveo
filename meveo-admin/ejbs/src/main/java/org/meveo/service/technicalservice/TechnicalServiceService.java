/*
 * (C) Copyright 2018-2019 Webdrone SAS (https://www.webdrone.fr/) and contributors.
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
package org.meveo.service.technicalservice;

import org.meveo.api.dto.technicalservice.TechnicalServiceFilters;
import org.meveo.commons.utils.QueryBuilder;
import org.meveo.model.technicalservice.TechnicalService;
import org.meveo.service.script.ExecutableService;
import org.meveo.service.script.technicalservice.TechnicalServiceEngine;

import javax.persistence.DiscriminatorValue;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Technical service persistence service
 *
 * @author Clément Bareth
 */
public abstract class TechnicalServiceService<T extends TechnicalService>
        extends ExecutableService<T, TechnicalServiceEngine<T>> {

    private final String serviceType = getEntityClass().getAnnotation(DiscriminatorValue.class).value();

    /**
     * Retrieve the last version of the technical service with the specified name
     *
     * @param name Name of the technical service to retrieve
     * @return The last version number or empty if the technical service does not exists
     */
    public Optional<Integer> latestVersionNumber(String name) {
        String queryString = "Select max(service.serviceVersion) from org.meveo.model.technicalservice.TechnicalService service \n" +
                "where service.name = :name and service.serviceType = :service_type";
        Query q = getEntityManager().createQuery(queryString)
                .setParameter("name", name)
                .setParameter("service_type", serviceType);
        try {
            return Optional.of((Integer) q.getSingleResult());
        } catch (NoResultException ignored) {
        }
        return Optional.empty();
    }

    /**
     * Retrieve all the version of the technical services that have the specified name
     *
     * @param name Name of the technical services to retrieve
     * @return The list of technical service's version
     */
    @SuppressWarnings("unchecked")
    public List<T> findByName(String name) {
        QueryBuilder qb = new QueryBuilder(getEntityClass(), "service", null);
        qb.addCriterion("service.name", "=", name, true);
        try {
            return (List<T>) qb.getQuery(getEntityManager()).getResultList();
        } catch (NoResultException e) {
            log.warn("No Technical service by name {} found", name);
        }
        return new ArrayList<>();
    }

    /**
     * Retrieve the latest version of the technical service
     *
     * @param name Name of the technical service to retrieve
     * @return The last version of the technical service
     */
    @SuppressWarnings("unchecked")
    public Optional<T> findLatestByName(String name) {
        try {
            QueryBuilder qb = new QueryBuilder(getEntityClass(), "service", null);
            qb.addCriterion("service.name", "=", name, true);
            qb.addSql("service.serviceVersion = (select max(ci.serviceVersion) from org.meveo.model.technicalservice.TechnicalService ci where "
                    + "ci.name = service.name and ci.serviceType = service.serviceType)");
            return Optional.of((T) qb.getQuery(getEntityManager()).getSingleResult());
        } catch (NoResultException e) {
            log.warn("No Technical service by name {} found", name);
        }
        return Optional.empty();
    }

    /**
     * Retrieve a technical service based on name and version
     *
     * @param name    Name of the technical service to retrieve
     * @param version Version of the technical service to retrieve
     * @return The retrieved technical service or empty if not found
     */
    @SuppressWarnings("unchecked")
    public Optional<T> findByNameAndVersion(String name, Integer version) {
        QueryBuilder qb = new QueryBuilder(getEntityClass(), "service", null);
        qb.addCriterion("service.name", "=", name, true);
        qb.addCriterion("service.serviceVersion", "=", version, true);
        try {
            return Optional.of((T) qb.getQuery(getEntityManager()).getSingleResult());
        } catch (NoResultException e) {
            log.warn("No Technical service by name {} and version {} found", name, version);
        }
        return Optional.empty();
    }

    //TODO: Document
    public List<T> list(TechnicalServiceFilters filters) {
        QueryBuilder qb = filteredQueryBuilder(filters);
        TypedQuery<T> query = qb.getTypedQuery(getEntityManager(), getEntityClass());
        return query.getResultList();
    }

    public List<String> names(){
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<String> query = cb.createQuery(String.class);
        Root<T> root = query.from(getEntityClass());
        query.select(root.get("name"));
        query.distinct(true);
        return getEntityManager().createQuery(query).getResultList();
    }

    public List<Integer> versions(String name){
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Integer> query = cb.createQuery(Integer.class);
        Root<T> service = query.from(getEntityClass());
        query.select(service.get("serviceVersion"));
        query.where(cb.equal(service.get("name"), name));
        return getEntityManager().createQuery(query).getResultList();
    }

    public long count(TechnicalServiceFilters filters){
        QueryBuilder qb = filteredQueryBuilder(filters);
        return qb.count(getEntityManager());
    }

    @Override
    protected void afterUpdateOrCreate(T executable) {}

    @Override
    protected void validate(T executable) {}

    @Override
    protected String getCode(T executable) {
        return executable.getName() + "." + executable.getServiceVersion();
    }

    private QueryBuilder filteredQueryBuilder(TechnicalServiceFilters filters) {
        QueryBuilder qb = new QueryBuilder(getEntityClass(), "service", null);
        if(filters.getName() != null){
            qb.addCriterion("service.name", "=", filters.getName(), true);
        }else if(filters.getLikeName() != null){
            qb.addCriterion("service.name", "like", filters.getName(), true);
        }
        return qb;
    }

}
