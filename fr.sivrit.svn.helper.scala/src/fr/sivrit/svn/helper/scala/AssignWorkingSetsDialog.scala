package fr.sivrit.svn.helper.scala;

import scala.Array.canBuildFrom
import scala.util.Sorting

import org.apache.commons.lang.StringUtils
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.swt.events.ModifyEvent
import org.eclipse.swt.events.ModifyListener
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Combo
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.Shell
import org.eclipse.swt.SWT
import org.eclipse.ui.internal.WorkbenchPlugin
import org.tigris.subversion.svnclientadapter.SVNUrl

class AssignWorkingSetsDialog(parentShell: Shell, dialogTitle: String, dialogMessage: String, urls: Array[SVNUrl])
  extends MessageDialog(parentShell, dialogTitle, null, dialogMessage, MessageDialog.QUESTION, Array(IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL), 0) {

  setShellStyle(getShellStyle() | SWT.SHEET)

  private val ws: Array[String] = urls.map(_.getLastPathSegment)

  override def createCustomArea(parent: Composite): Control = {
    val composite = new Composite(parent, SWT.NONE)
    composite.setLayout(new GridLayout(2, false))

    val wsManager = WorkbenchPlugin.getDefault().getWorkingSetManager()
    var workingSets = Set[String]()
    wsManager.getWorkingSets.foreach { workingSets += _.getName }
    urls.foreach { workingSets += _.getLastPathSegment }

    val comboContent = workingSets.toArray
    Sorting.quickSort(comboContent)

    urls.view.zipWithIndex foreach {
      case (url, comboIndex) =>
        val label = new Label(composite, SWT.LEFT)
        label.setText(url.toString)

        val combo = new Combo(composite, SWT.DROP_DOWN)
        combo.setItems(comboContent)
        combo.setText(url.getLastPathSegment)

        combo.addModifyListener(new ModifyListener() {
          def modifyText(e: ModifyEvent) = {
            val newText = combo.getText
            if (StringUtils.isNotBlank(newText)) {
              ws(comboIndex) = newText;
            }
          }
        })
    }

    composite
  }

  def getWs(): Array[String] = ws
}
