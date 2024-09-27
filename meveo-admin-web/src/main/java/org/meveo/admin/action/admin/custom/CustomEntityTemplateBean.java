package org.meveo.admin.action.admin.custom;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.collections.CollectionUtils;
import org.jboss.seam.international.status.builder.BundleKey;
import org.meveo.admin.action.catalog.CalendarBean;
import org.meveo.admin.exception.BusinessException;
import org.meveo.admin.web.interceptor.ActionMethod;
import org.meveo.cache.CustomFieldsCacheContainerProvider;
import org.meveo.commons.utils.StringUtils;
import org.meveo.elresolver.ELException;
import org.meveo.model.BusinessEntity;
import org.meveo.model.crm.CustomEntityTemplateUniqueConstraint;
import org.meveo.model.crm.CustomFieldTemplate;
import org.meveo.model.crm.CustomFieldTemplate.GroupedCustomFieldTreeItemType;
import org.meveo.model.crm.custom.EntityCustomAction;
import org.meveo.model.customEntities.CustomEntityCategory;
import org.meveo.model.customEntities.CustomEntityTemplate;
import org.meveo.model.customEntities.GraphQLQueryField;
import org.meveo.model.customEntities.Mutation;
import org.meveo.model.module.MeveoModule;
import org.meveo.model.module.MeveoModuleItem;
import org.meveo.model.persistence.DBStorageType;
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.model.persistence.sql.SQLStorageConfiguration;
import org.meveo.persistence.DBStorageTypeService;
import org.meveo.model.storage.Repository;
import org.meveo.service.admin.impl.MeveoModuleService;
import org.meveo.service.custom.CustomEntityTemplateService;
import org.meveo.service.custom.CustomizedEntity;
import org.meveo.service.job.Job;
import org.meveo.util.EntityCustomizationUtils;
import org.primefaces.model.DefaultTreeNode;
import org.primefaces.model.DualListModel;
import org.primefaces.model.TreeNode;
import org.primefaces.model.menu.DefaultSubMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bean for managing custom entity templates.
 *
 * @author Edward P. Legaspi | czetsuya@gmail.com
 * @author clement.bareth
 * @version 6.11.0
 * @since 6.0.0
 */
@Named
@ViewScoped
public class CustomEntityTemplateBean extends BackingCustomBean<CustomEntityTemplate> {

	private static final long serialVersionUID = 1187554162639618526L;
	
	private static Logger log = LoggerFactory.getLogger(CustomEntityTemplateBean.class);
	
	@Inject
	private CustomFieldsCacheContainerProvider cache;
	
	@Inject
	private DBStorageTypeService dbStorageTypeService;

	/**
	 * Object being customized in case customization corresponds to a non
	 * CustomEntityTemplate class instance
	 */
	private CustomizedEntity customizedEntity;

	/** The logger. */
	Logger logger = LoggerFactory.getLogger(CustomEntityTemplateBean.class);

	/**
	 * Prefix to apply to custom field templates (appliesTo value)
	 */
	private String cetPrefix;

	private SortedTreeNode groupedFields;

	private TranslatableLabel selectedFieldGroupingLabel = new TranslatableLabel();

	private EntityCustomAction selectedEntityAction;

	private transient List<CustomEntityTemplate> customEntityTemplates;

	private List<CustomEntityTemplate> cetConfigurations;

	private transient List<CustomEntityCategory> customEntityCategories;

	private List<CustomEntityTemplateUniqueConstraint> customEntityTemplateUniqueConstraints = new ArrayList<>();

	private CustomEntityTemplateUniqueConstraint customEntityTemplateUniqueConstraint = new CustomEntityTemplateUniqueConstraint();

	private Boolean isUpdate = false;

	private List<GraphQLQueryField> graphqlQueryFields = new ArrayList<>();

	private GraphQLQueryField graphqlQueryField = new GraphQLQueryField();

	private List<Mutation> mutations = new ArrayList<>();

	private Mutation mutation = new Mutation();

	private DualListModel<DBStorageType> availableStoragesDM;
	
	private DualListModel<String> repositoriesDM;

	private Map<String, List<CustomEntityTemplate>> listMap;

	private List<CustomizedEntity> selectedCustomizedEntities;

	private List<Map<String, String>> parameters;
	
	private List<DefaultSubMenu> menuModels;

	@Inject
	private MeveoModuleService meveoModuleService;

	/**
	 * Instantiates a new custom entity template bean.
	 */
	public CustomEntityTemplateBean() {
		super(CustomEntityTemplate.class);
		entityClass = CustomEntityTemplate.class;
	}

	@PostConstruct
	public void init() {
		customEntityTemplates = customEntityTemplateService.list();
		cetConfigurations = customEntityTemplateService.getCETForConfiguration();
		customEntityCategories = customEntityCategoryService.list();
	}

	@Override
	public CustomEntityTemplate initEntity() {
		CustomEntityTemplate entity = super.initEntity();
		entity.getCustomEntityCategory();
		
		if (entity != null) {
			List<String> availableRepos = repositoryService.list()
					.stream()
					.map(Repository::getCode)
					.collect(Collectors.toList());
			List<String> entityRepos = entity.getRepositories().stream()
					.map(Repository::getCode)
					.collect(Collectors.toList());
			availableRepos.removeIf(entityRepos::contains);
			if (entityRepos.isEmpty()) {
				entityRepos.add("default");
				availableRepos.remove("default");
			}
			repositoriesDM = new DualListModel<>(availableRepos, entityRepos);
		}
		
		return entity;
	}

	@Override
	protected CustomEntityTemplateService getPersistenceService() {
		return customEntityTemplateService;
	}

	/**
	 * Checks if is custom entity template.
	 *
	 * @return true, if is custom entity template
	 */
	public boolean isCustomEntityTemplate() {
		return entityClassName == null || CustomEntityTemplate.class.getName().equals(entityClassName);
	}
	
	/**
	 * @return the {@link #repositoriesDM}
	 */
	public DualListModel<String> getRepositoriesDM() {
		return repositoriesDM;
	}
	
	/**
	 * @param repositoriesDM the repositoriesDM to set
	 */
	public void setRepositoriesDM(DualListModel<String> repositoriesDM) {
		this.repositoriesDM = repositoriesDM;
	}

	/**
	 * Gets the custom entity templates.
	 *
	 * @return the custom entity templates
	 */
	public List<CustomEntityTemplate> getCustomEntityTemplates() {
		return customEntityTemplates;
	}

	/**
	 * Gets the cet configurations.
	 *
	 * @return the cet configurations
	 */
	public List<CustomEntityTemplate> getCetConfigurations() {
		return cetConfigurations;
	}

	/**
	 * Gets the custom entity categories.
	 *
	 * @return the custom entity categories
	 */
	public List<CustomEntityCategory> getCustomEntityCategories() {
		return customEntityCategories;
	}

