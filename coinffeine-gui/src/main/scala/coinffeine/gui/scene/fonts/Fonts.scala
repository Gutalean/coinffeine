package coinffeine.gui.scene.fonts

import scalafx.scene.text.Font

object Fonts {

  def loadAll(): Unit = {
    coinffeineGlyphs
    openSansLight
    openSansRegular
    openSansSemibold
    openSansBold
    openSansExtraBold
  }

  def coinffeineGlyphs = fromResource("fonts/CoinffeineIconography.ttf")

  def openSansLight = fromResource("fonts/OpenSans-Light.ttf")
  def openSansRegular = fromResource("fonts/OpenSans-Regular.ttf")
  def openSansSemibold = fromResource("fonts/OpenSans-Semibold.ttf")
  def openSansBold = fromResource("fonts/OpenSans-Bold.ttf")
  def openSansExtraBold = fromResource("fonts/OpenSans-ExtraBold.ttf")

  def fromResource(name: String): Font = new Font(
    Font.loadFont(ClassLoader.getSystemResource(name).toExternalForm, 10))
}
