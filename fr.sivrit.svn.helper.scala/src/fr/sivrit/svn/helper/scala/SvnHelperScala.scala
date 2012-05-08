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
import org.tigris.subversion.svnclientadapter.ISVNStatus
import fr.sivrit.svn.helper.scala.svn.SvnClient
import fr.sivrit.svn.helper.scala.svn.SvnAdapter
import org.eclipse.jface.dialogs.ProgressMonitorDialog
import org.eclipse.jface.operation.IRunnableWithProgress
import java.lang.reflect.InvocationTargetException
import fr.sivrit.svn.helper.Logger
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.SubMonitor
import org.tigris.subversion.subclipse.core.SVNException
import org.tigris.subversion.svnclientadapter.SVNClientException

class SvnHelperScala extends ISvnHelper {

  def checkoutBranch(repo: ISVNRepositoryLocation, svnUrls: Array[SVNUrl]): Boolean = {

    var svnProjects: Set[(ProjectDeps, SVNUrl)] = null
    var toSwitch: Set[(IProject, SVNUrl)] = null
    var toCo: Set[SVNUrl] = null

    val progress = new ProgressMonitorDialog(null);
    try {
      progress.run(true, true, new IRunnableWithProgress() {
        def run(monitor: IProgressMonitor) = {
          val subMonitor = SubMonitor.convert(monitor);

          subMonitor.setWorkRemaining(10);
          subMonitor.setTaskName("Looking for projects...");

          val crawler = new SvnCrawler(subMonitor.newChild(9,
            SubMonitor.SUPPRESS_BEGINTASK), true);
          try {
            svnProjects = crawler.findProjects(svnUrls);

            subMonitor.setTaskName("Comparing projects to workspace...");

            val switchAndCo = SvnHelperScala.sortOut(svnProjects, subMonitor.newChild(1, SubMonitor.SUPPRESS_BEGINTASK))
            toSwitch = switchAndCo._1
            toCo = switchAndCo._2
          } catch {
            case e: SVNException =>
              throw new InvocationTargetException(e)
            case e: SVNClientException =>
              throw new InvocationTargetException(e);
          }
        }
      })
    } catch {
      case e: InvocationTargetException =>
        Logger.log(IStatus.ERROR, SvnHelperScala.PLUGIN_ID, e); return false;
      case e: InterruptedException =>
        Logger.log(IStatus.ERROR, SvnHelperScala.PLUGIN_ID, e); return false;
    } finally {
      SvnAdapter.clearPool();
    }

    val title: String = if (svnUrls.length == 1) ("Pull branch " + svnUrls(0)) else "Pull multiple branches"
    var msg: String = "Projects found: " + svnProjects.size + "\nProjects to switch: " + toSwitch.size + "\nProjects to checkout: " + toCo.size
    if ((svnUrls.length > 1)) msg = "Pulling branches: " + svnUrls.mkString(", ") + "\n\n" + msg

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
    // Map URLs to WorkingSets
    val wsNames =
      if (Preferences.getByPassWorkingSetsMapping()) {
        urls.map(_.getLastPathSegment)
      } else {
        val shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell()
        val dialog = new AssignWorkingSetsDialog(shell,
          "Define Working Sets",
          "Please map the selected URLs to existing or new WorkingSets.", urls)
        if (dialog.open() != 0) {
          return false
        }
        dialog.getWs
      }

    // Find the projects to dispatch
    val newWS: Map[String, List[IProject]] = SvnHelperScala.resolveWorkingSetFromSvn(urls, wsNames)

    // Confirm the operation
    if (Preferences.getConfirmWorkingSetsCreation()) {
      val msg: StringBuilder = new StringBuilder("The following WorkingSets will be defined:\n")
      for (entry <- newWS) {
        msg.append(entry._1).append(" - ").append(entry._2.length).append(" projects\n")
      }

      if (!MessageDialog.openQuestion(null, "Define Working Sets", msg.toString())) {
        return false;
      }
    }

    if (Preferences.getSwitchPerspective()) {
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
    }

    // Create missing IWorkingSet and index all WS by name
    val workingSets: Map[String, IWorkingSet] = SvnHelperScala.createWorkingSets(wsNames)

    if (Preferences.getTransferFromWorkingSets()) {
      // Remove the projects we will dispatch from existing WS
      var allProjects: Set[IProject] = Set()
      for (val projects <- newWS.values) {
        allProjects ++= projects;
      }
      SvnHelperScala.removeProjectsFromWorkingSets(workingSets.values, allProjects);
    }

    val wsManager: IWorkingSetManager = WorkbenchPlugin.getDefault().getWorkingSetManager()
    for ((wsName, projects) <- newWS) {
      val workingSet = Array(workingSets.getOrElse(wsName, null))

      projects.foreach(wsManager.addToWorkingSets(_, workingSet))
    }

    true
  }

}

