package org.meveo.service.custom;

import java.io.Serializable;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.meveo.admin.util.pagination.PaginationConfiguration;
import org.meveo.commons.utils.StringUtils;
import org.meveo.model.CustomFieldEntity;
import org.meveo.model.ICustomFieldEntity;
import org.meveo.model.customEntities.CustomEntityCategory;
import org.meveo.model.customEntities.CustomEntityTemplate;
import org.meveo.model.customEntities.CustomRelationshipTemplate;
import org.meveo.model.jobs.JobInstance;
import org.meveo.service.base.QueryBuilderHelper;
import org.meveo.service.job.Job;
import org.meveo.service.job.JobInstanceService;
import org.meveo.util.EntityCustomizationUtils;
import org.reflections.Reflections;

/**
 * @author Edward P. Legaspi | <czetsuya@gmail.com>
 * @lastModifiedVersion 6.4.0
 */
public class CustomizedEntityService implements Serializable {

    private static final long serialVersionUID = 4108034108745598588L;
    
    private static Set<Class<? extends ICustomFieldEntity>> cfClasses = Collections.synchronizedSet(new HashSet<>());
    private static Set<String> scannedPackages = Collections.synchronizedSet(new HashSet<>());

    @Inject
    private JobInstanceService jobInstanceService;

    @Inject
    private CustomEntityTemplateService customEntityTemplateService;

    @Inject
    private CustomRelationshipTemplateService customRelationshipTemplateService;


	/**
	 * @deprecated use method getCustomizedEntities with
	 *             {@link CustomizedEntityFilter} parameter.
	 * @param entityName
	 * @param customEntityTemplatesOnly
	 * @param includeNonManagedEntities
	 * @param includeParentClassesOnly
	 * @param sortBy
	 * @param sortOrder
	 * @return
	 */
	@Deprecated
	public List<CustomizedEntity> getCustomizedEntities(String entityName, boolean customEntityTemplatesOnly, boolean includeNonManagedEntities, boolean includeParentClassesOnly,
			final String sortBy, final String sortOrder) {

		return getCustomizedEntities(entityName, customEntityTemplatesOnly, includeNonManagedEntities, includeParentClassesOnly, sortBy, sortOrder, true);
	}
    
	/**
	 * Get a list of customized/customizable entities optionally filtering by a name
	 * and custom entities only and whether to include non-managed entities.
	 * Non-managed Entities are entities that will not be shown in the Entity
	 * Customization list page.
	 * 
	 * @deprecated use the method that uses the {@linkplain CustomizedEntityFilter} parameter.
	 * 
	 * @param entityName                Optional filter by a name
	 * @param customEntityTemplatesOnly Return custom entity templates only
	 * @param includeNonManagedEntities If true, entities that are not managed
	 *                                  through the Entity Customization list page
	 *                                  will be included - that is those that
	 *                                  have @CustomFieldEntity(isManualyManaged=true)
	 * @param sortBy                    Sort by. Valid values are: "description" or
	 *                                  null to sort by entity name
	 * @param sortOrder                 Sort order. Valid values are "DESCENDING" or
	 *                                  "ASCENDING". By default will sort in
	 *                                  Ascending order.
	 * @param includeParentClassesOnly  true if including parent classes.
	 * @return A list of customized/customizable entities
	 */
    @Deprecated
	public List<CustomizedEntity> getCustomizedEntities(String entityName, boolean customEntityTemplatesOnly, boolean includeNonManagedEntities, boolean includeParentClassesOnly,
			final String sortBy, final String sortOrder, boolean includeRelationships) {

		CustomizedEntityFilter filter = new CustomizedEntityFilter();
		filter.setEntityName(entityName);
		filter.setCustomEntityTemplatesOnly(customEntityTemplatesOnly);
		filter.setIncludeNonManagedEntities(includeNonManagedEntities);
		filter.setIncludeParentClassesOnly(includeParentClassesOnly);
		filter.setSortBy(sortBy);
		filter.setSortOrder(sortOrder);
		filter.setIncludeRelationships(includeRelationships);
		filter.setPrimitiveEntity("0");

		return getCustomizedEntities(filter);
	}
    
