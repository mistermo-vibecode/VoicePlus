package voice.features.widget

import voice.app.features.widget.BaseWidgetProvider
import voice.app.features.widget.ChapterWidgetProvider

interface WidgetGraph {
  fun inject(target: BaseWidgetProvider)
  fun inject(target: ChapterWidgetProvider)
  fun inject(target: voice.features.widget.config.WidgetConfigActivity)
}