	/**
	 * Gets the table name if store in SQL.
	 *
	 * @return the table name
	 */
	public String getTableName() {
		return SQLStorageConfiguration.getDbTablename(entity);
	}

	/**
	 * Is entity being customized is a Custom entity template and will be stored as
	 * a separate table.
	 *
	 * @return True if entity being customized is a Custom entity template and will
	 *         be stored as a separate table
	 */
	public boolean isCustomTable() {
		return isCustomEntityTemplate() && entity != null && entity.getSqlStorageConfiguration() != null && entity.getSqlStorageConfiguration().isStoreAsTable();
	}

	
	/**
	 * @return the {@link #menuModels}
	 */
	public List<DefaultSubMenu> getMenuModels() {
		return menuModels;
	}

	/**
	 * List menu custom entities.
	 *
	 * @return the custom entities to be displayed in the menu
	 */
	public Map<String, List<CustomEntityTemplate>> listMenuCustomEntities() {
		if (listMap != null) {
			return listMap;
		}

		listMap = new HashMap<>();
		for (CustomEntityTemplate customEntityTemplate : cache.getCustomEntityTemplates()) {
			if (customEntityTemplate.getCustomEntityCategory() != null) {
				String name = customEntityTemplate.getCustomEntityCategory().getName();
				if (listMap.containsKey(name)) {
					List<CustomEntityTemplate> customEntityTemplates = listMap.get(name);
					customEntityTemplates.add(customEntityTemplate);
					listMap.put(name, customEntityTemplates);
				} else {
					List<CustomEntityTemplate> customEntityTemplates = new ArrayList<>();
					customEntityTemplates.add(customEntityTemplate);
					listMap.put(name, customEntityTemplates);
				}
			} else {
				if (listMap.containsKey(null)) {
					List<CustomEntityTemplate> customEntityTemplates = listMap.get(null);
					customEntityTemplates.add(customEntityTemplate);
					listMap.put(null, customEntityTemplates);
				} else {
					List<CustomEntityTemplate> customEntityTemplates = new ArrayList<>();
					customEntityTemplates.add(customEntityTemplate);
					listMap.put(resourceBundle.getString("menu.uncategorized"), customEntityTemplates);
				}
			}
		}

		return listMap;
	}

	/**
	 * Prepare to show entity customization for a particular class - To be used from
	 * GUI action button/link.
	 *
	 * @param entityClassName Entity class
	 */
	public void initCustomization(String entityClassName) {
		customizedEntity = null;
		this.entityClassName = entityClassName;
		try {
			getCustomizedEntity();
		} catch (ClassNotFoundException e) {
			log.error("Failed to initialize entity customization for a class {}", entityClassName);
		}
	}

	/**
	 * Construct customizedEntity instance which is a representation of customizable
	 * class (e.g. Customer)
	 *
	 * @return the {@link CustomizedEntity} implementation
	 * @throws ClassNotFoundException if customized entity is not on class path
	 */
	public CustomizedEntity getCustomizedEntity() throws ClassNotFoundException {

		// Convert appliesTo parameter to a entityClassName of objectId value in case of
		// customEntity template
		if (customizedEntity == null && appliesTo != null) {

			CustomizedEntity customizedEntityMatched = customizedEntityService.getCustomizedEntity(appliesTo);
			if (customizedEntityMatched != null) {
				setEntityClassName(customizedEntityMatched.getEntityClass().getName());
				if (customizedEntityMatched.getCustomEntityId() != null) {
					initEntity(customizedEntityMatched.getCustomEntityId());
				}
			}
		}

		if (customizedEntity == null && entityClassName != null && !CustomEntityTemplate.class.getName().equals(entityClassName)) {
			entityClass = Class.forName(entityClassName);
			customizedEntity = new CustomizedEntity(entityClass.getSimpleName(), entityClass, null, null);
			cetPrefix = EntityCustomizationUtils.getAppliesTo(entityClass, null);

			if (Job.class.isAssignableFrom(entityClass)) {

				// Check and instantiate missing custom field templates for a given job
				Job job = jobInstanceService.getJobByName(entityClass.getSimpleName());
				Map<String, CustomFieldTemplate> jobCustomFields = job.getCustomFields();

				// Create missing custom field templates if needed
				try {
					customFieldTemplateService.createMissingTemplates(cetPrefix, jobCustomFields.values());

				} catch (BusinessException e) {
					log.error("Failed to construct customized entity", e);
					messages.error(new BundleKey("messages", "error.unexpected"));
				}
			}
		}

		return customizedEntity;
	}

	@Override
	@ActionMethod
	public String saveOrUpdate(boolean killConversation) throws BusinessException, ELException {

		String message = entity.isTransient() ? "save.successful" : "update.successful";
		List<Repository> repositories = repositoriesDM.getTarget()
				.stream()
				.map(repositoryService::findByCode)
				.collect(Collectors.toList());
		entity.setRepositories(repositories);

		try {
			entity = saveOrUpdate(entity);
			messages.info(new BundleKey("messages", message));
			if (killConversation) {
				endConversation();
			}
		} catch (Exception e) {
			if (e.getMessage().endsWith("is a PostgresQL reserved keyword")) {
				messages.error(new BundleKey("messages", "error.createCetWithKeyWord"), entity.getCode());
			} else {
				messages.error("Entity can't be saved. Please retry.");
				log.error("Can't update entity", e);
			}
		}
		return getEditViewName();
	}

	/**
	 * On change available storages.
	 */
	public void onChangeAvailableStorages() {
		if (CollectionUtils.isNotEmpty(getEntity().getAvailableStorages())) {
			getEntity().getAvailableStorages().clear();
			getEntity().getAvailableStorages().addAll(availableStoragesDM.getTarget());
		} else {
			getEntity().setAvailableStorages(new HashSet<>(availableStoragesDM.getTarget()));
		}
	}

	public void addParentCFTs(CustomEntityTemplate parentCET) {

		if (parentCET == null) {
			return;
		}

		String parentCetPrefix = parentCET.getAppliesTo();

		Map<String, CustomFieldTemplate> fields = customFieldTemplateService.findByAppliesToNoCache(parentCetPrefix);

		if (parentCET != null && parentCET.getNeo4JStorageConfiguration() != null && parentCET.getNeo4JStorageConfiguration().isPrimitiveEntity()) {
			fields.remove("value");
		}
		
		for (CustomFieldTemplate field : fields.values()) {

			// Init primitve types
			if (!StringUtils.isBlank(field.getEntityClazz())) {
				final String cetCode = CustomFieldTemplate.retrieveCetCode(field.getEntityClazz());
				field.setPrimitiveType(customEntityTemplateService.getPrimitiveType(cetCode));
			}
		}

		GroupedCustomField groupedCFTAndActions = new GroupedCustomField(fields.values(), "ParentCET", false);

		// Append actions into the hierarchy of tabs and fieldgroups
		Map<String, EntityCustomAction> customActions = entityActionScriptService.findByAppliesTo(parentCetPrefix);
		groupedCFTAndActions.append(customActions.values());

		// Create tabs
		for (GroupedCustomField level1 : groupedCFTAndActions.getChildren()) {			
			SortedTreeNode level1Node = new SortedTreeNode(level1.getType(), level1.getData(), groupedFields, level1.getType() == GroupedCustomFieldTreeItemType.tab, true);

			// Create fields of field groups
			for (GroupedCustomField level2 : level1.getChildren()) {
				SortedTreeNode level2Node = new SortedTreeNode(level2.getType(), level2.getData(), level1Node, level2.getType() == GroupedCustomFieldTreeItemType.fieldGroup, true);

				// Create fields
				for (GroupedCustomField level3 : level2.getChildren()) {
					new SortedTreeNode(level3.getType(), level3.getData(), level2Node, null, true);
				}
			}
		}
	}