	/**
	 * @deprecated use method getCustomizedEntities with
	 *             {@link CustomizedEntityFilter} parameter.
	 * @param entityName
	 * @param cecId
	 * @param sortBy
	 * @param sortOrder
	 * @return
	 */
	public List<CustomizedEntity> getCustomizedEntities(String entityName, Long cecId, final String sortBy, final String sortOrder) {

		CustomizedEntityFilter filter = new CustomizedEntityFilter();
		filter.setEntityName(entityName);
		filter.setCecId(cecId);
		filter.setSortBy(sortBy);
		filter.setSortOrder(sortOrder);
		filter.setPrimitiveEntity("0");

		return getCustomizedEntities(filter);
	}
    
	/**
	 * Get a list of customized/customizable entities optionally filtering by a name
	 * and custom entities only and whether to include non-managed entities.
	 * Non-managed Entities are entities that will not be shown in the Entity
	 * Customization list page.
	 * 
	 * @param filter set of filter to be applied to the list of CETs.
	 * @return filtered list of {@link CustomizedEntity}
	 */
	public List<CustomizedEntity> getCustomizedEntities(CustomizedEntityFilter filter) {

		if (filter.getCecId() != null) {
			List<CustomizedEntity> entities = new ArrayList<>();
			entities.addAll(searchCustomEntityTemplates(filter.getEntityName(), filter.getCecId(), filter.getModuleCode()));
			Collections.sort(entities, sortEntitiesBy(filter.getSortBy(), filter.getSortOrder()));

			return entities;

		} else {
			List<CustomizedEntity> entities = new ArrayList<>();

			if (filter.getEntityName() != null) {
				filter.setEntityName(filter.getEntityName().toLowerCase());
			}

			if (!filter.isCustomEntityTemplatesOnly()) {
				entities.addAll(searchAllCustomFieldEntities(filter.getEntityName(), filter.isIncludeNonManagedEntities(), filter.isIncludeParentClassesOnly()));
				entities.addAll(searchJobs(filter.getEntityName()));
			}

			entities.addAll(searchCustomEntityTemplates(filter.getEntityName(), filter.getPrimitiveEntity(), filter.getModuleCode()));

			if (filter.isIncludeRelationships()) {
				entities.addAll(searchCustomRelationshipTemplates(filter.getEntityName()));
			}

			Collections.sort(entities, sortEntitiesBy(filter.getSortBy(), filter.getSortOrder()));

			return entities;
		}
	}

	/**
	 * Searches all custom field entities.
	 *
	 * @param entityName                Optional filter by a name
	 * @param includeNonManagedEntities If true, will include all entries including
	 *                                  those set not to appear in the Custom
	 *                                  Entities list.
	 * @param includeParentClassesOnly  Include only those classes that
	 *                                  have @CustomFieldEntity annotation directly
	 *                                  on them. E.g. Will not include all
	 *                                  AccountOperation subclasses.
	 * @return A list of customized/customizable entities.
	 */
    private List<CustomizedEntity> searchAllCustomFieldEntities(final String entityName, final boolean includeNonManagedEntities, boolean includeParentClassesOnly) {
    	
    	List<CustomizedEntity> entities = new ArrayList<>();
        
        initCfClasses();
        
        // Find standard entities that implement ICustomFieldEntity interface except JobInstance
        CustomFieldEntity annotation = null;
        for (Class<? extends ICustomFieldEntity> cfClass : cfClasses) {

            if (includeParentClassesOnly) {
                annotation = cfClass.getDeclaredAnnotation(CustomFieldEntity.class);
                
            } else {
                annotation = cfClass.getAnnotation(CustomFieldEntity.class);
            }

            boolean isSkipped = annotation == null 
								|| JobInstance.class.isAssignableFrom(cfClass) 
								|| Modifier.isAbstract(cfClass.getModifiers())
								|| (entityName != null && !cfClass.getSimpleName().toLowerCase().contains(entityName.toLowerCase()))
								|| (!includeNonManagedEntities && !annotation.isManuallyManaged());

            if (isSkipped) {
                continue;
            }

            entities.add(new CustomizedEntity(cfClass));
        }
        return entities;
    }

