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
package org.meveo.model.crm;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Cacheable;
import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OrderBy;
import javax.persistence.QueryHint;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.meveo.commons.utils.StringUtils;
import org.meveo.model.BaseEntity;
import org.meveo.model.BusinessEntity;
import org.meveo.model.CustomFieldEntity;
import org.meveo.model.DatePeriod;
import org.meveo.model.ExportIdentifier;
import org.meveo.model.ModuleItem;
import org.meveo.model.ModuleItemOrder;
import org.meveo.model.ObservableEntity;
import org.meveo.model.annotation.ImportOrder;
import org.meveo.model.catalog.Calendar;
import org.meveo.model.converter.StringListConverter;
import org.meveo.model.crm.custom.CustomFieldIndexTypeEnum;
import org.meveo.model.crm.custom.CustomFieldMapKeyEnum;
import org.meveo.model.crm.custom.CustomFieldMatrixColumn;
import org.meveo.model.crm.custom.CustomFieldMatrixColumn.CustomFieldColumnUseEnum;
import org.meveo.model.customEntities.CustomEntityTemplate;
import org.meveo.model.customEntities.CustomRelationshipTemplate;
import org.meveo.model.crm.custom.CustomFieldStorageTypeEnum;
import org.meveo.model.crm.custom.CustomFieldTypeEnum;
import org.meveo.model.crm.custom.CustomFieldValue;
import org.meveo.model.crm.custom.PrimitiveTypeEnum;
import org.meveo.model.persistence.DBStorageType;
import org.meveo.model.persistence.JsonTypes;
import org.meveo.model.shared.DateUtils;

/**
 * Represent a custom field, either related to a {@link CustomEntityTemplate} or a {@link CustomFieldEntity}, via the {@link #appliesTo} field
 *
 * @author clement.bareth
 * @author akadid abdelmounaim
 * @author Edward P. Legaspi | czetsuya@gmail.com
 * @version 6.9.0
 */
@Entity
@ModuleItem(value = "CustomFieldTemplate", path = "customFieldTemplates")
@ModuleItemOrder(20)
@Cacheable
@ExportIdentifier({ "code", "appliesTo" })
@ObservableEntity
@ImportOrder(3)
@Table(name = "crm_custom_field_tmpl", uniqueConstraints = @UniqueConstraint(columnNames = { "code", "applies_to" }))
@GenericGenerator(name = "ID_GENERATOR", strategy = "org.hibernate.id.enhanced.SequenceStyleGenerator", parameters = {
        @Parameter(name = "sequence_name", value = "crm_custom_fld_tmp_seq"), })
@NamedQueries({
		@NamedQuery(name = "CustomFieldTemplate.getCFTForCache", query = "SELECT DISTINCT cft from CustomFieldTemplate cft left join fetch cft.storages left join fetch cft.calendar where cft.disabled=false order by cft.appliesTo"),
		@NamedQuery(name = "CustomFieldTemplate.getCFTForIndex", query = "SELECT DISTINCT cft from CustomFieldTemplate cft left join fetch cft.storages where cft.disabled=false and cft.indexType is not null "),
		@NamedQuery(name = "CustomFieldTemplate.getCFTByCodeAndAppliesTo", query = "SELECT DISTINCT cft from CustomFieldTemplate cft left join fetch cft.storages where cft.code=:code and cft.appliesTo=:appliesTo", hints = {
				@QueryHint(name = "org.hibernate.cacheable", value = "true") }),
		@NamedQuery(name = "CustomFieldTemplate.getCftUniqueFieldsByApplies", query = "SELECT DISTINCT cft from CustomFieldTemplate cft left join fetch cft.storages where cft.unique=true and cft.appliesTo=:appliesTo", hints = {
				@QueryHint(name = "org.hibernate.cacheable", value = "true") }),
		@NamedQuery(name = "CustomFieldTemplate.getCFTByAppliesTo", query = "SELECT DISTINCT cft from CustomFieldTemplate cft left join fetch cft.storages where cft.appliesTo=:appliesTo order by cft.code", hints = {
				@QueryHint(name = "org.hibernate.cacheable", value = "false") }) })
public class CustomFieldTemplate extends BusinessEntity {

    private static final long serialVersionUID = -1403961759495272885L;

    /** The default max length string. */
    public static long DEFAULT_MAX_LENGTH_STRING = 255L;

    /** The entity reference classname cetcode separator. */
    public static String ENTITY_REFERENCE_CLASSNAME_CETCODE_SEPARATOR = " - ";

    /**
     * Grouped customField tree item type.
     */
    public enum GroupedCustomFieldTreeItemType {

    	/** As root. */
    	root(null),
    	
    	/** As tab. */
    	tab("tab"),
    	
    	/** As field group. */
    	fieldGroup("fieldGroup"), 
    	
    	/** As field. */
    	field("field"), 
    	
    	/** As action. */
    	action("action");

    	/** Position tag. */
    	public String positionTag;

    	/**
		 * Instantiates a new grouped custom field tree item type.
		 *
		 * @param tag the tag
		 */
	    GroupedCustomFieldTreeItemType(String tag) {
    		this.positionTag = tag;
    	}
    }

    @Column(name = "field_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private CustomFieldTypeEnum fieldType = CustomFieldTypeEnum.STRING;

    @Column(name = "applies_to", nullable = false, length = 100)
    @Size(max = 100)
    @NaturalId
    private String appliesTo;

    @Type(type = "numeric_boolean")
    @Column(name = "value_required")
    private boolean valueRequired;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "crm_custom_field_tmpl_val")
    private Map<String, String> listValues;

    @ElementCollection(fetch = FetchType.EAGER)
    @OrderBy("columnUse ASC, position ASC")
    @CollectionTable(name = "crm_custom_field_tmpl_mcols", joinColumns = { @JoinColumn(name = "cft_id") })
    @AttributeOverrides({ @AttributeOverride(name = "code", column = @Column(name = "code", nullable = false, length = 20)),
            @AttributeOverride(name = "label", column = @Column(name = "label", nullable = false, length = 50)),
            @AttributeOverride(name = "keyType", column = @Column(name = "key_type", nullable = false, length = 10)),
            @AttributeOverride(name = "columnUse", column = @Column(name = "column_use", nullable = false)) })
    private List<CustomFieldMatrixColumn> matrixColumns = new ArrayList<>();

