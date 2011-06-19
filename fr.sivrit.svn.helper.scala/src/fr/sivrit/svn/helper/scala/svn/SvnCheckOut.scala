package fr.sivrit.svn.helper.scala.svn

import java.lang.reflect.InvocationTargetException
import org.tigris.subversion.subclipse.core.resources.RemoteFolder
import org.tigris.subversion.subclipse.core.{ISVNRemoteFolder, ISVNRepositoryLocation}
import org.tigris.subversion.subclipse.ui.actions.CheckoutAsProjectAction
import org.tigris.subversion.svnclientadapter.{SVNRevision, SVNUrl}

object SvnCheckOut {
  def checkout(repo: ISVNRepositoryLocation, todo: List[SVNUrl]): Unit = {
    val foldersArray: Array[ISVNRemoteFolder] = todo.map(url => {
      new RemoteFolder(repo, url, SVNRevision.HEAD)
    }).toArray

    val checkoutAction: CheckoutAsProjectAction = new CheckoutAsProjectAction(
      foldersArray, null, null)
    try {
      checkoutAction.execute(null)
    } catch {
      case e: InvocationTargetException =>
        e.printStackTrace();
      case e: InterruptedException =>
        e.printStackTrace();
    }
  }
}