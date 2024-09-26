package org.meveo.api;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ejb.Stateless;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.meveo.admin.exception.BusinessException;
import org.meveo.api.dto.CustomEntityTemplateDto;
import org.meveo.api.dto.CustomEntityTemplateUniqueConstraintDto;
import org.meveo.api.dto.CustomFieldTemplateDto;
import org.meveo.api.dto.EntityCustomActionDto;
import org.meveo.api.dto.EntityCustomizationDto;
import org.meveo.api.dto.module.MeveoModuleItemDto;
import org.meveo.api.dto.persistence.Neo4JStorageConfigurationDto;
import org.meveo.api.exception.EntityAlreadyExistsException;
import org.meveo.api.exception.EntityDoesNotExistsException;
import org.meveo.api.exception.InvalidParameterException;
import org.meveo.api.exception.MeveoApiException;
import org.meveo.api.exception.MissingParameterException;
import org.meveo.commons.utils.ReflectionUtils;
import org.meveo.elresolver.ELException;
import org.meveo.model.CustomFieldEntity;
import org.meveo.model.ICustomFieldEntity;
import org.meveo.model.crm.CustomEntityTemplateUniqueConstraint;
import org.meveo.model.crm.CustomFieldTemplate;
import org.meveo.model.crm.custom.CustomFieldIndexTypeEnum;
import org.meveo.model.crm.custom.CustomFieldStorageTypeEnum;
import org.meveo.model.crm.custom.CustomFieldTypeEnum;
import org.meveo.model.crm.custom.EntityCustomAction;
import org.meveo.model.customEntities.CustomEntityCategory;
import org.meveo.model.customEntities.CustomEntityTemplate;
import org.meveo.model.persistence.DBStorageType;
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.model.persistence.sql.Neo4JStorageConfiguration;
import org.meveo.model.scripts.ScriptInstance;
import org.meveo.service.admin.impl.ModuleInstallationContext;
import org.meveo.service.base.MeveoValueExpressionWrapper;
import org.meveo.service.base.local.IPersistenceService;
import org.meveo.service.crm.impl.CustomFieldTemplateService;
import org.meveo.service.custom.CustomEntityCategoryService;
import org.meveo.service.custom.CustomEntityTemplateService;
import org.meveo.service.custom.CustomTableCreatorService;
import org.meveo.service.custom.EntityCustomActionService;
import org.meveo.service.script.ScriptInstanceService;
import org.meveo.service.storage.RepositoryService;
import org.meveo.util.EntityCustomizationUtils;

/**
 * @author Andrius Karpavicius
 * @author Edward P. Legaspi | czetsuya@gmail.com
 * @author Clement Bareth
 * @version 6.10.0
 */
@Stateless
public class CustomEntityTemplateApi extends BaseCrudApi<CustomEntityTemplate, CustomEntityTemplateDto> {

    public CustomEntityTemplateApi() {
		super(CustomEntityTemplate.class, CustomEntityTemplateDto.class);
	}

	@Inject
    private CustomEntityTemplateService customEntityTemplateService;

    @Inject
    private CustomFieldTemplateApi customFieldTemplateApi;

    @Inject
    private CustomFieldTemplateService customFieldTemplateService;

    @Inject
    private EntityCustomActionApi entityCustomActionApi;
    
    @Inject
    private EntityCustomActionService entityCustomActionService;
    
    @Inject
    private ScriptInstanceService scriptInstanceService;

    @Inject
    private CustomEntityCategoryService customEntityCategoryService;
    
    @Inject
    private Instance<CustomTableCreatorService> customTableCreatorService;
    
    @Inject
    private RepositoryService repositoryService;
    
    @Inject
    private ModuleInstallationContext moduleInstallationContext;
    
    @Override
	public int compareDtos(CustomEntityTemplateDto obj1, CustomEntityTemplateDto obj2, List<MeveoModuleItemDto> dtos) {
    	Map<String, CustomEntityTemplateDto> cetDtos = new HashMap<>();
    	dtos.stream()
    			.filter(dto -> dto.getDtoClassName().equals(CustomEntityTemplateDto.class.getName()))
    			.map(dto -> JacksonUtil.convert(dto.getDtoData(), CustomEntityTemplateDto.class))
    			.forEach(dto -> cetDtos.put(dto.getCode(), dto));
    	
		return countAncestors(obj1, cetDtos) - countAncestors(obj2, cetDtos);
	}
    