    @Transient
    private List<CustomFieldMatrixColumn> matrixKeyColumns;

    @Transient
    private List<CustomFieldMatrixColumn> matrixValueColumns;

    @Type(type = "numeric_boolean")
    @Column(name = "versionable")
    private boolean versionable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_id")
    private Calendar calendar;

    @Column(name = "default_value", length = 250)
    @Size(max = 250)
    private String defaultValue;

    @Type(type = "numeric_boolean")
    @Column(name = "inh_as_def_value")
    private boolean useInheritedAsDefaultValue;

    /**
     * Reference to an entity. A classname. In case of CustomEntityTemplate, classname consist of "CustomEntityTemplate - &lt;CustomEntityTemplate code&gt;"
     */
    @Column(name = "entity_clazz")
    @Size(max = 255)
    private String entityClazz;

    @Column(name = "storage_type", nullable = false)
    @Enumerated(EnumType.STRING)
    @NotNull
    private CustomFieldStorageTypeEnum storageType = CustomFieldStorageTypeEnum.SINGLE;

    @Column(name = "mapkey_type")
    @Enumerated(EnumType.STRING)
    private CustomFieldMapKeyEnum mapKeyType;

    @Type(type = "numeric_boolean")
    @Column(name = "trigger_end_period_event", nullable = false)
    private boolean triggerEndPeriodEvent;

    @Transient
    private PrimitiveTypeEnum primitiveType;
    
	@Transient
	private boolean isInDraft = false;

    /**
     * Where field should be displayed. Format: tab:&lt;tab name&gt;:&lt;tab relative position&gt;;fieldGroup:&lt;fieldgroup name&gt;:&lt;fieldgroup relative
     * position&gt;;field:&lt;field relative position in fieldgroup/tab&gt;
     * 
     * Tab and field group names support translation in the following format: &lt;default value&gt;|&lt;language3 letter key=translated value&gt;
     * 
     * e.g. tab:Tab default title|FRA=Title in french|ENG=Title in english:0;fieldGroup:Field group default label|FRA=Field group label in french|ENG=Field group label in
     * english:0;field:0 OR tab:Second tab:1;field:1
     */
    @Column(name = "gui_position", length = 2000)
    @Size(max = 2000)
    private String guiPosition;

    @Type(type = "numeric_boolean")
    @Column(name = "allow_edit")
    @NotNull
    private boolean allowEdit = true;

    @Type(type = "numeric_boolean")
    @Column(name = "hide_on_new")
    @NotNull
    private boolean hideOnNew;

    @Column(name = "max_value")
    private Long maxValue;

    @Column(name = "min_value")
    private Long minValue;

    @Column(name = "reg_exp", length = 80)
    @Size(max = 80)
    private String regExp;

    @Column(name = "applicable_on_el", length = 2000)
    @Size(max = 2000)
    private String applicableOnEl;
    
    /**
     * @apiNote Use {@link #relationship} instead
     */
    @Column(name = "relationship_name", updatable = false)
    private String relationshipName;
    
    /**
     * Relationship that links the current entity and the target entity.
     * Required in case of Neo4J storage and {@link CustomFieldTypeEnum#ENTITY} type.
     * Replacement for {@link #relationshipName}
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "crt_id")
    private CustomRelationshipTemplate relationship;

    /**
     * Child entity fields to display as summary. Field names are separated by a comma.
     */
    @Column(name = "che_fields", length = 500)
    @Size(max = 500)
    private String childEntityFields;

    /**
     * If and how custom field value should be indexed in Elastic Search
     */
    @Column(name = "index_type", length = 10)
    @Enumerated(EnumType.STRING)
    private CustomFieldIndexTypeEnum indexType;

    /**
     * Tags assigned to custom field template
     */
    @Column(name = "tags", length = 2000)
    @Size(max = 2000)
    private String tags;

    @Type(type = JsonTypes.JSON)
    @Column(name = "description_i18n", columnDefinition = "text")
    private Map<String, String> descriptionI18n;
    
    @Type(type="numeric_boolean") @ColumnDefault("0")
    @Column(name = "IS_UNIQUE")
    @NotNull
    private boolean unique;

    
    @Type(type="numeric_boolean") @ColumnDefault("1")
    @Column(name = "IS_FILTER")
    @NotNull
    private boolean filter = true;

    /**
     * Whether the field will appear in the listing of custom table
     */
    @Type(type="numeric_boolean") @ColumnDefault("0")
    @Column(name = "IS_SUMMARY")
    @NotNull
    private boolean summary = false;
    
    @Column(name = "EXPRESSION_SEPARATOR", length = 2)
    @Size(max = 2)
    private String expressionSeparator;

    /**
     * Whether we can use this field as the identifier of the entity
     */
    @Column(name = "identifier")
    @Type(type = "numeric_boolean")
    @ColumnDefault("0")
    private boolean identifier;

    /** Storage where the cft value will be stored. */
//    @Column(name = "storages", columnDefinition = "TEXT")
//    @Type(type = JsonTypes.JSON_LIST)
	@JoinTable(name = "cft_db_storage", inverseJoinColumns = @JoinColumn(name = "db_storage_code"), joinColumns = @JoinColumn(name = "cft_id"))
	@ManyToMany
	List<DBStorageType> storages = new ArrayList<>();

    /**
     * Display format for Date type only
     */
    @Column(name = "display_format", length = 20)
    @Size(max = 20)
    private String displayFormat = "dd-M-yyyy HH:mm:ss";

	/**
	 * List of content types
	 */
	@Column(name = "content_types", length = 2000)
	@Convert(converter = StringListConverter.class)
	private List<String> contentTypes = new ArrayList<>();

	/**
	 * List of file extensions
	 */
	@Column(name = "file_extensions", length = 2000)
	@Convert(converter = StringListConverter.class)
	private List<String> fileExtensions = new ArrayList<>();

	/**
	 * Maximum size in kb.
	 */
	@Column(name = "max_file_size_allowed_kb")
	private Long maxFileSizeAllowedInKb;

	/**
	 * Supports EL variables.
	 */
	@Column(name = "file_path")
	private String filePath;

