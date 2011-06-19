package fr.sivrit.svn.helper.scala

import fr.sivrit.svn.helper.{ Preferences, ISvnHelper }
import fr.sivrit.svn.helper.scala.core.ProjectDeps
import fr.sivrit.svn.helper.scala.svn.{ SvnSwitch, SvnCheckOut, SvnCrawler }
import org.eclipse.core.resources.{ ResourcesPlugin, IProject }
import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.team.core.RepositoryProvider
import org.eclipse.ui.{ IWorkingSet, IWorkingSetManager }
import org.eclipse.ui.internal.WorkbenchPlugin
import org.tigris.subversion.subclipse.core.{ SVNProviderPlugin, ISVNRepositoryLocation }
import org.tigris.subversion.svnclientadapter.SVNUrl
import scala.util.matching.Regex

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

  def resolveDependencies(svnUrl: Array[SVNUrl]): Boolean = {
    false
  }

  def createWorkingSets(urls: Array[SVNUrl]): Boolean = {
    val newWS: Map[String, List[IProject]] = SvnHelperScala.resolveWorkingSetFromSvn(urls)
    val workingSets: Map[String, IWorkingSet] = SvnHelperScala.createWorkingSets(newWS.keys);

    val wsManager: IWorkingSetManager = WorkbenchPlugin.getDefault().getWorkingSetManager();
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
        wsManager.addWorkingSet(ws)
        workingSets += ((wsName, ws))
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
}