	/**
	 * Gets the fields.
	 *
	 * @return the fields
	 */
	public TreeNode getFields() {
		if (groupedFields != null || cetPrefix == null) {
			return groupedFields;
		}

		CustomEntityTemplate entityTemplate = customEntityTemplateService.findByCode(CustomEntityTemplate.getCodeFromAppliesTo(cetPrefix));

		Map<String, CustomFieldTemplate> fields = customFieldTemplateService.findByAppliesToNoCache(cetPrefix);

		if (entityTemplate != null && entityTemplate.getNeo4JStorageConfiguration() != null && entityTemplate.getNeo4JStorageConfiguration().isPrimitiveEntity()) {
			fields.remove("value");
		}

		// Init primitve types
		for (CustomFieldTemplate field : fields.values()) {
			if (!StringUtils.isBlank(field.getEntityClazz())) {
				final String cetCode = CustomFieldTemplate.retrieveCetCode(field.getEntityClazz());
				field.setPrimitiveType(customEntityTemplateService.getPrimitiveType(cetCode));
			}
		}

		GroupedCustomField groupedCFTAndActions = new GroupedCustomField(fields.values(),
				CustomEntityTemplate.class.isAssignableFrom(entityClass) ? entity.getName() : "Custom fields", true);

		// Append actions into the hierarchy of tabs and fieldgroups
		Map<String, EntityCustomAction> customActions = entityActionScriptService.findByAppliesTo(cetPrefix);
		groupedCFTAndActions.append(customActions.values());

		groupedFields = new SortedTreeNode(groupedCFTAndActions.getType(), groupedCFTAndActions.getData(), null, true);

		// Create tabs
		for (GroupedCustomField level1 : groupedCFTAndActions.getChildren()) {
			SortedTreeNode level1Node = new SortedTreeNode(level1.getType(), level1.getData(), groupedFields, level1.getType() == GroupedCustomFieldTreeItemType.tab);

			// Create fields of field groups
			for (GroupedCustomField level2 : level1.getChildren()) {
				SortedTreeNode level2Node = new SortedTreeNode(level2.getType(), level2.getData(), level1Node, level2.getType() == GroupedCustomFieldTreeItemType.fieldGroup);

				// Create fields
				for (GroupedCustomField level3 : level2.getChildren()) {
					new SortedTreeNode(level3.getType(), level3.getData(), level2Node, null);
				}
			}
		}

		this.addParentCFTs(entityTemplate.getSuperTemplate());

		if (cachedTreeNodes != null && !cachedTreeNodes.isEmpty()) {
			for (TreeNode tabNode : cachedTreeNodes) {// tab
				TreeNode existedTab = getChildNodeByValue(groupedFields, tabNode.getData().toString());
				if (existedTab == null) {// check tab
					existedTab = new SortedTreeNode(GroupedCustomFieldTreeItemType.tab, tabNode.getData(), groupedFields, true);

				}
				for (TreeNode fieldGroupNode : tabNode.getChildren()) {// field groups of tab
					TreeNode existedFieldGroup = getChildNodeByValue(existedTab, fieldGroupNode.getData().toString());
					if (existedFieldGroup == null) {
						existedFieldGroup = new SortedTreeNode(GroupedCustomFieldTreeItemType.fieldGroup, fieldGroupNode.getData(), existedTab, null);
					}
				}
			}
		}

		return groupedFields;
	}

	/**
	 * Gets the entity actions.
	 *
	 * @return the entity actions
	 */
	public List<EntityCustomAction> getEntityActions() {

		if (entityActions != null || cetPrefix == null) {
			return entityActions;
		}

		Map<String, EntityCustomAction> scripts = entityActionScriptService.findByAppliesTo(cetPrefix);

		entityActions = new ArrayList<EntityCustomAction>();
		entityActions.addAll(scripts.values());

		return entityActions;
	}

	/**
	 * Remember the tabs and fieldgroups as they are reconstructed from field and
	 * action guiPosition fields. And then clear groupedFields value so it would be
	 * reconstructed again uppon the first reqeust.
	 */
	@Override
	public void refreshFields() {
		if (groupedFields != null) {
			cachedTreeNodes = new ArrayList<TreeNode>();
			for (TreeNode rootChildNode : groupedFields.getChildren()) {
				if (rootChildNode.getType().equals(GroupedCustomFieldTreeItemType.tab.name())) {
					SortedTreeNode tab = new SortedTreeNode(GroupedCustomFieldTreeItemType.tab, rootChildNode.getData(), null, null);
					cachedTreeNodes.add(tab);
					if (rootChildNode.getChildCount() != 0) {
						for (TreeNode fieldGroupNode : rootChildNode.getChildren()) {// fieldgroup
							SortedTreeNode fieldGroup = (SortedTreeNode) fieldGroupNode;
							if (fieldGroup.getType().equals(GroupedCustomFieldTreeItemType.fieldGroup.name()) && fieldGroupNode.getChildCount() == 0) {
								new SortedTreeNode(GroupedCustomFieldTreeItemType.fieldGroup, fieldGroupNode.getData(), tab, null);
							}
						}
					}
				}
			}
		}
		groupedFields = null;
	}

	/**
	 * Refresh actions.
	 */
	public void refreshActions() {
		entityActions = null;
	}

	/**
	 * Edits the field grouping.
	 *
	 * @param selectedFieldGrouping the selected field grouping
	 */
	public void editFieldGrouping(TreeNode selectedFieldGrouping) {
		setSelectedFieldGrouping(selectedFieldGrouping);
		// this.selectedFieldGroupingLabel = (TranslatableLabel)
		// selectedFieldGrouping.getData();
	}

	/**
	 * Sets the selected field grouping.
	 *
	 * @param selectedFieldGrouping the new selected field grouping
	 */
	public void setSelectedFieldGrouping(TreeNode selectedFieldGrouping) {
		this.selectedFieldGrouping = selectedFieldGrouping;
		this.selectedFieldGroupingLabel = (TranslatableLabel) selectedFieldGrouping.getData();
	}

