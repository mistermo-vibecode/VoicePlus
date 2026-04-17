package voice.app.di

import voice.app.features.widget.BaseWidgetProvider
import voice.app.features.widget.ChapterWidgetProvider
import voice.features.widget.WidgetGraph

interface AppGraph : WidgetGraph {

  fun inject(target: App)
  override fun inject(target: BaseWidgetProvider)
  override fun inject(target: ChapterWidgetProvider)
}
