package voice.core.data.store.snapshot.identity

import androidx.core.net.toUri
import voice.core.data.BookContent
import voice.core.data.Chapter
import voice.core.data.store.snapshot.rekey.BookIdentityStamp
import voice.core.data.store.snapshot.rekey.ChildEntry

/**
 * Builds a book's [BookIdentityStamp] purely from its stored URIs. Run identically over the
 * snapshot's stored ids (see OsWipeRestorer.reconstructStamp) and over the freshly-scanned library
 * at restore time; because the volume-namespaced documentId is location-stable, the two stamps line
 * up across a re-grant and the re-keyer can match them. Nothing is persisted — the URIs already in
 * the bundle are the identity.
 *
 * File sizes are intentionally 0 here: they are only available from the scanner's DocumentFile walk, and the
 * confirmer treats size as a soft signal (0 == unknown). The hard gates — identical child count and identical
 * relName multiset — already prevent attaching one book's data onto a different book.
 */
internal object IdentityStampBuilder {

  fun build(
    book: BookContent,
    chapters: List<Chapter>,
  ): BookIdentityStamp {
    val bookUri = book.id.value.toUri()
    val relPath = DeviceRelativePath.documentId(bookUri)
    val children = chapters
      .map { ChildEntry(relName = DeviceRelativePath.relName(it.id.value.toUri(), relPath), size = 0L) }
      .sortedBy { it.relName }
    // A single-file book is its own only chapter: the child documentId equals the book documentId, so its
    // relName is "". Such books have no folder to anchor chapters in and are gated out of auto-re-keying.
    val isSingleFile = children.size == 1 && children.single().relName.isEmpty()
    return BookIdentityStamp(
      authority = DeviceRelativePath.authority(bookUri),
      isSingleFile = isSingleFile,
      relPath = relPath,
      folderName = relPath.substringAfterLast('/'),
      children = children,
    )
  }
}