	/**
	 * Gets the selected field grouping.
	 *
	 * @return the selected field grouping
	 */
	public TreeNode getSelectedFieldGrouping() {
		return selectedFieldGrouping;
	}

	/**
	 * Gets the selected field grouping label.
	 *
	 * @return the selected field grouping label
	 */
	public TranslatableLabel getSelectedFieldGroupingLabel() {
		return selectedFieldGroupingLabel;
	}

	/**
	 * Sets the selected field grouping label.
	 *
	 * @param selectedFieldGroupingLabel the new selected field grouping label
	 */
	public void setSelectedFieldGroupingLabel(TranslatableLabel selectedFieldGroupingLabel) {
		this.selectedFieldGroupingLabel = selectedFieldGroupingLabel;
	}

	/**
	 * Sets the selected entity action.
	 *
	 * @param selectedEntityAction the new selected entity action
	 */
	public void setSelectedEntityAction(EntityCustomAction selectedEntityAction) {
		this.selectedEntityAction = selectedEntityAction;
	}

	/**
	 * Gets the selected entity action.
	 *
	 * @return the selected entity action
	 */
	public EntityCustomAction getSelectedEntityAction() {
		return selectedEntityAction;
	}

	@Override
	public String getEditViewName() {
		return "customizedEntity";
	}

	@Override
	public String getListViewName() {
		return "customizedEntities";
	}

	/**
	 * Gets the prefix to apply to custom field templates (appliesTo value).
	 *
	 * @return the prefix to apply to custom field templates (appliesTo value)
	 */
	public String getCetPrefix() {
		if (cetPrefix != null) {
			return cetPrefix;

		} else if (entity != null && entity.getCode() != null) {
			cetPrefix = entity.getAppliesTo();
			return cetPrefix;
		}
		return null;
	}

	@Override
	public void newTab() {
		setSelectedFieldGrouping(new SortedTreeNode(GroupedCustomFieldTreeItemType.tab, new TranslatableLabel(""), groupedFields, true));
	}

	@Override
	public void newFieldGroup(TreeNode parentNode) {
		setSelectedFieldGrouping(new SortedTreeNode(GroupedCustomFieldTreeItemType.fieldGroup, new TranslatableLabel(""), parentNode, true));
	}

	@Override
	public void saveUpdateFieldGrouping() {
		try {
			((SortedTreeNode) selectedFieldGrouping).setData(selectedFieldGroupingLabel);
			updateFieldGuiPositionValue((SortedTreeNode) selectedFieldGrouping);
		} catch (BusinessException e) {
			log.error("Failed to update field grouping {}", selectedFieldGrouping, e);
			messages.error(new BundleKey("messages", "error.unexpected"));
		}
	}

	@Override
	public void cancelFieldGrouping() {
		if (((TranslatableLabel) selectedFieldGrouping.getData()).isEmpty()) {
			selectedFieldGrouping.getParent().getChildren().remove(selectedFieldGrouping);
		}
	}

	/**
	 * Move up the node on the tree.
	 *
	 * @param node the node
	 */
	public void moveUp(SortedTreeNode node) {

		int currentIndex = node.getIndexInParent();

		// Move a position up within the same branch
		if (currentIndex > 0) {
			TreeNode parent = node.getParent();
			parent.getChildren().remove(currentIndex);
			parent.getChildren().add(currentIndex - 1, node);

			// Move a position up outside the branch
		} else if (currentIndex == 0 && node.canMoveUp()) {
			TreeNode parentSibling = node.getParentSiblingUp();
			node.getParent().getChildren().remove(currentIndex);
			parentSibling.getChildren().add(node);
		}

		try {
			updateFieldGuiPositionValue((SortedTreeNode) node.getParent());

		} catch (BusinessException e) {
			log.error("Failed to move up {}", node, e);
			messages.error(new BundleKey("messages", "error.unexpected"));
		}
	}

	/**
	 * Move down the node on the tree.
	 *
	 * @param node the node
	 */
	public void moveDown(SortedTreeNode node) {

		int currentIndex = node.getIndexInParent();
		boolean isLast = node.isLast();

		// Move a position down within the same branch
		if (!isLast) {
			TreeNode parent = node.getParent();
			boolean moved = false;
			if (node.getType().equals(GroupedCustomFieldTreeItemType.field.name()) || node.getType().equals(GroupedCustomFieldTreeItemType.action.name())) {
				SortedTreeNode siblingDown = node.getSiblingDown();
				if (siblingDown != null && !(siblingDown.getType().equals(GroupedCustomFieldTreeItemType.field.name())
						|| siblingDown.getType().equals(GroupedCustomFieldTreeItemType.action.name()))) {
					parent.getChildren().remove(currentIndex);
					siblingDown.getChildren().add(0, node);
					moved = true;
					// return;
				}
			}
			if (!moved) {
				parent.getChildren().remove(currentIndex);
				parent.getChildren().add(currentIndex + 1, node);
			}
			// Move a position down outside the branch
		} else if (isLast && node.canMoveDown()) {
			SortedTreeNode parentSibling = node.getParentSiblingDown();
			if (parentSibling != null) {
				node.getParent().getChildren().remove(currentIndex);

				if (parentSibling.getType().equals(GroupedCustomFieldTreeItemType.field.name()) || parentSibling.getType().equals(GroupedCustomFieldTreeItemType.action.name())) {
					parentSibling.getParent().getChildren().add(parentSibling.getIndexInParent(), node);
				} else {
					parentSibling.getChildren().add(0, node);
				}
			}
		}

		try {
			updateFieldGuiPositionValue((SortedTreeNode) node.getParent());

		} catch (BusinessException e) {
			log.error("Failed to move down {}", node, e);
			messages.error(new BundleKey("messages", "error.unexpected"));
		}
	}

