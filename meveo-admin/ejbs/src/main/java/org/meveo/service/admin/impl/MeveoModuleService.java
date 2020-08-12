/*
 * (C) Copyright 2018-2020 Webdrone SAS (https://www.webdrone.fr/) and contributors.
 * (C) Copyright 2015-2016 Opencell SAS (http://opencellsoft.com/) and contributors.
 * (C) Copyright 2009-2014 Manaty SARL (http://manaty.net/) and contributors.
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
package org.meveo.service.admin.impl;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.ejb.Stateless;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.persistence.NoResultException;
import javax.persistence.NonUniqueResultException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.ws.rs.core.Response;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.httpclient.util.HttpURLConnection;
import org.apache.commons.io.FileUtils;
import org.hibernate.Hibernate;
import org.hibernate.LockOptions;
import org.hibernate.NaturalIdLoadAccess;
import org.hibernate.proxy.HibernateProxy;
import org.jboss.resteasy.client.jaxrs.BasicAuthentication;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.meveo.admin.exception.BusinessException;
import org.meveo.admin.util.pagination.PaginationConfiguration;
import org.meveo.api.ApiService;
import org.meveo.api.dto.ActionStatus;
import org.meveo.api.dto.ActionStatusEnum;
import org.meveo.api.dto.BusinessEntityDto;
import org.meveo.api.dto.module.MeveoModuleDto;
import org.meveo.api.dto.module.ModuleReleaseDto;
import org.meveo.api.dto.response.module.MeveoModuleDtosResponse;
import org.meveo.api.exception.EntityDoesNotExistsException;
import org.meveo.commons.utils.EjbUtils;
import org.meveo.commons.utils.QueryBuilder;
import org.meveo.commons.utils.ReflectionUtils;
import org.meveo.commons.utils.StringUtils;
import org.meveo.event.qualifier.Created;
import org.meveo.event.qualifier.Removed;
import org.meveo.export.RemoteAuthenticationException;
import org.meveo.model.BusinessEntity;
import org.meveo.model.ModuleItem;
import org.meveo.model.communication.MeveoInstance;
import org.meveo.model.crm.CustomFieldTemplate;
import org.meveo.model.customEntities.CustomEntityTemplate;
import org.meveo.model.jobs.JobExecutionResultImpl;
import org.meveo.model.jobs.JobInstance;
import org.meveo.model.module.MeveoModule;
import org.meveo.model.module.MeveoModuleDependency;
import org.meveo.model.module.MeveoModuleItem;
import org.meveo.model.module.MeveoModulePatch;
import org.meveo.model.module.ModuleRelease;
import org.meveo.model.module.ModuleReleaseItem;
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.model.scripts.Function;
import org.meveo.model.technicalservice.endpoint.Endpoint;
import org.meveo.service.base.PersistenceService;
import org.meveo.service.communication.impl.MeveoInstanceService;
import org.meveo.service.job.JobExecutionService;
import org.meveo.service.job.JobInstanceService;
import org.meveo.service.script.ConcreteFunctionService;
import org.meveo.service.script.module.ModuleScriptInterface;
import org.meveo.service.script.module.ModuleScriptService;
import org.meveo.util.EntityCustomizationUtils;

/**
 * EJB for managing MeveoModule entities
 * @author Clément Bareth
 * @author Edward P. Legaspi | czetsuya@gmail.com
 * @lastModifiedVersion 6.10
 */
@Stateless
public class MeveoModuleService extends GenericModuleService<MeveoModule> {

    @Inject
    private ModuleScriptService moduleScriptService;

    @Inject
    private MeveoInstanceService meveoInstanceService;
    
    @Inject
    private MeveoModuleItemService meveoModuleItemService;

    @Inject
    private JobInstanceService jobInstanceService;

    @Inject
    private JobExecutionService jobExecutionService;

