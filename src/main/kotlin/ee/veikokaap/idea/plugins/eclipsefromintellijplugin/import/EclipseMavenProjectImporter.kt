package ee.veikokaap.idea.plugins.eclipsefromintellijplugin.import

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.compiler.impl.javaCompiler.eclipse.EclipseCompilerConfiguration
import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.externalSystem.service.project.AbstractIdeModifiableModelsProvider
import org.jetbrains.idea.maven.importing.MavenImporter
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectChanges
import org.jetbrains.idea.maven.project.MavenProjectsProcessorTask
import org.jetbrains.idea.maven.project.MavenProjectsTree
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.ModifiableWorkspace
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.*
import com.intellij.openapi.util.registry.Registry
import ee.veikokaap.idea.plugins.eclipsefromintellijplugin.TychoPackagingType
import ee.veikokaap.idea.plugins.eclipsefromintellijplugin.packagingType
import org.jetbrains.idea.maven.importing.MavenModuleImporter
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.jps.model.java.compiler.JavaCompilers

class EclipseMavenProjectImporter : MavenImporter("ee.veikokaap", "eclipse-from-intellij-plugin") {
  
  override fun isApplicable(mavenProject: MavenProject): Boolean {
    return mavenProject.packagingType is TychoPackagingType
  }
  
  override fun preProcess(module: Module, mavenProject: MavenProject, changes: MavenProjectChanges, modifiableModelsProvider: IdeModifiableModelsProvider) {
  }
  
  override fun process(modifiableModelsProvider: IdeModifiableModelsProvider, module: Module, rootModelAdapter: MavenRootModelAdapter, mavenModel: MavenProjectsTree, mavenProject: MavenProject, changes: MavenProjectChanges, mavenProjectToModuleName: Map<MavenProject, String>, postTasks: List<MavenProjectsProcessorTask>) {
    val substituteApiRegistryValue = Registry.get("external.system.substitute.library.dependencies")
    val previousRegistryValue = substituteApiRegistryValue.asBoolean()
    
    try {
      substituteApiRegistryValue.setValue(true)
      val originalIdFinder = MavenIdFinder(module, mavenProject)
      
      mavenProject.dependencies.forEach { dependency: MavenArtifact ->
        if (trySubstituteUsingBuiltInApi(dependency, originalIdFinder, rootModelAdapter, modifiableModelsProvider, module)) {
          return@forEach
        }
        
        val modifiableWorkspace = (modifiableModelsProvider as? AbstractIdeModifiableModelsProvider)?.modifiableWorkspace
        if (modifiableWorkspace != null && trySubstitutionUsingWorkspace(dependency, originalIdFinder, rootModelAdapter, mavenProjectToModuleName, modifiableWorkspace, module)) {
          return@forEach
        }
        
        tryManualSubstitution(dependency, originalIdFinder, rootModelAdapter, mavenProjectToModuleName, module)
      }
      
      rearrangeDependencies(rootModelAdapter)
    }
    catch (e: Exception) {
      e.printStackTrace()
    }
    finally {
      substituteApiRegistryValue.setValue(previousRegistryValue)
    }
  }
  
  override fun postProcess(module: Module?, mavenProject: MavenProject?, changes: MavenProjectChanges?, modifiableModelsProvider: IdeModifiableModelsProvider?) {
    val switchedToEclipseCompiler = switchModuleToEclipseCompiler(module)
    if (switchedToEclipseCompiler) {
      println("Switched $module to eclipse compiler")
    }
    else {
      println("Failed to switch $module to eclipse compiler")
    }
  }
  
  fun switchModuleToEclipseCompiler(module: Module?): Boolean {
    val project = module?.project ?: return false
    
    val compilerConfiguration = CompilerConfiguration.getInstance(project) as? CompilerConfigurationImpl ?: return false
    val eclipseCompiler = compilerConfiguration.registeredJavaCompilers.firstOrNull { it.id == JavaCompilers.ECLIPSE_ID } ?: return false
    val compilerOptions = EclipseCompilerConfiguration.getOptions(project, EclipseCompilerConfiguration::class.java) ?: return false
    
    compilerConfiguration.defaultCompiler = eclipseCompiler
    compilerOptions.PROCEED_ON_ERROR = false
    
    return true
  }
  
