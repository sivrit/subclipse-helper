package fr.sivrit.svn.helper.scala.svn
import org.tigris.subversion.svnclientadapter.SVNUrl
import org.tigris.subversion.svnclientadapter.ISVNDirEntry
import org.tigris.subversion.svnclientadapter.SVNNodeKind

case class SvnFolderEntry(name: String, version: Long, isDir: Boolean) {
  def this(entry: ISVNDirEntry) = this(
    entry.getPath,
    entry.getLastChangedRevision.getNumber,
    entry.getNodeKind == SVNNodeKind.DIR)
}

sealed abstract class SvnNode {
  def url: SVNUrl
  def version: Long
}
case class SvnFile(url: SVNUrl, version: Long, content: String) extends SvnNode
case class SvnDir(url: SVNUrl, version: Long, children: Set[SvnFolderEntry]) extends SvnNode

