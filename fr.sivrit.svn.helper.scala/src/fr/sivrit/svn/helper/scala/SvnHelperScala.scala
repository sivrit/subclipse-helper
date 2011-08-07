package fr.sivrit.svn.helper.scala

import scala.util.matching.Regex

import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerPart
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.team.core.RepositoryProvider
import org.eclipse.ui.internal.WorkbenchPlugin
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkingSet
import org.eclipse.ui.IWorkingSetManager
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.WorkbenchException
import org.tigris.subversion.subclipse.core.ISVNRepositoryLocation
import org.tigris.subversion.subclipse.core.SVNProviderPlugin
import org.tigris.subversion.svnclientadapter.SVNUrl

import fr.sivrit.svn.helper.scala.core.ProjectDeps
import fr.sivrit.svn.helper.scala.svn.SvnCheckOut
import fr.sivrit.svn.helper.scala.svn.SvnCrawler
import fr.sivrit.svn.helper.scala.svn.SvnSwitch
import fr.sivrit.svn.helper.ISvnHelper
import fr.sivrit.svn.helper.Preferences

class SvnHelperScala extends ISvnHelper {

  def checkoutBranch(repo: ISVNRepositoryLocation, svnUrls: Array[SVNUrl]): Boolean = {

    val exclusions: Array[Regex] = Preferences.getExclusions.map { pattern => pattern.r }

    val svnProjects = SvnCrawler.findProjects(svnUrls, exclusions.toList)

    val (toSwitch, toCo) = SvnHelperScala.sortOut(svnProjects)

    val title: String = if (svnUrls.length == 1) ("checkoutBranch: " + svnUrls(0)) else "checkoutBranches"
    var msg: String = "Projects found: " + svnProjects.size + "\nWill switch: " + toSwitch.size + "\nWill checkout: " + toCo.size
    if ((svnUrls.length > 1)) msg = "checkoutBranches: " + svnUrls.toList + "\n\n" + msg

    if (!MessageDialog.openQuestion(null, title, msg))
      return false

    // Checkout
    SvnCheckOut.checkout(repo, toCo.toList)

    // Switch
    SvnSwitch.switch(toSwitch)

    true
  }

  def resolveDependencies(svnUrls: Array[SVNUrl]): Boolean = {
    false
  }

  def createWorkingSets(urls: Array[SVNUrl]): Boolean = {
    // Find the projects to dispatch
    val newWS: Map[String, List[IProject]] = SvnHelperScala.resolveWorkingSetFromSvn(urls)

    // Confirm the operation
    val msg: StringBuilder = new StringBuilder("The following WorkingSets will be created:\n")
    for (entry <- newWS) {
      msg.append(entry._1).append(" - ").append(entry._2.length).append(" projects\n")
    }

    if (!MessageDialog.openQuestion(null, "Define Working Sets", msg.toString())) {
      return false;
    }

    // Show the Java perspective, the package explorer and witch to a view
    // by working sets.
    val workbench: IWorkbench = PlatformUI.getWorkbench()
    try {
      workbench.showPerspective(JavaUI.ID_PERSPECTIVE, workbench.getActiveWorkbenchWindow());
    } catch {
      case e: WorkbenchException =>
        e.printStackTrace();
        return false;
    }

    val explo: PackageExplorerPart = PackageExplorerPart.openInActivePerspective()
    if (explo.getRootMode() == PackageExplorerPart.PROJECTS_AS_ROOTS) {
      explo.rootModeChanged(PackageExplorerPart.WORKING_SETS_AS_ROOTS)
    }

    // Create missing IWorkingSet and index all WS by name
    val workingSets: Map[String, IWorkingSet] = SvnHelperScala.createWorkingSets(newWS.keys)

    // Remove the projects we will dispatch from existing WS
    var allProjects: Set[IProject] = Set()
    for (val projects <- newWS.values) {
      allProjects ++= projects;
    }
    SvnHelperScala.removeProjectsFromWorkingSets(workingSets.values, allProjects);

    val wsManager: IWorkingSetManager = WorkbenchPlugin.getDefault().getWorkingSetManager()
    for ((wsName, projects) <- newWS) {
      val workingSet = Array(workingSets.getOrElse(wsName, null))

      projects.foreach(proj => WorkbenchPlugin.log("Will add " + proj.getName + " to " + wsName))
      projects.foreach(wsManager.addToWorkingSets(_, workingSet))
    }

    true
  }

}