  private fun trySubstituteUsingBuiltInApi(dependency: MavenArtifact, mavenIdFinder: MavenIdFinder, rootModelAdapter: MavenRootModelAdapter, modifiableModelsProvider: IdeModifiableModelsProvider, ownerModule: Module): Boolean {
    val mavenId = mavenIdFinder.findArtifactMavenId(dependency) ?: return false
    val libraryEntry = findLibraryOrderEntry(rootModelAdapter, dependency) ?: return false
    val moduleEntry = modifiableModelsProvider.trySubstitute(ownerModule, libraryEntry, ProjectId(mavenId.groupId, mavenId.artifactId, mavenId.version)) ?: return false
    println("Added ${moduleEntry.moduleName} with ${moduleEntry.scope} scope as dependency for $ownerModule. Used trySubstituteUsingBuiltInApi")
    return true
  }
  
  private fun trySubstitutionUsingWorkspace(dependency: MavenArtifact, originalIdFinder: MavenIdFinder, rootModelAdapter: MavenRootModelAdapter, mavenProjectToModuleName: Map<MavenProject, String>, modifiableWorkspace: ModifiableWorkspace, ownerModule: Module): Boolean {
    val moduleName = findModuleName(originalIdFinder, dependency, mavenProjectToModuleName) ?: return false
    val substituteModule = rootModelAdapter.findModuleByName(moduleName) ?: return false
    val libraryEntry = findLibraryOrderEntry(rootModelAdapter, dependency) ?: return false
    
    val moduleOrderEntry = rootModelAdapter.rootModel.addModuleOrderEntry(substituteModule)
    moduleOrderEntry.scope = libraryEntry.getScope()
    moduleOrderEntry.isExported = libraryEntry.isExported()
    
    modifiableWorkspace.addSubstitution(ownerModule.name, substituteModule.name, libraryEntry.libraryName, libraryEntry.scope)
    rootModelAdapter.rootModel.removeOrderEntry(libraryEntry)
    
    println("Added $moduleName with ${libraryEntry.scope} scope as dependency for $substituteModule. Used trySubstitutionUsingWorkspace")
    
    return false
  }
  
  private fun tryManualSubstitution(dependency: MavenArtifact, originalIdFinder: MavenIdFinder, rootModelAdapter: MavenRootModelAdapter, mavenProjectToModuleName: Map<MavenProject, String>, ownerModule: Module): Boolean {
    val moduleName = findModuleName(originalIdFinder, dependency, mavenProjectToModuleName) ?: return false
    val libraryEntry = findLibraryOrderEntry(rootModelAdapter, dependency)
    val scope = libraryEntry?.scope ?: MavenModuleImporter.selectScope(dependency.scope)
    
    if (!moduleAlreadyExists(rootModelAdapter, moduleName)) {
      rootModelAdapter.addModuleDependency(moduleName, scope, false)
      println("Added $moduleName with ${scope} scope as dependency for $ownerModule. Used tryManualSubstitution")
      if (libraryEntry != null) {
        rootModelAdapter.rootModel.removeOrderEntry(libraryEntry)
      }
      return true
    }
    
    return false
  }
  
  private fun rearrangeDependencies(rootModelAdapter: MavenRootModelAdapter) {
    rootModelAdapter.rootModel.rearrangeOrderEntries(rootModelAdapter.rootModel.orderEntries.sortedBy {
      when (it) {
        is JdkOrderEntry -> 0
        is ModuleSourceOrderEntry -> 1
        is ModuleOrderEntry -> 2
        else -> 3
      }
    }.toTypedArray())
  }
  
  private fun findLibraryOrderEntry(rootModelAdapter: MavenRootModelAdapter, dependency: MavenArtifact): LibraryOrderEntry? {
    val library = rootModelAdapter.findLibrary(dependency) ?: return null
    return rootModelAdapter.rootModel.findLibraryOrderEntry(library)
  }
  
  private fun findModuleName(originalIdFinder: MavenIdFinder, dependency: MavenArtifact, mavenProjectToModuleName: Map<MavenProject, String>): String? {
    val mavenId = originalIdFinder.findArtifactMavenId(dependency) ?: return null
    return mavenProjectToModuleName.entries.asSequence()
        .filter { (project, _) -> project.mavenId.equals(mavenId.groupId, mavenId.artifactId) }
        .map { it.value }
        .firstOrNull()
  }
  
  private fun moduleAlreadyExists(rootModelAdapter: MavenRootModelAdapter, moduleName: String): Boolean {
    val module = rootModelAdapter.findModuleByName(moduleName)
    if (module != null && rootModelAdapter.rootModel.findModuleOrderEntry(module) != null) {
      return true
    }
    return false
  }
}