    private int countAncestors(CustomEntityTemplateDto cet, Map<String, CustomEntityTemplateDto> dtos) {
    	int count = 0;
    	
    	// Retrieve child parent's in the dtos
    	CustomEntityTemplateDto iteratbleCet = cet;
    	while (iteratbleCet != null && iteratbleCet.getSuperTemplate() != null) {
			count ++;
			iteratbleCet = dtos.get(iteratbleCet.getSuperTemplate());
    	}
    	
    	return count;
    }
    
	public CustomEntityTemplate create(CustomEntityTemplateDto dto) throws MeveoApiException, BusinessException {

        if (StringUtils.isBlank(dto.getCode())) {
            missingParameters.add("code");
        }
        if (StringUtils.isBlank(dto.getName())) {
            missingParameters.add("name");
        }

        handleMissingParameters();

        if (customEntityTemplateService.exists(dto.getCode())) {
            throw new EntityAlreadyExistsException(CustomEntityTemplate.class, dto.getCode());
        }
        
        checkPrimitiveEntity(dto);
        
        boolean storeAsTable = false;
		if (dto.getSqlStorageConfiguration() != null) {
			storeAsTable = dto.getSqlStorageConfiguration().isStoreAsTable();
			// Validate field types for custom table
			if (storeAsTable && dto.getFields() != null) {
				int pos = 0;
				for (CustomFieldTemplateDto cftDto : dto.getFields()) {

					// Default to 'Index but not analyze storage', 'single' storage type and
					// sequential field position for custom tables
					if (cftDto.getIndexType() == null) {
						cftDto.setIndexType(CustomFieldIndexTypeEnum.INDEX_NOT_ANALYZE);
					}
					if (cftDto.getStorageType() == null) {
						cftDto.setStorageType(CustomFieldStorageTypeEnum.SINGLE);
					}
					if (cftDto.getGuiPosition() == null) {
						cftDto.setGuiPosition("tab:" + dto.getName() + ":0;field:" + pos);
						pos++;
					}

					if (cftDto.isVersionable() != null && cftDto.isVersionable()) {
						throw new InvalidParameterException("Custom table supports only unversioned data");
					}
				}
			}
		}

    	boolean hasReferenceJpaEntity = hasReferenceJpaEntity(dto);
        CustomEntityTemplate cet = fromDTO(dto, null);
        
        // Override repositories on module installation
        if (moduleInstallationContext.isActive()) {
        	cet.setRepositories(new ArrayList<>());
        	cet.getRepositories().addAll(moduleInstallationContext.getRepositories());
        }
        
        // Prevent trigger of events in OntologyObserver
        cet.setInDraft(true);
        
        cet.setHasReferenceJpaEntity(hasReferenceJpaEntity);

        setSuperTemplate(dto, cet);
        
        try {
        	
			if (dto.getCustomEntityCategoryCode() != null) {
				if (!customEntityCategoryService.exists(dto.getCustomEntityCategoryCode())) {
					CustomEntityCategory customEntityCategory = new CustomEntityCategory();
					customEntityCategory.setCode(dto.getCustomEntityCategoryCode());
					customEntityCategory.setName(dto.getCustomEntityCategoryCode());
					customEntityCategoryService.create(customEntityCategory);
					cet.setCustomEntityCategory(customEntityCategory);
				} else {
					cet.setCustomEntityCategory(customEntityCategoryService.findByCodeLazy(dto.getCustomEntityCategoryCode()));
				}
			}
			
			customEntityTemplateService.create(cet);

	        if (dto.getFields() != null) {
	            for (CustomFieldTemplateDto cftDto : dto.getFields()) {
	            	cftDto.setHasReferenceJpaEntity(hasReferenceJpaEntity);
	            	cftDto.setInDraft(true);
	                customFieldTemplateApi.createOrUpdate(cftDto, cet.getAppliesTo());
	            }
	        }
	
	        if (dto.getActions() != null) {
	            for (EntityCustomActionDto actionDto : dto.getActions()) {
	                entityCustomActionApi.createOrUpdate(actionDto, cet.getAppliesTo());
	            }
	        }
        
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
            var message = e.getMessage();
			if (message != null && message.endsWith("is a PostgresQL reserved keyword")) {
                throw new IllegalArgumentException(e.getMessage());
            } else {
        		throw new BusinessException(e);
            }
		}

        return cet;
    }

