package ee.veikokaap.idea.plugins.base.runconfig

import com.intellij.application.options.ModulesComboBox
import com.intellij.execution.ui.CommonJavaParametersPanel
import com.intellij.execution.ui.DefaultJreSelector
import com.intellij.execution.ui.JrePathEditor
import javax.swing.JComponent
import javax.swing.JPanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.ui.PanelWithAnchor
import com.intellij.util.ui.UIUtil
import ee.veikokaap.idea.plugins.base.util.*
import java.awt.BorderLayout
import java.awt.GridBagLayout

class BasicJavaApplicationSettingsPanel(project: Project) : JPanel(), PanelWithAnchor {
  
  private val commonProgramParametersPanel: CommonJavaParametersPanel = CommonJavaParametersPanel()
  private val jrePathEditor: JrePathEditor = JrePathEditor()
  private val moduleComponent: LabeledComponent<ModulesComboBox> = LabeledComponent.create(ModulesComboBox(), "Search sources using module's classpath")
  private var anchor: JComponent? = UIUtil.mergeComponentsWithAnchor(commonProgramParametersPanel, jrePathEditor)
  
  init {
    initPanel()
    
    val modulesComboBox = moduleComponent.component
    modulesComboBox.allowEmptySelection("<whole project>")
    modulesComboBox.fillModules(project)
    jrePathEditor.setDefaultJreSelector(DefaultJreSelector.fromModuleDependencies(modulesComboBox, true))
    moduleComponent.labelLocation = BorderLayout.WEST
  }
  
  private fun initPanel() {
    layout = GridBagLayout()
    
    val constraints = gridBagConstraints(gridx = 0, gridy = 0).fillHorizontally(weightx = 1.0)
    add(commonProgramParametersPanel, constraints.fillVertically(weighty = 1.0))
    add(jrePathEditor, constraints.nextRow().staticVertically())
    add(moduleComponent, constraints.nextRow())
  }
  
  fun reset(configuration: BaseJavaApplicationRunConfiguration<*>) {
    commonProgramParametersPanel.reset(configuration)
    jrePathEditor.setPathOrName(configuration.getAlternativeJrePath(), configuration.isAlternativeJrePathEnabled())
    moduleComponent.component.selectedModule = configuration.configurationModule.module
  }
  
  fun applyTo(configuration: BaseJavaApplicationRunConfiguration<*>) {
    commonProgramParametersPanel.applyTo(configuration)
    configuration.setAlternativeJrePath(jrePathEditor.jrePathOrName)
    configuration.setAlternativeJrePathEnabled(jrePathEditor.isAlternativeJreSelected)
    configuration.configurationModule.module = moduleComponent.component.selectedModule
  }
  
  override fun getAnchor(): JComponent? {
    return anchor
  }
  
  override fun setAnchor(anchor: JComponent?) {
    this.anchor = anchor
    commonProgramParametersPanel.anchor = anchor
    jrePathEditor.anchor = anchor
  }
}
