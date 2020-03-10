package org.meveo.service.storage;

import org.meveo.commons.utils.QueryBuilder;
import org.meveo.jpa.EntityManagerWrapper;
import org.meveo.jpa.MeveoJpa;
import org.meveo.model.storage.RemoteRepository;
import org.meveo.service.base.PersistenceService;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.*;
import java.io.Serializable;

@Stateless
public class RemoteRepositoryService extends PersistenceService<RemoteRepository> implements Serializable {

    @PersistenceContext(unitName = "MeveoAdmin", type = PersistenceContextType.TRANSACTION)
    private EntityManager entityManager;

    @Inject
    @MeveoJpa
    private EntityManagerWrapper emWrapper;

    public boolean validateUniqueCode(String code) {

        String queryString = null;

        queryString = "select count(*) from RemoteRepository where lower(code)=:code";

        Query query = emWrapper.getEntityManager().createQuery(queryString).setParameter("code", code);
        long count = (Long) query.getSingleResult();
        return count == 0L;
    }

    public RemoteRepository findByCode(String code) {
        if (code == null) {
            return null;
        }
        QueryBuilder qb = new QueryBuilder(RemoteRepository.class, "c");
        qb.addCriterion("code", "=", code, false);

        try {
            return (RemoteRepository) qb.getQuery(entityManager)
            		.setParameter("code", code)
            		.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

}
