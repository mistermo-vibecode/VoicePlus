package voice.core.data.store.snapshot.identity

import androidx.core.net.toUri
import voice.core.data.BookContent
import voice.core.data.Chapter
import voice.core.data.store.snapshot.rekey.BookIdentityStamp

/**
 * Builds a book's [BookIdentityStamp] purely from its stored URIs. Run identically over the
 * snapshot's stored ids (see OsWipeRestorer.reconstructStamp) and over the freshly-scanned library
 * at restore time; because the volume-namespaced documentId is location-stable, the two stamps line
 * up across a re-grant and the re-keyer can match them. Nothing is persisted — the URIs already in
 * the bundle are the identity.
 *
 */
internal object IdentityStampBuilder {

  fun build(
    book: BookContent,
    chapters: List<Chapter>,
  ): BookIdentityStamp {
    val bookUri = book.id.value.toUri()
    val relPath = DeviceRelativePath.documentId(bookUri)
    val children = chapters
      .map { DeviceRelativePath.relName(it.id.value.toUri(), relPath) }
      .sorted()
    // A single-file book is its own only chapter: the child documentId equals the book documentId, so its
    // relName is "". Such books have no folder to anchor chapters in and are gated out of auto-re-keying.
    val isSingleFile = children.size == 1 && children.single().isEmpty()
    return BookIdentityStamp(
      authority = DeviceRelativePath.authority(bookUri),
      isSingleFile = isSingleFile,
      relPath = relPath,
      folderName = relPath.substringAfterLast('/'),
      children = children,
    )
  }
}