    private void setSuperTemplate(CustomEntityTemplateDto dto, CustomEntityTemplate cet) {
        if(dto.getSuperTemplate() != null){
            CustomEntityTemplate superTemplate = customEntityTemplateService.findByCode(dto.getSuperTemplate());
            if(superTemplate == null) {
            	throw new IllegalArgumentException("Can't set super template : cet with code " + dto.getSuperTemplate() + " does not exists");
            }
            cet.setSuperTemplate(superTemplate);
        }
    }

    public CustomEntityTemplate updateEntityTemplate(CustomEntityTemplateDto dto, boolean withData) throws MeveoApiException, BusinessException {

        checkPrimitiveEntity(dto);

        if (StringUtils.isBlank(dto.getCode())) {
            missingParameters.add("code");
        }
        if (StringUtils.isBlank(dto.getName())) {
            missingParameters.add("name");
        }

        handleMissingParameters();

        CustomEntityTemplate cet = customEntityTemplateService.findByCode(dto.getCode());
        if (cet == null) {
            throw new EntityDoesNotExistsException(CustomEntityTemplate.class, dto.getCode());
        }
        boolean hasReferenceJpaEntity = hasReferenceJpaEntity(dto);
        cet.setHasReferenceJpaEntity(hasReferenceJpaEntity);
        
        // Validate field types for custom table
        if (cet.getSqlStorageConfiguration() != null && cet.getSqlStorageConfiguration().isStoreAsTable() && dto.getFields() != null) {
            int pos = 0;
            for (CustomFieldTemplateDto cftDto : dto.getFields()) {

                // Default to 'Index but not analyze storage' and 'single' storeage type for custom tables
                if (cftDto.getIndexType() == null) {
                    cftDto.setIndexType(CustomFieldIndexTypeEnum.INDEX_NOT_ANALYZE);
                }

                if (cftDto.getStorageType() == null) {
                    cftDto.setStorageType(CustomFieldStorageTypeEnum.SINGLE);
                }
                if (cftDto.getGuiPosition() == null) {
                    cftDto.setGuiPosition("tab:" + dto.getName() + ":0;field:" + pos);
                    pos++;
                }

                if (cftDto.isVersionable() != null && cftDto.isVersionable()) {
                    throw new InvalidParameterException("Custom table supports only unversioned data");
                }
            }
        }
        
        var sqlStorageAddition = !cet.storedIn(DBStorageType.SQL) && 
        		dto.getAvailableStorages() != null &&
        		dto.getAvailableStorages().contains(DBStorageType.SQL);

        setSuperTemplate(dto, cet);

        cet = fromDTO(dto, cet);
        
        
        // If template becomes stored in sql, create the table
        if(sqlStorageAddition) {
        	customTableCreatorService.get().createTable(cet);
        }

        try {
			if (dto.getCustomEntityCategoryCode() != null) {
				if (StringUtils.isBlank(dto.getCustomEntityCategoryCode())) {
					cet.setCustomEntityCategory(null);
	
				} else {
					CustomEntityCategory customEntityCategory = customEntityCategoryService.findByCode(dto.getCustomEntityCategoryCode());
					if (customEntityCategory == null) {
						customEntityCategory = new CustomEntityCategory();
						customEntityCategory.setCode(dto.getCustomEntityCategoryCode());
						customEntityCategory.setName(dto.getCustomEntityCategoryCode());
						customEntityCategoryService.create(customEntityCategory);
					}
						
					cet.setCustomEntityCategory(customEntityCategory);
				}
			}
	
			cet = customEntityTemplateService.update(cet);
	    
        } catch (Exception e) {
        	
        	throw e;
        }

        return cet;
    }

    public void removeEntityTemplate(String code) throws EntityDoesNotExistsException, MissingParameterException, BusinessException {
    	removeEntityTemplate(code,false);
    }
    
    public void removeEntityTemplate(String code, boolean withData) throws EntityDoesNotExistsException, MissingParameterException, BusinessException {
        if (StringUtils.isBlank(code)) {
            missingParameters.add("customEntityTemplateCode");
        }

        handleMissingParameters();

        CustomEntityTemplate cet = customEntityTemplateService.findByCode(code);
        if (cet != null) {
            // Related custom field templates will be removed along with CET
            customEntityTemplateService.remove(cet);
            
            if(withData) {
    	    	customEntityTemplateService.removeData(cet);
            }
        }
    }
    