	/**
	 * Searches all custom entity templates.
	 *
	 * @param entityName      Optional filter by a name
	 * @param primitiveEntity if 1, only primitive entities will be displayed
	 *                        (entities that are store in Neo4J with
	 *                        "primitiveEntity=true"), 2 otherwise only non
	 *                        primitive entities will be displayed (neo4jStorage =
	 *                        null or primitiveEntity= false), 0 is default
	 * 
	 * @return A list of custom entity templates.
	 */
	private List<CustomizedEntity> searchCustomEntityTemplates(String entityName, String primitiveEntity, String module) {
		List<CustomizedEntity> entities = new ArrayList<>();
		List<CustomEntityTemplate> customEntityTemplates;

		PaginationConfiguration paginationConfig = new PaginationConfiguration();
		Map<String, Object> filters = new HashMap<>();

		// 0 is default = no filter
		if (primitiveEntity.equals("1")) {
			// Neo4J in availableStorages with primitiveEntity=true
			filters.put("neo4JStorageConfiguration", QueryBuilderHelper.SEARCH_IS_NOT_NULL);
			filters.put("neo4JStorageConfiguration.primitiveEntity", true);

		} else if (primitiveEntity.equals("2")) {
			// non primitive entities will be displayed (neo4jStorage=null or
			// primitiveEntity=false)
			filters.put(QueryBuilderHelper.SEARCH_SQL, "(a.neo4JStorageConfiguration IS NULL OR a.neo4JStorageConfiguration.primitiveEntity=false)");
		}

		if (!(entityName == null || CustomEntityTemplate.class.getSimpleName().toLowerCase().contains(entityName))) {
			filters.put(QueryBuilderHelper.SEARCH_WILDCARD_OR.concat(" code"), entityName);
		}

		paginationConfig.setFilters(filters);
		
		customEntityTemplates = customEntityTemplateService.list(paginationConfig);

		CustomEntityCategory cec = null;
		for (CustomEntityTemplate customEntityTemplate : customEntityTemplates) {
			cec = customEntityTemplate.getCustomEntityCategory();
			String belongingModule = customEntityTemplateService.findModuleOf(customEntityTemplate).getCode();
			if (module == null || module.equals("Meveo") || module.equals(belongingModule) ) {
			entities.add(
					new CustomizedEntity(customEntityTemplate.getCode(), CustomEntityTemplate.class, customEntityTemplate.getId(), customEntityTemplate.getDescription(), cec));
				}
		}

		return entities;
	}
    
	private List<CustomizedEntity> searchCustomEntityTemplates(String entityName, Long cecId, String module) {
		List<CustomizedEntity> entities = new ArrayList<>();
		List<CustomEntityTemplate> customEntityTemplates = null;
		if (entityName == null || CustomEntityTemplate.class.getSimpleName().toLowerCase().contains(entityName)) {
			customEntityTemplates = customEntityTemplateService.list((Boolean) null);
		} else if (entityName != null) {
			customEntityTemplates = customEntityTemplateService.findByCodeLike(entityName);
		}
		CustomEntityCategory cec = null;
		for (CustomEntityTemplate customEntityTemplate : customEntityTemplates) {
			cec = customEntityTemplate.getCustomEntityCategory();
			String belongingModule = customEntityTemplateService.findModuleOf(customEntityTemplate).getCode();
			if (cec != null && cec.getId().equals(cecId)
				&& (module == null || module.equals("Meveo") || module.equals(belongingModule)) ) {
				entities.add(new CustomizedEntity(customEntityTemplate.getCode(), CustomEntityTemplate.class, customEntityTemplate.getId(), customEntityTemplate.getDescription(), cec));
			}
		}
		return entities;
	}
    
    /**
     * Searches all custom entity templates.
     *
     * @param entityName Optional filter by a name
     * 
     * @return A list of custom entity templates.
     */
    private List<CustomizedEntity> searchCustomRelationshipTemplates(String entityName) {
        List<CustomizedEntity> entities = new ArrayList<>();
        List<CustomRelationshipTemplate> crt = null;
        if (entityName == null || CustomRelationshipTemplate.class.getSimpleName().toLowerCase().contains(entityName)) {
            crt = customRelationshipTemplateService.list();
        } else if (entityName != null) {
            crt = customRelationshipTemplateService.findByCodeLike(entityName);
        }

        for (CustomRelationshipTemplate customEntityTemplate : crt) {
            entities.add(new CustomizedEntity(customEntityTemplate.getCode(), CustomRelationshipTemplate.class, customEntityTemplate.getId(), customEntityTemplate.getDescription()));
        }
        return entities;
    }

