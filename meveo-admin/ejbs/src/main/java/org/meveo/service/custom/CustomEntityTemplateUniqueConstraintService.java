package org.meveo.service.custom;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ejb.Stateless;
import javax.persistence.NoResultException;

import org.meveo.commons.utils.QueryBuilder;
import org.meveo.model.ICustomFieldEntity;
import org.meveo.model.crm.CustomEntityTemplateUniqueConstraint;
import org.meveo.service.base.BusinessService;
import org.meveo.service.crm.impl.CustomFieldException;
import org.meveo.service.crm.impl.CustomFieldTemplateService;

@Stateless
public class CustomEntityTemplateUniqueConstraintService extends BusinessService<CustomEntityTemplateUniqueConstraint> {

    /**
     * Find a list of entity actions/scripts corresponding to a given entity
     * 
     * @param entity Entity that entity actions/scripts apply to

     * @return A map of entity actions/scripts mapped by a action code
     */
    public Map<String, CustomEntityTemplateUniqueConstraint> findByAppliesTo(ICustomFieldEntity entity) {
        try {
            return findByAppliesTo(CustomFieldTemplateService.calculateAppliesToValue(entity));

        } catch (CustomFieldException e) {
            // Its ok, handles cases when value that is part of CFT.AppliesTo calculation is not set yet on entity
            return new HashMap<>();
        }
    }

    /**
     * Find a list of entity actions/scripts corresponding to a given entity
     * 
     * @param appliesTo Entity (CFT appliesTo code) that entity actions/scripts apply to

     * @return A map of entity actions/scripts mapped by a action code
     */
    @SuppressWarnings("unchecked")
    public Map<String, CustomEntityTemplateUniqueConstraint> findByAppliesTo(String appliesTo) {

        QueryBuilder qb = new QueryBuilder(CustomEntityTemplateUniqueConstraint.class, "s", null);
        qb.addCriterion("s.appliesTo", "=", appliesTo, true);

        List<CustomEntityTemplateUniqueConstraint> actions = (List<CustomEntityTemplateUniqueConstraint>) qb.getQuery(getEntityManager()).getResultList();

        Map<String, CustomEntityTemplateUniqueConstraint> actionMap = new HashMap<>();
        for (CustomEntityTemplateUniqueConstraint action : actions) {
            actionMap.put(action.getCode(), action);
        }
        return actionMap;
    }

    /**
     * Find a specific entity action/script by a code
     *
     * @param code Entity action/script code. MUST be in a format of &lt;localCode&gt;|&lt;appliesTo&gt;
     * @param appliesTo Entity (CFT appliesTo code) that entity actions/scripts apply to

     * @return Entity action/script
     * @throws CustomFieldException An exception when AppliesTo value can not be calculated
     */
    public CustomEntityTemplateUniqueConstraint findByCodeAndAppliesTo(String code, String appliesTo) {

        QueryBuilder qb = new QueryBuilder(CustomEntityTemplateUniqueConstraint.class, "s", null);
        qb.addCriterion("s.code", "=", code, true);
        qb.addCriterion("s.appliesTo", "=", appliesTo, true);
        try {
            return (CustomEntityTemplateUniqueConstraint) qb.getQuery(getEntityManager()).getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }
}