object SvnHelperScala {
  val PLUGIN_ID: String = "fr.sivrit.svn.helper.scala"

  private def findWorkspaceProject(name: String): IProject =
    ResourcesPlugin.getWorkspace().getRoot().getProject(name)

  private def findTeamWorkspaceProjects(): Set[ProjectDeps] = {
    ProjectDeps.findWorkspaceProjects().filter(deps =>
      if (deps.name == null) false
      else {
        val iproject = findWorkspaceProject(deps.name)
        RepositoryProvider.getProvider(iproject, SVNProviderPlugin.getTypeId()) != null
      })
  }

  private def sortOut(remotes: Set[(ProjectDeps, SVNUrl)]): (Set[(IProject, SVNUrl)], Set[SVNUrl]) = {
    var toSwitch = Set[(IProject, SVNUrl)]()
    var toCo = Set[SVNUrl]()

    val workspace: Set[ProjectDeps] = findTeamWorkspaceProjects()

    remotes.foreach(item => item match {
      case (project, url) =>
        val existing = workspace.find {
          case deps => ProjectDeps.doMatch(project, deps)
        } match {
          case Some(deps) =>
            val duo = (findWorkspaceProject(deps.name), url)
            toSwitch = toSwitch + duo
          case None => toCo = toCo + url
        }
    })

    (toSwitch, toCo)
  }

  private def createWorkingSets(wsNames: Iterable[String]): Map[String, IWorkingSet] = {
    val wsManager: IWorkingSetManager = WorkbenchPlugin.getDefault().getWorkingSetManager();

    var workingSets: Map[String, IWorkingSet] =
      Map(wsManager.getWorkingSets.toList map { ws => (ws.getName, ws) }: _*)

    for (wsName <- wsNames) {
      if (!workingSets.contains(wsName)) {
        val ws = wsManager.createWorkingSet(wsName, Array.empty)
        ws.setId("org.eclipse.jdt.ui.JavaWorkingSetPage")
        wsManager.addWorkingSet(ws)
        workingSets += wsName -> ws
      }
    }

    workingSets
  }

  private def resolveWorkingSetFromSvn(urls: Array[SVNUrl]): Map[String, List[IProject]] = {
    val useExclusions = Preferences.getApplyOnWokingSet
    val exclusions: List[Regex] =
      if (useExclusions)
        Preferences.getExclusions.map({ pattern => pattern.r }).toList
      else List.empty;

    val workspace: Set[ProjectDeps] = findTeamWorkspaceProjects()

    var newWS: Map[String, List[IProject]] = Map.empty

    for (url <- urls) {
      val wsName = url.getLastPathSegment
      var wsProjects: List[IProject] = Nil

      val svnProjects = SvnCrawler.findProjects(Array(url), exclusions)

      svnProjects.foreach {
        case (svnProject, _) =>
          workspace.find {
            case locProject => ProjectDeps.doMatch(svnProject, locProject)
          } match {
            case Some(locProject) =>
              wsProjects = findWorkspaceProject(locProject.name) :: wsProjects
            case None =>
          }
      }

      if (!svnProjects.isEmpty)
        newWS = newWS.updated(wsName, wsProjects)
    }

    newWS
  }

  private def removeProjectsFromWorkingSets(sets: Iterable[IWorkingSet],
    projectsToRemove: Set[IProject]) = {
    for (ws <- sets) {
      removeProjectsFromWorkingSet(ws, projectsToRemove);
    }
  }

  private def removeProjectsFromWorkingSet(ws: IWorkingSet,
    projectsToRemove: Set[IProject]) = {
    val content = ws.getElements();

    var lastElement = content.length - 1;
    var idx = 0;
    while (idx <= lastElement) {
      val item: IAdaptable = content(idx)
      item.getAdapter(classOf[IProject]) match {
        case project: IProject if projectsToRemove.contains(project) => {
          content(idx) = content(lastElement);
          content(lastElement) = null;
          lastElement -= 1;
        }
        case _ => idx += 1;
      }
    }

    val newContent = new Array[IAdaptable](lastElement + 1);
    System.arraycopy(content, 0, newContent, 0, lastElement + 1);

    ws.setElements(newContent);
  }
}