	private void updateFieldGuiPositionValue(SortedTreeNode nodeToUpdate) throws BusinessException {

		// Re-position current and child nodes
		List<TreeNode> nodes = nodeToUpdate.getChildren();
		if (!nodeToUpdate.getType().equals(GroupedCustomFieldTreeItemType.root.name())) {
			nodes = new ArrayList<TreeNode>();
			nodes.add(nodeToUpdate);
		}

		for (TreeNode treeNode : nodes) {
			SortedTreeNode sortedNode = (SortedTreeNode) treeNode;
			String currentPosition = null;

			// Only tab, fieldGroup and action can be under root
			if (sortedNode.getType().equals(GroupedCustomFieldTreeItemType.tab.name())) {
				currentPosition = GroupedCustomFieldTreeItemType.tab.positionTag + ":" + sortedNode.getData() + ":" + sortedNode.getIndexInParent();

			} else if (sortedNode.getType().equals(GroupedCustomFieldTreeItemType.fieldGroup.name())) {
				if (!sortedNode.getParent().getType().equals(GroupedCustomFieldTreeItemType.root.name())) {
					currentPosition = GroupedCustomFieldTreeItemType.tab.positionTag + ":" + sortedNode.getParent().getData() + ":"
							+ ((SortedTreeNode) sortedNode.getParent()).getIndexInParent() + ";";
				}
				currentPosition = currentPosition + GroupedCustomFieldTreeItemType.fieldGroup.positionTag + ":" + sortedNode.getData() + ":" + sortedNode.getIndexInParent();

			} else if (sortedNode.getType().equals(GroupedCustomFieldTreeItemType.action.name())) {
				String guiPosition = GroupedCustomFieldTreeItemType.action.positionTag + ":" + sortedNode.getIndexInParent();
				EntityCustomAction action = (EntityCustomAction) sortedNode.getData();
				action = entityActionScriptService.refreshOrRetrieve(action);
				if (!guiPosition.equals(action.getGuiPosition())) {
					action.setGuiPosition(guiPosition);
					action = entityActionScriptService.update(action);
					sortedNode.setData(action);
				}
				// No children under action, so continue
				continue;
			}

			for (TreeNode node : sortedNode.getChildren()) {
				SortedTreeNode sortedChildNode = (SortedTreeNode) node;
				if (sortedChildNode.getType().equals(GroupedCustomFieldTreeItemType.field.name())) {
					String guiPosition = currentPosition + ";" + GroupedCustomFieldTreeItemType.field.positionTag + ":" + sortedChildNode.getIndexInParent();
					CustomFieldTemplate cft = (CustomFieldTemplate) sortedChildNode.getData();
					cft = customFieldTemplateService.refreshOrRetrieve(cft);
					if (!guiPosition.equals(cft.getGuiPosition())) {
						cft.setGuiPosition(guiPosition);
						cft = customFieldTemplateService.update(cft);
						sortedChildNode.setData(cft);
					}

				} else if (sortedChildNode.getType().equals(GroupedCustomFieldTreeItemType.action.name())) {
					String guiPosition = currentPosition + ";" + GroupedCustomFieldTreeItemType.action.positionTag + ":" + sortedChildNode.getIndexInParent();
					EntityCustomAction action = (EntityCustomAction) sortedChildNode.getData();
					action = entityActionScriptService.refreshOrRetrieve(action);
					if (!guiPosition.equals(action.getGuiPosition())) {
						action.setGuiPosition(guiPosition);
						action = entityActionScriptService.update(action);
						sortedChildNode.setData(action);
					}

				} else if (sortedChildNode.getType().equals(GroupedCustomFieldTreeItemType.fieldGroup.name())) {
					String childGroupPosition = currentPosition + ";" + GroupedCustomFieldTreeItemType.fieldGroup + ":" + sortedChildNode.getData() + ":"
							+ sortedChildNode.getIndexInParent();
					for (TreeNode childNode : sortedChildNode.getChildren()) {
						SortedTreeNode sortedChildChildNode = (SortedTreeNode) childNode;

						if (sortedChildChildNode.getType().equals(GroupedCustomFieldTreeItemType.field.name())) {
							String guiPosition = childGroupPosition + ";" + GroupedCustomFieldTreeItemType.field.positionTag + ":" + sortedChildChildNode.getIndexInParent();
							CustomFieldTemplate cft = (CustomFieldTemplate) sortedChildChildNode.getData();
							cft = customFieldTemplateService.refreshOrRetrieve(cft);
							if (!guiPosition.equals(cft.getGuiPosition())) {
								cft.setGuiPosition(guiPosition);
								cft = customFieldTemplateService.update(cft);
								sortedChildChildNode.setData(cft);
							}

						} else if (sortedChildChildNode.getType().equals(GroupedCustomFieldTreeItemType.action.name())) {
							String guiPosition = childGroupPosition + ";" + GroupedCustomFieldTreeItemType.action.positionTag + ":" + sortedChildChildNode.getIndexInParent();
							EntityCustomAction action = (EntityCustomAction) sortedChildChildNode.getData();
							action = entityActionScriptService.refreshOrRetrieve(action);
							if (!guiPosition.equals(action.getGuiPosition())) {
								action.setGuiPosition(guiPosition);
								action = entityActionScriptService.update(action);
								sortedChildChildNode.setData(action);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Gets the custom entity template unique constraints.
	 *
	 * @return the custom entity template unique constraints
	 */
	public List<CustomEntityTemplateUniqueConstraint> getCustomEntityTemplateUniqueConstraints() {
		if (entity.getNeo4JStorageConfiguration() != null) {
			if (entity != null && entity.getNeo4JStorageConfiguration().getUniqueConstraints() != null) {
				customEntityTemplateUniqueConstraints = entity.getNeo4JStorageConfiguration().getUniqueConstraints();
			}
			return customEntityTemplateUniqueConstraints;
		} else {
			return null;
		}
	}

	/**
	 * Gets the custom entity template unique constraint.
	 *
	 * @return the custom entity template unique constraint
	 */
	public CustomEntityTemplateUniqueConstraint getCustomEntityTemplateUniqueConstraint() {
		return customEntityTemplateUniqueConstraint;
	}

	/**
	 * Sets the custom entity template unique constraint.
	 *
	 * @param customEntityTemplateUniqueConstraint the new custom entity template
	 *                                             unique constraint
	 */
	public void setCustomEntityTemplateUniqueConstraint(CustomEntityTemplateUniqueConstraint customEntityTemplateUniqueConstraint) {
		this.customEntityTemplateUniqueConstraint = customEntityTemplateUniqueConstraint;
	}

	/**
	 * Adds an empty unique constraint.
	 */
	public void addUniqueConstraint() {
		isUpdate = false;
		customEntityTemplateUniqueConstraint = new CustomEntityTemplateUniqueConstraint();
		customEntityTemplateUniqueConstraint.setTrustScore(100);
	}

	/**
	 * Removes the unique constraint.
	 *
	 * @param selectedUniqueConstraint the selected unique constraint
	 */
	public void removeUniqueConstraint(CustomEntityTemplateUniqueConstraint selectedUniqueConstraint) {
		for (CustomEntityTemplateUniqueConstraint uniqueConstraint : customEntityTemplateUniqueConstraints) {
			if (uniqueConstraint != null && uniqueConstraint.equals(selectedUniqueConstraint)) {
				entity.getNeo4JStorageConfiguration().getUniqueConstraints().remove(selectedUniqueConstraint);
				break;
			}
		}
		String message = "customFieldInstance.childEntity.save.successful";
		messages.info(new BundleKey("messages", message));
	}

	/**
	 * Edits the unique constraint.
	 *
	 * @param selectedUniqueConstraint the selected unique constraint
	 */
	public void editUniqueConstraint(CustomEntityTemplateUniqueConstraint selectedUniqueConstraint) {
		isUpdate = true;
		customEntityTemplateUniqueConstraint = selectedUniqueConstraint;
	}

	/**
	 * Save the unique constraint being edited.
	 */
	public void saveUniqueConstraint() {
		if (!isUpdate) {
			customEntityTemplateUniqueConstraint.setCustomEntityTemplate(entity);
			customEntityTemplateUniqueConstraints.add(customEntityTemplateUniqueConstraint);
		} else {
			for (CustomEntityTemplateUniqueConstraint uniqueConstraint : customEntityTemplateUniqueConstraints) {
				if (uniqueConstraint != null && uniqueConstraint.getCode().equals(customEntityTemplateUniqueConstraint.getCode())) {
					uniqueConstraint.setDescription(customEntityTemplateUniqueConstraint.getDescription());
					uniqueConstraint.setCypherQuery(customEntityTemplateUniqueConstraint.getCypherQuery());
					uniqueConstraint.setTrustScore(customEntityTemplateUniqueConstraint.getTrustScore());
					uniqueConstraint.setApplicableOnEl(customEntityTemplateUniqueConstraint.getApplicableOnEl());
					break;
				}
			}
		}
		entity.getNeo4JStorageConfiguration().setUniqueConstraints(customEntityTemplateUniqueConstraints);
		isUpdate = false;
		String message = "customFieldInstance.childEntity.save.successful";
		messages.info(new BundleKey("messages", message));
	}

	/**
	 * Gets the graphql query field.
	 *
	 * @return the graphql query field
	 */
	public GraphQLQueryField getGraphqlQueryField() {
		return graphqlQueryField;
	}

	/**
	 * Gets the graphql query fields.
	 *
	 * @return the graphql query fields
	 */
	public List<GraphQLQueryField> getGraphqlQueryFields() {
		if (entity.getNeo4JStorageConfiguration() != null) {
			if (entity != null && entity.getNeo4JStorageConfiguration().getGraphqlQueryFields() != null) {
				graphqlQueryFields = entity.getNeo4JStorageConfiguration().getGraphqlQueryFields();
			}
			return graphqlQueryFields;
		} else {
			return new ArrayList<>();
		}

	}

	/**
	 * Removes the graphql query field.
	 *
	 * @param selectedGraphQLQueryField the selected graph QL query field
	 */
	public void removeGraphqlQueryField(GraphQLQueryField selectedGraphQLQueryField) {
		for (GraphQLQueryField graphqlQueryField : graphqlQueryFields) {
			if (graphqlQueryField != null && graphqlQueryField.equals(selectedGraphQLQueryField)) {
				entity.getNeo4JStorageConfiguration().getGraphqlQueryFields().remove(selectedGraphQLQueryField);
				break;
			}
		}
		String message = "graphqlQueryField.remove.successful";
		messages.info(new BundleKey("messages", message));
	}

	/**
	 * Edits the graphql query field.
	 *
	 * @param selectedGraphQLQueryField the selected graph QL query field
	 */
	public void editGraphqlQueryField(GraphQLQueryField selectedGraphQLQueryField) {
		isUpdate = true;
		graphqlQueryField = selectedGraphQLQueryField;
	}

	/**
	 * Adds a graphql query field.
	 */
	public void addGraphqlQueryField() {
		isUpdate = false;
		graphqlQueryField = new GraphQLQueryField();
	}

	/**
	 * Save graphql query field.
	 */
	public void saveGraphqlQueryField() {
		if (!isUpdate) {
			graphqlQueryFields.add(graphqlQueryField);
		} else {
			for (GraphQLQueryField graphqlQueryField : graphqlQueryFields) {
				if (graphqlQueryField != null && graphqlQueryField.getFieldType().equals(this.graphqlQueryField.getFieldName())) {
					graphqlQueryField.setFieldName(this.graphqlQueryField.getFieldName());
					graphqlQueryField.setFieldType(this.graphqlQueryField.getFieldType());
					graphqlQueryField.setMultivalued(this.graphqlQueryField.isMultivalued());
					graphqlQueryField.setQuery(this.graphqlQueryField.getQuery());
					break;
				}
			}
		}
		entity.getNeo4JStorageConfiguration().setGraphqlQueryFields(graphqlQueryFields);
		isUpdate = false;
		String message = "graphqlQueryField.save.successful";
		messages.info(new BundleKey("messages", message));
	}

	/**
	 * Gets the mutation.
	 *
	 * @return the mutation
	 */
	public Mutation getMutation() {
		return mutation;
	}

	/**
	 * Gets the mutations.
	 *
	 * @return the mutations
	 */
	public List<Mutation> getMutations() {
		if (entity.getNeo4JStorageConfiguration() != null) {
			if (entity != null && entity.getNeo4JStorageConfiguration().getMutations() != null) {
				mutations = entity.getNeo4JStorageConfiguration().getMutations();
			}
			return mutations;
		} else {
			return new ArrayList<>();
		}
	}

	/**
	 * Removes the mutation.
	 *
	 * @param selectedMutation the selected mutation
	 */
	public void removeMutation(Mutation selectedMutation) {
		for (Mutation mutation : mutations) {
			if (mutation != null && mutation.equals(selectedMutation)) {
				entity.getNeo4JStorageConfiguration().getMutations().remove(selectedMutation);
				break;
			}
		}
		String message = "mutation.remove.successful";
		messages.info(new BundleKey("messages", message));
	}

	/**
	 * Edits the mutation.
	 *
	 * @param selectedMutation the selected mutation
	 */
	public void editMutation(Mutation selectedMutation) {
		isUpdate = true;
		mutation = selectedMutation;
	}

	/**
	 * Adds a mutation.
	 */
	public void addMutation() {
		isUpdate = false;
		mutation = new Mutation();
	}

	/**
	 * Save mutation.
	 */
	public void saveMutation() {
		if (!isUpdate) {
			mutation.getParameters().clear();
			if (CollectionUtils.isNotEmpty(parameters)) {
				Map<String, String> mapParameters = new HashMap<>();
				for (Map<String, String> mapValue : parameters) {
					mapParameters.put(mapValue.get("key"), mapValue.get("value"));
				}
				mutation.setParameters(mapParameters);
			}
			mutations.add(mutation);
		} else {
			for (Mutation mutation : mutations) {
				if (mutation != null) {
					mutation.setCode(this.mutation.getCode());
					mutation.setCypherQuery(this.mutation.getCypherQuery());
					mutation.getParameters().clear();
					if (CollectionUtils.isNotEmpty(parameters)) {
						Map<String, String> mapParameters = new HashMap<>();
						for (Map<String, String> mapValue : parameters) {
							mapParameters.put(mapValue.get("key"), mapValue.get("value"));
						}
						mutation.setParameters(mapParameters);
					}
					break;
				}
			}
		}
		entity.getNeo4JStorageConfiguration().setMutations(mutations);
		isUpdate = false;
		String message = "mutation.save.successful";
		messages.info(new BundleKey("messages", message));
	}

	/**
	 * Removes the map param.
	 *
	 * @param mapValue the map value
	 */
	public void removeMapParam(Map<String, String> mapValue) {
		parameters.remove(mapValue);
	}

	/**
	 * Adds the map param.
	 */
	public void addMapParam() {
		Map<String, String> mapValue = new HashMap<String, String>();
		mapValue.put("key", null);
		mapValue.put("value", null);
		parameters.add(mapValue);
	}

	/**
	 * Gets the checks if is update.
	 *
	 * @return the checks if is update
	 */
	public Boolean getIsUpdate() {
		return isUpdate;
	}

	/**
	 * Sets the checks if is update.
	 *
	 * @param isUpdate the new checks if is update
	 */
	public void setIsUpdate(Boolean isUpdate) {
		this.isUpdate = isUpdate;
	}

	/**
	 * The Class SortedTreeNode.
	 */
	public class SortedTreeNode extends DefaultTreeNode {

		private static final long serialVersionUID = 3694377290046737073L;
		private boolean isDisplayOnly = false;

		/**
		 * Instantiates a new sorted tree node.
		 */
		public SortedTreeNode() {
			super();
		}

		/**
		 * Instantiates a new sorted tree node.
		 *
		 * @param type     the type
		 * @param data     the data
		 * @param parent   the parent
		 * @param expanded the expanded
		 */
		public SortedTreeNode(GroupedCustomFieldTreeItemType type, Object data, TreeNode parent, Boolean expanded, boolean isDisplayOnly) {
			super(type.name(), data, parent);
			if (expanded != null && expanded) {
				this.setExpanded(true);
			}

			this.isDisplayOnly = isDisplayOnly;
		}

		public SortedTreeNode(GroupedCustomFieldTreeItemType type, Object data, TreeNode parent, Boolean expanded) {
			this(type, data, parent, expanded, false);
		}

		/**
		 * Gets the gui position for field.
		 *
		 * @return the gui position for field
		 */
		public String getGuiPositionForField() {

			if (getType().equals(GroupedCustomFieldTreeItemType.tab.name())) {
				return GroupedCustomFieldTreeItemType.tab.positionTag + ":" + getData() + ":" + getParent().getChildren().indexOf(this) + ";"
						+ GroupedCustomFieldTreeItemType.field.positionTag + ":" + getChildCount();

			} else if (getType().equals(GroupedCustomFieldTreeItemType.fieldGroup.name())) {
				String guiPosition = GroupedCustomFieldTreeItemType.fieldGroup.positionTag + ":" + getData() + ":" + getParent().getChildren().indexOf(this) + ";"
						+ GroupedCustomFieldTreeItemType.field.positionTag + ":" + getChildCount();
				if (getParent().getType().equals(GroupedCustomFieldTreeItemType.tab.name())) {
					guiPosition = GroupedCustomFieldTreeItemType.tab.positionTag + ":" + getParent().getData() + ":" + getParent().getParent().getChildren().indexOf(getParent())
							+ ";" + guiPosition;
				}
				return guiPosition;
			}
			return null;
		}

		/**
		 * Gets the gui position for action.
		 *
		 * @return the gui position for action
		 */
		public String getGuiPositionForAction() {

			if (getType().equals(GroupedCustomFieldTreeItemType.tab.name())) {
				return GroupedCustomFieldTreeItemType.tab.positionTag + ":" + getData() + ":" + getParent().getChildren().indexOf(this) + ";"
						+ GroupedCustomFieldTreeItemType.action.positionTag + ":" + getChildCount();

			} else if (getType().equals(GroupedCustomFieldTreeItemType.fieldGroup.name())) {
				String guiPosition = GroupedCustomFieldTreeItemType.fieldGroup.positionTag + ":" + getData() + ":" + getParent().getChildren().indexOf(this) + ";"
						+ GroupedCustomFieldTreeItemType.action.positionTag + ":" + getChildCount();
				if (getParent().getType().equals(GroupedCustomFieldTreeItemType.tab.name())) {
					guiPosition = GroupedCustomFieldTreeItemType.tab.positionTag + ":" + getParent().getData() + ":" + getParent().getParent().getChildren().indexOf(getParent())
							+ ";" + guiPosition;
				}
				return guiPosition;
			}
			return null;
		}

		public boolean isDisplayOnly() {
			return this.isDisplayOnly;
		}

		/**
		 * Can move up.
		 *
		 * @return true, if successful
		 */
		public boolean canMoveUp() {
			// Can not move if its is a first item in a tree and nowhere to move
			return !(getIndexInParent() == 0 && (this.getType().equals(GroupedCustomFieldTreeItemType.tab.name())
					|| (this.getType().equals(GroupedCustomFieldTreeItemType.fieldGroup.name()) && ((SortedTreeNode) this.getParent()).getIndexInParent() == 0)
					|| ((this.getType().equals(GroupedCustomFieldTreeItemType.field.name()) || this.getType().equals(GroupedCustomFieldTreeItemType.action.name()))
							&& this.getParent().getType().equals(GroupedCustomFieldTreeItemType.tab.name()) && ((SortedTreeNode) this.getParent()).getIndexInParent() == 0)));

		}

		/**
		 * Can move down.
		 *
		 * @return true, if successful
		 */
		public boolean canMoveDown() {

			return !(isLast() && (this.getType().equals(GroupedCustomFieldTreeItemType.tab.name())
					|| (this.getType().equals(GroupedCustomFieldTreeItemType.fieldGroup.name()) && ((SortedTreeNode) this.getParent()).isLast())
					|| ((this.getType().equals(GroupedCustomFieldTreeItemType.field.name()) || this.getType().equals(GroupedCustomFieldTreeItemType.action.name()))
							&& this.getParent().getType().equals(GroupedCustomFieldTreeItemType.tab.name()) && ((SortedTreeNode) this.getParent()).isLast())
					|| (this.getType().equals(GroupedCustomFieldTreeItemType.field.name()) && this.getParent().getType().equals(GroupedCustomFieldTreeItemType.fieldGroup.name())
							&& !((SortedTreeNode) this.getParent()).canMoveDown())));

		}

		/**
		 * Gets the index in parent.
		 *
		 * @return the index in parent
		 */
		protected int getIndexInParent() {
			return getParent().getChildren().indexOf(this);
		}

		/**
		 * Checks if is last.
		 *
		 * @return true, if is last
		 */
		protected boolean isLast() {
			return getIndexInParent() == this.getParent().getChildCount() - 1;
		}

		/**
		 * Gets the parent sibling down.
		 *
		 * @return the parent sibling down
		 */
		public SortedTreeNode getParentSiblingDown() {

			SortedTreeNode parent = (SortedTreeNode) this.getParent();
			while (parent.getParent() != null) {
				int parentIndex = parent.getIndexInParent();
				if (parent.getParent().getChildCount() > parentIndex + 1) {
					SortedTreeNode sibling = (SortedTreeNode) parent.getParent().getChildren().get(parentIndex + 1);
					return sibling;
				}
				parent = (SortedTreeNode) parent.getParent();
			}

			return null;
		}

		/**
		 * Gets the sibling down.
		 *
		 * @return the sibling down
		 */
		public SortedTreeNode getSiblingDown() {
			int currentIndex = this.getIndexInParent();
			if (getParent().getChildCount() > currentIndex + 1) {
				return (SortedTreeNode) getParent().getChildren().get(currentIndex + 1);
			}

			return null;
		}

		/**
		 * Gets the parent sibling up.
		 *
		 * @return the parent sibling up
		 */
		public SortedTreeNode getParentSiblingUp() {

			SortedTreeNode parent = (SortedTreeNode) this.getParent();
			while (parent.getParent() != null) {
				int parentIndex = parent.getIndexInParent();
				if (parentIndex > 0) {
					SortedTreeNode sibling = (SortedTreeNode) parent.getParent().getChildren().get(parentIndex - 1);
					return sibling;
				}
				parent = (SortedTreeNode) parent.getParent();
			}

			return null;
		}
	}

	/**
	 * Gets the available storages DM.
	 *
	 * @return the available storages DM
	 */
	public DualListModel<DBStorageType> getAvailableStoragesDM() {
		if (availableStoragesDM == null) {
			List<DBStorageType> perksSource = new ArrayList<>();
			for (DBStorageType dbStorageType : dbStorageTypeService.list()) {
				perksSource.add(dbStorageType);
			}
			List<DBStorageType> perksTarget = new ArrayList<DBStorageType>();
			if (getEntity().getAvailableStorages() != null) {
				perksTarget.addAll(getEntity().getAvailableStorages());
			}
			perksSource.removeAll(perksTarget);
			availableStoragesDM = new DualListModel<DBStorageType>(perksSource, perksTarget);
		}
		return availableStoragesDM;
	}

	/**
	 * Sets the available storages DM.
	 *
	 * @param availableStoragesDM the new available storages DM
	 */
	public void setAvailableStoragesDM(DualListModel<DBStorageType> availableStoragesDM) {
		this.availableStoragesDM = availableStoragesDM;
	}

	/**
	 * Gets the storage types list.
	 *
	 * @return the storage types list
	 */
	public List<DBStorageType> getStorageTypesList() {
		ArrayList<DBStorageType> arrayList = new ArrayList<>(availableStoragesDM.getSource());
		arrayList.addAll(availableStoragesDM.getTarget());
		return arrayList;
	}

	/**
	 * Add CET and its CFTs to selected module.
	 * @throws BusinessException 
	 */
	public void addToModuleForCET() throws BusinessException {
		if (entity != null && !getMeveoModule().equals(entity)) {
			Map<String, CustomFieldTemplate> customFieldTemplateMap = customFieldTemplateService.findByAppliesTo(entity.getAppliesTo());
			BusinessEntity businessEntity = (BusinessEntity) entity;
			MeveoModule module = meveoModuleService.findById(getMeveoModule().getId(), Arrays.asList("moduleItems", "patches", "releases", "moduleDependencies", "moduleFiles"));
			MeveoModuleItem item = new MeveoModuleItem(businessEntity);
			if (!module.getModuleItems().contains(item)) {
				try {
					meveoModuleService.addModuleItem(item, module);
				} catch (BusinessException e) {
					throw new BusinessException("Entity cannot be add or remove from the module", e);
				}
				
			} else {
				messages.error(new BundleKey("messages", "customizedEntities.cetExisted.error"), businessEntity.getCode(), module.getCode());
				return;
			}

			for (Map.Entry<String, CustomFieldTemplate> entry : customFieldTemplateMap.entrySet()) {
				CustomFieldTemplate cft = entry.getValue();
				MeveoModuleItem moduleItem = new MeveoModuleItem(cft);
				if (!module.getModuleItems().contains(moduleItem)) {
					meveoModuleService.addModuleItem(moduleItem, module);
				}
			}
			try {
				if (!StringUtils.isBlank(module.getModuleSource())) {
					module.setModuleSource(JacksonUtil.toString(updateModuleItemDto(module)));
				}
				meveoModuleService.mergeModule(module);
				messages.info(new BundleKey("messages", "customizedEntities.addToModule.successfull"), module.getCode());
			} catch (Exception e) {
				messages.error(new BundleKey("messages", "customizedEntities.addToModule.error"), module.getCode());
			}
		}
	}

	@Override
	public void delete(Long customEntityId) throws BusinessException {
		super.delete(customEntityId);
	}

	/**
	 * Delete given entities.
	 *
	 * @param entities the entities to delete
	 * @throws Exception the exception
	 */
	public void deleteMany(List<CustomizedEntity> entities) throws Exception {
		if (entities == null || entities.isEmpty()) {
			messages.info(new BundleKey("messages", "delete.entitities.noSelection"));
			return;
		}

		boolean allOk = true;
		for (CustomizedEntity entity : entities) {
			super.delete(entity.getCustomEntityId());
		}

		if (allOk) {
			messages.info(new BundleKey("messages", "delete.entitities.successful"));
		}
	}

	/**
	 * Gets the selected customized entities.
	 *
	 * @return the selected customized entities
	 */
	public List<CustomizedEntity> getSelectedCustomizedEntities() {
		return selectedCustomizedEntities;
	}

	/**
	 * Sets the selected customized entities.
	 *
	 * @param selectedCustomizedEntities the new selected customized entities
	 */
	public void setSelectedCustomizedEntities(List<CustomizedEntity> selectedCustomizedEntities) {
		this.selectedCustomizedEntities = selectedCustomizedEntities;
	}

	/**
	 * Reset mutation.
	 */
	public void resetMutation() {
		mutation = new Mutation();
		parameters = null;
	}

	/**
	 * Gets the parameters.
	 *
	 * @return the parameters
	 */
	public List<Map<String, String>> getParameters() {
		if (CollectionUtils.isEmpty(parameters)) {
			if (mutation.getParameters() != null) {
				parameters = new ArrayList<>();
				for (Map.Entry<String, String> entry : mutation.getParameters().entrySet()) {
					Map<String, String> item = new HashMap<>();
					item.put("key", entry.getKey());
					item.put("value", entry.getValue());
					parameters.add(item);
				}
			} else {
				parameters = new ArrayList<>();
			}
		}
		return parameters;
	}

	/**
	 * Sets the parameters.
	 *
	 * @param parameters the parameters
	 */
	public void setParameters(List<Map<String, String>> parameters) {
		this.parameters = parameters;
	}

	@Override
	protected List<String> getFormFieldsToFetch() {
		return Arrays.asList("customEntityCategory", "availableStorages");
	}

	@Override
	protected List<String> getListFieldsToFetch() {
		return Arrays.asList("customEntityCategory", "availableStorages");
	}

	public boolean showAuditedField() {
		return getAvailableStoragesDM().getTarget().contains(DBStorageType.SQL);
	}
	
	public void setIsEqualFn(String fn) {
		entity.setIsEqualFn(fn);
	}
	
	public String getIsEqualFn() throws IOException {
		return entity.getIsEqualFn();
	}
}
