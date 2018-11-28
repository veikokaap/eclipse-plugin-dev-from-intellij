package ee.veikokaap.eclipsefromintellijplugin.runconfig

import com.intellij.execution.ExecutionException
import com.intellij.execution.application.BaseJavaApplicationCommandLineState
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.util.JavaParametersUtil
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.exists
import com.intellij.util.io.systemIndependentPath
import ee.veikokaap.eclipsefromintellijplugin.EclipsePluginPackagingType
import ee.veikokaap.eclipsefromintellijplugin.packagingType
import org.apache.commons.io.FileUtils
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.asSequence
import kotlin.streams.toList

class EclipsePluginCommandLineState(eclipsePluginRunConfiguration: EclipsePluginRunConfiguration, executionEnvironment: ExecutionEnvironment)
  : BaseJavaApplicationCommandLineState<EclipsePluginRunConfiguration>(executionEnvironment, eclipsePluginRunConfiguration) {
  
  override fun startProcess(): OSProcessHandler {
    try {
      val eclipseConfPath = findEclipseConfigurationPath()
      val pluginConfPath = findPluginConfigurationPath()
      FileUtils.deleteDirectory(Paths.get(pluginConfPath).toFile())
      FileUtils.copyDirectory(Paths.get(eclipseConfPath).toFile(), Paths.get(pluginConfPath).toFile())
      addSelectedEclipsePluginsToBundlesConfig(pluginConfPath)
    }
    catch (e: Exception) {
      throw ExecutionException("Failed to create plugin workspace due to exception: ${e.message}", e)
    }
    
    return super.startProcess()
  }
  
  override fun createJavaParameters(): JavaParameters {
    val javaParams = JavaParameters()
    val jrePath = if (configuration.isAlternativeJrePathEnabled) configuration.alternativeJrePath else null
    javaParams.jdk = JavaParametersUtil.createProjectJdk(configuration.project, jrePath)
    this.setupJavaParameters(javaParams)
    javaParams.jarPath = FileUtil.toSystemDependentName(configuration.equinoxLauncherPath)
    
    var vmParams = eclipseIniVmParameters()
    if (!atLeastJava9(javaParams)) {
      vmParams = vmParams
          .filterNot { it.contains("--add-modules") }
          .toTypedArray()
    }
    
    javaParams.vmParametersList.prependAll(*vmParams)
    javaParams.programParametersList.prependAll(*eclipseProgramParameters())
    
    return javaParams
  }
  
  private fun atLeastJava9(javaParameters: JavaParameters): Boolean {
    val jdk = javaParameters.jdk ?: return false
    val versionString = JavaSdk.getInstance().getVersionString(jdk) ?: return false
    val jdkVersion = JavaSdkVersion.fromVersionString(versionString) ?: return false
    
    return jdkVersion.isAtLeast(JavaSdkVersion.JDK_1_9)
  }
  
  private fun eclipseIniVmParameters(): Array<String> {
    try {
      val eclipseIniPath = Paths.get(this.configuration.eclipseHomeDirPath, "eclipse.ini")
      if (eclipseIniPath.exists()) {
        val lines = Files.lines(eclipseIniPath).toList()
        val vmArgsStart = lines.lastIndexOf("-vmargs")
        if (vmArgsStart != -1) {
          return lines.subList(vmArgsStart + 1, lines.size)
              .map(String::trim)
              .filter(String::isNotEmpty)
              .toTypedArray()
        }
      }
    }
    catch (e: Exception) {
      e.printStackTrace()
    }
    
    return defaultVmParameters()
  }
  
  private fun defaultVmParameters(): Array<String> {
    return """
              -Dosgi.requiredJavaVersion=1.8
              -Dosgi.instance.area.default=@user.home/eclipse-workspace
              -XX:+UseG1GC
              -XX:+UseStringDeduplication
              -Dosgi.dataAreaRequiresExplicitInit=true
              -Xms256m
              -Xmx1024m
              --add-modules=ALL-SYSTEM
              """
        .trimIndent()
        .lines()
        .flatMap { it.split("\\s+") }
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toTypedArray()
  }
  
  private fun eclipseProgramParameters(): Array<String> {
    try {
      val eclipseIniPath = Paths.get(this.configuration.eclipseHomeDirPath, "eclipse.ini")
      if (eclipseIniPath.exists()) {
        val lines = Files.lines(eclipseIniPath).toList()
        val vmArgsStart = lines.indexOf("-vmargs")
        if (vmArgsStart != -1) {
          val programParams = lines.subList(0, vmArgsStart)
              .map(String::trim)
              .filter(String::isNotEmpty)
              .toMutableList()
          
          programParams.addAll(arrayOf(
              "-data", configuration.workspaceDirPath,
              "-configuration", findPluginConfigurationPath(),
              "-dev", "target/classes,bin" // TODO: Don't hardcode these, but take from project files/properties
          ))
          
          if (!programParams.contains("-consoleLog")) {
            programParams.add("-consoleLog")
          }
          
          return programParams.toTypedArray()
        }
      }
    }
    catch (e: Exception) {
      e.printStackTrace()
    }
    
    return defaultEclipseProgramParameters()
  }
  
  private fun defaultEclipseProgramParameters(): Array<String> {
    val params = """
                -product org.eclipse.platform.ide
                -data ${configuration.workspaceDirPath}
                -configuration ${findPluginConfigurationPath()}
                -dev target/classes,bin
                """.trimIndent()
        .lines()
        .flatMap { it.split("\\s+".toRegex()) }
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toMutableList()
    
    findLauncherLibraryPath()?.let { libraryPath ->
      params.add("--launcher.library")
      params.add(libraryPath)
    }
  
    params.add("-consoleLog")
  
    return params.toTypedArray()
  }
  
  private fun addSelectedEclipsePluginsToBundlesConfig(pluginConfPath: String) {
    FileUtil.appendToFile(File(pluginConfPath, "org.eclipse.equinox.simpleconfigurator/bundles.info"), getPluginModulesConfigEntries())
  }
  
  private fun getPluginModulesConfigEntries(): String {
    return MavenProjectsManager.getInstance(configuration.project).projects
        .filter { it.packagingType == EclipsePluginPackagingType }
        .filter { configuration.deployedPluginProjects.getOrDefault(it.displayName, true) }
        .mapNotNull(::createProjectBundleEntry)
        .joinToString(separator = "\n")
  }
  
  @Throws(IOException::class)
  private fun createProjectBundleEntry(pr: MavenProject): String {
    return pr.mavenId.artifactId + "," + getBundleVersion(pr) + ",file:" + pr.directory + ",4,false"
  }
  
  @Throws(IOException::class)
  private fun getBundleVersion(mavenProject: MavenProject): String {
    return Files.walk(Paths.get(mavenProject.directory))
        .filter { path -> path.endsWith("META-INF/MANIFEST.MF") }
        .flatMap(Files::lines)
        .filter { line -> line.contains("Bundle-Version") }
        .map { line -> line.substring(15).trim() }
        .findFirst()
        .orElseThrow { IOException("Failed to find bundle-version") }
  }
  
  private fun findLauncherLibraryPath(): String? {
    return Files.lines(Paths.get(configuration.eclipseHomeDirPath, "eclipse.ini")).asSequence()
        .filter { line -> line.contains("org.eclipse.equinox.launcher.") }
        .firstOrNull()
  }
  
  private fun findPluginConfigurationPath(): String {
    return Paths.get(configuration.workspaceDirPath, "plugin-configuration").systemIndependentPath
  }
  
  private fun findEclipseConfigurationPath(): String {
    return Paths.get(configuration.eclipseHomeDirPath, "configuration").systemIndependentPath
  }
}