    /**
     * Whether the binaries will be accessible through the file explorer
     */
	@Column(name = "save_on_explorer")
    @Type(type = "numeric_boolean")
    @ColumnDefault("0")
    private boolean saveOnExplorer;

    @Column(name = "samples", columnDefinition = "TEXT")
    @Type(type = JsonTypes.JSON_LIST)
    private List<String> samples = new ArrayList<>();
    
    @Type(type = "numeric_boolean")
	@Column(name = "audited")
	private boolean audited = false;
    
    @Type(type = "numeric_boolean")
	@Column(name = "is_persisted")
	private boolean persisted = true;

    /**
     * Database field name - derived from code
     */
    @Transient
    private String dbFieldname;

    @Transient
    private String contentType;

    @Transient
    private String fileExtension;
    
    @Transient
	private boolean hasReferenceJpaEntity = false;

    /**
	 * Checks if is whether the binaries will be accessible through the file explorer.
	 *
	 * @return the whether the binaries will be accessible through the file explorer
	 */
    public boolean isSaveOnExplorer() {
        return saveOnExplorer;
    }

    /**
	 * Sets the whether the binaries will be accessible through the file explorer.
	 *
	 * @param saveOnExplorer the new whether the binaries will be accessible through the file explorer
	 */
    public void setSaveOnExplorer(boolean saveOnExplorer) {
        this.saveOnExplorer = saveOnExplorer;
    }

    /**
	 * Gets the storage where the cft value will be stored.
	 *
	 * @return the storage where the cft value will be stored
	 */
    public List<DBStorageType> getStoragesNullSafe() {
        return storages == null ? new ArrayList<>() : storages;
    }
    
    public boolean isInDraft() {
		return isInDraft;
	}

	public void setInDraft(boolean isInDraft) {
		this.isInDraft = isInDraft;
	}

	public List<DBStorageType> getStorages() {
        return storages;
    }

    /**
	 * Sets the storage where the cft value will be stored.
	 *
	 * @param storages the new storage where the cft value will be stored
	 */
    public void setStorages(List<DBStorageType> storages) {
        this.storages = storages;
    }

    /**
     * Get a database field name derived from a code value. Lowercase and spaces replaced by "_".
     *
     * @return Database field name
     */
    public String getDbFieldname() {
        if (dbFieldname == null && code != null) {
            dbFieldname = CustomFieldTemplate.getDbFieldname(code);
        }
        return dbFieldname;
    }


    /**
     * Get a database field name derived from a code value. Lowercase and spaces replaced by "_".
     *
     * @param code Field code
     * @return Database field name
     */
    public static String getDbFieldname(String code) {
        return BaseEntity.cleanUpAndLowercaseCodeOrId(code);
    }

    /**
     * Get GUI 'field' position value in a GUIPosition value as in e.g. "tab:Configuration:0;fieldGroup:Purge counter periods:1;field:0"
     *
     * @return GUI 'field' position value
     */
    public int getGUIFieldPosition() {
        if (guiPosition != null) {
            String position = getGuiPositionParsed().get(GroupedCustomFieldTreeItemType.field.positionTag + "_pos");
            if (position != null) {
                try {
                    return Integer.parseInt(position);
                } catch (NumberFormatException ignored) {

                }
            }
        }
        return 0;
    }

    /**
	 * Checks if is whether the field will appear in the listing of custom table.
	 *
	 * @return the whether the field will appear in the listing of custom table
	 */
    public boolean isSummary() {
        return summary;
    }

    /**
	 * Sets the whether the field will appear in the listing of custom table.
	 *
	 * @param summary the new whether the field will appear in the listing of custom table
	 */
    public void setSummary(boolean summary) {
        this.summary = summary;
    }

    /**
	 * Checks if is whether we can use this field as the identifier of the entity.
	 *
	 * @return the whether we can use this field as the identifier of the entity
	 */
    public boolean isIdentifier() {
        return identifier;
    }

    /**
	 * Sets the whether we can use this field as the identifier of the entity.
	 *
	 * @param identifier the new whether we can use this field as the identifier of the entity
	 */
    public void setIdentifier(boolean identifier) {
        this.identifier = identifier;
    }

    /**
	 * Gets the relationship name.
	 *
	 * @return the relationship name
	 */
    public String getRelationshipName() {
    	if(relationshipName != null) {
    		return relationshipName;
    		
    	} else if(relationship != null) {
    		return relationship.getName();
    	}
    	
    	return null;
	}

    /**
	 * Sets the relationship name that links current CET to target CET or the current CET to the target Binary
	 *
	 * @param relationshipName Name of the relationship 
	 */
	public void setRelationshipName(String relationshipName) {
		this.relationshipName = relationshipName;
	}
	
	/**
	 * Gets the relationship that links the current entity and the target entity.
	 *
	 * @return the relationship that links the current entity and the target entity
	 */
	public CustomRelationshipTemplate getRelationship() {
		return relationship;
	}

	/**
	 * Sets the relationship that links the current entity and the target entity.
	 *
	 * @param relationship the new relationship that links the current entity and the target entity
	 */
	public void setRelationship(CustomRelationshipTemplate relationship) {
		this.relationship = relationship;
	}

	/**
	 * Gets the field type.
	 *
	 * @return the field type
	 */
	public CustomFieldTypeEnum getFieldType() {
        return fieldType;
    }

    /**
	 * Sets the field type.
	 *
	 * @param fieldType the new field type
	 */
    public void setFieldType(CustomFieldTypeEnum fieldType) {
        this.fieldType = fieldType;
    }
    
    /**
	 * Gets the applies to.
	 *
	 * @return the applies to
	 */
    public String getAppliesTo() {
        return appliesTo;
    }

    /**
	 * Sets the applies to.
	 *
	 * @param appliesTo the new applies to
	 */
    public void setAppliesTo(String appliesTo) {
    	if(appliesTo == null) {
    		throw new IllegalArgumentException("Applies to query can't be null !");
    	}
    	
        this.appliesTo = appliesTo;
    }

    /**
	 * Checks if is value required.
	 *
	 * @return true, if is value required
	 */
    public boolean isValueRequired() {
        return valueRequired;
    }

    /**
	 * Sets the value required.
	 *
	 * @param valueRequired the new value required
	 */
    public void setValueRequired(boolean valueRequired) {
        this.valueRequired = valueRequired;
    }