	@Override
	public void remove(CustomEntityTemplateDto dto) throws MeveoApiException, BusinessException {
		try { 
			this.removeEntityTemplate(dto.getCode(), false);
		} catch (EntityDoesNotExistsException e) {
		}
	}

    /* (non-Javadoc)
     * @see org.meveo.api.ApiService#find(java.lang.String)
     */
    @Override
    public CustomEntityTemplateDto find(String code) throws MeveoApiException {
        if (StringUtils.isBlank(code)) {
            missingParameters.add("customEntityTemplateCode");
        }

        handleMissingParameters();

        CustomEntityTemplate cet = customEntityTemplateService.findByCode(code);

        if (cet == null) {
            throw new EntityDoesNotExistsException(CustomEntityTemplate.class, code);
        }
        Map<String, CustomFieldTemplate> cetFields = customFieldTemplateService.findByAppliesTo(cet.getAppliesTo());

        Map<String, EntityCustomAction> cetActions = entityCustomActionService.findByAppliesTo(cet.getAppliesTo());


        return toDTO(cet, cetFields.values(), cetActions.values());
    }
    
    @Override
    public CustomEntityTemplate createOrUpdate(CustomEntityTemplateDto postData) throws MeveoApiException, BusinessException {
        if (!customEntityTemplateService.exists(postData.getCode())) {
            return create(postData);
        } else {
            return updateEntityTemplate(postData, false);
        }
    }

    public List<CustomEntityTemplateDto> listCustomEntityTemplates(String code) {

        List<CustomEntityTemplate> cets;
        if (StringUtils.isBlank(code)) {
            cets = customEntityTemplateService.list();
        } else {
            cets = customEntityTemplateService.findByCodeLike(code);
        }

        List<CustomEntityTemplateDto> cetDtos = new ArrayList<>();

        for (CustomEntityTemplate cet : cets) {

            Map<String, CustomFieldTemplate> cetFields = customFieldTemplateService.findByAppliesTo(cet.getAppliesTo());
            Map<String, EntityCustomAction> cetActions = entityCustomActionService.findByAppliesTo(cet.getAppliesTo());

            cetDtos.add(toDTO(cet, cetFields.values(), cetActions.values()));
        }

        return cetDtos;
    }

    @SuppressWarnings("rawtypes")
    public void customizeEntity(EntityCustomizationDto dto) throws MeveoApiException, BusinessException {

        if (StringUtils.isBlank(dto.getClassname())) {
            missingParameters.add("className");
        }

        handleMissingParameters();

        Class clazz;
        try {
            clazz = Class.forName(dto.getClassname());
        } catch (ClassNotFoundException e) {
            throw new EntityDoesNotExistsException("Customizable entity of class " + dto.getClassname() + " not found");
        }

        String appliesTo = EntityCustomizationUtils.getAppliesTo(clazz, null);

        synchronizeCustomFieldsAndActions(appliesTo, dto.getFields(), dto.getActions(), false);
    }

    private void synchronizeCustomFieldsAndActions(String appliesTo, List<CustomFieldTemplateDto> fields, List<EntityCustomActionDto> actions, boolean withData)
            throws MeveoApiException, BusinessException {

        Map<String, CustomFieldTemplate> cetFields = customFieldTemplateService.findByAppliesToNoCache(appliesTo);

        // Create, update or remove fields as necessary
        List<CustomFieldTemplate> cftsToRemove = new ArrayList<>();
        if (fields != null && !fields.isEmpty()) {

            for (CustomFieldTemplate cft : cetFields.values()) {
                boolean found = false;
                for (CustomFieldTemplateDto cftDto : fields) {
                    if (cftDto.getCode().equals(cft.getCode())) {
                        found = true;
                        break;
                    }
                }

                // Old field is no longer needed. Remove by id, as CFT might come detached from cache
                if (!found) {
                    cftsToRemove.add(cft);
                }
            }
            // Update or create custom field templates
            for (CustomFieldTemplateDto cftDto : fields) {
                customFieldTemplateApi.createOrUpdate(cftDto, appliesTo);
            }

        } else {
            cftsToRemove.addAll(cetFields.values());
        }

        for (CustomFieldTemplate cft : cftsToRemove) {
            customFieldTemplateService.remove(cft, withData);
        }

        Map<String, EntityCustomAction> cetActions = entityCustomActionService.findByAppliesTo(appliesTo);

        // Create, update or remove fields as necessary
        List<EntityCustomAction> actionsToRemove = new ArrayList<>();
        if (actions != null && !actions.isEmpty()) {

            for (EntityCustomAction action : cetActions.values()) {
                boolean found = false;
                for (EntityCustomActionDto actionDto : actions) {
                    if (actionDto.getCode().equals(action.getCode())) {
                        found = true;
                        break;
                    }
                }

                // Old action is no longer needed. Remove by id, as Action might come detached from cache
                if (!found) {
                    actionsToRemove.add(action);
                }
            }
            // Update or create custom field templates
            for (EntityCustomActionDto actionDto : actions) {
                entityCustomActionApi.createOrUpdate(actionDto, appliesTo);
            }

        } else {
            actionsToRemove.addAll(cetActions.values());
        }

        for (EntityCustomAction action : actionsToRemove) {
            entityCustomActionService.remove(action.getId());
        }
    }