object SvnHelperScala {
  val PLUGIN_ID: String = "fr.sivrit.svn.helper.scala"

  private def findWorkspaceProject(name: String): IProject =
    ResourcesPlugin.getWorkspace().getRoot().getProject(name)

  private def findTeamWorkspaceProjects(subMonitor: SubMonitor): Set[ProjectDeps] = {
    subMonitor.subTask("Gathering projects from workspace...");
    subMonitor.setWorkRemaining(2);

    val projects = ProjectDeps.findWorkspaceProjects(subMonitor.newChild(1))

    subMonitor.subTask("Filtering SVN projects...");
    subMonitor.setWorkRemaining(projects.size);
    projects.filter { deps =>
      subMonitor.worked(1)
      if (deps.name == null) false
      else {
        val iproject = findWorkspaceProject(deps.name)
        RepositoryProvider.getProvider(iproject, SVNProviderPlugin.getTypeId()) != null
      }
    }
  }

  private def sortOut(remotes: Set[(ProjectDeps, SVNUrl)], subMonitor: SubMonitor): (Set[(IProject, SVNUrl)], Set[SVNUrl]) = {
    var toSwitch = Set[(IProject, SVNUrl)]()
    var toCo = Set[SVNUrl]()

    subMonitor.setWorkRemaining(2)

    val workspace: Set[ProjectDeps] = findTeamWorkspaceProjects(subMonitor.newChild(1))

    subMonitor.setWorkRemaining(remotes.size)
    SvnAdapter.withSvnAdapter { svn =>
      remotes.foreach(_ match {
        case (project, url) => workspace.find {
          ProjectDeps.doMatch(project, _)
        } match {
          case Some(deps) =>
            val iproj: IProject = findWorkspaceProject(deps.name)

            assert(iproj != null, deps.name)

            val status: ISVNStatus = svn.getStatus(iproj.getLocation().toFile())

            if (!url.equals(status.getUrl())) {
              val duo = (iproj, url)
              toSwitch += duo
            }
          case None => toCo += url
        }
      })
      subMonitor.worked(1)
    }

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

  private def resolveWorkingSetFromSvn(urls: Array[SVNUrl], wsNames: Array[String]): Map[String, List[IProject]] = {
    require(urls.length == wsNames.length)

    val useExclusions = Preferences.getApplyOnWokingSet

    var newWS: Map[String, List[IProject]] = Map.empty

    val progress = new ProgressMonitorDialog(null)
    progress.run(true, true, new IRunnableWithProgress() {
      def run(monitor: IProgressMonitor) = {
        val subMonitor = SubMonitor.convert(monitor)
        subMonitor.setWorkRemaining(10);
        val workspace: Set[ProjectDeps] = findTeamWorkspaceProjects(subMonitor.newChild(1))

        subMonitor.setWorkRemaining(urls.length)
        for ((wsName, url) <- wsNames.zip(urls)) {
          var wsProjects: List[IProject] = Nil

          subMonitor.setTaskName("Resolving projects in " + url.toString + "...")

          val svnProjects = try {
            new SvnCrawler(subMonitor.newChild(1, SubMonitor.SUPPRESS_BEGINTASK), useExclusions).findProjects(Array(url))
          } catch { case e: SVNException => throw new InvocationTargetException(e) }
          finally { SvnAdapter.clearPool() }

          svnProjects.foreach {
            case (svnProject, _) =>
              workspace.find(ProjectDeps.doMatch(svnProject, _)) match {
                case Some(locProject) => wsProjects = findWorkspaceProject(locProject.name) :: wsProjects
                case None =>
              }
          }

          if (!svnProjects.isEmpty) {
            val preExistingProjects = newWS.getOrElse(wsName, Nil)
            newWS = newWS.updated(wsName, preExistingProjects ++ wsProjects)
          }
        }
      }
    })

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