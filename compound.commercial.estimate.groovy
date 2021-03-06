// Number|Number
enableCache = { ->false }
def customFieldName = "Compound Commercial Estimate"
def customUiFieldName = "∑ Commercial Estimate (d)"

import com.atlassian.jira.ComponentManager
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.link.IssueLinkManager
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.ModifiedValue;
import com.atlassian.jira.issue.util.DefaultIssueChangeHolder;
import org.apache.commons.lang3.StringUtils;

import org.apache.log4j.Logger
import org.apache.log4j.Level
def log = Logger.getLogger("SCRIPTED")

def issueLinkManager = ComponentAccessor.getIssueLinkManager()
def customField = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName(customFieldName);
def customUiField = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName(customUiFieldName);
def circularityCache = []

Double getCustomFieldValue(Issue issue, CustomField customField) {
  def customValue
  if (customField != null) {
    customValue = issue.getCustomFieldValue(customField);
  }
  if (customValue != null) {
    return customValue as Double;
  }
  return 0
}

Double calculateEstimate(Issue issue, List circularityCache, IssueLinkManager issueLinkManager, CustomField customField, Logger log, Integer level) {
  def pad = StringUtils.repeat(" ", level*2)
  log.info(String.format("%sbegin calculate %s for %s", pad, customField.getName(), issue.getKey()))
  
  Double result
  
  // avoiding circularity
  if (circularityCache.contains(issue) == false) {
    circularityCache.add(issue)

    // getting original estimate
    Double thisEstimate = 0
    def estimate = issue.getOriginalEstimate()
    if (estimate > 0) {
      thisEstimate = estimate / (8 * 3600) as Double;
    }
    log.info(String.format("%sthis %s for %s: %s", pad, customField.getName(), issue.getKey(), thisEstimate))

    // checking issue type
    String issueTypeName = issue.getIssueType().getName()
    switch (issueTypeName) {
      case "Milestone":
        if (thisEstimate == 0) {
          // traversing direct children
          Double subsEstimate = 0
          issueLinkManager.getOutwardLinks(issue.id).each {
            issueLink ->
            Issue childIssue = issueLink.getDestinationObject()
            String childIssueTypeName = childIssue.getIssueType().getName()
            log.info(String.format("%sprocessing child of %s: %s", pad, issue.getKey(), childIssue.getKey()))
            if (issueLink.issueLinkType.name == "Hierarchy") {
              if (childIssue.getIssueType().getName() == "Epic") {
                // getting this estimate from child
                //Double childEstimate = getCustomFieldValue(childIssue, customField)
                Double childEstimate = calculateEstimate(childIssue, circularityCache, issueLinkManager, customField, log, level+1)
                log.info(String.format("%schild %s for %s: %s", pad, customField.getName(), childIssue.getKey(), childEstimate))
                // adding each child estimate
                subsEstimate += childEstimate
              }
            }
          }
          result = subsEstimate
        } else {
          result = thisEstimate
        }
        break;
      case "Epic":
        result = thisEstimate
        break;
      default:
        result = 0
        break;
    }
  }
  log.info(String.format("%send calculate %s for %s: %s", pad, customField.getName(), issue.getKey(), result))
  return result
}

Double result = calculateEstimate(issue, circularityCache, issueLinkManager, customField, log, 0)

// memoizing data in number field (for Portfolio)
log.info(String.format("update %s for %s: %s", customUiField.getName(), issue.getKey(), result))
customUiField.updateValue(null, issue, new ModifiedValue(issue.getCustomFieldValue(customUiField), result.round(2)), new DefaultIssueChangeHolder());

return result