    @SuppressWarnings("rawtypes")
    public EntityCustomizationDto findEntityCustomizations(String customizedEntityClass) throws EntityDoesNotExistsException, MissingParameterException {
        if (StringUtils.isBlank(customizedEntityClass)) {
            missingParameters.add("customizedEntityClass");
        }

        handleMissingParameters();

        Class clazz;
        try {
            clazz = Class.forName(customizedEntityClass);
        } catch (ClassNotFoundException e) {
            throw new EntityDoesNotExistsException("Customizable entity of class " + customizedEntityClass + " not found");
        }

        String appliesTo = EntityCustomizationUtils.getAppliesTo(clazz, null);

        Map<String, CustomFieldTemplate> cetFields = customFieldTemplateService.findByAppliesTo(appliesTo);

        Map<String, EntityCustomAction> cetActions = entityCustomActionService.findByAppliesTo(appliesTo);

        return EntityCustomizationDto.toDTO(clazz, cetFields.values(), cetActions.values());
    }
    
    /**
     * Finds an entity that match the given criterion. Evaluates applicableEL on custom fields and actions of the entity, if false it will not be included in the resulting object.
     * 
     * @param appliesTo type of entity
     * @param entityCode code of the entity
     * @return an object with a list of custom fields and actions
     * @throws MissingParameterException when there is a missing parameter
     */
	public EntityCustomizationDto listELFiltered(String appliesTo, String entityCode)
            throws MissingParameterException, ELException {
        new EntityCustomizationDto();
        EntityCustomizationDto result = new EntityCustomizationDto();

		if (StringUtils.isBlank(appliesTo)) {
			missingParameters.add("appliesTo");
		}
		if (StringUtils.isBlank(entityCode)) {
			missingParameters.add("entityCode");
		}

		handleMissingParameters();

		@SuppressWarnings("rawtypes")
		Class entityClass = null;
		// get all the class annotated with customFieldEntity
		Set<Class<?>> cfClasses = ReflectionUtils.getClassesAnnotatedWith(CustomFieldEntity.class, "org.meveo.model");
		for (Class<?> clazz : cfClasses) {
			// check if appliesTo match, eg OFFER
			if (appliesTo.equals(clazz.getAnnotation(CustomFieldEntity.class).cftCodePrefix())) {
				entityClass = clazz;
				break;
			}
		}


        if (entityClass != null) {
            // search for custom field entity filtered by type and code
            ICustomFieldEntity entityInstance = customEntityTemplateService.findByClassAndCode(entityClass, entityCode);

            // custom fields that applies to an entity type, eg. OFFER
            Map<String, CustomFieldTemplate> cetFields = customFieldTemplateService.findByAppliesTo(appliesTo);
            Map<String, EntityCustomAction> caFields = entityCustomActionService.findByAppliesTo(appliesTo);
            result = EntityCustomizationDto.toDTO(entityClass, cetFields.values(), caFields.values());

            // evaluate the CFT againsts the entity
            List<CustomFieldTemplateDto> evaluatedCFTDto = new ArrayList<>();
            for (CustomFieldTemplateDto cft : result.getFields()) {
                if (MeveoValueExpressionWrapper.evaluateToBooleanOneVariable(cft.getApplicableOnEl(), "entity", entityInstance)) {
                    evaluatedCFTDto.add(cft);
                }
            }
            result.setFields(evaluatedCFTDto);

            // evaluate the CA againsts the entity
            List<EntityCustomActionDto> evaluatedCA = new ArrayList<>();
            for (EntityCustomActionDto eca : result.getActions()) {
                if (MeveoValueExpressionWrapper.evaluateToBooleanOneVariable(eca.getApplicableOnEl(), "entity", entityInstance)) {
                    evaluatedCA.add(eca);
                }
            }
            result.setActions(evaluatedCA);
        }

		return result;
	}

