/*
    B2Context - Class providing basic support functions for Building Blocks
    Copyright (C) 2018  Stephen P Vickers

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License along
    with this program; if not, write to the Free Software Foundation, Inc.,
    51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.

    Contact: stephen@spvsoftwareproducts.com

    Version history:
      1.0.05  11-Oct-10  Initial release
      1.1.00  19-Jan-12  Added setContext, setReceiptOptions, getVersionNumber and getEditMode methods
                         Added error checking to request parameter methods
                         Added override options to hasCourseContext, hasContentContext
                         and hasGroupContext methods of context object
      1.2.00   2-Sep-12  Added addReceiptOptionsToRequest
                         Invalidated all settings when context changes
      1.3.00   5-Nov-12  Added getVersionNumber method returning an integer array
                         Added getIsVersion method to check for a specific release
                         Deleted settings for a module are now removed rather than just emptied
                         Updated setReceiptOptions to support 9.1 SP10
      1.4.00  24-Feb-14  Added support for saving global anonymous settings against a node in the Institutional Hierarchy (requires 9.1SP8+)
                         Allow instance to be created with a null Request object
                         Added simple logging methods: setLogDebug ang log
      1.4.01  18-Jun-14  Added support for new version numbering system introduced with April 2014 release
                         Ensures all parent directories exist when accessing a properties file
      1.4.02   8-Sep-14  Fixed bug with failure to reinitialise the node after changing the context
      1.5.00  16-Oct-14  Added getSchema, getPath(String), getLogDebug, getB2Version and getIsB2Version methods
      1.5.01   1-Jan-15  Fixed bug with getPath(String) method
      1.6.00  24-May-15  Fixed bug with persistSettings(global, anonymous, suffix, settings) method
                         Added unfiltered option to getRequest and getRequestParameter methods
      1.6.01  12-Nov-15  Fixed bug with persisting the debug mode setting
      1.7.00  29-Feb-16  Added constructor using a context object (so "new B2Context(null);" constructor should now be coded as "new B2Context();")
      1.8.00  14-Feb-18  Remove dependency on Blackboard context object
                         Use log file in plugins log directory
                         Fix bug with saving group settings
      1.9.00  22-Apr-18  Removed hard-coded file separator characters
                         Added log methods for error messages and objects
                         Log messages written to System.err if unable to access plugin log file
                         Added getSettings method for node only settings
                         Fixed bug when deleting empty properties files
                         Fixed bug with getSchema() when B2 running from cache directory
                         Added code to migrate old group settings files
 */
package com.spvsoftwareproducts.blackboard.utils;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import java.util.Iterator;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import javax.servlet.http.HttpServletRequest;

import blackboard.data.user.User;
import blackboard.data.course.Course;
import blackboard.data.course.Group;
import blackboard.data.content.Content;
import blackboard.platform.context.Context;
import blackboard.platform.context.ContextManagerFactory;
import blackboard.platform.plugin.PlugInManagerFactory;
import blackboard.platform.plugin.PlugIn;
import blackboard.platform.intl.BundleManagerFactory;
import blackboard.platform.intl.BbResourceBundle;
import blackboard.platform.plugin.PlugInConfig;
import blackboard.platform.plugin.PlugInException;

import blackboard.platform.persistence.PersistenceServiceFactory;
import blackboard.persist.BbPersistenceManager;
import blackboard.platform.filesystem.FileSystemException;
import blackboard.platform.filesystem.manager.CourseFileManager;
import blackboard.platform.filesystem.manager.CourseContentFileManager;
import blackboard.platform.cx.component.CopyControl;
import blackboard.platform.cx.component.ExportControl;
import blackboard.platform.cx.component.ImportControl;

import blackboard.persist.navigation.NavigationItemDbLoader;
import blackboard.data.navigation.NavigationItem;
import blackboard.persist.PersistenceException;

import blackboard.util.EditModeUtil;
import blackboard.platform.config.BbConfig;
import blackboard.platform.config.ConfigurationServiceFactory;

import blackboard.data.ReceiptOptions;
import blackboard.platform.servlet.InlineReceiptUtil;

import blackboard.data.registry.Registry;
import blackboard.data.registry.UserRegistryEntry;
import blackboard.persist.registry.UserRegistryEntryDbLoader;
import blackboard.persist.registry.UserRegistryEntryDbPersister;
import blackboard.data.ValidationException;
import blackboard.persist.Id;
import blackboard.persist.KeyNotFoundException;
import blackboard.persist.user.UserDbLoader;
import blackboard.persist.course.CourseDbLoader;
import blackboard.persist.course.GroupDbLoader;
import blackboard.persist.content.ContentDbLoader;
import blackboard.platform.BbServiceException;

import blackboard.platform.plugin.PlugInUtil;
import blackboard.portal.data.Module;
import blackboard.portal.servlet.PortalUtil;
import blackboard.portal.external.CustomData;
import blackboard.portal.data.PortalExtraInfo;
import blackboard.portal.data.ExtraInfo;
import blackboard.xss.request.BaseXssRequestWrapper;

import blackboard.platform.institutionalhierarchy.service.Node;
import blackboard.platform.institutionalhierarchy.service.NodeManager;
import blackboard.platform.institutionalhierarchy.service.NodeManagerFactory;
import blackboard.platform.institutionalhierarchy.service.ObjectType;
import blackboard.platform.log.Log;
import blackboard.platform.log.LogService;
import blackboard.platform.log.LogServiceFactory;

/**
 * B2Context provides basic support functions for Building Blocks including
 * easy access to language bundle values, configuration settings, navigation
 * items, receipt messages and simple logging.
 * <p>
 * For example,
 * <pre>
 *   B2Context b2Context = new B2Context(request);
 *   pageContext.setAttribute("bundle", b2Context.getResourceStrings());
 *   pageContext.setAttribute("setting", b2Context.getSettings());
 * </pre>
 * will create an instance of this class and save all the resource strings from
 * the <em>bb-manifest</em> bundle for the current locale in a map named
 * <code>bundle</code> and all the systemwide setting values in a map named
 * <code>setting</code>.  This allows these values to be inserted within
 * a JSP file using references such as:
 * <pre>
 *   ${bundle['plugin.name']}
 *   ${setting.homeURL}
 * </pre>
 * New settings can be added, or existing settings updated, as follows:
 * <pre>
 *   setSetting("homeURL", "/");
 * </pre>
 * Changes made to setting values can be saved permanently as follows:
 * <pre>
 *   persistSettings();
 * </pre>
 * The class supports 2 types of setting (giving 4 possible combinations):
 * <ul>
 *   <li><em>global</em>: apply to all instances (contexts) of the building block
 *   <li><em>anonymous</em>: apply to all users of the building block
 * </ul>
 * By default (as in the above examples), a setting is assumed to be global and
 * anonymous, but alternative versions of the settings methods are provided
 * which allow the global and anonymous parameters to be specified.  For example,
 * if <em>global</em> and <em>anonymous</em> are both <code>false</code> then
 * the setting values will apply only to the current user and in the current
 * context (course or content-item).  The settings file used to persist these
 * settings will be saved within the applicable context.
 * <p>
 * Associating anonymous global settings with a node in the Institutional Hierarchy
 * requires Learn 9.1SP8 (or higher); accessing these settings from a module requires
 * Learn 9.1SP10 (or higher).
 *
 * @author      Stephen P Vickers
 * @version     1.9 (22-Apr-18)
 */
public class B2Context {

  private static final int MAX_VALUE_LENGTH = 255;
  private static final String SETTINGS_FILE_EXTENSION = ".properties";
  private static final int[] V90_RELEASE = {351, 440, 505, 539, 572, 613, 670, 692};
  private static final int[] V91_RELEASE = {407, 452, 482};
  private static final String LOG_LEVEL_SETTING = "loglevel";
  private static final String LOG_LEVEL_DEBUG_SETTING = "debug";
  private static final String GROUPS_SETTING = "groups";

// Class variables
  private static volatile UserRegistryEntryDbLoader urLoader = null;
  private static volatile UserRegistryEntryDbPersister urPersister = null;
  private static volatile String className = null;
  private static volatile Boolean nodeSupport = null;
  private static volatile Log log = null;
  private static volatile boolean logDebug = false;
  private static volatile String logPrefix = null;
  private Context context = null;
  private User user = null;
  private Course course = null;
  private Group group = null;
  private Content content = null;
  private Id userId = Id.UNSET_ID;
  private Id courseId = Id.UNSET_ID;
  private Id groupId = Id.UNSET_ID;
  private Id contentId = Id.UNSET_ID;
  private Node node = null;
  private String vendorId = null;
  private String handle = null;
  private String schema = null;
  private String b2Version = null;
  private PlugIn plugIn = null;
  private BbResourceBundle resourceBundle = null;
  private final Properties[][] settings = new Properties[2][2];
  private final Map<Id, Properties> nodeSettings = new HashMap<Id, Properties>();
  private final Map<String, Properties> userSettings = new HashMap<String, Properties>();
  private boolean inheritSettings = false;
  private List<Node> activeNodes = null;
  private HttpServletRequest request = null;
  private String serverUrl = null;
  private Module module = null;
  private String path = null;
  private String root = null;
  private boolean saveEmptyValues = true;
  private boolean ignoreCourseContext = false;
  private boolean ignoreGroupContext = false;
  private boolean ignoreContentContext = false;

  /**
   * Class constructor using context.
   *
   * @deprecated
   *
   * @param context    the current context object
   */
  public B2Context(Context context) {

    this.context = context;
    this.request = context.getRequest();

    init(context);

  }

  /**
   * Class constructor using request.
   *
   * @param request    the current request object
   */
  public B2Context(HttpServletRequest request) {

    Context aContext = null;
    if (request != null) {
      this.request = request;
      aContext = ContextManagerFactory.getInstance().setContext(request);
    }
    this.context = aContext;

    init(aContext);

  }

  /**
   * Class constructor.
   */
  public B2Context() {

    init(null);

  }

  private void initContext(Context context) {

    this.user = null;
    this.course = null;
    this.group = null;
    this.content = null;
    this.userId = Id.UNSET_ID;
    this.courseId = Id.UNSET_ID;
    this.groupId = Id.UNSET_ID;
    this.contentId = Id.UNSET_ID;
    if (context != null) {
      if (context.hasUserContext()) {
        this.user = context.getUser();
        this.userId = context.getUserId();
      }
      if (context.hasCourseContext()) {
        this.course = context.getCourse();
        this.courseId = context.getCourseId();
      }
      if (context.hasGroupContext()) {
        this.group = context.getGroup();
        this.groupId = context.getGroupId();
      }
      if (context.hasContentContext()) {
        this.content = context.getContent();
        this.contentId = context.getContentId();
      }
    }

  }