    /**
     * Searches all jobs.
     *
     * @param entityName Optional filter by a name
     * @return A list of jobs.
     */
    private List<CustomizedEntity> searchJobs(String entityName) {
        List<CustomizedEntity> jobs = new ArrayList<>();
        for (Job job : jobInstanceService.getJobs()) {

            if (job.getCustomFields() != null && (entityName == null || (entityName != null && job.getClass().getSimpleName().toLowerCase().contains(entityName)))) {
                jobs.add(new CustomizedEntity(job.getClass()));
            }
        }
        return jobs;
    }

    /**
     * The comparator used to sort customized entities.
     * 
     * @param sortBy Sort by. Valid values are: "description" or null to sort by entity name
     * @param sortOrder Sort order. Valid values are "DESCENDING" or "ASCENDING". By default will sort in Ascending order.
     * @return The customized entity comparator instance.
     */
    private Comparator<CustomizedEntity> sortEntitiesBy(final String sortBy, final String sortOrder) {
        return new Comparator<CustomizedEntity>() {

            @Override
            public int compare(CustomizedEntity o1, CustomizedEntity o2) {
                int order = 1;
                if ("DESCENDING".equalsIgnoreCase(sortOrder)) {
                    order = -1;
                }
				if ("description".equals(sortBy)) {
					return StringUtils.compare(o1.getDescription(), o2.getDescription()) * order;

				} else if ("customEntityCategory.code".equals(sortBy)) {
					CustomEntityCategory c1 = o1.getCustomEntityCategory();
					CustomEntityCategory c2 = o2.getCustomEntityCategory();
					if (c1 == null && c2 == null) {
						int o = StringUtils.compare(o1.getClassnameToDisplayHuman(), o2.getClassnameToDisplayHuman());
						return o * order;
					} else if (c1 != null && c2 == null) {
						return 1 * order;
					} else if (c1 == null && c2 != null) {
						return -1 * order;
					} else {
						int c = StringUtils.compare(c1.getCode(), c2.getCode());
						if (c == 0) {
							int o = StringUtils.compare(o1.getClassnameToDisplayHuman(), o2.getClassnameToDisplayHuman());
							return o * order;
						}
						return c * order;
					}
				} else {
					return StringUtils.compare(o1.getClassnameToDisplayHuman(), o2.getClassnameToDisplayHuman()) * order;
				}
            }

        };
    }

    /**
     * Get a customized/customizable entity that matched a given appliesTo value as it is used in customFieldtemplate or EntityActionScript
     * 
     * @param appliesTo appliesTo value as it is used in customFieldtemplate or EntityActionScript
     * 
     * @return A customized/customizable entity
     */
    public CustomizedEntity getCustomizedEntity(String appliesTo) {

    	System.out.println("getCustomizedEntity: " + appliesTo);
        initCfClasses();

        for (Class<? extends ICustomFieldEntity> cfClass : cfClasses) {

            if (JobInstance.class.isAssignableFrom(cfClass) || Modifier.isAbstract(cfClass.getModifiers())) {
                continue;
            }

            if (appliesTo.equals(EntityCustomizationUtils.getAppliesTo(cfClass, null))) {
                return new CustomizedEntity(cfClass);
            }
        }

        // Find Jobs
        for (Job job : jobInstanceService.getJobs()) {
            if (appliesTo.equals(EntityCustomizationUtils.getAppliesTo(job.getClass(), null))) {
                return new CustomizedEntity(job.getClass());
            }
        }

        for (CustomEntityTemplate cet : customEntityTemplateService.list()) {
            if (appliesTo.equals(cet.getAppliesTo())) {
                return new CustomizedEntity(cet.getCode(), CustomEntityTemplate.class, cet.getId(), cet.getDescription());
            }
        }
        
        return null;
    }
    
    public void scanPackageForCfClasses(String packageStr) {
    	if(!scannedPackages.contains(packageStr)) {
	    	// Find standard entities that implement ICustomFieldEntity interface except JobInstance
	        Reflections reflections = new Reflections(packageStr);
	    	cfClasses.addAll(reflections.getSubTypesOf(ICustomFieldEntity.class));
	    	scannedPackages.add(packageStr);
    	}
    }

	private void initCfClasses() {
		scanPackageForCfClasses("org.meveo.model");
	}
}