package fr.sivrit.svn.helper.scala.svn

import fr.sivrit.svn.helper.scala.SvnHelperScala
import java.lang.reflect.InvocationTargetException
import org.eclipse.core.resources.{ IProject, IResource }
import org.eclipse.core.runtime.IStatus.ERROR
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.core.runtime.{ IProgressMonitor, IStatus, Status }
import org.tigris.subversion.subclipse.core.ISVNCoreConstants
import org.tigris.subversion.subclipse.ui.operations.SwitchOperation
import org.tigris.subversion.svnclientadapter.{ SVNRevision, SVNUrl }
import fr.sivrit.svn.helper.Preferences

object SvnSwitch {
  def switch(todo: Set[(IProject, SVNUrl)]): Unit = {

    val len = todo.size
    val urls = new Array[SVNUrl](len)
    val projects = new Array[IResource](len)

    var i = 0;
    for ((proj, url) <- todo) {
      urls(i) = url
      projects(i) = proj
      i = i + 1
    }

    callSwitchOperation(urls, projects, SVNRevision.HEAD);
  }

  private def callSwitchOperation(svnUrls: Array[SVNUrl], projects: Array[IResource],
    svnRevision: SVNRevision): Unit = {
    val switchOperation: SwitchOperation = new SwitchOperation(null, projects, svnUrls,
      svnRevision)

    switchOperation.setDepth(if (Preferences.isUseWorkingCopy()) ISVNCoreConstants.DEPTH_UNKNOWN else ISVNCoreConstants.DEPTH_INFINITY)
    switchOperation.setSetDepth(Preferences.isSetDepth());
    switchOperation.setForce(Preferences.isForce());
    switchOperation.setIgnoreAncestry(Preferences.isIgnoreAncestry());
    switchOperation.setIgnoreExternals(Preferences.isIgnoreExtenals());

    val job: Job = new Job("SwitchOperation job") {
      def run(monitor: IProgressMonitor): IStatus = {
        monitor.beginTask("Some nice progress message here ...", 100)
        try {
          switchOperation.run(monitor)
        } catch {
          case e: InvocationTargetException =>
            return new Status(ERROR, SvnHelperScala.PLUGIN_ID, e.getMessage(), e)
          case e: InterruptedException =>
            return new Status(ERROR, SvnHelperScala.PLUGIN_ID, e.getMessage(), e)
        }
        monitor.done()
        return Status.OK_STATUS
      }
    }
    job.schedule()
  }
}