  private void init(Context context) {

    initContext(context);
    if (this.request != null) {
      this.module = (Module)request.getAttribute("blackboard.portal.data.Module");
      this.serverUrl = this.request.getRequestURL().toString();
      this.serverUrl = this.serverUrl.substring(0, this.serverUrl.indexOf('/', this.serverUrl.indexOf("://") + 3));
    }

    if (className == null) {
      String name = this.getClass().getName();
      className = name.substring(name.lastIndexOf(".") + 1);
    }
    String location = this.getClass().getClassLoader().getResource(this.getClass().getName().replace('.', '/') + ".class").toString();
    int pos = location.indexOf("/plugins/");
    if (pos >= 0) {
      location = location.substring(pos + 9);
      location = location.substring(0, location.indexOf('/'));
      String[] plugInElements = location.split("-", 2);
      this.vendorId = plugInElements[0];
      this.handle = plugInElements[1];
      this.path = PlugInUtil.getUri(this.vendorId, this.handle, "");
      pos = this.path.indexOf(location);
      this.schema = this.path.substring(pos + location.length() + 1);
      this.schema = this.schema.substring(0, this.schema.indexOf('/'));
      PlugInConfig config;
      try {
        config = new PlugInConfig(this.vendorId, this.handle);
        File configFile = config.getConfigDirectory();
        this.root = configFile.getPath();
        this.root = this.root.substring(0, this.root.lastIndexOf(File.separatorChar) + 1) + "webapp" + File.separator;
      } catch (PlugInException e) {
        this.root = null;
      }
    }

    initNode();

  }

  /**
   * Gets the current request object.
   *
   * @return         the request object
   */
  public HttpServletRequest getRequest() {

    return getRequest(false);

  }

  /**
   * Gets the current request object.
   *
   * @param unfiltered    true if an unfiltered value should be returned
   * @return         the request object
   */
  public HttpServletRequest getRequest(boolean unfiltered) {

    HttpServletRequest httpRequest = this.request;
    if (unfiltered && getIsVersion(9, 1, 10) && (httpRequest instanceof BaseXssRequestWrapper)) {
      httpRequest = (HttpServletRequest)(((BaseXssRequestWrapper)httpRequest).getRequest());
    }

    return httpRequest;

  }

  /**
   * Gets the current inherit settings value.
   *
   * @return         <code>true</code> if settings are being inherited by nodes
   */
  public boolean getInheritSettings() {

    return this.inheritSettings;

  }

  /**
   * Gets the current inherit settings value.
   *
   * @param inheritSettings     <code>true</code> if settings are to be inherited by nodes
   */
  public void setInheritSettings(boolean inheritSettings) {

    if (this.inheritSettings ^ inheritSettings) {
      this.inheritSettings = inheritSettings;
      this.settings[1][1] = null;
    }

  }

  /**
   * Gets the current node support setting.
   *
   * @return         <code>true</code> if node support is enabled
   */
  public synchronized static boolean getNodeSupport() {

    if (nodeSupport == null) {
      nodeSupport = getIsVersion(9, 1, 8);
    }

    return nodeSupport.booleanValue();

  }

  /**
   * Gets the current node.
   *
   * @return         the node
   */
  public Node getNode() {

    return this.node;

  }

  /**
   * Clears the current node.
   */
  public void clearNode() {

    if (this.node != null) {
      this.node = null;
      this.settings[1][1] = null;
    }

  }

  /**
   * Sets the current node.
   *
   * @param aNode    the node
   */
  public void setNode(Node aNode) {

    if (getNodeSupport()) {
      if (aNode == null) {
        NodeManager nodeManager = NodeManagerFactory.getHierarchyManager();
        aNode = nodeManager.loadRootNode();
      }
      if (!aNode.equals(this.node)) {
        this.node = aNode;
        this.settings[1][1] = null;
      }
    } else if (this.node != null) {
      this.node = null;
      this.settings[1][1] = null;
    }

  }

  /**
   * Checks if the current node is the root of the Institutional Hierarchy.
   *
   * @return  <code>true</code> if the current node is the root of the Institutional Hierarchy
   */
  public boolean getIsRootNode() {

    boolean isRoot = true;
    if (this.node != null) {
      try {
        NodeManager nodeManager = NodeManagerFactory.getHierarchyManager();
        isRoot = nodeManager.isRootNode(this.node.getNodeId());
      } catch (PersistenceException e) {
        isRoot = false;
      }
    }

    return isRoot;

  }

  /**
   * Gets the list of nodes for which there is at least one setting value.
   *
   * @return         the list of nodes
   */
  public List<Node> getActiveNodes() {

    return getActiveNodes(null);

  }

  /**
   * Gets the list of nodes for which the named setting value exists.
   *
   * @param settingName  name of setting to confirm node is active (null for any setting)
   *
   * @return         the list of nodes
   */
  public List<Node> getActiveNodes(String settingName) {

    if (this.activeNodes == null) {
      if (getNodeSupport()) {
        this.activeNodes = getFileActiveNodes(settingName);
      } else {
        this.activeNodes = new ArrayList<Node>();
      }
    }

    return Collections.unmodifiableList(this.activeNodes);

  }

  /**
   * Checks if the current node has any settings.
   *
   * @return  <code>true</code> if the current node has at least one setting
   */
  public boolean getIsActiveNode() {

    return getIsActiveNode(null);

  }

  /**
   * Checks if the current node has a setting of a specified name.
   *
   * @param settingName  name of setting to check for (null for any name)
   *
   * @return  <code>true</code> if the current node has the setting specified
   */
  public boolean getIsActiveNode(String settingName) {

    Node aNode = this.node;
    this.getActiveNodes(settingName);
    if (this.node == null) {
      NodeManager nodeManager = NodeManagerFactory.getHierarchyManager();
      aNode = nodeManager.loadRootNode();
    }

    return this.activeNodes.contains(aNode);

  }

  /**
   * Gets the current Blackboard context object.
   *
   * @return         the Blackboard context object
   */
  public Context getContext() {

    return this.context;

  }

  /**
   * Sets the current user.
   *
   * @param user The current user
   */
  public void setUser(User user) {

    boolean changed = true;
    if (user == null) {
      changed = !this.userId.equals(Id.UNSET_ID);
      this.user = null;
      this.userId = Id.UNSET_ID;
    } else if (!user.getId().equals(this.userId)) {
      this.user = user;
      this.userId = user.getId();
    } else {
      changed = false;
    }
    if (changed) {
      this.settings[0][0] = null;
      this.settings[1][0] = null;
    }

  }

  /**
   * Gets the current user.
   *
   * @return the user
   */
  public User getUser() {

    if ((this.user == null) && !this.userId.equals(Id.UNSET_ID)) {
      try {
        UserDbLoader userLoader = UserDbLoader.Default.getInstance();
        this.user = userLoader.loadById(this.userId);
      } catch (PersistenceException e) {
        log(true, "Error in B2Context.getUser:", e);
      }
    }

    return this.user;

  }

  /**
   * Sets the current course.
   *
   * @param course The current course
   */
  public void setCourse(Course course) {

    boolean changed = !this.courseId.equals(Id.UNSET_ID);
    if (course == null) {
      this.course = null;
      this.courseId = Id.UNSET_ID;
    } else if (!course.getId().equals(this.courseId)) {
      this.course = course;
      this.courseId = course.getId();
    } else {
      changed = false;
    }
    if (changed && (this.ignoreContentContext || !this.hasContentContext()) && (this.ignoreGroupContext || !this.hasGroupContext())) {
      this.settings[0][0] = null;
      this.settings[0][1] = null;
    }
    initNode();

  }

  /**
   * Gets the current course.
   *
   * @return the course
   */
  public Course getCourse() {

    if ((this.course == null) && !this.courseId.equals(Id.UNSET_ID)) {
      try {
        CourseDbLoader courseLoader = CourseDbLoader.Default.getInstance();
        this.course = courseLoader.loadById(this.courseId);
      } catch (PersistenceException e) {
        log(true, "Error in B2Context.getCourse:", e);
      }
    }

    return this.course;

  }

  /**
   * Sets the current group.
   *
   * @param group The current group
   */
  public void setGroup(Group group) {

    boolean changed = !this.groupId.equals(Id.UNSET_ID);
    if (group == null) {
      this.group = null;
      this.groupId = Id.UNSET_ID;
    } else if (!group.getId().equals(this.groupId)) {
      this.group = group;
      this.groupId = group.getId();
      changed = true;
    } else {
      changed = false;
    }
    if (changed && (this.ignoreContentContext || !this.hasContentContext()) && !this.ignoreGroupContext) {
      this.settings[0][0] = null;
      this.settings[0][1] = null;
    }

  }

  /**
   * Gets the current group.
   *
   * @return the group
   */
  public Group getGroup() {

    if ((this.group == null) && !this.groupId.equals(Id.UNSET_ID)) {
      try {
        GroupDbLoader groupLoader = GroupDbLoader.Default.getInstance();
        this.group = groupLoader.loadById(this.groupId);
      } catch (PersistenceException e) {
        log(true, "Error in B2Context.getGroup:", e);
      }
    }

    return this.group;

  }

  /**
   * Sets the current content.
   *
   * @param content The current content
   */
  public void setContent(Content content) {

    boolean changed = !this.contentId.equals(Id.UNSET_ID);
    if (content == null) {
      this.content = null;
      this.contentId = Id.UNSET_ID;
    } else if (!content.getId().equals(this.contentId)) {
      this.content = content;
      this.contentId = content.getId();
    } else {
      changed = false;
    }
    if (changed && !this.ignoreContentContext) {
      this.settings[0][0] = null;
      this.settings[0][1] = null;
    }

  }

  /**
   * Gets the current content.
   *
   * @return the content
   */
  public Content getContent() {

    if (!this.contentId.equals(Id.UNSET_ID)) {
      try {
        ContentDbLoader contentLoader = ContentDbLoader.Default.getInstance();
        this.content = contentLoader.loadById(this.contentId);
      } catch (PersistenceException e) {
        log(true, "Error in B2Context.geContent:", e);
      }
    }

    return this.content;

  }

  /**
   * Sets the current user ID.
   *
   * @param userId the user ID
   */
  public void setUserId(Id userId) {

    boolean changed = !this.userId.equals(Id.UNSET_ID);
    if (userId == null) {
      this.userId = Id.UNSET_ID;
      this.user = null;
    } else if (!userId.equals(this.userId)) {
      this.userId = userId;
      this.user = null;
    }
    if (changed) {
      this.settings[0][0] = null;
      this.settings[1][0] = null;
    }

  }

  /**
   * Sets the current user ID.
   *
   * @param userIdString the user ID as a string
   *
   * @return  <code>true</code> if the user ID was set
   */
  public boolean setUserId(String userIdString) {

    boolean ok = true;
    Id aUserId = Id.UNSET_ID;
    if ((userIdString != null) && !userIdString.equals(this.userId.toExternalString())) {
      try {
        aUserId = Id.generateId(User.DATA_TYPE, userIdString);
      } catch (PersistenceException e) {
        log(true, "Error in B2Context.setUserId:", e);
        ok = false;
      }
    }
    setUserId(aUserId);

    return ok;

  }

  /**
   * Checks if the current context has a User reference.
   *
   * @return  <code>true</code> if a User reference exists
   */
  public boolean hasUserContext() {

    return !this.userId.equals(Id.UNSET_ID);

  }

  /**
   * Gets the current user ID.
   *
   * @return the user ID
   */
  public Id getUserId() {

    return this.userId;

  }

  /**
   * Gets the current user ID.
   *
   * @return the user ID
   */
  public String getUserIdAsString() {

    String id = null;
    if (this.hasUserContext()) {
      id = this.userId.toExternalString();
    }

    return id;

  }

  /**
   * Sets the current course ID.
   *
   * @param courseId the course ID
   */
  public void setCourseId(Id courseId) {

    boolean changed = !this.courseId.equals(Id.UNSET_ID);
    if (courseId == null) {
      this.courseId = Id.UNSET_ID;
      this.course = null;
    } else if (!courseId.equals(this.courseId)) {
      this.courseId = courseId;
      this.course = null;
    }
    if (changed && (this.ignoreContentContext || !this.hasContentContext()) && (this.ignoreGroupContext || !this.hasGroupContext())) {
      this.settings[0][0] = null;
      this.settings[0][1] = null;
    }
    initNode();

  }

