package coinffeine.peer.properties

import coinffeine.peer.global.MutableGlobalProperties

trait DefaultCoinffeinePropertiesComponent extends MutableGlobalProperties.Component {

  override lazy val globalProperties = new MutableGlobalProperties
}