    /**
	 * Gets the list values.
	 *
	 * @return the list values
	 */
    public Map<String, String> getListValues() {
        return listValues;
    }

    /**
	 * Gets the list values sorted.
	 *
	 * @return the list values sorted
	 */
    public Map<String, String> getListValuesSorted() {
        if (listValues != null && !listValues.isEmpty()) {
            Comparator<String> dropdownListComparator = (s1, s2) -> {
                try {
                    return Integer.valueOf(s1).compareTo(Integer.valueOf(s2));
                } catch (NumberFormatException e) {
                    return s1.compareTo(s2);
                }
            };

            Map<String, String> newList = new TreeMap<>(dropdownListComparator);
            newList.putAll(listValues);
            return newList;
        }

        return listValues;
    }

    /**
	 * Sets the list values.
	 *
	 * @param listValues the list values
	 */
    public void setListValues(Map<String, String> listValues) {
        this.listValues = listValues;
    }

    /**
	 * Gets the matrix columns.
	 *
	 * @return the matrix columns
	 */
    public List<CustomFieldMatrixColumn> getMatrixColumns() {
        return matrixColumns;
    }

    /**
	 * Sets the matrix columns.
	 *
	 * @param matrixColumns the new matrix columns
	 */
    public void setMatrixColumns(List<CustomFieldMatrixColumn> matrixColumns) {
        this.matrixColumns = matrixColumns;
    }

    /**
	 * Adds the matrix key column.
	 */
    public void addMatrixKeyColumn() {
        CustomFieldMatrixColumn column = new CustomFieldMatrixColumn();
        column.setColumnUse(CustomFieldColumnUseEnum.USE_KEY);
        column.setPosition(getMatrixKeyColumns().size() + 1);
        this.matrixColumns.add(column);
        matrixKeyColumns = null;
    }

    /**
	 * Adds the matrix value column.
	 */
    public void addMatrixValueColumn() {
        CustomFieldMatrixColumn column = new CustomFieldMatrixColumn();
        column.setColumnUse(CustomFieldColumnUseEnum.USE_VALUE);
        column.setPosition(getMatrixValueColumns().size() + 1);
        this.matrixColumns.add(column);
        matrixValueColumns = null;
    }

    /**
	 * Removes the matrix column.
	 *
	 * @param columnToRemove the column to remove
	 */
    public void removeMatrixColumn(CustomFieldMatrixColumn columnToRemove) {
        this.matrixColumns.remove(columnToRemove);
        matrixKeyColumns = null;
        matrixValueColumns = null;

        // Reorder position
        int i = 0;
        for (CustomFieldMatrixColumn column : getMatrixKeyColumns()) {
            i++;
            column.setPosition(i);
        }
        i = 0;
        for (CustomFieldMatrixColumn column : getMatrixValueColumns()) {
            i++;
            column.setPosition(i);
        }
    }

    /**
     * Find a corresponding matrix column by its index (position). Note: result might differ if matrix column was added and value was not updated
     * 
     * @param index Index to return the column for
     * @return Matched matrix column
     */
    public CustomFieldMatrixColumn getMatrixColumnByIndex(int index) {
        if (index >= matrixColumns.size()) {
            return null;
        }
        return matrixColumns.get(index);
    }

    /**
     * Extract codes of matrix columns into a sorted list by column index.
     * 
     * @return A list of matrix column codes
     */
    public List<String> getMatrixColumnCodes() {

        List<String> matrixColumnNames = null;
        if (storageType == CustomFieldStorageTypeEnum.MATRIX) {
            matrixColumnNames = new ArrayList<>();
            for (CustomFieldMatrixColumn column : matrixColumns) {
                matrixColumnNames.add(column.getCode());
            }
        }
        return matrixColumnNames;
    }

    /**
	 * Sets whether the field is versionable.
	 *
	 * @param versionable true if the field is versionable
	 */
    public void setVersionable(boolean versionable) {
        this.versionable = versionable;
    }

    /**
	 * Checks if is versionable.
	 *
	 * @return true, if is versionable
	 */
    public boolean isVersionable() {
        return versionable;
    }

    /**
	 * Gets the calendar.
	 *
	 * @return the calendar
	 */
    public Calendar getCalendar() {
        return calendar;
    }

    /**
	 * Sets the calendar.
	 *
	 * @param calendar the new calendar
	 */
    public void setCalendar(Calendar calendar) {
        this.calendar = calendar;
    }

    /**
	 * Gets the default value.
	 *
	 * @return the default value
	 */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
	 * Sets the default value.
	 *
	 * @param defaultValue the new default value
	 */
    public void setDefaultValue(String defaultValue) {
        if (StringUtils.isBlank(defaultValue) && fieldType == CustomFieldTypeEnum.BOOLEAN) {
            defaultValue = "false";
        }
        this.defaultValue = defaultValue;
    }

    /**
	 * Checks if is use inherited as default value.
	 *
	 * @return true, if is use inherited as default value
	 */
    public boolean isUseInheritedAsDefaultValue() {
        return useInheritedAsDefaultValue;
    }

    /**
	 * Sets the use inherited as default value.
	 *
	 * @param useInheritedAsDefaultValue the new use inherited as default value
	 */
    public void setUseInheritedAsDefaultValue(boolean useInheritedAsDefaultValue) {
        this.useInheritedAsDefaultValue = useInheritedAsDefaultValue;
    }

    /**
	 * Gets the reference to an entity.
	 *
	 * @return the reference to an entity
	 */
    public String getEntityClazz() {
        return entityClazz;
    }

    /**
	 * Sets the reference to an entity.
	 *
	 * @param entityClazz the new reference to an entity
	 */
    public void setEntityClazz(String entityClazz) {
        this.entityClazz = entityClazz;
    }

    /**
	 * Gets the entity clazz cet code.
	 *
	 * @return the entity clazz cet code
	 */
    public String getEntityClazzCetCode() {
        return CustomFieldTemplate.retrieveCetCode(entityClazz);
    }

    /**
	 * Gets the primitive type.
	 *
	 * @return the primitive type
	 */
    public PrimitiveTypeEnum getPrimitiveType() {
        return primitiveType;
    }

    /**
	 * Sets the primitive type.
	 *
	 * @param primitiveType the new primitive type
	 */
    public void setPrimitiveType(PrimitiveTypeEnum primitiveType) {
        this.primitiveType = primitiveType;
    }