    /**
     * Generates the response schema of the custom entity template.
     *
     * @param cetCode code of the custom entity template
     * @return response schema of the custom entity template
     */
    public Response responseJsonSchema(@NotNull String cetCode) {

        CustomEntityTemplate customEntityTemplate = customEntityTemplateService.findByCode(cetCode);
        if (customEntityTemplate == null) {
            return Response.status(404).entity("Custom entity template " + cetCode + " was not found").build();
        } else {
            try {
                String jsonSchema = customEntityTemplateService.getJsonSchemaContent(customEntityTemplate);
                
                var builder = Response.ok(jsonSchema);
                
                CacheControl cc = new CacheControl();
                cc.setMaxAge((int) Duration.ofDays(1).toSeconds());
                cc.setPrivate(true);
                builder.cacheControl(cc);
                
                return builder.build();
            } catch (Exception e) {
                return Response.status(404).entity(e.getMessage()).build();
            }
        }

    }
	
    /**
     * Convert CustomEntityTemplateDto to a CustomEntityTemplate instance. Note: does not convert custom fields that are part of DTO
     * 
     * @param dto CustomEntityTemplateDto object to convert
     * @param cetToUpdate CustomEntityTemplate to update with values from dto, or if null create a new one
     * @return A new or updated CustomEntityTemplate instance
     */
    private CustomEntityTemplate fromDTO(CustomEntityTemplateDto dto, CustomEntityTemplate cetToUpdate) {
        final CustomEntityTemplate cet = cetToUpdate != null ? cetToUpdate : new CustomEntityTemplate();
        cet.setCode(dto.getCode());
        cet.setName(dto.getName());
        cet.setDescription(dto.getDescription());
        cet.setAvailableStorages(dto.getAvailableStorages());
        cet.setAudited(dto.isAudited());
        cet.setIsEqualFn(dto.getIsEqualFn());
        
        if(dto.getCrudEventListenerScript() != null) {
        	var crudListenerScript = scriptInstanceService.findByCode(dto.getCrudEventListenerScript());
        	if(crudListenerScript != null) {
        		cet.setCrudEventListenerScript(crudListenerScript);
        	}
        }

        // sql configuration
		if (dto.getSqlStorageConfiguration() != null && cet.getSqlStorageConfiguration() != null) {
			cet.getSqlStorageConfiguration().setStoreAsTable(dto.getSqlStorageConfiguration().isStoreAsTable());
		}
        
        // Neo4J configuration if defined
        if(dto.getNeo4jStorageConfiguration() != null && dto.getAvailableStorages().contains(DBStorageType.NEO4J)) {

            if (cetToUpdate != null && cet.getNeo4JStorageConfiguration() != null) {
            	cet.getNeo4JStorageConfiguration().getUniqueConstraints().clear();
            	cet.getNeo4JStorageConfiguration().getLabels().clear();
                customEntityTemplateService.flush();
            } else if(cet.getNeo4JStorageConfiguration() == null) {
                cet.setNeo4JStorageConfiguration(new Neo4JStorageConfiguration());
            }

        	Neo4JStorageConfiguration configuration = cet.getNeo4JStorageConfiguration();
        	configuration.setPrimitiveEntity(dto.getNeo4jStorageConfiguration().isPrimitiveEntity());
        	configuration.setPrimitiveType(dto.getNeo4jStorageConfiguration().getPrimitiveType());
        	configuration.setGraphqlQueryFields(dto.getNeo4jStorageConfiguration().getGraphqlQueryFields());
        	configuration.setMutations(dto.getNeo4jStorageConfiguration().getMutations());

            if(dto.getNeo4jStorageConfiguration().getUniqueConstraints() != null){
                final List<CustomEntityTemplateUniqueConstraint> constraintList = dto.getNeo4jStorageConfiguration().getUniqueConstraints().stream()
                        .map((CustomEntityTemplateUniqueConstraintDto dto1) -> fromConstraintDto(dto1, cet))
                        .collect(Collectors.toList());

                configuration.getUniqueConstraints().addAll(constraintList);
            }

            if(dto.getNeo4jStorageConfiguration().getLabels() != null){
                configuration.getLabels().addAll(dto.getNeo4jStorageConfiguration().getLabels());
            }
        }

        if(dto.getPrePersistScripCode() != null) {
        	ScriptInstance scriptInstance = scriptInstanceService.findByCode(dto.getPrePersistScripCode());
        	cet.setPrePersistScript(scriptInstance);
        }
        
        // Parse repositories where to create the CET data
        if (cet.getRepositories() == null || cet.getRepositories().isEmpty()) {
	        if (dto.getRepositories() == null || dto.getRepositories().isEmpty()) {
	        	cet.setRepositories(List.of(repositoryService.findDefaultRepository()));
	        } else {
	        	cet.setRepositories(new ArrayList<>());
	        	dto.getRepositories().forEach(repository -> {
					var storageRepo = repositoryService.findByCode(repository);
					if (storageRepo != null) {
						cet.getRepositories().add(storageRepo);
					} else {
						throw new IllegalArgumentException("Repository " + repository + " does not exists");
					}
				});
	        }
        }

        return cet;
    }