    /**
     * Add missing dependencies of each module item
     * 
     * @param moduleCode Code of the module to synchronize
     * @return the total number of items added to the module
     * @throws BusinessException if the module can't be updated
     */
	public int synchronizeLinkedItems(String moduleCode) throws BusinessException {
		MeveoModule meveoModule = findByCode(moduleCode, List.of("moduleItems"));
		List<MeveoModuleItem> newItems = getMissingItems(meveoModule);
		int totalCount = 0;
		
		while (!newItems.isEmpty()) {
			totalCount += newItems.size();
			meveoModule.getModuleItems().addAll(newItems);
			newItems = getMissingItems(meveoModule);
		}
		
		if(totalCount > 0) {
			meveoModule.getModuleItems().forEach(m -> m.setMeveoModule(meveoModule));
			update(meveoModule);
		}
		
		return totalCount;
	}

	/**
	 * @param MeveoModule meveoModule the module to get missing items froms
	 * @return the missing dependencies of each module item
	 */
	private List<MeveoModuleItem> getMissingItems(MeveoModule meveoModule) {
		List<MeveoModuleItem> newItems = new ArrayList<>();
		
		for (MeveoModuleItem item : meveoModule.getModuleItems()) {
			try {
				// Check if the module item has possible dependencies
				Class<?> clazz = Class.forName(item.getItemClass());
				List<Field> fields = ReflectionUtils.getAllFields(new ArrayList<>(), clazz)
						.stream()
						.filter(f -> f.getType().getAnnotation(ModuleItem.class) != null)
						.collect(Collectors.toList());
				
				// Load each dependency and add them as module item if they are not present
				if(!fields.isEmpty()) {
					NaturalIdLoadAccess<?> query = getEntityManager().
							unwrap(org.hibernate.Session.class)
							.byNaturalId(clazz)
							.with(LockOptions.READ)
							.using("code", item.getItemCode());
					
					if(item.getAppliesTo() != null) {
						query = query.using("appliesTo", item.getAppliesTo());
					}
					
					Object loadedItem = query.load();
					
					for(Field field : fields) {
						boolean canAccess = field.canAccess(loadedItem);
						field.setAccessible(true);
						BusinessEntity fieldValue = (BusinessEntity) field.get(loadedItem);
						
						if(fieldValue instanceof HibernateProxy) {
							Hibernate.initialize(fieldValue); 
							fieldValue = (BusinessEntity) ((HibernateProxy) fieldValue)
					                  .getHibernateLazyInitializer()
					                  .getImplementation();
						}
						
						if(fieldValue != null) {
							MeveoModuleItem newItem = new MeveoModuleItem(fieldValue);
							if(!meveoModule.getModuleItems().contains(newItem)) {
								newItems.add(newItem);
							}
						}
						field.setAccessible(canAccess);
					}
				}
			} catch (Exception e) {
				log.error("Cannot retrieve dependencies for module item {}", item, e);
			}
		}
		

		return newItems;
	}

