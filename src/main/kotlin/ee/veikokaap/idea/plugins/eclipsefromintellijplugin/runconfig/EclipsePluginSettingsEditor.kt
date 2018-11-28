package ee.veikokaap.idea.plugins.eclipsefromintellijplugin.runconfig

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.PanelWithAnchor
import com.intellij.util.io.exists
import com.intellij.util.io.isDirectory
import com.intellij.util.io.isFile
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.ui.UIUtil
import ee.veikokaap.idea.plugins.base.runconfig.BasicJavaApplicationSettingsPanel
import ee.veikokaap.idea.plugins.base.ColumnedTable
import ee.veikokaap.idea.plugins.base.EditableTableColumn
import ee.veikokaap.idea.plugins.base.TableColumn
import ee.veikokaap.idea.plugins.base.util.fillHorizontally
import ee.veikokaap.idea.plugins.base.util.fillVertically
import ee.veikokaap.idea.plugins.base.util.gridBagConstraints
import ee.veikokaap.idea.plugins.base.util.nextRow
import java.awt.BorderLayout
import java.awt.GridBagLayout
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.streams.asSequence

class EclipsePluginSettingsEditor(project: Project) : SettingsEditor<EclipsePluginRunConfiguration>(), PanelWithAnchor {
  
  companion object {
    private data class EclipsePluginState(val pluginName: String, var selected: Boolean)
    
    private val COLUMNS: List<TableColumn<EclipsePluginState, out Any?>> = listOf(
        TableColumn("Module name", String::class.javaObjectType,
            rowToCellDataMapper = { state -> state.pluginName }
        ),
        EditableTableColumn("Enabled", Boolean::class.javaObjectType,
            rowToCellDataMapper = { state -> state.selected },
            valueChangeListener = { state, newSelectedValue -> state.selected = newSelectedValue }
        )
    )
  }
  
  private val eclipseInstallDirComponent = LabeledComponent.create(TextFieldWithBrowseButton(), "Eclipse installation directory", BorderLayout.WEST)
  private val workspaceDirComponent = LabeledComponent.create(TextFieldWithBrowseButton(), "Workspace directory", BorderLayout.WEST)
  private val pluginsTable = LabeledComponent.create(ColumnedTable(COLUMNS), "Enabled plugins", BorderLayout.WEST)
  private val javaAppSettingsPanel = BasicJavaApplicationSettingsPanel(project)
  private var anchor: JComponent? = UIUtil.mergeComponentsWithAnchor(eclipseInstallDirComponent, workspaceDirComponent, pluginsTable, javaAppSettingsPanel)
  
  override fun createEditor(): JComponent {
    val panel = JPanel()
    panel.layout = GridBagLayout()
  
    eclipseInstallDirComponent.component.addBrowseFolderListener(
        "Select Eclipse Installation Directory", "Select eclipse installation directory", null, FileChooserDescriptorFactory.createSingleFolderDescriptor()
    )
  
    workspaceDirComponent.component.addBrowseFolderListener(
        "Select Eclipse Workspace Directory", "Select eclipse Workspace directory", null, FileChooserDescriptorFactory.createSingleFolderDescriptor()
    )
  
    val constraints = gridBagConstraints(gridx = 0, gridy = 0).fillHorizontally(weightx = 1.0)
    panel.add(eclipseInstallDirComponent, constraints)
    panel.add(workspaceDirComponent, constraints.nextRow())
    panel.add(pluginsTable, constraints.nextRow())
    panel.add(javaAppSettingsPanel, constraints.nextRow().fillVertically(weighty = 1.0))
    
    return panel
  }
  
  override fun resetEditorFrom(configuration: EclipsePluginRunConfiguration) {
    javaAppSettingsPanel.reset(configuration)
    eclipseInstallDirComponent.component.text = configuration.eclipseHomeDirPath
    workspaceDirComponent.component.text = configuration.workspaceDirPath
  
    pluginsTable.component.data = configuration.deployedPluginProjects
        .map { (name, selected) -> EclipsePluginState(name, selected) }
  }
  
  override fun applyEditorTo(configuration: EclipsePluginRunConfiguration) {
    javaAppSettingsPanel.applyTo(configuration)
    val eclipseDirPath = getPathIfExistsAndDirectory(eclipseInstallDirComponent.component.text, "Eclipse directory")
    val pluginsDirPath = getPathIfExistsAndDirectory("${eclipseDirPath.toAbsolutePath().systemIndependentPath}/plugins", "eclipse/plugins directory")
    try {
      val equinoxLauncherPath = Files.list(pluginsDirPath).asSequence()
          .firstOrNull { it.isFile() and it.fileName.toString().startsWith("org.eclipse.equinox.launcher_") }
          ?: throw ConfigurationException("No equinox launcher jar found from ${pluginsDirPath.toAbsolutePath()}")
      
      configuration.eclipseHomeDirPath = eclipseDirPath.toAbsolutePath().systemIndependentPath
      configuration.equinoxLauncherPath = equinoxLauncherPath.toAbsolutePath().systemIndependentPath
    }
    catch (e: IOException) {
      throw ConfigurationException(e.message)
    }
    
    val workspaceDirPath = getPathIfExistsAndDirectory(workspaceDirComponent.component.text, "Workspace directory")
    configuration.workspaceDirPath = workspaceDirPath.toAbsolutePath().systemIndependentPath
    
    configuration.deployedPluginProjects = pluginsTable.component.data
        .associateBy(EclipsePluginState::pluginName, EclipsePluginState::selected)
        .toMutableMap()
  }
  
  override fun getAnchor(): JComponent? {
    return anchor
  }
  
  override fun setAnchor(anchor: JComponent?) {
    this.anchor = anchor
    eclipseInstallDirComponent.anchor = anchor
    workspaceDirComponent.anchor = anchor
    pluginsTable.anchor = anchor
    javaAppSettingsPanel.anchor = anchor
  }
  
  @Throws(ConfigurationException::class)
  private fun getPathIfExistsAndDirectory(pathText: String, directoryNameForErrors: String): Path {
    val trimmedPathText = pathText.trim()
    if (trimmedPathText.isEmpty()) {
      throw ConfigurationException("$directoryNameForErrors path is empty!")
    }
    try {
      val eclipseDirPath = Paths.get(trimmedPathText)
      if (!eclipseDirPath.exists()) {
        throw ConfigurationException("$directoryNameForErrors does not exist!")
      }
      if (!eclipseDirPath.isDirectory()) {
        throw ConfigurationException("$directoryNameForErrors is not a directory!")
      }
      return eclipseDirPath
    }
    catch (e: InvalidPathException) {
      throw ConfigurationException(e.message)
    }
  }
}