  /**
   * Sets the current course ID.
   *
   * @param courseIdString the course ID as a string
   *
   * @return  <code>true</code> if the course ID was set
   */
  public boolean setCourseId(String courseIdString) {

    boolean ok = true;
    Id aCourseId = Id.UNSET_ID;
    if ((courseIdString != null) && !courseIdString.equals(this.courseId.toExternalString())) {
      try {
        aCourseId = Id.generateId(Course.DATA_TYPE, courseIdString);
      } catch (PersistenceException e) {
        log(true, "Error in B2Context.setCourseId:", e);
        ok = false;
      }
    }
    setCourseId(aCourseId);

    return ok;

  }

  /**
   * Checks if the current context has a Course reference.
   *
   * @return  <code>true</code> if a Course reference exists
   */
  public boolean hasCourseContext() {

    return !this.courseId.equals(Id.UNSET_ID);

  }

  /**
   * Gets the current course ID.
   *
   * @return the course ID
   */
  public Id getCourseId() {

    return this.courseId;

  }

  /**
   * Gets the current course ID.
   *
   * @return the course ID
   */
  public String getCourseIdAsString() {

    String id = null;
    if (this.hasCourseContext()) {
      id = this.courseId.toExternalString();
    }

    return id;

  }

  /**
   * Sets the current group ID.
   *
   * @param groupId the group ID
   */
  public void setGroupId(Id groupId) {

    boolean changed = !this.groupId.equals(Id.UNSET_ID);
    if (groupId == null) {
      this.groupId = Id.UNSET_ID;
      this.group = null;
    } else if (!groupId.equals(this.groupId)) {
      this.groupId = groupId;
      this.group = null;
    }
    if (changed && (this.ignoreContentContext || !this.hasContentContext()) && !this.ignoreGroupContext) {
      this.settings[0][0] = null;
      this.settings[0][1] = null;
    }

  }

  /**
   * Sets the current group ID.
   *
   * @param groupIdString the group ID as a string
   *
   * @return  <code>true</code> if the group ID was set
   */
  public boolean setGroupId(String groupIdString) {

    boolean ok = true;
    Id aGroupId = Id.UNSET_ID;
    if ((groupIdString != null) && !groupIdString.equals(this.groupId.toExternalString())) {
      try {
        aGroupId = Id.generateId(Group.DATA_TYPE, groupIdString);
      } catch (PersistenceException e) {
        log(true, "Error in B2Context.setGroupId:", e);
        ok = false;
      }
    }
    setGroupId(aGroupId);

    return ok;

  }

  /**
   * Checks if the current context has a Group reference.
   *
   * @return  <code>true</code> if a Group reference exists
   */
  public boolean hasGroupContext() {

    return !this.groupId.equals(Id.UNSET_ID);

  }

  /**
   * Gets the current group ID.
   *
   * @return the group ID
   */
  public Id getGroupId() {

    return this.groupId;

  }

  /**
   * Gets the current group ID.
   *
   * @return the group ID
   */
  public String getGroupIdAsString() {

    String id = null;
    if (this.hasGroupContext()) {
      id = this.groupId.toExternalString();
    }

    return id;

  }

  /**
   * Sets the current content ID.
   *
   * @param contentId the content ID
   */
  public void setContentId(Id contentId) {

    boolean changed = !this.contentId.equals(Id.UNSET_ID);
    if (contentId == null) {
      this.contentId = Id.UNSET_ID;
      this.content = null;
    } else if (!contentId.equals(this.contentId)) {
      this.contentId = contentId;
      this.content = null;
    }
    if (changed && !this.ignoreContentContext) {
      this.settings[0][0] = null;
      this.settings[0][1] = null;
    }

  }

  /**
   * Sets the current content ID.
   *
   * @param contentIdString the content ID as a string
   *
   * @return  <code>true</code> if the content ID was set
   */
  public boolean setContentId(String contentIdString) {

    boolean ok = true;
    Id aContentId = Id.UNSET_ID;
    if ((contentIdString != null) && !contentIdString.equals(this.contentId.toExternalString())) {
      try {
        aContentId = Id.generateId(Content.DATA_TYPE, contentIdString);
      } catch (PersistenceException e) {
        log(true, "Error in B2Context.setContentId:", e);
        ok = false;
      }
    }
    setContentId(aContentId);

    return ok;

  }

  /**
   * Checks if the current context has a Content reference.
   *
   * @return  <code>true</code> if a Content reference exists
   */
  public boolean hasContentContext() {

    return !this.contentId.equals(Id.UNSET_ID);

  }

  /**
   * Gets the current content ID.
   *
   * @return the content ID
   */
  public Id getContentId() {

    return this.contentId;

  }

  /**
   * Gets the current content ID.
   *
   * @return the content ID
   */
  public String getContentIdAsString() {

    String id = null;
    if (this.hasContentContext()) {
      id = this.contentId.toExternalString();
    }

    return id;

  }

  /**
   * Sets the current Blackboard context object.
   *
   * @deprecated Replaced by {@link #setUser(User)}, {@link #setCourse(Course)}, {@link #setGroup(Group)}, {@link #setContent(Content)}
   *
   * @param context    the context object
   */
  public void setContext(Context context) {

    this.context = context;
    initContext(context);
    this.settings[0][0] = null;  // invalidate any loaded settings
    this.settings[0][1] = null;
    this.settings[1][0] = null;
    this.settings[1][1] = null;

    initNode();

  }

  /**
   * Gets the vendor ID for the current Building Block.
   *
   * @return         the value of the vendor ID
   */
  public String getVendorId() {

    return this.vendorId;

  }

  /**
   * Gets the handle for the current Building Block.
   *
   * @return         the value of the handle
   */
  public String getHandle() {

    return this.handle;

  }

  /**
   * Gets the schema for the current Learn 9 instance.
   *
   * @return         the value of the schema
   */
  public String getSchema() {

    return this.schema;

  }

  /**
   * Gets the version for the current Building Block.
   *
   * @return         the version of the building block
   */
  public String getB2Version() {

    if (this.b2Version == null) {
      this.getPlugIn();
      if (this.plugIn != null) {
        this.b2Version = this.plugIn.getVersion().toString();
      }
    }

    return this.b2Version;

  }

  /**
   * Checks whether the current Building Block is at a specific version, or later.
   * <p>
   * For example, to check if the current version of the Building Block is 2.12.5, or later
   * use parameters of 2, 12 and 5.
   *
   * @param  major        the major release number
   * @param  minor        the minor release number
   * @param  build        the build number
   * @return              <code>true</code> if this building block has the specified version number or greater
   */
  public boolean getIsB2Version(int major, int minor, int build) {

    boolean ok = false;

    int[] iVersion = new int[3];
    iVersion[0] = 0;
    iVersion[1] = 0;
    iVersion[2] = 0;

    String version = getB2Version();
    if (version != null) {
      String[] sVersion = version.split("\\.");
      if (sVersion.length >= 1) {
        iVersion[0] = stringToInt(sVersion[0], 0);
      }
      if (sVersion.length >= 2) {
        iVersion[1] = stringToInt(sVersion[1], 0);
      }
      if (sVersion.length >= 3) {
        iVersion[2] = stringToInt(sVersion[2], 0);
      }
    }

    if (iVersion[0] > major) {
      ok = true;
    } else if (iVersion[0] == major) {
      if (iVersion[1] > minor) {
        ok = true;
      } else if (iVersion[1] == minor) {
        ok = iVersion[2] >= build;
      }
    }

    return ok;

  }

  /**
   * Gets the path for the current Building Block.
   *
   * @return         the URL path
   */
  public String getServerUrl() {

    return this.serverUrl;

  }

  /**
   * Gets the path for the current Building Block.
   *
   * @return         the URL path
   */
  public String getPath() {

    return this.path;

  }

  /**
   * Gets the path for the current Building Block using a specified schema name.
   *
   * @param schema   name of schema to use in path
   *
   * @return         the URL path
   */
  public String getPath(String schema) {

    return this.path.replace("-" + this.schema + "/", "-" + schema + "/");

  }

  /**
   * Gets the file root for the current Building Block.
   *
   * @return         the file root
   */
  public String getWebappRoot() {

    return this.root;

  }

  /**
   * Gets a parameter from the current request object.
   * <p>
   * The default value is returned if no value exists for the specified parameter.
   *
   * @param name          the name of the request parameter
   * @param defaultValue  the default value
   * @return              the value of the request parameter
   */
  public String getRequestParameter(String name, String defaultValue) {

    return getRequestParameter(name, defaultValue, false);

  }

  /**
   * Gets a parameter from the current request object.
   * <p>
   * The default value is returned if no value exists for the specified parameter.
   *
   * @param name          the name of the request parameter
   * @param defaultValue  the default value
   * @param unfiltered    true if an unfiltered value should be returned
   * @return              the value of the request parameter
   */
  public String getRequestParameter(String name, String defaultValue, boolean unfiltered) {

    if ((name != null) && (name.length() > 0)) {
      String value;
      HttpServletRequest aRequest = getRequest(unfiltered);
      value = aRequest.getParameter(name);
      if (value != null) {
        defaultValue = value;
      }
    }

    return defaultValue;

  }

  /**
   * Gets the values for a parameter from a request object.
   * <p>
   * If more than one value is included in the request for the specified parameter,
   * the values are concatenated into a single string separated by a comma.
   * <p>
   * The default value is returned if no value exists for the specified parameter.
   *
   * @param name          the name of the request parameter
   * @param defaultValue  the default value
   * @return              the value of the request parameter
   */
  public String getRequestParameterValues(String name, String defaultValue) {

    return getRequestParameterValues(name, defaultValue, false);

  }

  /**
   * Gets the values for a parameter from a request object.
   * <p>
   * If more than one value is included in the request for the specified parameter,
   * the values are concatenated into a single string separated by a comma.
   * <p>
   * The default value is returned if no value exists for the specified parameter.
   *
   * @param name          the name of the request parameter
   * @param defaultValue  the default value
   * @param unfiltered    true if unfiltered values should be returned
   * @return              the value of the request parameter
   */
  public String getRequestParameterValues(String name, String defaultValue, boolean unfiltered) {

    if ((this.request != null) && (name != null) && (name.length() > 0)) {
      String[] values;
      HttpServletRequest aRequest = getRequest(unfiltered);
      values = aRequest.getParameterValues(name);
      if ((values != null) && (values.length > 0)) {
        StringBuilder valueList = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
          if (values[i].length() > 0) {
            if (valueList.length() > 0) {
              valueList.append(',');
            }
            valueList.append(values[i]);
          }
        }
        defaultValue = valueList.toString();
      }
    }

