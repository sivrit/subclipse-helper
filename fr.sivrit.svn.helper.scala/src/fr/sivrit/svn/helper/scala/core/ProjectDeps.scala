package fr.sivrit.svn.helper.scala.core

import java.io.File
import org.eclipse.core.resources.{ IProject, ResourcesPlugin }
import scala.io.Source
import scala.util.matching.Regex
import org.tigris.subversion.svnclientadapter.SVNUrl
import scala.xml.Elem
import org.eclipse.core.runtime.SubMonitor

case class ProjectDeps(name: String, plugin: String, projectDeps: Set[String], pluginDeps: Set[String], natures: Set[String] = Set())

object ProjectDeps {
  def doMatch(a: ProjectDeps, b: ProjectDeps): Boolean = {
    if (a.name != null && b.name != null && a.name.equals(b.name))
      true
    else a.plugin != null && b.plugin != null && a.plugin.equals(b.plugin)
  }

  def findWorkspaceProjects(subMonitor: SubMonitor): Set[ProjectDeps] = {
    val projects: Array[IProject] = ResourcesPlugin.getWorkspace().getRoot().getProjects()

    subMonitor.setWorkRemaining(projects.length);
    val projectDeps: Array[ProjectDeps] = projects.map { subMonitor.worked(1); fromProject(_) }

    projectDeps.toSet
  }

  private def fromProject(project: IProject): ProjectDeps = {
    val name = project.getName();

    val (pluginName, pluginDeps) = findPluginInfo(project)

    new ProjectDeps(name, pluginName, Set.empty, pluginDeps);
  }

  private def findPluginInfo(project: IProject): (String, Set[String]) = {
    val manifest: File = project.getFile("META-INF/MANIFEST.MF").getLocation.toFile;

    if (manifest.exists) (findPluginName(Source.fromFile(manifest)), findPluginDeps(Source.fromFile(manifest)))
    else (null, null)
  }

  private val SymbolicName = new Regex("""Bundle-SymbolicName:\s*([^;]+)(?:.*)""")

  def findPluginName(manifest: Source): String = {
    // ex:
    //  Bundle-SymbolicName:SvnHelperImplid;singleton:=true

    manifest.getLines().find {
      case SymbolicName(name) => true
      case _                  => false
    } match {
      case Some(SymbolicName(name)) => name
      case None                     => null;
    }
  }

  private val RequireBundle = new Regex("""Require-Bundle:\s*([^;]+)(?:.*)""")
  private val RequireBundleItem = new Regex("""\s*([^;]+)(?:.*)""")

  def findPluginDeps(manifest: Source): Set[String] = {
    // ex:
    // Require-Bundle: org.eclipse.core.resources;bundle-version="3.5.2",
    //  org.eclipse.core.runtime;bundle-version="3.5.0"
    var inRequire = false;
    var result = Set[String]();
    for (line <- manifest.getLines()) {
      if (inRequire) {
        val RequireBundleItem(bundle) = line;
        result += bundle.replaceAll(",", "");
        if (!line.endsWith(",")) return result;
      } else {
        val bundle = line match {
          case RequireBundle(name) => name
          case _                   => null;
        }
        if (bundle != null) {
          inRequire = true;
          result += bundle.replaceAll(",", "");
          if (!line.endsWith(",")) return result;
        }
      }
    }

    return result;
  }

  /**
   * Extracts the name of an eclipse project from its <code>.project</code> file.
   *
   * @param definition the content of an eclipse <code>.project</code> file as a <code>scala.xml.Elem</code>
   * @return the name of the project
   */
  def findProjectName(definition: Elem): String = {
    (definition \ "name").text
  }

  def findProjectNatures(definition: Elem): Set[String] = {
    val natureSeq = definition \\ "nature" map (_.text)
    natureSeq.toSet
  }
}