    private CustomEntityTemplateUniqueConstraint fromConstraintDto(CustomEntityTemplateUniqueConstraintDto dto, CustomEntityTemplate cet){
        CustomEntityTemplateUniqueConstraint constraint = new CustomEntityTemplateUniqueConstraint();
        constraint.setTrustScore(dto.getTrustScore());
        constraint.setApplicableOnEl(dto.getApplicableOnEl());
        constraint.setCypherQuery(dto.getCypherQuery());
        constraint.setPosition(dto.getOrder());
        constraint.setCode(dto.getCode());
        constraint.setCustomEntityTemplate(cet);
        return constraint;
    }

    private static CustomEntityTemplateUniqueConstraintDto toConstraintDto(CustomEntityTemplateUniqueConstraint constraint){
        CustomEntityTemplateUniqueConstraintDto constraintDto = new CustomEntityTemplateUniqueConstraintDto();
        constraintDto.setTrustScore(constraint.getTrustScore());
        constraintDto.setApplicableOnEl(constraint.getApplicableOnEl());
        constraintDto.setCode(constraint.getCode());
        constraintDto.setCypherQuery(constraint.getCypherQuery());
        constraintDto.setOrder(constraint.getPosition());
        return constraintDto;
    }


    /**
     * If the CET is a primitive type :  <br>
     * - Type of primitive should not be null  <br>
     *
     * @param dto The CET dto to validate
     */
	private void checkPrimitiveEntity(CustomEntityTemplateDto dto) throws BusinessException {

		if (dto.getAvailableStorages() != null && dto.getAvailableStorages().contains(DBStorageType.NEO4J)) {
            final Neo4JStorageConfigurationDto configuration = dto.getNeo4jStorageConfiguration();
            if(configuration != null) {
                if (!configuration.isPrimitiveEntity()) {
                    return; // If not a primitive type, skip tests
                }
                if (configuration.getPrimitiveType() == null) {
                    throw new BusinessException("Primitive type must be defined");
                }
            }
		}

	}