    return defaultValue;

  }

  /**
   * Gets the values of all parameters from a request object.
   * <p>
   * If more than one value is included in the request for a parameter,
   * the values are concatenated into a single entry separated by a comma.
   *
   * @return              a map of the request parameters (name=value)
   */
  public Map<String, String> getRequestParameters() {

    return getRequestParameters(false);

  }

  /**
   * Gets the values of all parameters from a request object.
   * <p>
   * If more than one value is included in the request for a parameter,
   * the values are concatenated into a single entry separated by a comma.
   *
   * @param unfiltered    true if unfiltered values should be returned
   * @return              a map of the request parameters (name=value)
   */
  public Map<String, String> getRequestParameters(boolean unfiltered) {

    Map<String, String> params = new HashMap<String, String>();
    if (this.request != null) {
      for (Enumeration e = this.request.getParameterNames(); e.hasMoreElements();) {
        String name = (String)e.nextElement();
        String[] values;
        HttpServletRequest aRequest = getRequest(unfiltered);
        values = aRequest.getParameterValues(name);
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
          if (value.length() > 0) {
            value.append(',');
          }
          value.append(values[i]);
        }
        params.put(name, value.toString());
      }
    }

    return params;

  }

  /**
   * Gets the plugin object for this building block.
   *
   * @return         the plugin object
   */
  public PlugIn getPlugIn() {

    if (this.plugIn == null) {
      this.plugIn = PlugInManagerFactory.getInstance().getPlugIn(this.vendorId, this.handle);
    }

    return this.plugIn;

  }

  private BbResourceBundle getResourceBundle() {

    if (this.resourceBundle == null) {
      this.resourceBundle = BundleManagerFactory.getInstance().getPluginBundle(getPlugIn().getId());
    }

    return this.resourceBundle;

  }

  /**
   * Gets a resource string from the bundle for the current locale.
   * <p>
   * The resource strings are loaded from the bb-manifest bundle file
   * for the current locale as located in the <code>WEB-INF/bundles</code>
   * directory.
   *
   * @param key     the name of the resource string
   * @return         the value of the resource string
   */
  public String getResourceString(String key) {

    return getResourceString(key, null);

  }

  /**
   * Gets a resource string from the bundle for the current locale.
   * <p>
   * The default value is returned if no value exists for the specified key.
   * <p>
   * The resource strings are loaded from the bb-manifest bundle file
   * for the current locale as located in the <code>WEB-INF/bundles</code>
   * directory.
   *
   * @param key           the name of the resource string
   * @param defaultValue  the default value
   * @return              the value of the resource string
   */
  public String getResourceString(String key, String defaultValue) {

    if (key != null) {
      String value = getResourceBundle().getString(key, true);
      if (value != null) {
        defaultValue = value;
      }
    }

    return defaultValue;

  }

  /**
   * Gets all the resource strings from the bundle for the current locale.
   * <p>
   * The resource strings are loaded from the bb-manifest bundle file
   * for the current locale as located in the <code>WEB-INF/bundles</code>
   * directory.
   *
   * @return         a map containing the key and value of each resource string
   */
  public Map<String, String> getResourceStrings() {

    Map<String, String> resourceStrings = new HashMap<String, String>();
    for (Iterator<String> iter = getResourceBundle().getKeys().iterator(); iter.hasNext();) {
      String name = iter.next();
      resourceStrings.put(name, getResourceString(name));
    }

    return resourceStrings;

  }

  private Properties loadSettings(boolean global, boolean anonymous, String suffix) {

    return this.loadSettings(global, anonymous, suffix, null, false);

  }

  private Properties loadSettings(boolean global, boolean anonymous, Node aNode, boolean nodeOnly) {

    return this.loadSettings(global, anonymous, null, aNode, nodeOnly);

  }

  private Properties loadSettings(boolean global, boolean anonymous, String suffix, Node aNode, boolean nodeOnly) {

    int iGlobal = (global) ? 1 : 0;
    int iAnonymous = (anonymous) ? 1 : 0;
    Properties props = null;
    if ((suffix == null) || (suffix.length() <= 0)) {
      if (!global || !anonymous || (aNode == null) || !nodeOnly) {
        props = this.settings[iGlobal][iAnonymous];
      } else {
        props = this.nodeSettings.get(aNode.getNodeId());
      }
    }

    if (props == null) {

      if (!anonymous) {
        props = this.loadUserSettings(global, suffix);
      } else if ((this.module != null) && !global) {
        props = this.loadCustomSettings(suffix);
      } else if (aNode != null) {
        props = this.loadFileSettings(global, suffix, aNode);
      } else {
        props = this.loadFileSettings(global, suffix);
      }

      if ((suffix == null) || (suffix.length() <= 0)) {
        if (global && anonymous && (aNode == null)) {
          String logSetting = props.getProperty(className + "." + LOG_LEVEL_SETTING);
          if (logSetting != null) {
            setLogDebug(logSetting.equals(LOG_LEVEL_DEBUG_SETTING));
            props.remove(className + "." + LOG_LEVEL_SETTING);
          }
        }
        this.settings[iGlobal][iAnonymous] = props;
      }
    }

    return props;

  }

  private void initNode() {

    if (getNodeSupport()) {
      if (!this.courseId.equals(Id.UNSET_ID)) {
        try {
          Id nodeId = NodeManagerFactory.getAssociationManager().loadPrimaryNodeId(this.courseId, ObjectType.Course); //.loadCoursePrimaryNodeId(this.courseId);
          if (nodeId != null) {
            this.node = NodeManagerFactory.getHierarchyManager().loadNodeById(nodeId);
          }
        } catch (PersistenceException e) {
          log(true, "Error in B2Context.initNode:", e);
        }
      } else if ((this.module != null) && getIsVersion(9, 1, 10)) {
        try {
          List<Node> nodes = NodeManagerFactory.getAssociationManager().loadAssociatedNodes(this.module.getId(), ObjectType.Module);
          if ((nodes != null) && (nodes.size() > 0)) {
            this.node = nodes.get(0);
          }
        } catch (PersistenceException e) {
          log(true, "Error in B2Context.initNode:", e);
        }
      }
    }

  }

  List<Node> getFileActiveNodes(String settingName) {

    List<Node> active = new ArrayList<Node>();

    NodeManager nodeManager = NodeManagerFactory.getHierarchyManager();
    List<Node> allNodes;
    try {
      allNodes = nodeManager.loadAllChildren(nodeManager.loadRootNode().getNodeId());
    } catch (PersistenceException e) {
      allNodes = new ArrayList<Node>();
    }
    Node aNode;
    Properties aNodeSettings;
    boolean isActive;
    for (Iterator<Node> iter = allNodes.iterator(); iter.hasNext();) {
      aNode = iter.next();
      aNodeSettings = loadFileSettings(true, null, aNode);
      if ((settingName == null) || (settingName.length() <= 0)) {
        isActive = !aNodeSettings.isEmpty();
      } else {
        isActive = aNodeSettings.getProperty(settingName) != null;
      }
      if (isActive) {
        active.add(aNode);
      }
    }

    return active;

  }

  private Properties getFileNodeProps(Id nodeId) {

    Properties props = new Properties();
    try {
      NodeManager nodeManager = NodeManagerFactory.getHierarchyManager();
      if (!this.inheritSettings) {
        props.putAll(loadFileSettings(true, null, null));
        Node aNode = nodeManager.loadNodeById(nodeId);
        props.putAll(loadFileSettings(true, null, aNode));
      } else if (nodeId == null) {
        props.putAll(loadFileSettings(true, null, null));
      } else {
        Node aNode = nodeManager.loadNodeById(nodeId);
        props.putAll(getFileNodeProps(aNode.getParentId()));
        props.putAll(loadFileSettings(true, null, aNode));
      }
    } catch (PersistenceException e) {
      log(true, "Error in B2Context.getFileNodeProps:", e);
    }

    return props;

  }

  private Properties loadFileSettings(boolean global, String suffix) {

    Properties props;
    if (global && (this.node != null) && ((suffix == null) || (suffix.length() <= 0))) {
      props = getFileNodeProps(this.node.getNodeId());
    } else {
      props = loadFileSettings(global, suffix, this.node);
    }

    return props;

  }

  private Properties loadFileSettings(boolean global, String suffix, Node aNode) {

    Properties props = new Properties();
    File configFile = getConfigFile(global, suffix, aNode);
    if ((configFile != null) && configFile.exists()) {
      FileInputStream fiStream = null;
      try {
        fiStream = new FileInputStream(configFile);
        props.load(fiStream);
      } catch (FileNotFoundException e) {
        props.clear();
      } catch (IOException e) {
        log(true, "Error in B2Context.loadFileSettings:", e);
        props.clear();
      } finally {
        if (fiStream != null) {
          try {
            fiStream.close();
          } catch (IOException e) {
            log(true, "Error in B2Context.loadFileSettings:", e);
            props.clear();
          }
        }
      }
    }
    if (global && (aNode != null) && ((suffix == null) || (suffix.length() <= 0))) {
      this.nodeSettings.put(aNode.getNodeId(), props);
    }

    return props;

  }

  private Properties loadCustomSettings(String suffix) {

    Properties props = new Properties();
    CustomData customData = getCustomData();
    if ((customData != null) && (customData.getKeySet() != null)) {
      String prefix = "";
      if ((suffix != null) && (suffix.length() > 0)) {
        prefix = suffix + ":";
      }
      for (Iterator iter = customData.getKeySet().iterator(); iter.hasNext();) {
        String key = (String)iter.next();
        if (prefix.length() <= 0) {
          props.put(key, customData.getValue(key));
        } else if (key.startsWith(prefix)) {
          key = key.substring(prefix.length());
          props.put(key.substring(prefix.length()), customData.getValue(key));
        }
      }
    }

    return props;

  }

  private CustomData getCustomData() {

    CustomData customData = null;
    try {
      customData = CustomData.getCustomData(this.module.getId(), null);
    } catch (blackboard.persist.PersistenceException e) {
      log(true, "Error in B2Context.getCustomData:", e);
//// May need to comment out the next 2 lines when compiling against Learn 9.1
//    } catch (blackboard.data.ValidationException e) {
    } catch (Exception e) {
      log(true, "Error in B2Context.getCustomData:", e);
////
    }

    return customData;

  }

  private void getURLoader() {

    if (urLoader == null) {
      try {
        BbPersistenceManager pm = PersistenceServiceFactory.getInstance().getDbPersistenceManager();
        urLoader = (UserRegistryEntryDbLoader)pm.getLoader("UserRegistryEntryDbLoader");
      } catch (PersistenceException e) {
        log(true, "Error in B2Context.getURLoader:", e);
      }
    }

  }

  private void getURPersister() {

    if (urPersister == null) {
      try {
        BbPersistenceManager pm = PersistenceServiceFactory.getInstance().getDbPersistenceManager();
        urPersister = (UserRegistryEntryDbPersister)pm.getPersister("UserRegistryEntryDbPersister");
      } catch (PersistenceException e) {
        log(true, "Error in B2Context.getURPersister:", e);
      }
    }

  }

  private Properties loadUserSettings(boolean global, String suffix) {

    StringBuilder saveAsName = new StringBuilder(String.valueOf(global));
    if ((suffix != null) && (suffix.length() > 0)) {
      saveAsName = saveAsName.append("-").append(suffix);
    }
    String prefix;
    String name;
    String value;
    Properties props = this.userSettings.get(saveAsName.toString());
    if (props == null) {
      getURLoader();
      Registry userRegistry = null;
      if ((urLoader != null) && !this.userId.equals(Id.UNSET_ID)) {
        try {
          userRegistry = urLoader.loadRegistryByUserId(this.userId);
        } catch (KeyNotFoundException e) {
          log(true, "Error in B2Context.loadUserSettings:", e);
        } catch (PersistenceException e) {
          log(true, "Error in B2Context.loadUserSettings:", e);
        }
      }
      props = new Properties();
      if (userRegistry != null) {
        prefix = this.vendorId + "-" + this.handle;
        UserRegistryEntry userRegEntry;
        for (Iterator iter = userRegistry.entries().iterator(); iter.hasNext();) {
          userRegEntry = (UserRegistryEntry)iter.next();
          name = userRegEntry.getKey();
          if (name.startsWith(prefix)) {
            value = userRegEntry.getValue();
            if (value.length() <= 0) {
              value = userRegEntry.getLongValue();
            }
            if (value != null) {
              props.put(name, value);
            }
          }
        }
      }
      Properties saveProps = new Properties();
      saveProps.putAll(props);
      this.userSettings.put(saveAsName.toString(), saveProps);
    }
    Properties userProps = new Properties();
    Map.Entry<String, String> entry;
    prefix = getUserSettingsPrefix(global, suffix);
    for (Iterator<String> iter = props.stringPropertyNames().iterator(); iter.hasNext();) {
      name = iter.next();
      if (name.startsWith(prefix)) {
        value = props.getProperty(name);
        name = name.substring(prefix.length());
        userProps.put(name, value);
      }
    }

    return userProps;

  }

  /**
   * Gets the SaveEmptyValues setting.
   *
   * @return         the value of the setting
   */
  public boolean getSaveEmptyValues() {

    return this.saveEmptyValues;

  }

  /**
   * Sets the SaveEmptyValues setting.
   *
   * @param saveEmptyValues  true (default) if settings with empty values should be saved; false if settings should be deleted if they have no value (empty string)
   */
  public void setSaveEmptyValues(boolean saveEmptyValues) {

    this.saveEmptyValues = saveEmptyValues;

  }

  /**
   * Gets the IgnoreCourseContext setting.
   *
   * @deprecated
   *
   * @return         the value of the setting
   */
  public boolean getIgnoreCourseContext() {

    return this.ignoreCourseContext;

  }

  /**
   * Sets the IgnoreCourseContext setting.
   *
   * @deprecated
   *
   * @param ignoreCourseContext  true if the existence of a course context is to be ignored (default = true)
   */
  public void setIgnoreCourseContext(boolean ignoreCourseContext) {

    if (this.ignoreCourseContext != ignoreCourseContext) {
      this.ignoreCourseContext = ignoreCourseContext;
      this.settings[0][0] = null;  // invalidate any loaded settings
      this.settings[0][1] = null;
    }

  }

  /**
   * Gets the IgnoreGroupContext setting.
   *
   * @return         the value of the setting
   */
  public boolean getIgnoreGroupContext() {

    return this.ignoreGroupContext;

  }

  /**
   * Sets the IgnoreGroupContext setting.
   *
   * @param ignoreGroupContext  true if the existence of a group context is to be ignored (default = true)
   */
  public void setIgnoreGroupContext(boolean ignoreGroupContext) {

    if (this.ignoreGroupContext != ignoreGroupContext) {
      this.ignoreGroupContext = ignoreGroupContext;
      this.settings[0][0] = null;  // invalidate any loaded settings
      this.settings[0][1] = null;
    }

  }

  /**
   * Gets the IgnoreContentContext setting.
   *
   * @return         the value of the setting
   */
  public boolean getIgnoreContentContext() {

    return this.ignoreContentContext;

  }

  /**
   * Sets the IgnoreContentContext setting.
   *
   * @param ignoreContentContext  true if the existence of a content context is to be ignored (default = true)
   */
  public void setIgnoreContentContext(boolean ignoreContentContext) {

    if (this.ignoreContentContext != ignoreContentContext) {
      this.ignoreContentContext = ignoreContentContext;
      this.settings[0][0] = null;  // invalidate any loaded settings
      this.settings[0][1] = null;
    }

  }

  /**
   * Gets a global anonymous configuration setting value.
   * <p>
   * The global anonymous configuration settings are loaded from a file named
   * <code><em>vvvv</em>-<em>hhhhhh</em>.properties</code> where <em>vvvv</em> and
   * <em>hhhhhh</em> are the vendor ID and handle for the current Building
   * Block, respectively.  This file is located in the Building Block
   * <code>config</code> directory.
   *
   * @param key      the name of the setting
   * @return         the value of the setting
   */
  public String getSetting(String key) {

    return getSetting(true, true, key);

  }

  /**
   * Gets a configuration setting value.
   * <p>
   * Anonymous configuration settings are loaded from a file named
   * <code><em>vvvv</em>-<em>hhhhhh</em>.properties</code> where <em>vvvv</em> and
   * <em>hhhhhh</em> are the vendor ID and handle for the current Building
   * Block, respectively.  Files containing global settings are located in the
   * Building Block <code>config</code> directory.  Non-global settings files are
   * located in the content directory (for content-item tools) or the course directory
   * (for course tools).
   * <p>
   * Non-anonymous settings are retrieved from the Blackboard user registry.
   *
   * @param global   <code>true</code> if the setting applies to all instances of the tool
   * @param anonymous     <code>true</code> if the setting applies to all users
   * @param key      the name of the setting
   * @return         the value of the setting
   */
  public String getSetting(boolean global, boolean anonymous, String key) {

    return getSetting(global, anonymous, key, "");

  }

  /**
   * Gets a global anonymous configuration setting value.
   * <p>
   * The default value is returned if no value exists for the specified key.
   * <p>
   * The global anonymous configuration settings are loaded from a file named
   * <code><em>vvvv</em>-<em>hhhhhh</em>.properties</code> where <em>vvvv</em> and
   * <em>hhhhhh</em> are the vendor ID and handle for the current Building
   * Block, respectively.  This file is located in the Building Block
   * <code>config</code> directory.
   *
   * @param key             the name of the setting
   * @param defaultValue    the default value of the setting
   * @return                the value of the setting
   */
  public String getSetting(String key, String defaultValue) {

    return getSetting(true, true, key, defaultValue);

  }

  /**
   * Gets a configuration setting value.
   * <p>
   * The default value is returned if no value exists for the specified key.
   * <p>
   * Anonymous configuration settings are loaded from a file named
   * <code><em>vvvv</em>-<em>hhhhhh</em>.properties</code> where <em>vvvv</em> and
   * <em>hhhhhh</em> are the vendor ID and handle for the current Building
   * Block, respectively.  Files containing global settings are located in the
   * Building Block <code>config</code> directory.  Non-global settings files are
   * located in the content directory (for content-item tools) or the course directory
   * (for course tools).
   * <p>
   * Non-anonymous settings are retrieved from the Blackboard user registry.
   *
   * @param global        <code>true</code> if the setting applies to all instances of the tool
   * @param anonymous     <code>true</code> if the setting applies to all users
   * @param key           the name of the setting
   * @param defaultValue  the default value of the setting
   * @return              the value of the setting
   */
  public String getSetting(boolean global, boolean anonymous, String key, String defaultValue) {

    return getSetting(global, anonymous, key, defaultValue, null);

  }

  /**
   * Gets a configuration setting value from a specific node.
   * <p>
   * The default value is returned if no value exists for the specified key.
   * <p>
   * Anonymous configuration settings are loaded from a file named
   * <code><em>vvvv</em>-<em>hhhhhh</em>.properties</code> where <em>vvvv</em> and
   * <em>hhhhhh</em> are the vendor ID and handle for the current Building
   * Block, respectively.  Files containing global settings are located in the
   * Building Block <code>config</code> directory.  Non-global settings files are
   * located in the content directory (for content-item tools) or the course directory
   * (for course tools).
   * <p>
   * Non-anonymous settings are retrieved from the Blackboard user registry.
   *
   * @param global        <code>true</code> if the setting applies to all instances of the tool
   * @param anonymous     <code>true</code> if the setting applies to all users
   * @param key           the name of the setting
   * @param defaultValue  the default value of the setting
   * @param aNode         the node to get the setting value from
   * @return              the value of the setting
   */
  public String getSetting(boolean global, boolean anonymous, String key, String defaultValue, Node aNode) {

    String value = this.loadSettings(global, anonymous, aNode, true).getProperty(key);
    if (value == null) {
      value = defaultValue;
    } else {
      value = value.trim();
    }

    return value;

  }

  /**
   * Gets a list of all global anonymous configuration settings.
   * <p>
   * The global anonymous configuration settings are loaded from a file named
   * <code><em>vvvv</em>-<em>hhhhhh</em>.properties</code> where <em>vvvv</em> and
   * <em>hhhhhh</em> are the vendor ID and handle for the current Building
   * Block, respectively.  This file is located in the Building Block
   * <code>config</code> directory.
   *
   * @return         a map containing the settings found
   */
  public Map<String, String> getSettings() {

    return getSettings(true, true, null);

  }

  /**
   * Gets a list of all configuration settings.
   * <p>
   * Anonymous configuration settings are loaded from a file named
   * <code><em>vvvv</em>-<em>hhhhhh</em>.properties</code> where <em>vvvv</em> and
   * <em>hhhhhh</em> are the vendor ID and handle for the current Building
   * Block, respectively.  Files containing global settings are located in the
   * Building Block <code>config</code> directory.  Non-global settings files are
   * located in the content directory (for content-item tools) or the course directory
   * (for course tools).
   * <p>
   * Non-anonymous settings are retrieved from the Blackboard user registry.
   *
   * @param global      <code>true</code> if the setting applies to all instances of the tool
   * @param anonymous   <code>true</code> if the setting applies to all users
   * @return            a map containing the settings found
   */
  public Map<String, String> getSettings(boolean global, boolean anonymous) {

    return getSettings(global, anonymous, null);

  }

  /**
   * Gets a list of all configuration settings.
   * <p>
   * Anonymous configuration settings are loaded from a file named
   * <code><em>vvvv</em>-<em>hhhhhh</em>.properties</code> where <em>vvvv</em> and
   * <em>hhhhhh</em> are the vendor ID and handle for the current Building
   * Block, respectively.  Files containing global settings are located in the
   * Building Block <code>config</code> directory.  Non-global settings files are
   * located in the content directory (for content-item tools) or the course directory
   * (for course tools).
   * <p>
   * Non-anonymous settings are retrieved from the Blackboard user registry.
   *
   * @param global      <code>true</code> if the setting applies to all instances of the tool
   * @param anonymous   <code>true</code> if the setting applies to all users
   * @param nodeOnly    <code>true</code> if only settings specified at the current node level should be returned
   * @return            a map containing the settings found
   */
  public Map<String, String> getSettings(boolean global, boolean anonymous, boolean nodeOnly) {

    return getSettings(global, anonymous, null, nodeOnly);

  }

  /**
   * Gets a list of all global anonymous configuration settings.
   * <p>
   * The global anonymous configuration settings are loaded from a file named
   * <code><em>vvvv</em>-<em>hhhhhh</em>-<em>x</em>.properties</code> where
   * <em>vvvv</em> and <em>hhhhhh</em> are the vendor ID and handle for the
   * current Building Block, respectively, and <em>x</em> is the specified suffix.
   * This file is located in the Building Block <code>config</code> directory.
   *
   * @param fileSuffix  suffix of the settings file name
   * @return            a map containing the settings found
   */
  public Map<String, String> getSettings(String fileSuffix) {

    return getSettings(true, true, fileSuffix);

  }

  /**
   * Gets a list of all configuration settings.
   * <p>
   * Anonymous configuration settings are loaded from a file named
   * <code><em>vvvv</em>-<em>hhhhhh</em>.properties</code> where <em>vvvv</em> and
   * <em>hhhhhh</em> are the vendor ID and handle for the current Building
   * Block, respectively.  Files containing global settings are located in the
   * Building Block <code>config</code> directory.  Non-global settings files are
   * located in the content directory (for content-item tools) or the course directory
   * (for course tools).
   * <p>
   * Non-anonymous settings are retrieved from the Blackboard user registry.
   *
   * @param global      <code>true</code> if the setting applies to all instances of the tool
   * @param anonymous   <code>true</code> if the setting applies to all users
   * @param fileSuffix  suffix of the settings file name
   * @return            a map containing the settings found
   */
  public Map<String, String> getSettings(boolean global, boolean anonymous, String fileSuffix) {

    return getSettings(global, anonymous, fileSuffix, false);

  }

  /**
   * Gets a list of all configuration settings.
   * <p>
   * Anonymous configuration settings are loaded from a file named
   * <code><em>vvvv</em>-<em>hhhhhh</em>.properties</code> where <em>vvvv</em> and
   * <em>hhhhhh</em> are the vendor ID and handle for the current Building
   * Block, respectively.  Files containing global settings are located in the
   * Building Block <code>config</code> directory.  Non-global settings files are
   * located in the content directory (for content-item tools) or the course directory
   * (for course tools).
   * <p>
   * Non-anonymous settings are retrieved from the Blackboard user registry.
   *
   * @param global      <code>true</code> if the setting applies to all instances of the tool
   * @param anonymous   <code>true</code> if the setting applies to all users
   * @param fileSuffix  suffix of the settings file name
   * @param nodeOnly    <code>true</code> if only settings specified at the current node level should be returned
   * @return            a map containing the settings found
   */
  public Map<String, String> getSettings(boolean global, boolean anonymous, String fileSuffix, boolean nodeOnly) {

    Node aNode = null;
    if (this.node == null) {
      nodeOnly = false;
    } else if (nodeOnly) {
      aNode = this.node;
    }
    Map<String, String> mapSettings = new HashMap<String, String>();
    Properties props = this.loadSettings(global, anonymous, fileSuffix, aNode, nodeOnly);
    for (Iterator<Object> iter = props.keySet().iterator(); iter.hasNext();) {
      String name = (String)iter.next();
      mapSettings.put(name, props.getProperty(name));
    }

    return mapSettings;

  }

  /**
   * Adds a global anonymous configuration setting value.
   * <p>
   * If no value exists for the setting then it is added, otherwise the
   * existing entry is updated.  <code>Null</code> values are ignored and
   * any existing entry for the setting is deleted.
   * <p>
   * Note: this only saves the setting value in memory; use the
   * <code>{@link #persistSettings()}</code> method to permanently
   * save the values.
   *
   * @param key        the name of the setting
   * @param value      the value of the setting
   */
  public void setSetting(String key, String value) {

    this.setSetting(true, true, key, value);

  }

  /**
   * Adds a configuration setting value.
   * <p>
   * If no value exists for the setting then it is added, otherwise the
   * existing entry is updated.  <code>Null</code> values are ignored and
   * any existing entry for the setting is deleted.
   * <p>
   * Note: this only saves the setting value in memory; use the
   * <code>{@link #persistSettings()}</code> method to permanently
   * save the values.
   *
   * @param global        <code>true</code> if the setting applies to all instances of the tool
   * @param anonymous     <code>true</code> if the setting applies to all users
   * @param key        the name of the setting
   * @param value      the value of the setting
   */
  public void setSetting(boolean global, boolean anonymous, String key, String value) {

    this.setSetting(global, anonymous, key, value, null);
    if (global && (this.node != null)) {
      this.setSetting(global, anonymous, key, value, this.node);
    }

  }

  private void setSetting(boolean global, boolean anonymous, String key, String value, Node aNode) {

    if ((value != null) && (this.saveEmptyValues || (value.trim().length() > 0))) {
      this.loadSettings(global, anonymous, aNode, true).setProperty(key.trim(), value.trim());
    } else {
      this.loadSettings(global, anonymous, aNode, true).remove(key.trim());
    }

  }

  /**
   * Deletes all global anonymous configuration settings.
   * <p>
   * Note: this only clears the setting values in memory; use the
   * <code>{@link #persistSettings()}</code> method to permanently
   * delete the values.
   */
  public void clearSettings() {

    this.clearSettings(true, true, null);

  }

  /**
   * Deletes all configuration settings.
   * <p>
   * Note: this only clears the setting values in memory; use the
   * <code>{@link #persistSettings()}</code> method to permanently
   * delete the values.
   *
   * @param global        <code>true</code> if the setting applies to all instances of the tool
   * @param anonymous     <code>true</code> if the setting applies to all users
   * @param suffix
   */
  public void clearSettings(boolean global, boolean anonymous, String suffix) {

    int iGlobal = (global) ? 1 : 0;
    int iAnonymous = (anonymous) ? 1 : 0;
    Properties props;
    this.loadSettings(global, anonymous, suffix);
    if (!global || !anonymous || (this.node == null)) {
      props = this.settings[iGlobal][iAnonymous];
    } else {
      props = this.nodeSettings.get(this.node.getNodeId());
      if (props != null) {
        this.settings[iGlobal][iAnonymous] = null;  // invalidate consolidated global settings
      }
    }
    if (props != null) {
      props.clear();
    }

  }

  /**
   * Permanently saves the current global anonymous configuration setting values.
   * <p>
   * The global anonymous configuration settings are loaded from a file named
   * <code><em>vvvv</em>-<em>hhhhhh</em>.properties</code> where <em>vvvv</em> and
   * <em>hhhhhh</em> are the vendor ID and handle for the current Building
   * Block, respectively.  This file is located in the Building Block
   * <code>config</code> directory.
   */
  public void persistSettings() {

    this.persistSettings(true, true);

  }

  /**
   * Permanently saves the current configuration setting values.
   * <p>
   * Anonymous configuration settings are loaded from a file named
   * <code><em>vvvv</em>-<em>hhhhhh</em>.properties</code> where <em>vvvv</em> and
   * <em>hhhhhh</em> are the vendor ID and handle for the current Building
   * Block, respectively.  Files containing global settings are located in the
   * Building Block <code>config</code> directory.  Non-global settings files are
   * located in the content directory (for content-item tools) or the course directory
   * (for course tools).
   * <p>
   * Non-anonymous settings are saved to the Blackboard user registry.
   *
   * @param global      <code>true</code> if the setting applies to all instances of the tool
   * @param anonymous   <code>true</code> if the setting applies to all users
   */
  public void persistSettings(boolean global, boolean anonymous) {

    this.persistSettings(global, anonymous, this.loadSettings(global, anonymous, this.node, true));

  }

  /**
   * Permanently saves a set of setting values.
   * <p>
   * Anonymous configuration settings are loaded from a file named
   * <code><em>vvvv</em>-<em>hhhhhh</em>.properties</code> where <em>vvvv</em> and
   * <em>hhhhhh</em> are the vendor ID and handle for the current Building
   * Block, respectively.  Files containing global settings are located in the
   * Building Block <code>config</code> directory.  Non-global settings files are
   * located in the content directory (for content-item tools) or the course directory
   * (for course tools).
   * <p>
   * Non-anonymous settings are saved to the Blackboard user registry.
   *
   * @param global      <code>true</code> if the setting applies to all instances of the tool
   * @param anonymous   <code>true</code> if the setting applies to all users
   * @param properties  a properties object containing the settings to be saved
   */
  public void persistSettings(boolean global, boolean anonymous, Properties properties) {

    this.persistSettings(global, anonymous, null, properties);

  }

  private void persistSettings(boolean global, boolean anonymous, String suffix, Properties props) {

    if (!anonymous) {
      this.saveUserSettings(global, suffix, props);
    } else if ((this.module != null) && !global) {
      this.saveCustomSettings(suffix, props);
    } else if (this.node != null) {
      this.saveFileSettings(global, suffix, props, this.node);
    } else if (!global || !getLogDebug()) {
      this.saveFileSettings(global, suffix, props);
    } else {
      props.setProperty(className + "." + LOG_LEVEL_SETTING, LOG_LEVEL_DEBUG_SETTING);
      this.saveFileSettings(global, suffix, props);
      props.remove(className + "." + LOG_LEVEL_SETTING);
    }

  }

  /**
   * Permanently saves the global anonymous configuration setting values.
   *
   * @param suffix  suffix of the settings file name
   * @param settings    Map containing the settings to save
   */
  public void persistSettings(String suffix, Map<String, String> settings) {

    this.persistSettings(true, true, suffix, settings);

  }

  /**
   * Permanently saves the current configuration setting values.
   * <p>
   * Anonymous configuration settings are loaded from a file named
   * <code><em>vvvv</em>-<em>hhhhhh</em>.properties</code> where <em>vvvv</em> and
   * <em>hhhhhh</em> are the vendor ID and handle for the current Building
   * Block, respectively.  Files containing global settings are located in the
   * Building Block <code>config</code> directory.  Non-global settings files are
   * located in the content directory (for content-item tools) or the course directory
   * (for course tools).
   * <p>
   * Non-anonymous settings are saved to the Blackboard user registry.
   *
   * @param global      <code>true</code> if the setting applies to all instances of the tool
   * @param anonymous   <code>true</code> if the setting applies to all users
   * @param suffix      suffix for the settings set
   * @param settings    Map containing the settings to save
   */
  public void persistSettings(boolean global, boolean anonymous, String suffix, Map<String, String> settings) {

    Properties props = new Properties();
    props.putAll(settings);
    this.persistSettings(global, anonymous, suffix, props);
    if (suffix == null) {
      int iGlobal = (global) ? 1 : 0;
      int iAnonymous = (anonymous) ? 1 : 0;
      this.settings[iGlobal][iAnonymous] = props;
    }

  }

  private void saveCustomSettings(String suffix, Properties props) {

    try {
      PortalExtraInfo portalExtraInfo = PortalUtil.loadPortalExtraInfo(this.module.getId(), null);
      ExtraInfo extraInfo = null;
      if (portalExtraInfo != null) {
        extraInfo = portalExtraInfo.getExtraInfo();
      }
      if (extraInfo != null) {
        String prefix = "";
        if ((suffix != null) && (suffix.length() > 0)) {
          prefix = suffix + ":";
        }
        extraInfo.clearAllStartingWith(prefix);
        for (Iterator<Object> iter = props.keySet().iterator(); iter.hasNext();) {
          String key = (String)iter.next();
          extraInfo.setValue(prefix + key, props.getProperty(key));
        }
        PortalUtil.savePortalExtraInfo(portalExtraInfo);
      }
    } catch (blackboard.persist.PersistenceException e) {
      log(true, "Error in B2Context.saveCustomSettings:", e);
    }

  }

  private void saveFileSettings(boolean global, String suffix, Properties props) {

    saveFileSettings(global, suffix, props, this.node);

  }

  private void saveFileSettings(boolean global, String suffix, Properties props, Node aNode) {

    File configFile = getConfigFile(global, suffix, aNode);
// Delete file if no settings
    if ((configFile != null) && props.isEmpty()) {
      if (!configFile.exists()) {
        configFile = null;
      } else if (configFile.delete()) {
        configFile = null;
      }
    }
    if (configFile != null) {
      StringBuilder description = new StringBuilder();
      if (global) {
        description = description.append("Global");
      } else {
        description = description.append("Context");
      }
      description = description.append(" configuration settings");
      FileOutputStream foStream = null;
      try {
        foStream = new FileOutputStream(configFile);
        props.store(foStream, description.toString());
      } catch (FileNotFoundException e) {
        log(true, "Error in B2Context.saveFileSettings:", e);
      } catch (IOException e) {
        log(true, "Error in B2Context.saveFileSettings:", e);
      } finally {
        if (foStream != null) {
          try {
            foStream.close();
          } catch (IOException e) {
            log(true, "Error in B2Context.saveFileSettings:", e);
          }
        }
      }
    }
  }

  private void saveUserSettings(boolean global, String suffix, Properties props) {

    this.getURPersister();

    if ((urPersister != null) && !this.userId.equals(Id.UNSET_ID)) {
      loadUserSettings(global, suffix);
      StringBuilder saveAsName = new StringBuilder(String.valueOf(global));
      if ((suffix != null) && (suffix.length() > 0)) {
        saveAsName = saveAsName.append("-").append(suffix);
      }
      Properties currentSettings = this.userSettings.get(saveAsName.toString());
      String prefix = getUserSettingsPrefix(global, suffix);
      for (Iterator<Object> iter = props.keySet().iterator(); iter.hasNext();) {
        String name = (String)iter.next();
        String value = props.getProperty(name);
        String currentValue = currentSettings.getProperty(prefix + name);
        if (!value.equals(currentValue)) {
          try {
            if (currentValue != null) {
              urPersister.deleteByKeyAndUserId(prefix + name, this.userId);
            }
            if (value.length() > 0) {
              UserRegistryEntry entry = new UserRegistryEntry();
              entry.setUserId(this.userId);
              entry.setKey(prefix + name);
              if (value.length() <= MAX_VALUE_LENGTH) {
                entry.setValue(value);
              } else {
                entry.setLongValue(value);
              }
              urPersister.persist(entry);
            }
          } catch (ValidationException e) {
            log(true, "Error in B2Context.saveUserSettings:", e);
          } catch (PersistenceException e) {
            log(true, "Error in B2Context.saveUserSettings:", e);
          }
        }
        currentSettings.remove(name);
      }
      for (Iterator<Object> iter = currentSettings.keySet().iterator(); iter.hasNext();) {
        String name = (String)iter.next();
        if (name.startsWith(prefix) && (props.getProperty(prefix + name) == null)) {
          try {
            urPersister.deleteByKeyAndUserId(prefix + name, this.userId);
          } catch (KeyNotFoundException e) {
            log(true, "Error in B2Context.saveUserSettings:", e);
          } catch (PersistenceException e) {
            log(true, "Error in B2Context.saveUserSettings:", e);
          }
        }
      }
    }

  }

  private File getConfigFile(boolean global, String suffix, Node aNode) {

    File configFile = null;

    if (global) {
// System configuration
      PlugInConfig config;
      try {
        config = new PlugInConfig(this.vendorId, this.handle);
        configFile = config.getConfigDirectory();
      } catch (PlugInException e) {
        configFile = null;
      }
    } else {
      if (!this.contentId.equals(Id.UNSET_ID) && !this.ignoreContentContext) {
// For a content item
        try {
          getCourse();
          CourseContentFileManager ccfm = new CourseContentFileManager();
          configFile = ccfm.getRootDirectory(this.course, this.contentId);
        } catch (FileSystemException e) {
          log(true, "Error creating properties file for course content", e);
          configFile = null;
        }
      } else if (this.hasGroupContext() && !this.ignoreGroupContext) {
// For a group item
        try {
          getCourse();
          CourseFileManager cfm = new CourseFileManager();
          configFile = cfm.getRootDirectory(this.course);
          configFile = new File(configFile, File.separator + "ppg");
        } catch (FileSystemException e) {
          log(true, "Error creating properties file for course group", e);
          configFile = null;
        }
      } else if (!this.courseId.equals(Id.UNSET_ID) && !this.ignoreCourseContext) {
// For a course tool
        try {
          getCourse();
          CourseFileManager cfm = new CourseFileManager();
          configFile = cfm.getRootDirectory(this.course);
          configFile = new File(configFile, File.separator + "ppg");
        } catch (FileSystemException e) {
          log(true, "Error creating properties file for course", e);
          configFile = null;
        }
      }
    }

    if (configFile != null) {
      if (!configFile.exists()) {
        if (!configFile.mkdirs()) {
          log(true, "Unable to create directory for settings properties file");
          configFile = null;
        }
      }
    }

    if (configFile != null) {
      StringBuilder filename = new StringBuilder();
      filename = filename.append(this.vendorId).append("-").append(this.handle);
      if ((suffix != null) && (suffix.length() > 0)) {
        filename = filename.append("-").append(suffix);
      }
      if (global && (aNode != null)) {
        filename.append(aNode.getNodeId().toExternalString());
      } else if (!global && this.hasGroupContext() && !this.ignoreGroupContext) {
        filename.append(this.groupId.toExternalString());
      }
      filename = filename.append(SETTINGS_FILE_EXTENSION);
      configFile = new File(configFile, filename.toString());
      if (!configFile.exists() && !global && this.hasGroupContext() && !this.ignoreGroupContext) {
        configFile = checkOldGroupLocation(configFile, suffix);
      }
    }

    return configFile;

  }

  private File checkOldGroupLocation(File configFile, String suffix) {

    try {
      getCourse();
      CourseContentFileManager ccfm = new CourseContentFileManager();
      File oldFile = ccfm.getRootDirectory(this.course, this.groupId);
      StringBuilder filename = new StringBuilder();
      filename = filename.append(this.vendorId).append("-").append(this.handle);
      if ((suffix != null) && (suffix.length() > 0)) {
        filename = filename.append("-").append(suffix);
      }
      filename = filename.append(SETTINGS_FILE_EXTENSION);
      oldFile = new File(oldFile, filename.toString());
      if (oldFile.exists() && oldFile.renameTo(configFile)) {
        configFile = oldFile;
        log(false, "Group file moved to " + configFile.getPath());
      }
    } catch (FileSystemException e) {
    }

    return configFile;
  }

  private String getUserSettingsPrefix(boolean global, String suffix) {

    StringBuilder name = new StringBuilder();
    name = name.append(this.vendorId).append("-").append(this.handle);
    if (!global) {
      if (this.hasContentContext() && !this.ignoreContentContext) {
        name = name.append("-").append(this.courseId.toExternalString());
        name = name.append("-").append(this.contentId.toExternalString());
      } else if (this.hasGroupContext() && !this.ignoreGroupContext) {
        name = name.append("-").append(this.courseId.toExternalString());
        name = name.append("-G").append(this.groupId.toExternalString());
      } else if (this.hasCourseContext() && !this.ignoreCourseContext) {
        name = name.append("-").append(this.courseId.toExternalString());
      }
    }
    if ((suffix != null) && (suffix.length() > 0)) {
      name = name.append("-").append(suffix);
    }
    name = name.append(":");

    return name.toString();

  }

  /**
   * Gets a named navigation item.
   *
   * @param name  name of navigation item
   * @return      navigation item
   */
  public NavigationItem getNavigationItem(String name) {

    NavigationItem navItem;
    try {
      NavigationItemDbLoader niLoader = NavigationItemDbLoader.Default.getInstance();
      navItem = niLoader.loadByInternalHandle(name);
    } catch (PersistenceException e) {
      log(true, "Error in B2Context.getNavigationItem:", e);
      navItem = null;
    }

    return navItem;

  }

  /**
   * Sets a success receipt option.
   *
   * @param message     message to be displayed
   */
  public void setReceipt(String message) {

    this.setReceipt(message, null);

  }

  /**
   * Sets a success or error receipt option.
   *
   * @param message     message to be displayed
   * @param isSuccess   <code>true</code> if this is a success receipt
   */
  public void setReceipt(String message, boolean isSuccess) {

    Exception e = null;
    if (!isSuccess) {
      e = new Exception();
    }

    this.setReceipt(message, e);

  }

  /**
   * Sets a warning receipt option.
   *
   * @param message     message to be displayed
   * @param exception   error exception or null for a success receipt
   */
  public void setReceipt(String message, Exception exception) {

    if ((this.request != null) && (message != null) && (message.length() > 0)) {
      ReceiptOptions ro = new ReceiptOptions();
      if (exception == null) {
        ro.addSuccessMessage(message);
      } else {
        ro.addErrorMessage(message, exception);
      }
      InlineReceiptUtil.addReceiptToRequest(this.request, ro);
    }

  }

  /**
   * Set receipts in session.
   *
   * @param success     success message to be displayed
   * @param error       error message to be displayed
   */
  public void setReceiptOptions(String success, String error) {

    if (this.request != null) {
      ReceiptOptions ro = new ReceiptOptions();
      if ((success != null) && (success.length() > 0)) {
        ro.addSuccessMessage(success);
      }
      if ((error != null) && (error.length() > 0)) {
        ro.addErrorMessage(error, null);
      }
      InlineReceiptUtil.addReceiptToRequest(this.request, ro);
    }

  }

  /**
   * Add receipts in current request.
   *
   * @param success     success message to be displayed
   * @param warning     warning message to be displayed
   * @param error       error message to be displayed
   */
  public void addReceiptOptionsToRequest(String success, String warning, String error) {

    if (this.request != null) {
      ReceiptOptions ro = new ReceiptOptions();
      if ((success != null) && (success.length() > 0)) {
        ro.addSuccessMessage(success);
      }
      if ((warning != null) && (warning.length() > 0)) {
        if (!getIsVersion(9, 1, 8)) {
          ro.addErrorMessage(warning, null);
        } else {
          ro.addWarningMessage(warning);
        }
      }
      if ((error != null) && (error.length() > 0)) {
        ro.addErrorMessage(error, null);
      }
      InlineReceiptUtil.addReceiptToRequest(this.request, ro);
    }

  }

  /**
   * Set receipts to a URL.
   *
   * @param url         URL
   * @param success     success message to be displayed
   * @param error       error message to be displayed
   * @return            URL with receipt parameters
   */
  public String setReceiptOptions(String url, String success, String error) {

    if (!getIsVersion(9, 1, 0)) {
      try {
        String sep = "?";
        int pos = url.indexOf("?");
        if (pos >= 0) {
          if (pos < url.length() - 1) {
            sep = "&";
          } else {
            sep = "";
          }
        }
        if ((success != null) && (success.length() > 0)) {
          url += sep + InlineReceiptUtil.SIMPLE_STRING_KEY + "=" + URLEncoder.encode(success, "UTF-8");
          sep = "&";
        }
        if ((error != null) && (error.length() > 0)) {
          url += sep + InlineReceiptUtil.SIMPLE_ERROR_KEY + "=" + URLEncoder.encode(error, "UTF-8");
        }
      } catch (UnsupportedEncodingException e) {
      }
    } else {
      if ((success != null) && (success.length() > 0)) {
        url = InlineReceiptUtil.addSuccessReceiptToUrl(url, success);
      }
      if ((error != null) && (error.length() > 0)) {
        url = InlineReceiptUtil.addErrorReceiptToUrl(url, error);
      }
    }

    return url;

  }

  /**
   * Gets the current status of edit mode (Learn 9.1+).
   *
   * @return            <code>false</code> if edit mode is off or Learn 9.0, otherwise <code>true</code>
   */
  public static boolean getEditMode() {

    boolean editMode = false;
    if (getIsVersion(9, 1, 0)) {
      editMode = EditModeUtil.getEditMode();
    }

    return editMode;

  }

  /**
   * Processes changes arising from a course copy.
   *
   * This method should be called from within the blackboard.platform.cx.component.CxComponent.doCopy method.
   *
   * @param  copyControl   the CopyControl object for the course copy in progress
   */
  public static void processCourseCopy(CopyControl copyControl) {

    B2Context b2Context = new B2Context();
    String filename = b2Context.vendorId + "-" + b2Context.handle;
    try {
      CourseDbLoader courseLoader = CourseDbLoader.Default.getInstance();
      Course destinationCourse = courseLoader.loadById(copyControl.getDestinationCourseId());
      CourseFileManager cfm = new CourseFileManager();
      File configFile = cfm.getRootDirectory(destinationCourse);
      configFile = new File(configFile, File.separator + "ppg");
      BbPersistenceManager bbPm = PersistenceServiceFactory.getInstance().getDbPersistenceManager();
      GroupDbLoader groupDbLoader = (GroupDbLoader)bbPm.getLoader(GroupDbLoader.TYPE);
      List<Group> groups = groupDbLoader.loadByCourseId(copyControl.getSourceCourseId());
      Group group;
      Id destId;
      File sourceGroupFile;
      File destinationGroupFile;
      int n = 0;
      for (Iterator<Group> iter = groups.iterator(); iter.hasNext();) {
        group = iter.next();
        destId = copyControl.lookupIdMapping(group.getId());
        sourceGroupFile = new File(configFile, filename + group.getId().toExternalString() + SETTINGS_FILE_EXTENSION);
        if (sourceGroupFile.exists()) {
          destinationGroupFile = new File(configFile, filename + destId.toExternalString() + SETTINGS_FILE_EXTENSION);
          if (sourceGroupFile.renameTo(destinationGroupFile)) {
            n++;
          } else {
            copyControl.getLogger().logError("Unable to rename properties file for group " + group.getId().toExternalString());
          }
        }
      }
      if (n > 0) {
        copyControl.getLogger().logInfo(String.format("%d group settings file(s) renamed", n));
      }
    } catch (FileSystemException e) {
      copyControl.getLogger().logError(e.getMessage());
    } catch (PersistenceException e) {
      copyControl.getLogger().logError(e.getMessage());
    }

  }

  /**
   * Processes changes arising from a course export/archive.
   *
   * This method should be called from within the blackboard.platform.cx.component.CxComponent.doExport method.
   *
   * @param  exportControl   the ExportControl object for the course export/archive in progress
   * @return                 a Map of any settings to be added
   */
  public static Map<String, String> processCourseExport(ExportControl exportControl) {

    Map<String, String> settings = new HashMap<String, String>();
    try {
      BbPersistenceManager bbPm = PersistenceServiceFactory.getInstance().getDbPersistenceManager();
      GroupDbLoader groupDbLoader = (GroupDbLoader)bbPm.getLoader(GroupDbLoader.TYPE);
      List<Group> groups = groupDbLoader.loadByCourseId(exportControl.getSourceCourseId());
      Group group;
      StringBuilder sb = new StringBuilder();
      for (Iterator<Group> iter = groups.iterator(); iter.hasNext();) {
        group = iter.next();
        sb.append(",").append(group.getId().toExternalString());
      }
      settings.put(className + "." + GROUPS_SETTING, sb.substring(1));
    } catch (PersistenceException e) {
      exportControl.getLogger().logError(e.getMessage());
    }
    return settings;

  }

  /**
   * Processes changes arising from a course export/archive.
   *
   * This method should be called from within the blackboard.platform.cx.component.CxComponent.doExport method.
   *
   * @param  importControl   the ImportControl object for the course import in progress
   * @param  props           the properties saved during the export of the file being imported
   */
  public static void processCourseImport(ImportControl importControl, Properties props) {

    B2Context b2Context = new B2Context();
    String filename = b2Context.vendorId + "-" + b2Context.handle;
    try {
      CourseDbLoader courseLoader = CourseDbLoader.Default.getInstance();
      Course destinationCourse = courseLoader.loadById(importControl.getDestinationCourseId());
      CourseFileManager cfm = new CourseFileManager();
      File configFile = cfm.getRootDirectory(destinationCourse);
      configFile = new File(configFile, File.separator + "ppg");
      String[] groups = props.getProperty(className + "." + GROUPS_SETTING, "").split(",");
      String groupId;
      Id destId;
      File sourceGroupFile;
      File destinationGroupFile;
      int n = 0;
      for (int i = 0; i < groups.length; i++) {
        groupId = groups[i];
        destId = importControl.lookupIdMapping(importControl.generateId(Group.DATA_TYPE, groupId));
        sourceGroupFile = new File(configFile, filename + groupId + SETTINGS_FILE_EXTENSION);
        if (sourceGroupFile.exists()) {
          destinationGroupFile = new File(configFile, filename + destId.toExternalString() + SETTINGS_FILE_EXTENSION);
          if (sourceGroupFile.renameTo(destinationGroupFile)) {
            n++;
          } else {
            importControl.getLogger().logError("Unable to rename properties file for group " + groupId);
          }
        }
      }
      if (n > 0) {
        importControl.getLogger().logInfo(String.format("%d group settings file(s) renamed", n));
      }
    } catch (FileSystemException e) {
      importControl.getLogger().logError(e.getMessage());
    } catch (PersistenceException e) {
      importControl.getLogger().logError(e.getMessage());
    }

  }

  /**
   * Gets the Blackboard version number.
   *
   * @param  defaultValue  the default value to return when unable to access version number
   * @return               version number as a string (e.g. 9.1.90132)
   */
  public static String getVersionNumber(String defaultValue) {

    return ConfigurationServiceFactory.getInstance().getBbProperty(BbConfig.VERSION_NUMBER, defaultValue);

  }

  /**
   * Gets the Blackboard version number as an array of its elements.
   * <p>
   * The third element is split into two to separate the service pack from the build number.
   *
   * @return               version number separated into its 4 integer elements (e.g. 9, 1, 9, 132)
   */
  public static int[] getVersionNumber() {

    int[] iVersion = new int[4];

    String version = getVersionNumber("0.0.00000");
    String[] sVersion = version.split("\\.");
    if (sVersion.length < 3) {
      sVersion = "0.0.00000".split("\\.");
    }

    iVersion[0] = stringToInt(sVersion[0], 0);
    iVersion[1] = stringToInt(sVersion[1], 0);
    iVersion[2] = stringToInt(sVersion[2], 0);
    iVersion[3] = stringToInt(sVersion[3], 0);

    if ((sVersion[2].length() == 6) && (sVersion[2].startsWith("20"))) {
      iVersion[2] = stringToInt(sVersion[2], 0);
      iVersion[3] = stringToInt(sVersion[3], 0);
    } else if (sVersion[2].length() > 4) {
      iVersion[2] = stringToInt(sVersion[2].substring(0, sVersion[2].length() - 4), 0);
      iVersion[3] = stringToInt(sVersion[2].substring(sVersion[2].length() - 4), 0);
    } else if ((iVersion[0] == 9) && (iVersion[1] == 0)) {
      for (int i = 0; i < V90_RELEASE.length; i++) {
        if (iVersion[2] <= V90_RELEASE[i]) {
          iVersion[2] = i;
          break;
        }
      }
    } else if ((iVersion[0] == 9) && (iVersion[1] == 1)) {
      for (int i = 0; i < V91_RELEASE.length; i++) {
        if (iVersion[2] <= V91_RELEASE[i]) {
          iVersion[2] = i;
          break;
        }
      }
    }

    return iVersion;

  }

  /**
   * Checks whether the Blackboard is at a specific release, or later.
   * <p>
   * For example, to check if the current version of Blackboard is 9.1 SP10, or later
   * use parameters of 9, 1 and 10.  For the Q4 2017 CU1 release (or later) use
   * parameters of 3300, 0 and 1.
   *
   * @param  major        the major release number
   * @param  minor        the minor release number
   * @param  servicePack  the service pack number
   * @return              <code>true</code> if the Blackboard release has the specified version number or greater
   */
  public static boolean getIsVersion(int major, int minor, int servicePack) {

    boolean ok = false;

    int[] version = getVersionNumber();
    if (version[0] > major) {
      ok = true;
    } else if (version[0] == major) {
      if (version[1] > minor) {
        ok = true;
      } else if (version[1] == minor) {
        ok = version[2] >= servicePack;
      }
    }

    return ok;

  }

  /**
   * Gets the debug level for the logger.
   *
   * @return boolean  <code>true</code> if information messages are to be logged as well as errors
   */
  public static boolean getLogDebug() {

    return logDebug;

  }

  /**
   * Sets the debug level for the logger.
   *
   * @param  logDebugSetting  <code>true</code> if information messages are to be logged as well as errors
   */
  public static void setLogDebug(boolean logDebugSetting) {

    logDebug = logDebugSetting;

  }

  /**
   * Sends an error message to the <code>logs/bb-services-log.txt</code> file.
   *
   * @param  message  error to be logged
   */
  public static void log(String message) {

    log(true, message);

  }

  /**
   * Sends an object to the <code>logs/bb-services-log.txt</code> file.
   *
   * @param  isError  <code>true</code> if the message being logged is an error; <code>false</code> if it is just for information
   * @param  prefix  prefix to log before object
   * @param  obj     object to be logged
   */
  public static void log(boolean isError, String prefix, Object obj) {

    if (prefix == null) {
      prefix = "";
    } else if (prefix.length() > 0) {
      prefix = prefix + "\n";
    }
    log(isError, prefix + obj);

  }

  /**
   * Sends a log message to the <code>logs/bb-services-log.txt</code> file.
   *
   * @param  isError  <code>true</code> if the message being logged is an error; <code>false</code> if it is just for information
   * @param  message  string to be logged
   */
  public static void log(boolean isError, String message) {

    if ((isError || logDebug)) {
      if (isError) {
        message = "[ERROR]: " + message;
      } else {
        message = "[INFO]:  " + message;
      }
      if (getLog()) {
        log.logError(message);
      } else {
        System.err.println(logPrefix + message);
      }
    }

  }

// ---------------------------------------------------
// Function to initialises the logger and sets the default logging level to errors only.
  private static boolean getLog() {

    if (log == null) {
      LogService logService = LogServiceFactory.getInstance();
      B2Context b2Context = new B2Context();
      logPrefix = "[" + b2Context.getVendorId() + "-" + b2Context.getHandle() + "] - ";
      File logDirectory = PlugInUtil.getLogDirectory(b2Context.getVendorId(), b2Context.getHandle());
      try {
        logService.defineNewFileLog(B2Context.class.getName(), logDirectory.getPath() + File.separator + b2Context.getVendorId() + "-" + b2Context.getHandle() + ".log",
            LogService.Verbosity.INFORMATION, false);
        log = logService.getConfiguredLog(B2Context.class.getName());
      } catch (BbServiceException ex) {
        Logger.getLogger(B2Context.class.getName()).log(Level.SEVERE, null, ex);
      }
    }

    return (log != null);

  }

// ---------------------------------------------------
// Function to convert a String value to an int value
  private static int stringToInt(String value, int defaultValue) {

    int iValue;
    try {
      iValue = Integer.parseInt(value);
    } catch (NumberFormatException e) {
      iValue = defaultValue;
    }

    return iValue;

  }

}
