package voice.core.data.store.snapshot.rekey

import voice.core.data.store.snapshot.BookIdentityStampDto
import voice.core.data.store.snapshot.ChildEntryDto

internal fun BookIdentityStampDto.toStamp(): BookIdentityStamp = BookIdentityStamp(
  authority = authority,
  isSingleFile = isSingleFile,
  relPath = relPath,
  folderName = folderName,
  children = children.map { it.toChildEntry() },
)

internal fun ChildEntryDto.toChildEntry(): ChildEntry = ChildEntry(relName = relName, size = size)