    /**
	 * Gets the reference entity clazz cet code.
	 *
	 * @return the reference entity clazz cet code
	 */
    public String getReferenceEntityClazzCetCode() {
        boolean isChildEntity = CustomFieldTypeEnum.CHILD_ENTITY.equals(this.fieldType);
        String labelPrefix = isChildEntity ? "Child entity - " : "Reference to ";
        return labelPrefix + CustomFieldTemplate.retrieveCetCode(entityClazz);
    }

    /**
	 * Gets the reference entity class name.
	 *
	 * @return the reference entity class name
	 */
    public String getReferenceEntityClassName() {
        return CustomFieldTemplate.retrieveClassName(entityClazz);
    }

    /**
     * Retrieve a class name from classname and code as it is stored in entityClazz field.
     *
     * @param entityClazz entity class
     * @return className
     */
    public static String retrieveClassName(String entityClazz) {
        if (entityClazz == null) {
            return null;
        }
        if (entityClazz.startsWith(CustomEntityTemplate.class.getName()) || 
        		entityClazz.startsWith("org.meveo.model.customEntities.CustomEntityTemplate")
    		) {
            return CustomEntityTemplate.class.getName();
		}
        return entityClazz;
    }

    /**
     * Retrieve a cet code from classname and code as it is stored in entityClazz field.
     * 
     * @param entityClazz entity class
     * @return code
     */
    public static String retrieveCetCode(String entityClazz) {
        if (entityClazz == null) {
            return null;
        }
        
        if (entityClazz.startsWith(CustomEntityTemplate.class.getName())) {
            return entityClazz.substring(entityClazz.indexOf(ENTITY_REFERENCE_CLASSNAME_CETCODE_SEPARATOR) + ENTITY_REFERENCE_CLASSNAME_CETCODE_SEPARATOR.length());
        }
        
        // Suport for old api
        if (entityClazz.startsWith("org.meveo.model.customEntities.CustomEntityTemplate")) {
            return entityClazz.substring(entityClazz.indexOf(ENTITY_REFERENCE_CLASSNAME_CETCODE_SEPARATOR) + ENTITY_REFERENCE_CLASSNAME_CETCODE_SEPARATOR.length());
        }
        
        // Suport for old api
        if (entityClazz.startsWith("org.meveo.model.custom.entities.CustomEntityTemplate")) {
            return entityClazz.substring(entityClazz.indexOf(ENTITY_REFERENCE_CLASSNAME_CETCODE_SEPARATOR) + ENTITY_REFERENCE_CLASSNAME_CETCODE_SEPARATOR.length());
        }
        
        return entityClazz;
    }

