package org.meveo.model.module;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.SecondaryTable;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.meveo.model.BusinessEntity;
import org.meveo.model.CustomFieldEntity;
import org.meveo.model.ExportIdentifier;
import org.meveo.model.ICustomFieldEntity;
import org.meveo.model.ModuleItem;
import org.meveo.model.ModuleItemOrder;
import org.meveo.model.ObservableEntity;
import org.meveo.model.crm.custom.CustomFieldValues;
import org.meveo.model.git.GitRepository;
import org.meveo.model.persistence.CustomFieldValuesConverter;
import org.meveo.model.scripts.ScriptInstance;
import org.meveo.model.storage.Repository;

/**
 * Meveo module has CETs, CFTs, CEIs, filters, scripts, jobs, notifications, endpoints
 *
 * @author Clément Bareth * 
 * @author Tyshan Shi(tyshanchn@manaty.net)
 * @author Edward P. Legaspi | czetsuya@gmail.com
 * @version 6.9.0
 */
@Entity
@ObservableEntity
@CustomFieldEntity(cftCodePrefix = "MODULE")
@ModuleItem(value = "Module", path = "")
@ModuleItemOrder(300)
@ExportIdentifier({ "code"})
@Table(name = "meveo_module", uniqueConstraints = @UniqueConstraint(columnNames = { "code"}))
@GenericGenerator(name = "ID_GENERATOR", strategy = "org.hibernate.id.enhanced.SequenceStyleGenerator", parameters = {@Parameter(name = "sequence_name", value = "meveo_module_seq"), })
@NamedQueries({@NamedQuery(name = "MeveoModule.deleteModule", query = "DELETE from MeveoModule m where m.id=:id and m.version=:version")})
@Inheritance(strategy = InheritanceType.JOINED)
@SecondaryTable(name = "meveo_module_source", pkJoinColumns = @PrimaryKeyJoinColumn(referencedColumnName = "id"))
public class MeveoModule extends BusinessEntity implements ICustomFieldEntity {
	
	public static final String VERSION_PATTERN = "^(\\d+\\.)?(\\d+\\.)?(\\*|\\d+)[^\\s]*$"; 

    private static final long serialVersionUID = 1L;

    @OneToMany(mappedBy = "meveoModule", cascade = { CascadeType.MERGE, CascadeType.PERSIST }, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<MeveoModuleItem> moduleItems = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "module_files", joinColumns = { @JoinColumn(name = "module_id") })
    @Column(name = "module_file")
    private Set<String> moduleFiles = new HashSet<>();
    
	@OneToOne(mappedBy = "meveoModule", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY, targetEntity = MeveoModuleSource.class)
    private MeveoModuleSource meveoModuleSource;
	
    /**
     * A list of order items. Not modifiable once started processing.
     */
    @Fetch(FetchMode.JOIN)
    @OneToMany(mappedBy = "meveoModule", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ModuleRelease> releases = new HashSet<>();

    @OneToMany(mappedBy = "meveoModule", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<MeveoModuleDependency> moduleDependencies = new HashSet<>();
    
    /**
     * Patches applied to install this module.
     */
	@OneToMany(mappedBy = "meveoModulePatchId.meveoModule", cascade = { CascadeType.REMOVE }, fetch = FetchType.LAZY)
    private Set<MeveoModulePatch> patches = new HashSet<>();
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_instance_id")
    private ScriptInstance script;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "git_repository_id")
    private GitRepository gitRepository;
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "meveo_module_storage_repository",
        joinColumns = @JoinColumn(name = "module_id"),
        inverseJoinColumns = @JoinColumn(name = "repo_id")
    )
    private List<Repository> repositories;

    @Enumerated(EnumType.STRING)
    @Column(name = "module_license", nullable = false)
    @NotNull
    private ModuleLicenseEnum license = ModuleLicenseEnum.GPL;

    @Column(name = "logo_picture")
    @Size(max = 255)
    private String logoPicture;

    @Type(type="numeric_boolean")
    @Column(name = "installed")
    private boolean installed;

    @Column(name = "current_version")
    @Pattern(regexp = VERSION_PATTERN)
    private String currentVersion;

    @Column(name = "meveo_version_base")
    @Pattern(regexp = "^(?<major>\\d+\\.)?(?<minor>\\d+\\.)?(?<patch>\\*|\\d+)$")
    private String meveoVersionBase;

    @Column(name = "meveo_version_ceiling")
    @Pattern(regexp = "^(?<major>\\d+\\.)?(?<minor>\\d+\\.)?(?<patch>\\*|\\d+)$")
    private String meveoVersionCeiling;

    @Type(type="numeric_boolean")
    @Column(name = "is_in_draft")
    private boolean isInDraft = true;
    
    @Type(type = "numeric_boolean")
    @Column(name = "auto_commit")
    private boolean autoCommit = false;

    @Column(name = "uuid", nullable = false, updatable = false, length = 60)
    @Size(max = 60)
    @NotNull
    private String uuid = UUID.randomUUID().toString();

    @Convert(converter = CustomFieldValuesConverter.class)
    @Column(name = "cf_values", columnDefinition = "text")
    private CustomFieldValues cfValues;
    
    @PrePersist()
    @PreUpdate()
    public void processDisabled() {
    	// If module is downloaded but not installed, consider it as disabled
    	if(isDownloaded() && !installed) {
    		setDisabled(true);
    	} else if(isDownloaded() && installed) {
    		setDisabled(false);
    	}
    }
    
