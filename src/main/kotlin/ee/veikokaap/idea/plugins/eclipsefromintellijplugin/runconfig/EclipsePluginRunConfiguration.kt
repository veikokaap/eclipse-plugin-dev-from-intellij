package ee.veikokaap.idea.plugins.eclipsefromintellijplugin.runconfig

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.util.getAttributeBooleanValue
import com.intellij.util.xmlb.XmlSerializer
import ee.veikokaap.idea.plugins.base.runconfig.BaseJavaApplicationRunConfiguration
import ee.veikokaap.idea.plugins.eclipsefromintellijplugin.EclipsePluginPackagingType
import ee.veikokaap.idea.plugins.eclipsefromintellijplugin.packagingType
import org.jdom.Element
import org.jetbrains.idea.maven.project.MavenProjectsManager

class EclipsePluginRunConfiguration(project: Project, factory: EclipseConfigurationFactory, name: String)
  : BaseJavaApplicationRunConfiguration<EclipsePluginRunConfiguration>(project, factory, name) {
  
  companion object {
    private val SELECTED_PLUGINS_MAP = "SELECTED_PLUGINS_MAP"
    private val SELECTED_PLUGIN = "PLUGIN"
    private val PLUGIN_ENABLED = "ENABLED"
    private val PLUGIN_NAME = "NAME"
  }
  
  private var eclipsePlugins: MutableMap<String, Boolean> = hashMapOf()
  private var eclipseConfigurationBean = EclipsePluginRunConfigurationBean()
  
  var eclipseHomeDirPath: String
    get() = eclipseConfigurationBean.eclipseHomeDirPath
    set(path) {
      eclipseConfigurationBean.eclipseHomeDirPath = path
    }
  
  var workspaceDirPath: String
    get() = eclipseConfigurationBean.workspaceDirPath
    set(path) {
      eclipseConfigurationBean.workspaceDirPath = path
    }
  
  var equinoxLauncherPath: String
    get() = eclipseConfigurationBean.equinoxLauncherPath
    set(path) {
      eclipseConfigurationBean.equinoxLauncherPath = path
    }
  
  var deployedPluginProjects: MutableMap<String, Boolean>
    get() {
      val newMap = MavenProjectsManager.getInstance(project).projects
          .filter { it.packagingType === EclipsePluginPackagingType }
          .map { it -> it.displayName to eclipsePlugins.getOrDefault(it.displayName, true) }
          .toMap()
      
      eclipsePlugins.clear()
      eclipsePlugins.putAll(newMap)
      
      return eclipsePlugins
    }
    set(pluginMap) {
      eclipsePlugins.clear()
      eclipsePlugins.putAll(pluginMap)
    }
  
  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
    if (eclipseHomeDirPath == "" || workspaceDirPath == "") {
      return null
    }
    
    return EclipsePluginCommandLineState(this, environment)
  }
  
  override fun getMainSettingsEditor(): SettingsEditor<EclipsePluginRunConfiguration> {
    return EclipsePluginSettingsEditor(project)
  }
  
  override fun readExternal(element: Element) {
    super.readExternal(element)
    XmlSerializer.deserializeInto(eclipseConfigurationBean, element)
    readEclipsePluginsMap(element)
  }
  
  override fun writeExternal(element: Element) {
    super.writeExternal(element)
    XmlSerializer.serializeInto(eclipseConfigurationBean, element)
    writeSelectedPluginsMap(element)
  }
  
  private fun readEclipsePluginsMap(element: Element) {
    val pluginsMap = element.getChild(SELECTED_PLUGINS_MAP)
    if (pluginsMap != null) {
      for (plugin in pluginsMap.getChildren(SELECTED_PLUGIN)) {
        val pluginName = plugin.getAttributeValue(PLUGIN_NAME)
        val pluginEnabled = plugin.getAttributeBooleanValue(PLUGIN_ENABLED)
        if (pluginName != null) {
          eclipsePlugins[pluginName] = pluginEnabled
        }
      }
    }
  }
  
  private fun writeSelectedPluginsMap(element: Element) {
    if (eclipsePlugins.isEmpty()) {
      return
    }
    
    val pluginsElement = Element(SELECTED_PLUGINS_MAP)
    for (pluginName in eclipsePlugins.keys) {
      val pluginElement = Element(SELECTED_PLUGIN)
      pluginElement.setAttribute(PLUGIN_NAME, pluginName)
      pluginElement.setAttribute(PLUGIN_ENABLED, eclipsePlugins[pluginName].toString())
      pluginsElement.addContent(pluginElement)
    }
    element.addContent(pluginsElement)
  }
  
  override fun clone(): EclipsePluginRunConfiguration {
    val clone = super.clone()
    clone.eclipseConfigurationBean = eclipseConfigurationBean.copy()
    clone.eclipsePlugins = HashMap(eclipsePlugins)
    return clone
  }
}

data class EclipsePluginRunConfigurationBean(
    var eclipseHomeDirPath: String = "",
    var workspaceDirPath: String = "",
    var equinoxLauncherPath: String = ""
)
