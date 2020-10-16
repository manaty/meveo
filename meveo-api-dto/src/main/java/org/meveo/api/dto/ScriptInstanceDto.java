package org.meveo.api.dto;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.meveo.api.dto.script.CustomScriptDto;
import org.meveo.model.scripts.MavenDependency;
import org.meveo.model.scripts.ScriptInstance;
import org.meveo.model.scripts.ScriptSourceTypeEnum;
import org.meveo.model.security.Role;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The Class ScriptInstanceDto.
 *
 * @author Edward P. Legaspi | czetsuya@gmail.com
 * @version 6.10
 */
@XmlRootElement(name = "ScriptInstance")
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("ScriptInstanceDto")
public class ScriptInstanceDto extends CustomScriptDto {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 4555037251902559699L;

    /** The execution roles. */
    @ApiModelProperty("The execution roles.")
    @XmlElementWrapper(name = "executionRoles")
	@XmlElement(name = "executionRole")
    private List<RoleDto> executionRoles = new ArrayList<RoleDto>();
    
    /** The sourcing roles. */
    @ApiModelProperty("The sourcing roles")
    @XmlElementWrapper(name = "sourcingRoles")
	@XmlElement(name = "sourcingRole")
    private List<RoleDto> sourcingRoles = new ArrayList<RoleDto>();

    @ApiModelProperty("Whether the script has compilation errors")
    private Boolean error;

    /** The maven dependencies. */
    @XmlElementWrapper(name = "mavenDependencies")
	@XmlElement(name = "mavenDependency")
    @ApiModelProperty("The maven dependencies")
    private List<MavenDependencyDto> mavenDependencies = new ArrayList<>();

    /** The import script instances. */
    @ApiModelProperty("The import script instances")
    @XmlElementWrapper(name = "importScriptInstances")
	@XmlElement(name = "importScriptInstancy")
    private List<ScriptInstanceDto> importScriptInstances = new ArrayList<>();

    /**
     * Instantiates a new script instance dto.
     */
    public ScriptInstanceDto() {
        super();
    }

    public ScriptInstanceDto(Long id, String code, ScriptSourceTypeEnum type, Boolean error) {
    	this.id = id;
    	this.code = code;
    	setType(type);
    	this.error = error;
    }

    /**
     * Instantiates a new script instance dto.
     *
     * @param scriptInstance the ScriptInstance entity
     */
    public ScriptInstanceDto(ScriptInstance scriptInstance, String source) {
        super(scriptInstance, source);

        if (scriptInstance.getExecutionRolesNullSafe() != null) {
            for (Role role : scriptInstance.getExecutionRolesNullSafe()) {
                executionRoles.add(new RoleDto(role, true, true));
            }
        }
        if (scriptInstance.getSourcingRolesNullSafe() != null) {
            for (Role role : scriptInstance.getSourcingRolesNullSafe()) {
                sourcingRoles.add(new RoleDto(role, true, true));
            }
        }

        if (scriptInstance.getMavenDependenciesNullSafe() != null) {
            for (MavenDependency maven : scriptInstance.getMavenDependenciesNullSafe() ) {
                mavenDependencies.add(new MavenDependencyDto(maven));
            }
        }

        if (scriptInstance.getImportScriptInstancesNullSafe() != null) {
            for (ScriptInstance script : scriptInstance.getImportScriptInstancesNullSafe() ) {
                importScriptInstances.add(new ScriptInstanceDto(script, script.getScript()));
            }
        }
        
        if(scriptInstance.getCategory() != null) {
        	super.setCategory(scriptInstance.getCategory().getCode());
        }

    }


    @Override
    public String toString() {
        return "ScriptInstanceDto [code=" + getCode() + ", description=" + getDescription() + ", type=" + getType() + ", category=" + getCategory() + "script=" + getScript() + ", executionRoles="
                + executionRoles + ", sourcingRoles=" + sourcingRoles + "]";
    }

    /**
     * Gets the execution roles.
     *
     * @return the executionRoles
     */
    public List<RoleDto> getExecutionRoles() {
        return executionRoles;
    }

    /**
     * Sets the execution roles.
     *
     * @param executionRoles the executionRoles to set
     */
    public void setExecutionRoles(List<RoleDto> executionRoles) {
        this.executionRoles = executionRoles;
    }

    /**
     * Gets the sourcing roles.
     *
     * @return the sourcingRoles
     */
    public List<RoleDto> getSourcingRoles() {
        return sourcingRoles;
    }

    /**
     * Sets the sourcing roles.
     *
     * @param sourcingRoles the sourcingRoles to set
     */
    public void setSourcingRoles(List<RoleDto> sourcingRoles) {
        this.sourcingRoles = sourcingRoles;
    }

    /**
     * Gets the maven dependencies.
     *
     * @return the mavenDependencies
     */
    public List<MavenDependencyDto> getMavenDependencies() {
        return mavenDependencies;
    }

    /**
     * Sets the maven dependencies.
     *
     * @param mavenDependencies the mavenDependencies to set
     */
    public void setMavenDependencies(List<MavenDependencyDto> mavenDependencies) {
        this.mavenDependencies = mavenDependencies;
    }

    /**
     * Gets the import script instances.
     *
     * @return the importScriptInstances
     */
    public List<ScriptInstanceDto> getImportScriptInstances() {
        return importScriptInstances;
    }

    /**
     * Sets the import script instances.
     *
     * @param importScriptInstances the importScriptInstances to set
     */
    public void setImportScriptInstances(List<ScriptInstanceDto> importScriptInstances) {
        this.importScriptInstances = importScriptInstances;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        } else if (!(obj instanceof ScriptInstanceDto)) { // Fails with proxed objects: getClass() != obj.getClass()){
            return false;
        }

        ScriptInstanceDto other = (ScriptInstanceDto) obj;

        if (getCode() == null) {
            if (other.getCode() != null) {
                return false;
            }
        } else if (!getCode().equals(other.getCode())) {
            return false;
        }
        return true;
    }

	public Boolean getError() {
		return error;
	}

	public void setError(Boolean error) {
		this.error = error;
	}

}