    /**
	 * @param autoCommit the autoCommit to set
	 */
	public void setAutoCommit(boolean autoCommit) {
		this.autoCommit = autoCommit;
	}
	
	/**
	 * @return the {@link #autoCommit}
	 */
	public boolean isAutoCommit() {
		return autoCommit;
	}

    public Set<MeveoModuleItem> getModuleItems() {
        return moduleItems;
    }

    public void setModuleItems(Set<MeveoModuleItem> moduleItems) {
        this.moduleItems = moduleItems;
    }
    
    public void removeItem(MeveoModuleItem item) {
        this.moduleItems.remove(item);
        item.setMeveoModule(null);
    }

    public Set<String> getModuleFiles() {
        return moduleFiles;
    }

    public void setModuleFiles(Set<String> moduleFiles) {
        this.moduleFiles = moduleFiles;
    }

    public void addModuleFile(String moduleFile) {
        this.moduleFiles.add(moduleFile);
    }

    public void removeModuleFile(String moduleFile) {
        this.moduleFiles.remove(moduleFile);
    }

    public ModuleLicenseEnum getLicense() {
        return license;
    }

    public void setLicense(ModuleLicenseEnum license) {
        this.license = license;
    }

    public String getLogoPicture() {
        return logoPicture;
    }

    public void setLogoPicture(String logoPicture) {
        this.logoPicture = logoPicture;
    }

    public boolean isInstalled() {
        return installed;
    }

    public void setInstalled(boolean installed) {
        this.installed = installed;
    }

    public boolean getIsInDraft() {
        return isInDraft;
    }

    public void setIsInDraft(boolean isInDraft) {
        this.isInDraft = isInDraft;
    }

    public void setScript(ScriptInstance script) {
        this.script = script;
    }

    public ScriptInstance getScript() {
        return script;
    }
    
    public void setGitRepository(GitRepository gitRepository) {
    	this.gitRepository = gitRepository;
    }
    
    public GitRepository getGitRepository() {
    	return this.gitRepository;
    }

    public void setModuleSource(String moduleSource) {
    	if (this.meveoModuleSource == null) {
	    	MeveoModuleSource meveoModuleSource = new MeveoModuleSource();
	        meveoModuleSource.setModuleSource(moduleSource);
	        meveoModuleSource.setMeveoModule(this);
	        this.meveoModuleSource = meveoModuleSource;
    	} else {
    		this.meveoModuleSource.setModuleSource(moduleSource);
    	}
    }

    public String getModuleSource() {
    	if (this.meveoModuleSource != null) {
    		return this.meveoModuleSource.getModuleSource();
    	} else {
    		return null;
    	}
    }

	public boolean isDownloaded() {
        return !(meveoModuleSource == null);
    }

	public String getCurrentVersion() {
		return currentVersion;
	}

	public void setCurrentVersion(String currentVersion) {
		this.currentVersion = currentVersion;
	}

	

	public Set<MeveoModuleDependency> getModuleDependencies() {
		return moduleDependencies;
	}

	public void setModuleDependencies(Set<MeveoModuleDependency> moduleDependencies) {
		this.moduleDependencies = moduleDependencies;
	}

	public void addModuleDependency(MeveoModuleDependency moduleDependency) {
        this.moduleDependencies.add(moduleDependency);
        moduleDependency.setMeveoModule(this);
    }

    public Set<ModuleRelease> getReleases() {
        return releases;
    }

    public void setReleases(Set<ModuleRelease> releases) {
        this.releases = releases;
    }

    public String getMeveoVersionBase() {
        return meveoVersionBase;
    }

    public void setMeveoVersionBase(String meveoVersionBase) {
        this.meveoVersionBase = meveoVersionBase;
    }

    public String getMeveoVersionCeiling() {
        return meveoVersionCeiling;
    }

    public void setMeveoVersionCeiling(String meveoVersionCeiling) {
        this.meveoVersionCeiling = meveoVersionCeiling;
    }

    public void removeModuleDependency(MeveoModuleDependency moduleDependency) {
        this.moduleDependencies.remove(moduleDependency);
        moduleDependency.setMeveoModule(null);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((currentVersion == null) ? 0 : currentVersion.hashCode());
        result = prime * result + ((code == null) ? 0 : code.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        MeveoModule other = (MeveoModule) obj;
        if (currentVersion == null) {
            if (other.currentVersion != null)
                return false;
        } else if (!currentVersion.equals(other.currentVersion))
            return false;
        if (code != other.code)
            return false;
        return true;
    }

	public Set<MeveoModulePatch> getPatches() {
		return patches;
	}

	public void setPatches(Set<MeveoModulePatch> patches) {
		this.patches = patches;
	}

	/**
	 * @return the {@link #repositories}
	 */
	public List<Repository> getRepositories() {
		return repositories;
	}

	/**
	 * @param repositories the repositories to set
	 */
	public void setRepositories(List<Repository> repositories) {
		this.repositories = repositories;
	}

    @Override
    public String getUuid() {
        return uuid;
    }

    @Override
    public String clearUuid() {
        String oldUuid = uuid;
        uuid = UUID.randomUUID().toString();
        return oldUuid;
    }

    @Override
    public ICustomFieldEntity[] getParentCFEntities() {
        return null;
    }

    @Override
    public CustomFieldValues getCfValues() {
        return cfValues;
    }

    @Override
    public CustomFieldValues getCfValuesNullSafe() {
        if (cfValues == null) {
            cfValues = new CustomFieldValues();
        }
        return cfValues;
    }

    @Override
    public void clearCfValues() {
        cfValues = null;
        
    }


}