    /**
     * import module from remote meveo instance.
     * 
     * @param meveoInstance meveo instance
     * @return list of meveo module
     * @throws BusinessException business exception.
     */
    public List<MeveoModuleDto> downloadModulesFromMeveoInstance(MeveoInstance meveoInstance) throws BusinessException {
        List<MeveoModuleDto> result;
        try {
            String url = "api/rest/module/list";
            String baseurl = meveoInstance.getUrl().endsWith("/") ? meveoInstance.getUrl() : meveoInstance.getUrl() + "/";
            String username = meveoInstance.getAuthUsername() != null ? meveoInstance.getAuthUsername() : "";
            String password = meveoInstance.getAuthPassword() != null ? meveoInstance.getAuthPassword() : "";
            ResteasyClient client = meveoInstanceService.getRestEasyClient();
            ResteasyWebTarget target = client.target(baseurl + url);
            BasicAuthentication basicAuthentication = new BasicAuthentication(username, password);
            target.register(basicAuthentication);

            Response response = target.request().get();
            if (response.getStatus() != HttpURLConnection.HTTP_OK) {
                if (response.getStatus() == HttpURLConnection.HTTP_UNAUTHORIZED || response.getStatus() == HttpURLConnection.HTTP_FORBIDDEN) {
                    throw new RemoteAuthenticationException("Http status " + response.getStatus() + ", info " + response.getStatusInfo().getReasonPhrase());
                } else {
                    throw new BusinessException("Http status " + response.getStatus() + ", info " + response.getStatusInfo().getReasonPhrase());
                }
            }

            MeveoModuleDtosResponse resultDto = response.readEntity(MeveoModuleDtosResponse.class);
            if (resultDto == null) {
                throw new BusinessException("No response body from meveo instance " + meveoInstance.getCode());
            }

            if (ActionStatusEnum.SUCCESS != resultDto.getActionStatus().getStatus()) {
                throw new BusinessException("Code " + resultDto.getActionStatus().getErrorCode() + ", info " + resultDto.getActionStatus().getMessage());
            }

            result = resultDto.getModules();
            if (result != null) {
                result.sort(Comparator.comparing(BusinessEntityDto::getCode));
            }

            return result;

        } catch (Exception e) {
            log.error("Failed to communicate {}. Reason {}", meveoInstance.getCode(), (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
            throw new BusinessException("Fail to communicate " + meveoInstance.getCode() + ". Error " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

	/**
	 * Publish meveo module with DTO items to remote meveo instance.
	 * 
	 * @param module        meveo module
	 * @param meveoInstance meveo instance.
	 * @throws BusinessException business exception.
	 */
	@SuppressWarnings("unchecked")
	public void publishModule2MeveoInstance(MeveoModule module, MeveoInstance meveoInstance) throws BusinessException {
		log.debug("export module {} to {}", module, meveoInstance);
		final String url = "api/rest/module/createOrUpdate";

		// check if module is installed
		if (!module.isInstalled()) {
			throw new BusinessException("Only installed modules are allowed to be publish");
		}

		try {
			ApiService<MeveoModule, MeveoModuleDto> moduleApi = (ApiService<MeveoModule, MeveoModuleDto>) EjbUtils.getServiceInterface("MeveoModuleApi");
			if (moduleApi == null) {
				throw new IllegalArgumentException("Cannot find api MeveoModuleApi bean");
			}

			MeveoModuleDto moduleDto = moduleApi.find(module.getCode());

			Response response = meveoInstanceService.publishDto2MeveoInstance(url, meveoInstance, moduleDto);
			ActionStatus actionStatus = response.readEntity(ActionStatus.class);
			if (actionStatus == null) {
				throw new BusinessException("Cannot read response status");
			}

			if (ActionStatusEnum.SUCCESS != actionStatus.getStatus()) {
				throw new BusinessException("Code " + actionStatus.getErrorCode() + ", info " + actionStatus.getMessage());
			}
		} catch (Exception e) {
			log.error("Error when export module {} to {}. Reason {}", module.getCode(), meveoInstance.getCode(),
					(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
			throw new BusinessException("Fail to communicate " + meveoInstance.getCode() + ". Error " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
		}
	}

    @SuppressWarnings("unchecked")
    public List<MeveoModuleItem> findByCodeAndItemType(String code, String className) {
        QueryBuilder qb = new QueryBuilder(MeveoModuleItem.class, "m");
        qb.addCriterion("itemCode", "=", code, true);
        qb.addCriterion("itemClass", "=", className, true);

        try {
            return (List<MeveoModuleItem>) qb.getQuery(getEntityManager()).getResultList();
        } catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Check whether the given item is a child of an other active module
     *
     * @param moduleItemCode Module item code
     * @param itemClass      Class of the module item
     * @return <code>true</code> if the given item exists in an other module
     */
    public boolean isChildOfOtherActiveModule(String moduleItemCode, String itemClass) {
        QueryBuilder qb = new QueryBuilder(MeveoModuleItem.class, "i", null, Collections.singletonList("meveoModule"));
        qb.addCriterion("i.itemCode", "=", moduleItemCode, true);
        qb.addCriterion("i.itemClass", "=", itemClass, false);
        qb.addBooleanCriterion("meveoModule.disabled", false);
        return qb.count(getEntityManager()) > 1;
    }

    @SuppressWarnings("unchecked")
    public String getRelatedModulesAsString(String itemCode, String itemClazz, String appliesTo) {
        QueryBuilder qb = new QueryBuilder(MeveoModule.class, "m", Collections.singletonList("moduleItems as i"));
        qb.addCriterion("i.itemCode", "=", itemCode, true);
        qb.addCriterion("i.itemClass", "=", itemClazz, false);
        qb.addCriterion("i.appliesTo", "=", appliesTo, false);
        List<MeveoModule> modules = qb.getQuery(getEntityManager()).getResultList();

        if (modules != null) {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (MeveoModule module : modules) {
                if (i != 0) {
                    sb.append(";");
                }
                sb.append(module.getCode());
                i++;
            }
            return sb.toString();
        }
        return null;
    }

    /**
     * Find entity by code - match the beginning of code.
     *
     * @param code Beginning of code
     * @return A list of entities which code starts with a given value
     */
    @SuppressWarnings("unchecked")
    public List<MeveoModule> findLikeWithCode(String code) {
        try {
            QueryBuilder qb = new QueryBuilder(getEntityClass(), "be");
            if (!StringUtils.isBlank(code)) {
                qb.like("be.code", code, QueryBuilder.QueryLikeStyleEnum.MATCH_ANYWHERE, false);
            }

            return (List<MeveoModule>) qb.getQuery(getEntityManager()).getResultList();

        } catch (NoResultException | NonUniqueResultException ne) {
            return null;
        }
    }

    public List<MeveoModule> list(MeveoModuleFilters filters) {
        return list(filters, "*", MeveoModule.class);
    }

    public List<String> listCodesOnly(MeveoModuleFilters filters) {
        return list(filters, "code", String.class);
    }

    private <T> List<T> list(MeveoModuleFilters filters, String projection, Class<T> returnedClass) {
    	if (projection == "*") {
			projection = "";

		} else {
			projection = "." + projection;
		}

    	StringBuilder queryBuilder = new StringBuilder("SELECT DISTINCT m" + projection + " FROM " + entityClass.getName() + " m");
        if(filters.getItemType() !=null && filters.getItemClass() != null){
            queryBuilder.append(" INNER JOIN m.moduleItems i");
        }

        Map<String, Object> parameters = new HashMap<>();

        queryBuilder.append(" WHERE 1 = 1 \n");

        if(filters.getActive() != null){
            if(filters.getActive()) {
                queryBuilder.append(" AND m.disabled = 0 ");
            } else {
                queryBuilder.append(" AND m.disabled = 1 ");
            }
        }

        if(filters.getDownloaded() != null){
            if(!filters.getDownloaded()) {
                queryBuilder.append(" AND m.moduleSource = '' OR m.moduleSource = null ");
            } else {
                queryBuilder.append(" AND m.moduleSource != null AND m.moduleSource != '' ");
            }
        }

        if(filters.getItemCode() != null && filters.getItemClass() != null){
            queryBuilder.append(" AND i.itemCode = :itemCode AND i.itemClass = :itemClass");
            parameters.put("itemCode", filters.getItemCode());
            parameters.put("itemClass", filters.getItemClass());
        }

        final TypedQuery<T> query = getEntityManager().createQuery(queryBuilder.toString(), returnedClass);
        parameters.forEach(query::setParameter);

        return query.getResultList();
    }


	/**
	 * Observer when an entity that extends a BusinessEntity is deleted which is
	 * annotated by MeveoModuleItem.
	 *
	 * @param be BusinessEntity
	 * @throws BusinessException
	 */
	public void onMeveoModuleItemDelete(@Observes @Removed BusinessEntity be) throws BusinessException {
		if (be.getClass().isAnnotationPresent(ModuleItem.class)) {
			QueryBuilder qb = new QueryBuilder(MeveoModuleItem.class, "i");
			qb = qb.addCriterion("itemCode", "=", be.getCode(), true);
			qb = qb.addCriterion("itemClass", "=", be.getClass().getName(), true);

			try {
				Long count = qb.count(getEntityManager());

				// need to do the check when uninstalling
				if (count > 0) {
					Query query = getEntityManager().createNamedQuery("MeveoModuleItem.delete");
					query = query.setParameter("itemCode", be.getCode());
					query = query.setParameter("itemClass", be.getClass().getName());
					query.executeUpdate();
				}
			} catch (NoResultException e) {

			}
		}
	}

	@SuppressWarnings("unchecked")
	public void onCftCreated(@Observes @Created CustomFieldTemplate cft) throws BusinessException {
		String cetCode = EntityCustomizationUtils.getEntityCode(cft.getAppliesTo());
		Query q = getEntityManager().createNamedQuery("MeveoModuleItem.synchronizeCftCreate");
		q = q.setParameter("itemCode", cetCode);
		q = q.setParameter("itemClass", CustomEntityTemplate.class.getName());

		try {
			List<MeveoModule> modules = q.getResultList();
			if (modules != null && !modules.isEmpty()) {
				for (MeveoModule module : modules) {
					// check if item already exists
					Optional<MeveoModuleItem> moduleItem = Optional.empty();
					if (module.getModuleItems() != null && !module.getModuleItems().isEmpty()) {
						Predicate<MeveoModuleItem> isCft = e -> e.getItemClass().equals(CustomFieldTemplate.class.getName());
						Predicate<MeveoModuleItem> isExisting = e -> e.getAppliesTo().equals(cft.getAppliesTo()) && e.getItemCode().equals(cft.getCode());

						moduleItem = module.getModuleItems().stream().filter(isCft).filter(isExisting).findAny();
					}

					if (!moduleItem.isPresent()) {
						MeveoModuleItem mi = new MeveoModuleItem();
						mi.setMeveoModule(module);
						mi.setAppliesTo(cft.getAppliesTo());
						mi.setItemClass(CustomFieldTemplate.class.getName());
						mi.setItemCode(cft.getCode());
						meveoModuleItemService.create(mi);
					}
				}
			}

		} catch (NoResultException e) {

		}
	}

	public void onCftDeleted(@Observes @Removed CustomFieldTemplate cft) {
		Query q = getEntityManager().createNamedQuery("MeveoModuleItem.synchronizeCftDelete");
		q = q.setParameter("itemCode", cft.getCode());
		q = q.setParameter("itemClass", CustomEntityTemplate.class.getName());
		q.executeUpdate();
	}

	public void releaseModule(MeveoModule entity, String nextVersion) throws BusinessException {
        entity = findById(entity.getId());
        ModuleRelease moduleRelease = new ModuleRelease();
        moduleRelease.setCode(entity.getCode());
        moduleRelease.setDescription(entity.getDescription());
        moduleRelease.setLicense(entity.getLicense());
        moduleRelease.setLogoPicture(entity.getLogoPicture());
        moduleRelease.setScript(entity.getScript());
        moduleRelease.setCurrentVersion(entity.getCurrentVersion());
        moduleRelease.setMeveoVersionBase(entity.getMeveoVersionBase());
        moduleRelease.setMeveoVersionCeiling(entity.getMeveoVersionCeiling());
        moduleRelease.setModuleSource(entity.getModuleSource());
        moduleRelease.setInDraft(false);
        if (CollectionUtils.isNotEmpty(entity.getModuleFiles())) {
            Set<String> moduleFiles = new HashSet<>();
            for (String moduleFile : entity.getModuleFiles()) {
                moduleFiles.add(moduleFile);
            }
            moduleRelease.setModuleFiles(moduleFiles);
        }
        if (CollectionUtils.isNotEmpty(entity.getModuleDependencies())) {
            List<MeveoModuleDependency> dependencies = new ArrayList<>();
            for (MeveoModuleDependency moduleDependency : entity.getModuleDependencies()) {
                dependencies.add(moduleDependency);
            }
            moduleRelease.setModuleDependencies(dependencies);
        }
        if (CollectionUtils.isNotEmpty(entity.getModuleItems())) {
            List<ModuleReleaseItem> moduleReleaseItems = new ArrayList<>();
            for (MeveoModuleItem meveoModuleItem : entity.getModuleItems()) {
                ModuleReleaseItem moduleReleaseItem = new ModuleReleaseItem();
                moduleReleaseItem.setAppliesTo(meveoModuleItem.getAppliesTo());
                moduleReleaseItem.setItemClass(meveoModuleItem.getItemClass());
                moduleReleaseItem.setItemEntity(meveoModuleItem.getItemEntity());
                moduleReleaseItem.setItemCode(meveoModuleItem.getItemCode());
                moduleReleaseItem.setModuleRelease(moduleRelease);
                moduleReleaseItems.add(moduleReleaseItem);
            }
            moduleRelease.setModuleItems(moduleReleaseItems);
        } else if (!StringUtils.isBlank(entity.getModuleSource())) {
            ModuleReleaseDto moduleReleaseDto = JacksonUtil.fromString(entity.getModuleSource(), ModuleReleaseDto.class);
            moduleReleaseDto.setCurrentVersion(entity.getCurrentVersion());
            moduleReleaseDto.setInDraft(false);

            if (CollectionUtils.isNotEmpty(moduleReleaseDto.getModuleFiles())) {
                moduleReleaseDto.getModuleFiles().clear();
            }
            if (CollectionUtils.isNotEmpty(entity.getModuleFiles())) {
                for (String moduleFile : entity.getModuleFiles()) {
                    moduleReleaseDto.getModuleFiles().add(moduleFile);
                }
            }

            if (CollectionUtils.isNotEmpty(moduleReleaseDto.getModuleDependencies())) {
                moduleReleaseDto.getModuleDependencies().clear();
            }
            if (CollectionUtils.isNotEmpty(entity.getModuleDependencies())) {
                for (MeveoModuleDependency dependency : entity.getModuleDependencies()) {
                    moduleReleaseDto.addModuleDependency(dependency);
                }
            }
            moduleRelease.setModuleSource(JacksonUtil.toString(moduleReleaseDto));
        }
        entity.setInstalled(false);
        entity.setCurrentVersion(nextVersion);
        moduleRelease.setMeveoModule(entity);
        entity.getReleases().add(moduleRelease);
        this.update(entity);
    }

	/**
	 * Retrieves if a function JMeter tests run successfully.
	 * 
	 * @param codeScript code of a function
	 * @return true if there are no error
	 */
	public boolean checkTestSuites(String codeScript) {

		JobInstance jobInstance = jobInstanceService.findByCode("FunctionTestJob_" + codeScript);
		JobExecutionResultImpl result = jobExecutionService.findLastExecutionByInstance(jobInstance);
		if (result != null && result.getNbItemsProcessedWithError() > Long.valueOf("0")) {
			return false;
		}

		return true;
	}

    @Override
    public MeveoModule update(MeveoModule entity) throws BusinessException {
	    MeveoModule meveoModule = updateModule(entity);
        return super.update(meveoModule);
    }

    public MeveoModule mergeModule(MeveoModule entity) {
	    MeveoModule meveoModule = updateModule(entity);
	    return getEntityManager().merge(meveoModule);
    }

    private MeveoModule updateModule(MeveoModule entity) {
        MeveoModule meveoModule = findById(entity.getId());
        Set<MeveoModuleDependency> moduleDependencies = new HashSet<>();
        if (CollectionUtils.isNotEmpty(entity.getModuleDependencies())) {
            for (MeveoModuleDependency meveoModuleDependency : entity.getModuleDependencies()) {
                moduleDependencies.add(meveoModuleDependency);
            }
        }

        if(meveoModule.getModuleDependencies() != null) {
            meveoModule.getModuleDependencies().clear();
        }
        Set<String> moduleFiles = new HashSet<>();
        if (CollectionUtils.isNotEmpty(entity.getModuleFiles())) {
            for (String moduleFile : entity.getModuleFiles()) {
                moduleFiles.add(moduleFile);
            }
        }

        if(meveoModule.getModuleFiles() != null) {
            meveoModule.getModuleFiles().clear();
        }
        Set<MeveoModuleItem> moduleItems = new HashSet<>();
        if (CollectionUtils.isNotEmpty(entity.getModuleItems())) {
            for (MeveoModuleItem meveoModuleItem : entity.getModuleItems()) {
                moduleItems.add(meveoModuleItem);
            }
        }

        if(meveoModule.getModuleItems() != null) {
            meveoModule.getModuleItems().clear();
        }

        Set<MeveoModulePatch> modulePatches = new HashSet<>();
        if (CollectionUtils.isNotEmpty(entity.getPatches())) {
            for (MeveoModulePatch meveoModulePatch : entity.getPatches()) {
                modulePatches.add(meveoModulePatch);
            }
        }

        if(meveoModule.getPatches() != null) {
           meveoModule.getPatches().clear();
        }

	    meveoModule.setDescription(entity.getDescription());
        meveoModule.setLicense(entity.getLicense());
        meveoModule.setLogoPicture(entity.getLogoPicture());
	    meveoModule.setCurrentVersion(entity.getCurrentVersion());
        meveoModule.setMeveoVersionBase(entity.getMeveoVersionBase());
        meveoModule.setMeveoVersionCeiling(entity.getMeveoVersionCeiling());
        meveoModule.setModuleSource(entity.getModuleSource());
        if (CollectionUtils.isNotEmpty(moduleItems)) {
            for (MeveoModuleItem meveoModuleItem : moduleItems) {
                meveoModule.getModuleItems().add(meveoModuleItem);
            }
        }
        if (CollectionUtils.isNotEmpty(moduleFiles)) {
            for (String moduleFile : moduleFiles) {
                meveoModule.getModuleFiles().add(moduleFile);
            }
        }
        if (CollectionUtils.isNotEmpty(moduleDependencies)) {
            for (MeveoModuleDependency moduleDependency : moduleDependencies) {
                meveoModule.getModuleDependencies().add(moduleDependency);
            }
        }
        if (CollectionUtils.isNotEmpty(modulePatches)) {
            for (MeveoModulePatch modulePatch : modulePatches) {
                meveoModule.getPatches().add(modulePatch);
            }
        }
        return meveoModule;
    }

    public MeveoModule getMeveoModuleByVersionModule(String code, String currentVersion) throws EntityDoesNotExistsException {
        MeveoModule meveoModule = findByCode(code);
        if (meveoModule == null) {
            throw new EntityDoesNotExistsException("The module file cannot be imported because module dependency "+ code +" doesn't exists locally.");
        }
        if (currentVersion.equals(meveoModule.getCurrentVersion())) {
            return meveoModule;
        }
        if (CollectionUtils.isNotEmpty(meveoModule.getReleases())) {
            for (ModuleRelease moduleRelease : meveoModule.getReleases()) {
                if (currentVersion.equals(moduleRelease.getCurrentVersion())) {
                    MeveoModule module = new MeveoModule();
                    module.setCode(moduleRelease.getCode());
                    module.setDescription(moduleRelease.getDescription());
                    module.setScript(moduleRelease.getScript());
                    module.setCurrentVersion(moduleRelease.getCurrentVersion());
                    module.setMeveoVersionBase(moduleRelease.getMeveoVersionBase());
                    module.setMeveoVersionCeiling(moduleRelease.getMeveoVersionCeiling());
                    module.setModuleSource(moduleRelease.getModuleSource());
                    if (CollectionUtils.isNotEmpty(moduleRelease.getModuleFiles())) {
                        Set<String> moduleFiles = new HashSet<>();
                        for (String moduleFile : moduleRelease.getModuleFiles()) {
                            moduleFiles.add(moduleFile);
                        }
                        module.setModuleFiles(moduleFiles);
                    }
                    if (CollectionUtils.isNotEmpty(moduleRelease.getModuleDependencies())) {
                        Set<MeveoModuleDependency> dependencies = new HashSet<>();
                        for (MeveoModuleDependency moduleDependency : moduleRelease.getModuleDependencies()) {
                            dependencies.add(moduleDependency);
                        }
                        module.setModuleDependencies(dependencies);
                    }
                    if (CollectionUtils.isNotEmpty(moduleRelease.getModuleItems())) {
                        Set<MeveoModuleItem> meveoModuleItems = new HashSet<>();
                        for (ModuleReleaseItem releaseItem : moduleRelease.getModuleItems()) {
                            MeveoModuleItem item = new MeveoModuleItem();
                            item.setAppliesTo(releaseItem.getAppliesTo());
                            item.setItemClass(releaseItem.getItemClass());
                            item.setItemEntity(releaseItem.getItemEntity());
                            item.setItemCode(releaseItem.getItemCode());
                            item.setMeveoModule(module);
                            meveoModuleItems.add(item);
                        }
                        module.setModuleItems(meveoModuleItems);
                    }
                    return module;
                }
            }
        }
        return null;
    }

    public Integer findLaterNearestVersion(List<Integer> versions, Integer version) {

	    List<Integer> versionList = new ArrayList<>();
	    for (Integer versionRelease : versions) {
	        if (versionRelease > version) {
	            versionList.add(versionRelease);
            }
        }

        Integer laterVersion = getClosestVersion(versionList, version);
	    return laterVersion;
    }

    public Integer findEarlierNearestVersion(List<Integer> versions, Integer version) {

        List<Integer> versionList = new ArrayList<>();
        for (Integer versionRelease : versions) {
            if (versionRelease < version) {
                versionList.add(versionRelease);
            }
        }

        Integer earlierVersion = getClosestVersion(versionList, version);
        return earlierVersion;
    }

    public Integer getClosestVersion(List<Integer> versions, Integer version) {

        if (versions.size() < 1)
            return null;
        if (versions.size() == 1) {
            return versions.get(0);
        }
        Integer closestValue = versions.get(0);
        Integer leastDistance = Math.abs(versions.get(0) - version);
        for (int i = 0; i < versions.size(); i++) {
            int currentDistance = Math.abs(versions.get(i) - version);
            if (currentDistance < leastDistance) {
                closestValue = versions.get(i);
                leastDistance = currentDistance;
            }
        }
        return closestValue;
    }

    public void removeFilesIfModuleIsDeleted(List<String> moduleFiles) throws IOException {
        for (String moduleFile : moduleFiles) {
            String path = paramBeanFactory.getInstance().getChrootDir(currentUser.getProviderCode()) + File.separator + moduleFile;
            File file = new File(path);
            if (file.exists() && file.isDirectory()) {
                FileUtils.deleteDirectory(file);
            } else if (file.exists()) {
                file.delete();
            }
        }
    }

    @Override
    public MeveoModule findById(Long id, List<String> fetchFields, boolean refresh) {
	    MeveoModule meveoModule = findById(id);
	    if (getEntityManager().contains(meveoModule)) {
	        getEntityManager().detach(meveoModule);
        }
        return super.findById(id, fetchFields, refresh);
    }

    @Override
    public List<MeveoModule> list(PaginationConfiguration config) {
    	return super.list(config);
    }
}