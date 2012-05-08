package fr.sivrit.svn.helper.scala.svn
import org.tigris.subversion.svnclientadapter.SVNUrl
import java.io.File
import fr.sivrit.svn.helper.Preferences
import org.tigris.subversion.svnclientadapter.ISVNInfo
import org.tigris.subversion.svnclientadapter.SVNNodeKind
import org.tigris.subversion.svnclientadapter.ISVNDirEntry

class SvnClient {
  def fetch(url: SVNUrl): SvnNode = {
    val svn: SvnAdapter = SvnAdapter.borrow

    try {
      val info: ISVNInfo = svn.getInfo(url)

      val version: Long = info.getLastChangedRevision.getNumber

      return fetch(url, version, info.getNodeKind() == SVNNodeKind.DIR)
    } finally {
      SvnAdapter.release(svn);
    }
  }

  def fetch(url: SVNUrl, version: Long, isDir: Boolean): SvnNode =
    {
      val svn: SvnAdapter = SvnAdapter.borrow()
      try {
        if (isDir) {
          val content: Array[ISVNDirEntry] = svn.getList(url)
          var entries: Set[SvnFolderEntry] = Set.empty
          for (isvnDirEntry <- content) {
            entries += new SvnFolderEntry(isvnDirEntry)
          }

          return new SvnDir(url, version, entries);
        } else {
          val content: String = svn.getStringContent(url)
          return new SvnFile(url, version, content);
        }
      } finally {
        SvnAdapter.release(svn);
      }
    }
}

object SvnClient {
  def createClient(): SvnClient = {
    val location = Preferences.getCacheFolder
    if (location == null) {
      new SvnClient();
    } else {
      val cacheDir = if (location.endsWith(File.separator)) location else (location
        + File.separator);
      return new CachedSvnClient(cacheDir);
    }
  }
}