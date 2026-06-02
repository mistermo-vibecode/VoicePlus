package voice.core.data.store.snapshot.identity

import android.net.Uri

/**
 * Derives re-grant-invariant identity from a SAF document URI using only the URI itself (no DocumentFile IO).
 *
 * For the on-device ExternalStorageProvider the URI's last path segment IS the decoded, volume-namespaced
 * documentId (e.g. "primary:Audiobooks/Dune"). That documentId is a function of the on-device location, not
 * of the SAF grant, so it is identical before and after an uninstall/reinstall/re-grant — which is exactly
 * what makes it a safe re-key. The full BookId/ChapterId URI string, by contrast, embeds the tree-grant
 * prefix and changes on every re-grant.
 */
internal object DeviceRelativePath {

  fun authority(uri: Uri): String = uri.authority.orEmpty()

  /** Volume-namespaced documentId, e.g. "primary:Audiobooks/Dune". `Uri.getLastPathSegment()` URL-decodes it. */
  fun documentId(uri: Uri): String = uri.lastPathSegment.orEmpty()

  /**
   * Folder-relative tail of [childUri] under [bookRelPath]. Example: bookRelPath "primary:Books/Dune" with
   * child documentId "primary:Books/Dune/Disc1/01.mp3" -> "Disc1/01.mp3". Sub-path-qualified so two files
   * named "01.mp3" in different sub-folders never collide. A single-file book (the child's documentId equals
   * the book's documentId) yields "".
   */
  fun relName(childUri: Uri, bookRelPath: String): String =
    documentId(childUri).removePrefix(bookRelPath).trimStart('/')
}
