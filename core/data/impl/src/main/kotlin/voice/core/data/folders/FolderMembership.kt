package voice.core.data.folders

import android.net.Uri
import android.provider.DocumentsContract
import voice.core.data.store.snapshot.identity.DeviceRelativePath

/**
 * Whether a book — identified by its SAF document URI — lives under a configured audiobook [folderUri] of the
 * given [folderType]. Pure URI algebra over the re-grant-invariant documentId (no DocumentFile IO), mirroring
 * how [AudiobookFoldersImpl.toDocumentFile] and the scanner derive book URIs from a folder. Used to deactivate
 * exactly a removed folder's books, without the scanner's whole-library reconcile.
 *
 * Matching is on (authority, treeDocumentId, documentId) — never the full BookId URI string, which embeds the
 * re-grantable tree prefix (see [DeviceRelativePath]).
 */
internal object FolderMembership {

  fun isBookUnderFolder(
    bookUri: Uri,
    folderUri: Uri,
    folderType: FolderType,
  ): Boolean {
    if (DeviceRelativePath.authority(bookUri) != DeviceRelativePath.authority(folderUri)) return false
    val bookDocId = DeviceRelativePath.documentId(bookUri)
    return when (folderType) {
      // The configured URI IS the book (a single-file grant), stored verbatim — compare documentId directly.
      FolderType.SingleFile -> bookDocId == DeviceRelativePath.documentId(folderUri)
      // The configured folder IS the book root; the scanner passes it straight through.
      FolderType.SingleFolder -> bookDocId == DocumentsContract.getTreeDocumentId(folderUri)
      // Books are path-descendants of the tree (a direct child for Root; a child or grandchild for Author).
      FolderType.Root, FolderType.Author -> {
        val treeDocId = DocumentsContract.getTreeDocumentId(folderUri)
        // '/'-boundary check so "primary:Books" does not falsely match "primary:BooksArchive".
        bookDocId == treeDocId || bookDocId.startsWith("$treeDocId/")
      }
    }
  }
}
