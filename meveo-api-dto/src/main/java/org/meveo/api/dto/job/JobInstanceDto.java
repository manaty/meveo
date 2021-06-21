package org.meveo.api.dto.job;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.meveo.api.dto.BusinessEntityDto;
import org.meveo.api.dto.CustomFieldsDto;
import org.meveo.model.jobs.JobCategoryEnum;

/**
 * The Class JobInstanceDto.
 * 
 * @author anasseh
 * @author Edward P. Legaspi | czetsuya@gmail.com
 * @version 6.7.0
 */
@XmlRootElement(name = "JobInstance")
@XmlAccessorType(XmlAccessType.FIELD)
@ApiModel("JobInstanceDto")
public class JobInstanceDto extends BusinessEntityDto implements Comparable<JobInstanceDto> {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 5166093858617578774L;

    /** Job category. */
    @XmlElement(required = true)
    @ApiModelProperty(required = true, value = "Job category type")
    private JobCategoryEnum jobCategory;

    /** Job template. */
    @XmlAttribute(required = true)
    @ApiModelProperty(required = true, value = "Job template")
    private String jobTemplate;

    /** Following job to execute. */
    @ApiModelProperty("Following job to execute")
    private String followingJob;

    /** Parameter to job execution. */
    @ApiModelProperty("Parameter to job execution")
    private String parameter;

    /** Custom fields. */
    @ApiModelProperty("Custom fields information")
    private CustomFieldsDto customFields;

    /** Job scheduling timer code. */
    @XmlAttribute(required = false)
    @ApiModelProperty(required = false, value = "Job scheduling timer code")
    private String timerCode;

    /**
     * What cluster nodes job could/should run on. A comma separated list of custer nodes. A job can/will be run on any node if value is null.
     */
    @ApiModelProperty("What cluster nodes job could/should run on")
    private String runOnNodes;

    /**
     * Can job be run in parallel on several cluster nodes. Value of True indicates that job can be run on a single node at a time.
     */
    @ApiModelProperty("Limit to single node")
    private Boolean limitToSingleNode;

    /**
     * Gets the job category.
     *
     * @return the job category
     */
    public JobCategoryEnum getJobCategory() {
        return jobCategory;
    }

    /**
     * Sets the job category.
     *
     * @param jobCategory the new job category
     */
    public void setJobCategory(JobCategoryEnum jobCategory) {
        this.jobCategory = jobCategory;
    }

    /**
     * Gets the job template.
     *
     * @return the job template
     */
    public String getJobTemplate() {
        return jobTemplate;
    }

    /**
     * Sets the job template.
     *
     * @param jobTemplate the new job template
     */
    public void setJobTemplate(String jobTemplate) {
        this.jobTemplate = jobTemplate;
    }

    /**
     * Gets the parameter.
     *
     * @return the parameter
     */
    public String getParameter() {
        return parameter;
    }

    /**
     * Gets the following job.
     *
     * @return the following job
     */
    public String getFollowingJob() {
        return followingJob;
    }

    /**
     * Sets the following job.
     *
     * @param followingJob the new following job
     */
    public void setFollowingJob(String followingJob) {
        this.followingJob = followingJob;
    }

    /**
     * Sets the parameter.
     *
     * @param parameter the new parameter
     */
    public void setParameter(String parameter) {
        this.parameter = parameter;
    }

    /**
     * Gets the custom fields.
     *
     * @return the custom fields
     */
    public CustomFieldsDto getCustomFields() {
        return customFields;
    }

    /**
     * Sets the custom fields.
     *
     * @param customFields the new custom fields
     */
    public void setCustomFields(CustomFieldsDto customFields) {
        this.customFields = customFields;
    }

    /**
     * Gets the timer code.
     *
     * @return the timer code
     */
    public String getTimerCode() {
        return timerCode;
    }

    /**
     * Sets the timer code.
     *
     * @param timerCode the new timer code
     */
    public void setTimerCode(String timerCode) {
        this.timerCode = timerCode;
    }

    /**
     * Gets the run on nodes.
     *
     * @return the run on nodes
     */
    public String getRunOnNodes() {
        return runOnNodes;
    }

    /**
     * Sets the run on nodes.
     *
     * @param runOnNodes the new run on nodes
     */
    public void setRunOnNodes(String runOnNodes) {
        this.runOnNodes = runOnNodes;
    }

    /**
     * Gets the limit to single node.
     *
     * @return the limit to single node
     */
    public Boolean getLimitToSingleNode() {
        return limitToSingleNode;
    }

    /**
     * Sets the limit to single node.
     *
     * @param limitToSingleNode the new limit to single node
     */
    public void setLimitToSingleNode(Boolean limitToSingleNode) {
        this.limitToSingleNode = limitToSingleNode;
    }

    @Override
    public String toString() {
        return "JobInstanceDto [jobCategory=" + jobCategory + ", jobTemplate=" + jobTemplate + ", followingJob=" + followingJob + ", parameter=" + parameter + ", active=" + isActive()
                + ", customFields=" + customFields + ", timerCode=" + timerCode + ", runOnNodes=" + runOnNodes + ", limitToSingleNode=" + limitToSingleNode + "]";
    }

	@Override
	public int compareTo(JobInstanceDto o) {
		if (o == null)
			return -1;
		if (this.getFollowingJob() == null && o.getFollowingJob() == null)
			return 0;
		else if (this.getFollowingJob() == null && o.getFollowingJob() != null)
			return -1;
		else if (this.getFollowingJob() != null && o.getFollowingJob() == null)
			return 1;
		else /*if (this.getFollowingJob() != null && o.getFollowingJob() != null)*/ {
			if (this.getFollowingJob().contentEquals(o.code) )
				return 1;
			else if (o.getFollowingJob().contentEquals(this.code) )
				return -1;
			else 
				return 0;
		}
	}
}