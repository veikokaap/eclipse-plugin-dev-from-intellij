package ee.veikokaap.idea.plugins.base.runconfig

import com.intellij.diagnostic.logging.LogConfigurationPanel
import com.intellij.execution.CommonJavaRunConfigurationParameters
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.JavaRunConfigurationExtensionManager
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.configurations.*
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.options.SettingsEditorGroup
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import java.util.*
import kotlin.collections.LinkedHashMap

abstract class BaseJavaApplicationRunConfiguration<RUN_CONFIGURATION: BaseJavaApplicationRunConfiguration<RUN_CONFIGURATION>>(
    project: Project, factory: ConfigurationFactory, name: String
) : LocatableConfigurationBase(project, factory, name),
    CommonJavaRunConfigurationParameters,
    SearchScopeProvidingRunProfile,
    RunProfileWithCompileBeforeLaunchOption {
  
  private var environmentalVariables: MutableMap<String, String> = HashMap()
  private var configurationBean = BaseJavaApplicationRunConfigurationBean()
  internal var configurationModule: JavaRunConfigurationModule = JavaRunConfigurationModule(project, true)
  
  abstract fun getMainSettingsEditor(): SettingsEditor<RUN_CONFIGURATION>
  
  override fun getConfigurationEditor(): SettingsEditor<RUN_CONFIGURATION> {
    val group = SettingsEditorGroup<RUN_CONFIGURATION>()
    group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"), getMainSettingsEditor())
    JavaRunConfigurationExtensionManager.getInstance().appendEditors(this, group)
    group.addEditor(ExecutionBundle.message("logs.tab.title"), LogConfigurationPanel())
    return group
  }
  
  override fun readExternal(element: Element) {
    super.readExternal(element)
    JavaRunConfigurationExtensionManager.getInstance().readExternal(this, element)
    XmlSerializer.deserializeInto(configurationBean, element)
    EnvironmentVariablesComponent.readExternal(element, environmentalVariables)
    configurationModule.readExternal(element)
  }
  
  override fun writeExternal(element: Element) {
    super.writeExternal(element)
    JavaRunConfigurationExtensionManager.getInstance().writeExternal(this, element)
    XmlSerializer.serializeInto(configurationBean, element)
    EnvironmentVariablesComponent.writeExternal(element, environmentalVariables)
    if (configurationModule.module != null) {
      configurationModule.writeExternal(element)
    }
  }
  
  override fun clone(): RUN_CONFIGURATION {
    val clone = super.clone() as RUN_CONFIGURATION
    clone.envs = LinkedHashMap(envs)
    clone.configurationModule = JavaRunConfigurationModule(project, true)
    clone.configurationModule.module = configurationModule.module
    clone.configurationBean = configurationBean.copy()
    return clone
  }
  
  override fun getEnvs(): MutableMap<String, String> {
    return environmentalVariables
  }
  
  override fun setEnvs(envs: MutableMap<String, String>) {
    environmentalVariables = envs
  }
  
  override fun getVMParameters(): String {
    return configurationBean.vmParams
  }
  
  override fun setVMParameters(value: String?) {
    if (value != null) {
      configurationBean.vmParams = value
    }
  }
  
  override fun getProgramParameters(): String {
    return configurationBean.programParams
  }
  
  override fun setProgramParameters(value: String?) {
    if (value != null) {
      configurationBean.programParams = value
    }
  }
  
  override fun isPassParentEnvs(): Boolean {
    return configurationBean.passParentEnvs
  }
  
  override fun setPassParentEnvs(passParentEnvs: Boolean) {
    configurationBean.passParentEnvs = passParentEnvs
  }
  
  override fun getWorkingDirectory(): String {
    return configurationBean.workingDirectory
  }
  
  override fun setWorkingDirectory(value: String?) {
    if (value != null) {
      configurationBean.workingDirectory = value
    }
  }
  
  override fun isAlternativeJrePathEnabled(): Boolean {
    return configurationBean.alternativeJrePathEnabled
  }
  
  override fun setAlternativeJrePathEnabled(enabled: Boolean) {
    configurationBean.alternativeJrePathEnabled = enabled
  }
  
  override fun getAlternativeJrePath(): String {
    return configurationBean.alternativeJrePath
  }
  
  override fun setAlternativeJrePath(path: String?) {
    if (path != null) {
      configurationBean.alternativeJrePath = path
    }
  }
  
  override fun getSearchScope(): GlobalSearchScope {
    return configurationModule.searchScope
  }
  
  override fun getPackage(): String? {
    return null
  }
  
  override fun getRunClass(): String? {
    return null
  }
}

data class BaseJavaApplicationRunConfigurationBean(
    var vmParams: String = "",
    var programParams: String = "",
    var passParentEnvs: Boolean = true,
    var alternativeJrePath: String = "",
    var alternativeJrePathEnabled: Boolean = false,
    var workingDirectory: String = ""
)
