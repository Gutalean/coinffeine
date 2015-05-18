package coinffeine.peer.market.orders

/** Same tests as in [[PersistentOrderActorTest]] but having a snapshot just before a restart */
class PersistentWithSnapshotsOrderActorTest extends PersistentOrderActorTest {
  override protected def useSnapshots = true
}
