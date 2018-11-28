package ee.veikokaap.idea.plugins.eclipsefromintellijplugin.runconfig

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import org.jetbrains.annotations.Nls
import javax.swing.Icon

class EclipseConfigurationFactory(type: EclipsePluginConfigurationType) : ConfigurationFactory(type) {
  override fun createTemplateConfiguration(project: Project): RunConfiguration {
    return EclipsePluginRunConfiguration(project, this, "Eclipse plugin")
  }
  
  override fun getName(): String {
    return "Eclipse plugin configuration factory"
  }
}

class EclipsePluginConfigurationType : ConfigurationType {
  @Nls
  override fun getDisplayName(): String {
    return "Eclipse plugin"
  }
  
  @Nls
  override fun getConfigurationTypeDescription(): String {
    return "Run Eclipse plugins"
  }
  
  override fun getIcon(): Icon {
    return IconLoader.getIcon("/icons/eclipse.png")
  }
  
  override fun getId(): String {
    return "ECLIPSE_PLUGIN_RUN_CONFIGURATION"
  }
  
  override fun getConfigurationFactories(): Array<ConfigurationFactory> {
    return arrayOf(EclipseConfigurationFactory(this))
  }
}