    /**
     * Convert CustomEntityTemplate instance to CustomEntityTemplateDto object including the fields and actions.
     *
     * @param cet CustomEntityTemplate object to convert
     * @param cetFields Fields (CustomFieldTemplate) that are part of CustomEntityTemplate
     * @param cetActions Actions (EntityActionScript) available on CustomEntityTemplate
     * @return A CustomEntityTemplateDto object with fields set
     */
    private static CustomEntityTemplateDto toDTO(CustomEntityTemplate cet, Collection<CustomFieldTemplate> cetFields, Collection<EntityCustomAction> cetActions) {
        CustomEntityTemplateDto dto = new CustomEntityTemplateDto();
        dto.setCode(cet.getCode());
        dto.setName(cet.getName());
        dto.setDescription(cet.getDescription());
        dto.setAvailableStorages(cet.getAvailableStorages());
        dto.setAudited(cet.isAudited());
        
        if(cet.getSuperTemplate() != null) {
        	dto.setSuperTemplate(cet.getSuperTemplate().getCode());
        }

        if(cet.getPrePersistScript() != null) {
            dto.setPrePersistScripCode(cet.getPrePersistScript().getCode());
        }
        
        if(cet.getCrudEventListenerScript() != null) {
        	dto.setCrudEventListenerScript(cet.getCrudEventListenerScript().getCode());
        }

        if (cetFields != null) {
            List<CustomFieldTemplateDto> fields = new ArrayList<>();
            for (CustomFieldTemplate cft : cetFields) {
                fields.add(new CustomFieldTemplateDto(cft));
            }
            dto.setFields(fields);
        }

        if (cetActions != null) {
            List<EntityCustomActionDto> actions = new ArrayList<>();
            for (EntityCustomAction action : cetActions) {
                actions.add(new EntityCustomActionDto(action));
            }
            dto.setActions(actions);
        }
        
        // Sql configuration
		if (cet.getSqlStorageConfiguration() != null) {
			dto.setSqlStorageConfiguration(cet.getSqlStorageConfiguration());
		}

        // Neo4J configuration if defined
        if(cet.getNeo4JStorageConfiguration() != null) {

        	Neo4JStorageConfigurationDto neo4jConf = new Neo4JStorageConfigurationDto();
            neo4jConf.getLabels().addAll(cet.getNeo4JStorageConfiguration().getLabels());
            neo4jConf.setGraphqlQueryFields(cet.getNeo4JStorageConfiguration().getGraphqlQueryFields());
            neo4jConf.setMutations(cet.getNeo4JStorageConfiguration().getMutations());
            neo4jConf.setPrimitiveEntity(cet.getNeo4JStorageConfiguration().isPrimitiveEntity());
            neo4jConf.setPrimitiveType(cet.getNeo4JStorageConfiguration().getPrimitiveType());

			if (cet.getNeo4JStorageConfiguration().getUniqueConstraints() != null) {
				List<CustomEntityTemplateUniqueConstraintDto> constraintDtoList = cet.getNeo4JStorageConfiguration()
						.getUniqueConstraints().stream().map(CustomEntityTemplateApi::toConstraintDto)
						.collect(Collectors.toList());

				neo4jConf.setUniqueConstraints(constraintDtoList);
			}

            dto.setNeo4jStorageConfiguration(neo4jConf);
        }

        if(cet.getCustomEntityCategory() != null) {
        	dto.setCustomEntityCategoryCode(cet.getCustomEntityCategory().getCode());
        }

        return dto;
    }
    
	private boolean hasReferenceJpaEntity(CustomEntityTemplateDto cetDto) {
		if (cetDto.getFields() != null) {
			Optional<CustomFieldTemplateDto> opt = cetDto.getFields().stream()
					.filter(e -> e.getFieldType().equals(CustomFieldTypeEnum.ENTITY))
					.filter(e -> !e.getEntityClazzCetCode().equals(cetDto.getCode()))	// Exclude self-references 
					.filter(e -> customFieldTemplateService.isReferenceJpaEntity(e.getEntityClazzCetCode()))		
					.findAny();
			if (opt.isPresent()) {
				return true;
			}
		}

		return false;
	}

	@Override
	public CustomEntityTemplateDto toDto(CustomEntityTemplate entity) {
		var cfts = customFieldTemplateService.findByAppliesToNoCache(entity.getAppliesTo());
		var actions = entityCustomActionService.findByAppliesTo(entity.getAppliesTo());
		
		// Avoid lazy initialization problems
		if (entity.getCustomEntityCategory() != null) {
			entity.setCustomEntityCategory(customEntityCategoryService.findById(entity.getCustomEntityCategory().getId()));
		}
		
		return toDTO(entity, cfts.values(), actions.values());
	}

	@Override
	public CustomEntityTemplate fromDto(CustomEntityTemplateDto dto) throws MeveoApiException {
		return fromDTO(dto, null);
	}

	@Override
	public IPersistenceService<CustomEntityTemplate> getPersistenceService() {
		return customEntityTemplateService;
	}

	@Override
	public boolean exists(CustomEntityTemplateDto dto) {
		return customEntityTemplateService.findByCode(dto.getCode()) != null;
	}
}
