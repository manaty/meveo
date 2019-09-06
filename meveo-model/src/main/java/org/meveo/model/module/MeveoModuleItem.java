package org.meveo.model.module;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.meveo.commons.utils.ReflectionUtils;
import org.meveo.commons.utils.StringUtils;
import org.meveo.model.BaseEntity;
import org.meveo.model.BusinessEntity;
import org.meveo.model.DatePeriod;
import org.meveo.model.ExportIdentifier;

/**
 * @author Edward P. Legaspi <czetsuya@gmail.com>
 * @lastModifiedVersion 6.3.0
 */
@Entity
@ExportIdentifier({ "meveoModule.code", "appliesTo", "itemClass", "itemCode" })
@Table(name = "meveo_module_item")
@GenericGenerator(name = "ID_GENERATOR", strategy = "org.hibernate.id.enhanced.SequenceStyleGenerator", parameters = {
        @Parameter(name = "sequence_name", value = "meveo_module_item_seq"), })
@NamedQueries({ //
	@NamedQuery(name = "MeveoModuleItem.delete", query = "DELETE FROM MeveoModuleItem WHERE itemCode=:itemCode AND itemClass=:itemClass")
})
public class MeveoModuleItem extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id")
    private MeveoModule meveoModule;

    @Column(name = "applies_to", length = 100)
    @Size(max = 100)
    private String appliesTo;

    @Column(name = "item_type", length = 100, nullable = false)
    @Size(max = 100)
    @NotNull
    private String itemClass;

    @Column(name = "item_code", length = 255, nullable = false)
    @Size(max = 255)
    @NotNull
    private String itemCode;

    @AttributeOverrides({ @AttributeOverride(name = "from", column = @Column(name = "valid_from")), @AttributeOverride(name = "to", column = @Column(name = "valid_to")) })
    private DatePeriod validity = new DatePeriod();

    @Transient
    private BusinessEntity itemEntity;

    public MeveoModuleItem() {
    }

    public MeveoModuleItem(BusinessEntity itemEntity) {
        this.itemEntity = itemEntity;
        this.itemClass = itemEntity.getClass().getName();
        this.itemCode = itemEntity.getCode();
        if (ReflectionUtils.hasField(itemEntity, "appliesTo")) {
            try {
                this.appliesTo = (String) FieldUtils.readField(itemEntity, "appliesTo", true);
            } catch (IllegalAccessException e) {
            }
        }
        if (ReflectionUtils.hasField(itemEntity, "validity")) {
            try {
                this.validity = (DatePeriod) FieldUtils.readField(itemEntity, "validity", true);
            } catch (IllegalAccessException e) {
            }
        }
    }

    public MeveoModuleItem(String itemCode, String itemClass, String appliesTo, DatePeriod validity) {
        this.itemClass = itemClass;
        this.itemCode = itemCode;
        this.appliesTo = appliesTo;
        this.validity = validity;
    }

    public MeveoModule getMeveoModule() {
        return meveoModule;
    }

    public void setMeveoModule(MeveoModule meveoModule) {
        this.meveoModule = meveoModule;
    }

    public String getItemClass() {
        return itemClass;
    }

    public void setItemClass(String itemClass) {
        this.itemClass = itemClass;
    }

    public String getItemClassSimpleName() {
        try {
            final Entity annotation = Class.forName(itemClass).getAnnotation(Entity.class);
            if (org.apache.commons.lang3.StringUtils.isNotBlank(annotation.name())) {
                return annotation.name();

            } else if (itemClass != null) {
                return itemClass.substring(itemClass.lastIndexOf('.') + 1);
            }
        } catch (ClassNotFoundException e) {
            //NOOP
        }

        return null;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getAppliesTo() {
        return appliesTo;
    }

    public void setAppliesTo(String applyTo) {
        this.appliesTo = applyTo;
    }

    public void setValidity(DatePeriod validity) {
        this.validity = validity;
    }

    public DatePeriod getValidity() {
        return validity;
    }

    @Override
    public int hashCode() {
        int result = 31;
        result += itemClass != null ? itemClass.hashCode() : 0;
        result += itemCode != null ? itemCode.hashCode() : 0;
        result += appliesTo != null ? appliesTo.hashCode() : 0;
        result += validity != null ? validity.hashCode() : 0;
        return result;
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (!(obj instanceof MeveoModuleItem)) {
            return false;
        }

        MeveoModuleItem other = (MeveoModuleItem) obj;
        if (id != null && other.getId() != null && id.equals(other.getId())) {
            return true;
        }

        if (!itemClass.equals(other.getItemClass()) || !itemCode.equalsIgnoreCase(other.getItemCode()) || StringUtils.compare(appliesTo, other.getAppliesTo()) != 0) {
            return false;
        }
        if (validity != null && !validity.equals(other.getValidity())) {
            return false;
        } else if (validity == null && (other.getValidity() != null && !other.getValidity().isEmpty())) {
            return false;
        }
        return true;
    }

    public BusinessEntity getItemEntity() {
        return itemEntity;
    }

    public void setItemEntity(BusinessEntity itemEntity) {
        this.itemEntity = itemEntity;
    }

    @Override
    public String toString() {
        return String.format("MeveoModuleItem [itemClass=%s, itemCode=%s, appliesTo=%s, validity=%s]", itemClass, itemCode, appliesTo, validity);
    }
}