    /**
	 * Gets the default value converted.
	 *
	 * @return the default value converted
	 */
    public Object getDefaultValueConverted() {
        if (defaultValue == null) {
        	return null;
        }
        try {
            if (fieldType == CustomFieldTypeEnum.DOUBLE) {
                return Double.parseDouble(defaultValue);
            } else if (fieldType == CustomFieldTypeEnum.LONG) {
                return Long.parseLong(defaultValue);
            } else if (fieldType == CustomFieldTypeEnum.STRING || fieldType == CustomFieldTypeEnum.LIST || fieldType == CustomFieldTypeEnum.TEXT_AREA || fieldType == CustomFieldTypeEnum.LONG_TEXT) {
                return defaultValue;
            } else if (fieldType == CustomFieldTypeEnum.DATE) {
                return DateUtils.parseDateWithPattern(defaultValue, DateUtils.DATE_TIME_PATTERN);
            }else if (fieldType == CustomFieldTypeEnum.BOOLEAN) {
                return Boolean.parseBoolean(defaultValue);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /**
	 * Gets the storage type.
	 *
	 * @return the storage type
	 */
    public CustomFieldStorageTypeEnum getStorageType() {
        return storageType;
    }

    /**
	 * Sets the storage type.
	 *
	 * @param storageType the new storage type
	 */
    public void setStorageType(CustomFieldStorageTypeEnum storageType) {
        this.storageType = storageType;
    }

    /**
	 * Gets the map key type.
	 *
	 * @return the map key type
	 */
    public CustomFieldMapKeyEnum getMapKeyType() {
        return mapKeyType;
    }

    /**
	 * Sets the map key type.
	 *
	 * @param mapKeyType the new map key type
	 */
    public void setMapKeyType(CustomFieldMapKeyEnum mapKeyType) {
        this.mapKeyType = mapKeyType;
    }

    /**
	 * Checks if is trigger end period event.
	 *
	 * @return true, if is trigger end period event
	 */
    public boolean isTriggerEndPeriodEvent() {
        return triggerEndPeriodEvent;
    }

    /**
	 * Sets the trigger end period event.
	 *
	 * @param triggerEndPeriodEvent the new trigger end period event
	 */
    public void setTriggerEndPeriodEvent(boolean triggerEndPeriodEvent) {
        this.triggerEndPeriodEvent = triggerEndPeriodEvent;
    }

    /**
	 * Gets the where field should be displayed.
	 *
	 * @return the where field should be displayed
	 */
    public String getGuiPosition() {
        return guiPosition;
    }

    /**
	 * Sets the where field should be displayed.
	 *
	 * @param guiPosition the new where field should be displayed
	 */
    public void setGuiPosition(String guiPosition) {
        this.guiPosition = guiPosition;
    }

    /**
	 * Gets the gui position parsed.
	 *
	 * @return the gui position parsed
	 */
    public Map<String, String> getGuiPositionParsed() {

        if (guiPosition == null) {
            return null;
        }

        Map<String, String> parsedInfo = new HashMap<>();

        String[] positions = guiPosition.split(";");

        for (String position : positions) {
            String[] positionDetails = position.split(":");
            if (!positionDetails[0].equals(GroupedCustomFieldTreeItemType.field.positionTag)) {
                parsedInfo.put(positionDetails[0] + "_name", positionDetails[1]);
                if (positionDetails.length == 3) {
                    parsedInfo.put(positionDetails[0] + "_pos", positionDetails[2]);
                }
            } else if (positionDetails.length == 2) {
                parsedInfo.put(positionDetails[0] + "_pos", positionDetails[1]);
            }
        }

        return parsedInfo;
    }

    /**
	 * Checks if is allow edit.
	 *
	 * @return true, if is allow edit
	 */
    public boolean isAllowEdit() {
        return allowEdit;
    }

    /**
	 * Sets the allow edit.
	 *
	 * @param allowEdit the new allow edit
	 */
    public void setAllowEdit(boolean allowEdit) {
        this.allowEdit = allowEdit;
    }

    /**
	 * Checks if is hide on new.
	 *
	 * @return true, if is hide on new
	 */
    public boolean isHideOnNew() {
        return hideOnNew;
    }

    /**
	 * Sets the hide on new.
	 *
	 * @param hideOnNew the new hide on new
	 */
    public void setHideOnNew(boolean hideOnNew) {
        this.hideOnNew = hideOnNew;
    }

    /**
	 * Gets the max value.
	 *
	 * @return the max value
	 */
    public Long getMaxValue() {
        return maxValue;
    }

    /**
	 * Sets the max value.
	 *
	 * @param maxValue the new max value
	 */
    public void setMaxValue(Long maxValue) {
        this.maxValue = maxValue;
    }

    /**
	 * Gets the min value.
	 *
	 * @return the min value
	 */
    public Long getMinValue() {
        return minValue;
    }

    /**
	 * Sets the min value.
	 *
	 * @param minValue the new min value
	 */
    public void setMinValue(Long minValue) {
        this.minValue = minValue;
    }

    /**
	 * Gets the reg exp.
	 *
	 * @return the reg exp
	 */
    public String getRegExp() {
        return regExp;
    }

    /**
	 * Sets the reg exp.
	 *
	 * @param regExp the new reg exp
	 */
    public void setRegExp(String regExp) {
        this.regExp = regExp;
    }

    /**
	 * Gets the applicable on el.
	 *
	 * @return the applicable on el
	 */
    public String getApplicableOnEl() {
        return applicableOnEl;
    }

    /**
	 * Sets the applicable on el.
	 *
	 * @param applicableOnEl the new applicable on el
	 */
    public void setApplicableOnEl(String applicableOnEl) {
        this.applicableOnEl = applicableOnEl;
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (!(obj instanceof CustomFieldTemplate)) {
            return false;
        }

        CustomFieldTemplate other = (CustomFieldTemplate) obj;

        if (getId() != null && other.getId() != null && getId().equals(other.getId())) {
             return true;
        }

        if (code == null && other.getCode() != null) {
            return false;
        } else if (!code.equals(other.getCode())) {
            return false;
        } else if (appliesTo == null && other.getAppliesTo() != null) {
            return false;
        } else return appliesTo.equals(other.getAppliesTo());
    }

    /**
	 * Gets the child entity fields to display as summary.
	 *
	 * @return the child entity fields to display as summary
	 */
    public String getChildEntityFields() {
        return childEntityFields;
    }

    /**
	 * Gets the child entity fields as list.
	 *
	 * @return the child entity fields as list
	 */
    public String[] getChildEntityFieldsAsList() {
        if (childEntityFields != null) {
            return childEntityFields.split("\\|");
        }
        return new String[0];
    }

    /**
	 * Sets the child entity fields to display as summary.
	 *
	 * @param childEntityFields the new child entity fields to display as summary
	 */
    public void setChildEntityFields(String childEntityFields) {
        this.childEntityFields = childEntityFields;
    }

    /**
	 * Sets the child entity fields as list.
	 *
	 * @param cheFields the new child entity fields as list
	 */
    public void setChildEntityFieldsAsList(List<String> cheFields) {
        this.childEntityFields = StringUtils.concatenate("|", cheFields);
    }

    /**
	 * Gets the if and how custom field value should be indexed in Elastic Search.
	 *
	 * @return the if and how custom field value should be indexed in Elastic Search
	 */
    public CustomFieldIndexTypeEnum getIndexType() {
        return indexType;
    }

    /**
	 * Sets the if and how custom field value should be indexed in Elastic Search.
	 *
	 * @param indexType the new if and how custom field value should be indexed in Elastic Search
	 */
    public void setIndexType(CustomFieldIndexTypeEnum indexType) {
        this.indexType = indexType;
    }
    
    /**
	 * Checks if is visible on UI.
	 *
	 * @return true, if is visible on UI
	 */
    public boolean isVisibleOnUI() {
        return allowEdit;
    }

    /**
	 * Gets the tags assigned to custom field template.
	 *
	 * @return the tags assigned to custom field template
	 */
    public String getTags() {
        return tags;
    }

    /**
	 * Sets the tags assigned to custom field template.
	 *
	 * @param tags the new tags assigned to custom field template
	 */
    public void setTags(String tags) {
        this.tags = tags;
    }

    
    
    /**
	 * Checks if is unique.
	 *
	 * @return true, if is unique
	 */
    public boolean isUnique() {
		return unique;
	}

	/**
	 * Sets the unique.
	 *
	 * @param unique the new unique
	 */
	public void setUnique(boolean unique) {
		this.unique = unique;
	}

	/**
	 * Checks if is filter.
	 *
	 * @return true, if is filter
	 */
	public boolean isFilter() {
		return filter;
	}

	/**
	 * Sets the filter.
	 *
	 * @param filter the new filter
	 */
	public void setFilter(boolean filter) {
		this.filter = filter;
	}

	/**
	 * Gets the expression separator.
	 *
	 * @return the expression separator
	 */
	public String getExpressionSeparator() {
		return expressionSeparator;
	}

	/**
	 * Sets the expression separator.
	 *
	 * @param expressionSeparator the new expression separator
	 */
	public void setExpressionSeparator(String expressionSeparator) {
		this.expressionSeparator = expressionSeparator;
	}

	@Override
    public String toString() {
        return String.format("CustomFieldTemplate [id=%s, appliesTo=%s, code=%s]", id, appliesTo, code);
    }

    /**
     * Instantiate a CustomFieldValue from a template, setting a default value if applicable.
     *
     * @return CustomFieldValue object
     */
    public CustomFieldValue toDefaultCFValue() {
        CustomFieldValue cfValue = new CustomFieldValue();

        // Set a default value
        if (getStorageType() == CustomFieldStorageTypeEnum.SINGLE) {
            cfValue.setValue(getDefaultValueConverted());
        }

        return cfValue;
    }

    /**
     * Get a date period for a given date. Applies only to CFT versionable by a calendar.
     * 
     * @param date Date
     * @return Date period matching calendar's dates
     */
    public DatePeriod getDatePeriod(Date date) {
        if (isVersionable() && getCalendar() != null) {
            return new DatePeriod(getCalendar().previousCalendarDate(date), getCalendar().nextCalendarDate(date));
        }
        return null;
    }

    /**
	 * Gets the description I 18 n.
	 *
	 * @return the description I 18 n
	 */
    public Map<String, String> getDescriptionI18n() {
        return descriptionI18n;
    }

    /**
	 * Sets the description I 18 n.
	 *
	 * @param descriptionI18n the description I 18 n
	 */
    public void setDescriptionI18n(Map<String, String> descriptionI18n) {
        this.descriptionI18n = descriptionI18n;
    }

    /**
     * Instantiate descriptionI18n field if it is null. NOTE: do not use this method unless you have an intention to modify it's value, as entity will be marked dirty and record
     * will be updated in DB
     * 
     * @return descriptionI18n value or instantiated descriptionI18n field value
     */
    public Map<String, String> getDescriptionI18nNullSafe() {
        if (descriptionI18n == null) {
            descriptionI18n = new HashMap<>();
        }
        return descriptionI18n;
    }

    /**
	 * Get description in a given language. Will return default description if not found for the language
	 *
	 * @author akadid abdelmounaim
	 * @param language language code
	 * @return descriptionI18n value or instantiated descriptionI18n field value
	 * @lastModifiedVersion 5.0
	 */
    public String getDescription(String language) {

        if (language == null || descriptionI18n == null || descriptionI18n.isEmpty()) {
            return description;
        }

        language = language.toUpperCase();
        if (StringUtils.isBlank(descriptionI18n.get(language))) {
            return description;
        } else {
            return descriptionI18n.get(language);
        }
    }

    /**
	 * Get a list of matrix columns used as key columns.
	 *
	 * @return A list of matrix columns where isKeyColumn = true
	 */
    public List<CustomFieldMatrixColumn> getMatrixKeyColumns() {

        if (matrixKeyColumns != null) {
            return matrixKeyColumns;
        }
        matrixKeyColumns = matrixColumns.stream().filter(CustomFieldMatrixColumn::isColumnForKey).collect(Collectors.toList());
        return matrixKeyColumns;
    }

    /**
	 * Extract codes of matrix columns used as key columns into a sorted list by column index.
	 *
	 * @return A list of matrix column codes
	 */
    public List<String> getMatrixKeyColumnCodes() {

        List<String> matrixColumnNames = null;
        if (storageType == CustomFieldStorageTypeEnum.MATRIX) {
            matrixColumnNames = new ArrayList<>();
            for (CustomFieldMatrixColumn column : getMatrixKeyColumns()) {
                matrixColumnNames.add(column.getCode());
            }
        }
        return matrixColumnNames;
    }

    /**
	 * Get a list of matrix columns used as value columns.
	 *
	 * @return A list of matrix columns where isKeyColumn = false
	 */
    public List<CustomFieldMatrixColumn> getMatrixValueColumns() {

        if (matrixValueColumns != null) {
            return matrixValueColumns;
        }

        matrixValueColumns = matrixColumns.stream().filter(elem -> !elem.isColumnForKey()).collect(Collectors.toList());

        return matrixValueColumns;
    }

    /**
	 * Extract codes of matrix columns used as value columns into a sorted list by column index.
	 *
	 * @return A list of matrix column codes
	 */
    public List<String> getMatrixValueColumnCodes() {

        List<String> matrixColumnNames = null;
        if (storageType == CustomFieldStorageTypeEnum.MATRIX) {
            matrixColumnNames = new ArrayList<>();
            for (CustomFieldMatrixColumn column : getMatrixValueColumns()) {
                matrixColumnNames.add(column.getCode());
            }
        }
        return matrixColumnNames;
    }

    /**
	 * Parse multi-value value from string to a map of values.
	 *
	 * @param multiValue  Multi-value value as string
	 * @param appendToMap Map to append values to. If not provided a new map will be instantiated.
	 * @return Map of values (or same as appendToMap if provided)
	 */
    public Map<String, Object> deserializeMultiValue(String multiValue, Map<String, Object> appendToMap) {

        // DO NOT REMOVE - Initialize matrixValueColumns field
        getMatrixValueColumns();

        Map<String, Object> values = appendToMap;
        if (values == null) {
            values = new HashMap<>();
        }

        // Multi-value values are concatenated when stored - split them and set as separate map key/values
        String[] splitValues = multiValue.split("\\" + CustomFieldValue.MATRIX_KEY_SEPARATOR);
        for (int i = 0; i < splitValues.length; i++) {
            CustomFieldMapKeyEnum dataType = matrixValueColumns.get(i).getKeyType();
            if (dataType == CustomFieldMapKeyEnum.STRING || dataType == CustomFieldMapKeyEnum.TEXT_AREA) {
                values.put(matrixValueColumns.get(i).getCode(), splitValues[i]);

            } else if (!StringUtils.isBlank(splitValues[i])) {
                try {
                    if (dataType == CustomFieldMapKeyEnum.LONG) {
                        values.put(matrixValueColumns.get(i).getCode(), Long.parseLong(splitValues[i]));
                    } else if (dataType == CustomFieldMapKeyEnum.DOUBLE) {
                        values.put(matrixValueColumns.get(i).getCode(), Double.parseDouble(splitValues[i]));
                    }
                } catch (Exception e) {
                    // Was not a number - ignore
                }
            }
        }

        return values;
    }

    /**
	 * Serialize multi-value from a map of values to a string.
	 *
	 * @param mapValues Map of values
	 * @return A string with concatenated values
	 */
    public String serializeMultiValue(Map<String, Object> mapValues) {

        // DO NOT REMOVE - Initialize matrixValueColumns field
        getMatrixValueColumns();

        boolean valueSet = false;
        StringBuilder valBuilder = new StringBuilder();
        for (CustomFieldMatrixColumn column : matrixValueColumns) {
            valBuilder.append(valBuilder.length() == 0 ? "" : CustomFieldValue.MATRIX_KEY_SEPARATOR);
            Object columnValue = mapValues.get(column.getCode());
            if (StringUtils.isBlank(columnValue)) {
                continue;
            }
            valueSet = true;
            CustomFieldMapKeyEnum dataType = column.getKeyType();
            if (dataType == CustomFieldMapKeyEnum.STRING || dataType == CustomFieldMapKeyEnum.TEXT_AREA) {
                valBuilder.append((String) columnValue);
            } else if (dataType == CustomFieldMapKeyEnum.LONG || dataType == CustomFieldMapKeyEnum.DOUBLE) {
                valBuilder.append(columnValue.toString());
            }
        }

        if (!valueSet) {
            return null;
        }

        return valBuilder.toString();
    }

    /**
	 * Gets the display format for Date type only.
	 *
	 * @return the display format for Date type only
	 */
    public String getDisplayFormat() {
        return displayFormat;
    }

    /**
	 * Sets the display format for Date type only.
	 *
	 * @param displayFormat the new display format for Date type only
	 */
    public void setDisplayFormat(String displayFormat) {
        this.displayFormat = displayFormat;
    }

	/**
	 * Gets the list of content types.
	 *
	 * @return the list of content types
	 */
	public List<String> getContentTypes() {
		return contentTypes;
	}

	/**
	 * Sets the list of content types.
	 *
	 * @param contentTypes the new list of content types
	 */
	public void setContentTypes(List<String> contentTypes) {
		this.contentTypes = contentTypes;
	}

	/**
	 * Gets the list of file extensions.
	 *
	 * @return the list of file extensions
	 */
	public List<String> getFileExtensions() {
		return fileExtensions;
	}

	/**
	 * Sets the list of file extensions.
	 *
	 * @param fileExtensions the new list of file extensions
	 */
	public void setFileExtensions(List<String> fileExtensions) {
		this.fileExtensions = fileExtensions;
	}

	/**
	 * Gets the supports EL variables.
	 *
	 * @return the supports EL variables
	 */
	public String getFilePath() {
		return filePath;
	}

	/**
	 * Sets the supports EL variables.
	 *
	 * @param filePath the new supports EL variables
	 */
	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	/**
	 * Gets the content type.
	 *
	 * @return the content type
	 */
	public String getContentType() {
		return contentType;
	}

	/**
	 * Sets the content type.
	 *
	 * @param contentType the new content type
	 */
	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	/**
	 * Gets the file extension.
	 *
	 * @return the file extension
	 */
	public String getFileExtension() {
		return fileExtension;
	}

	/**
	 * Sets the file extension.
	 *
	 * @param fileExtension the new file extension
	 */
	public void setFileExtension(String fileExtension) {
		this.fileExtension = fileExtension;
	}

	/**
	 * Adds the content type.
	 *
	 * @param ct the ct
	 */
	public void addContentType(String ct) {
		if (getContentTypes() == null) {
			contentTypes = new ArrayList<>();
		}

		contentTypes.add(ct);
	}

	/**
	 * Adds the file extension.
	 *
	 * @param fe the fe
	 */
	public void addFileExtension(String fe) {
		if (getFileExtensions() == null) {
			fileExtensions = new ArrayList<>();
		}

		fileExtensions.add(fe);
	}

	/**
	 * Gets the maximum size in kb.
	 *
	 * @return the maximum size in kb
	 */
	public Long getMaxFileSizeAllowedInKb() {
		return maxFileSizeAllowedInKb;
	}

	/**
	 * Sets the maximum size in kb.
	 *
	 * @param maxFileSizeAllowedInKb the new maximum size in kb
	 */
	public void setMaxFileSizeAllowedInKb(Long maxFileSizeAllowedInKb) {
		this.maxFileSizeAllowedInKb = maxFileSizeAllowedInKb;
	}

	/**
	 * Gets the max file size allowed in bytes.
	 *
	 * @return the max file size allowed in bytes
	 */
	public Long getMaxFileSizeAllowedInBytes() {
		return maxFileSizeAllowedInKb != null ? maxFileSizeAllowedInKb * 1000 : 0L;
	}

    /**
	 * Checks if is sql storage.
	 *
	 * @return true, if is sql storage
	 */
    public boolean isSqlStorage() {
        return storages != null && storages.contains(DBStorageType.SQL);
    }

    /**
	 * Gets the samples.
	 *
	 * @return the samples
	 */
    public List<String> getSamples() {
    	if(samples == null) {
    		this.samples = new ArrayList<>();
    	}
    	
        return samples;
    }

    /**
	 * Sets the samples.
	 *
	 * @param samples the new samples
	 */
    public void setSamples(List<String> samples) {
    	if(samples == null && this.samples == null) {
    		samples = new ArrayList<>();
    	} else if (samples == null && this.samples != null) { 
    		this.samples.clear();
    	} else {
	        this.samples = samples;
    	}
    }

    /**
	 * Gets the new list value.
	 *
	 * @return the new list value
	 */
    public List<?> getNewListValue() {
		if (storageType != CustomFieldStorageTypeEnum.LIST) {
			return null;
		}

		switch (fieldType) {
		case BOOLEAN:
			return new ArrayList<Boolean>();
		case DATE:
			return new ArrayList<Instant>();
		case DOUBLE:
			return new ArrayList<Double>();
		case EXPRESSION:
		case TEXT_AREA:
        case LONG_TEXT:
		case LIST:
		case SECRET:
		case STRING:
			return new ArrayList<String>();
		case LONG:
			return new ArrayList<Long>();
		default:
			return new ArrayList<>();
		}
	}

	/**
	 * Checks for reference jpa entity.
	 *
	 * @return true, if successful
	 */
	public boolean hasReferenceJpaEntity() {
		return hasReferenceJpaEntity;
	}

	/**
	 * Sets the checks for reference jpa entity.
	 *
	 * @param hasReferenceJpaEntity the new checks for reference jpa entity
	 */
	public void setHasReferenceJpaEntity(boolean hasReferenceJpaEntity) {
		this.hasReferenceJpaEntity = hasReferenceJpaEntity;
	}

	public boolean isAudited() {
		return audited;
	}

	public void setAudited(boolean audited) {
		this.audited = audited;
	}

	public boolean isPersisted() {
		return persisted;
	}

	public void setPersisted(boolean persisted) {
		this.persisted = persisted;
	}

}
