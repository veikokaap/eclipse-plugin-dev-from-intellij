package ee.veikokaap.idea.plugins.eclipsefromintellijplugin.import

import com.intellij.openapi.module.Module
import com.intellij.util.io.delete
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.model.MavenModel
import org.jetbrains.idea.maven.project.MavenEmbeddersManager
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

class MavenIdFinder(module: Module, mavenProject: MavenProject) {
  
  private val mavenServerEmbedder = MavenProjectsManager.getInstance(module.project).embeddersManager.getEmbedder(mavenProject, MavenEmbeddersManager.FOR_MODEL_READ)
  private val mavenIdFinders: MutableList<(MavenArtifact) -> MavenId?> = arrayListOf(::findFromPomXml, ::findMavenIdFromJarDirPom, ::findMavenIdFromInsideJar)
  
  fun findArtifactMavenId(artifact: MavenArtifact): MavenId? =
      mavenIdFinders.asSequence()
          .mapNotNull { it(artifact) }
          .firstOrNull()
  
  private fun findFromPomXml(mavenArtifact: MavenArtifact): MavenId? {
    if (mavenArtifact.file.name == "pom.xml") {
      try {
        val mavenModel = mavenServerEmbedder.readModel(mavenArtifact.file)
        return readMavenIdFromModel(mavenModel)
      }
      catch (e: Exception) {
        e.printStackTrace()
      }
    }
    
    return null
  }
  
  private fun findMavenIdFromInsideJar(artifact: MavenArtifact): MavenId? {
    if (artifact.file.extension != "jar") return null
    
    var tmpFile: Path? = null
    try {
      ZipFile(artifact.file).use { zipFile ->
        val pomEntry = zipFile.entries().asSequence()
            .filter { entry -> entry.name.endsWith("/" + artifact.file.nameWithoutExtension + "/pom.xml") }
            .firstOrNull()
        
        if (pomEntry != null) {
          zipFile.getInputStream(pomEntry).use { zipEntryInputStream ->
            tmpFile = Files.createTempFile("pom", ".xml")
            Files.copy(zipEntryInputStream, tmpFile, StandardCopyOption.REPLACE_EXISTING)
          }
        }
      }
      
      if (tmpFile != null) {
        val mavenModel = mavenServerEmbedder.readModel(tmpFile!!.toFile())
        return readMavenIdFromModel(mavenModel)
      }
      
      return null
    }
    catch (e: Exception) {
      e.printStackTrace()
      return null
    }
    finally {
      try {
        tmpFile?.delete()
      }
      catch (e: Exception) {
        println("Failed to delete file $tmpFile")
        e.printStackTrace()
      }
    }
  }
  
  private fun findMavenIdFromJarDirPom(artifact: MavenArtifact): MavenId? {
    if (artifact.file.extension != "jar") return null
    
    try {
      val pomFile: File? = artifact.file.parentFile
          ?.listFiles { file -> file.absolutePath == "${artifact.file.absolutePath.substringBeforeLast(".")}.pom" }
          ?.firstOrNull()
      
      if (pomFile != null) {
        val mavenModel = mavenServerEmbedder.readModel(pomFile)
        return readMavenIdFromModel(mavenModel)
      }
      return null
    }
    catch (e: Exception) {
      e.printStackTrace()
      return null
    }
  }
  
  private fun readMavenIdFromModel(mavenModel: MavenModel): MavenId {
    var groupId = mavenModel.mavenId.groupId
    val artifactId = mavenModel.mavenId.artifactId
    var version = mavenModel.mavenId.version
    
    if (mavenModel.parent != null) {
      if (groupId == null) {
        groupId = mavenModel.parent.mavenId.groupId
      }
      if (version == null) {
        version = mavenModel.parent.mavenId.version
      }
    }
    
    return MavenId(groupId, artifactId, version)
  }
}