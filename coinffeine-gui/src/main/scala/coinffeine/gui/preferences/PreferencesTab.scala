package coinffeine.gui.preferences

import scalafx.scene.control.Tab

abstract class PreferencesTab extends Tab {

  def close(): Unit
}
