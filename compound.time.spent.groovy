enableCache = {-> false}

import com.atlassian.jira.ComponentManager
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.link.IssueLinkManager
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.ModifiedValue;
import com.atlassian.jira.issue.util.DefaultIssueChangeHolder;

import org.apache.log4j.Logger
import org.apache.log4j.Level

def log = Logger.getLogger("COMPUTED")
log.setLevel(Level.DEBUG)

def issueLinkManager = ComponentAccessor.getIssueLinkManager()
def customFieldManager = ComponentAccessor.getCustomFieldManager()
def circularityCache = []

def calculateTimeSpent(Issue issue, List circularityCache, IssueLinkManager issueLinkManager, CustomFieldManager customFieldManager, Logger log) {
    double thisTimeSpent = 0
    double subsTimeSpent = 0
    def compoundTimeSpent = 0
    
    // avoiding circularity
    if (circularityCache.contains(issue) == false) {
        circularityCache.add(issue)
        
        // getting original estimate
        def timespent = issue.getTimeSpent()
        if (timespent > 0) {
            thisTimeSpent = (double) timespent
            thisTimeSpent  = thisTimeSpent / (8 * 3600)
        }
        
        // traversing direct children
        issueLinkManager.getOutwardLinks(issue.id).each {
            issueLink ->
            if (issueLink.issueLinkType.name == "Hierarchy"
                || issueLink.issueLinkType.name == "Epic-Story Link"
                || issueLink.issueLinkType.isSubTaskLinkType() == true) { 
                                
                // reading this custom - scripted - field on child (hopefully triggering deep calculation)
                def childTimeSpent = 0
                Issue childIssue = issueLink.getDestinationObject()
                //def customEstimateField =  ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Compound Time Spent (computed)");
                def customTimeSpentField =  ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Compound Time Spent");
                def customTimeSpent
                if(customTimeSpentField != null) {
                	customTimeSpent = childIssue.getCustomFieldValue(customTimeSpentField);
                } 
                if (customTimeSpent != null) {
                	 childTimeSpent = (double) customTimeSpent
                }
                
                // adding each child estimate
                subsTimeSpent += childTimeSpent
            }
        }
   
    }
    
    // tree compound wins over issue estimate
    compoundTimeSpent = ((subsTimeSpent > 0) ? subsTimeSpent : thisTimeSpent)
    
    // memoizing data in number field (for UI)
    //def compoundField = customFieldManager.getCustomFieldObjectByName("Compound Time Spent");
	//compoundField.updateValue(null, issue, new ModifiedValue(issue.getCustomFieldValue(compoundField), compoundTimeSpent), new DefaultIssueChangeHolder());            
    return compoundTimeSpent;
}

return (Double)calculateTimeSpent(issue,circularityCache,issueLinkManager,customFieldManager,log)