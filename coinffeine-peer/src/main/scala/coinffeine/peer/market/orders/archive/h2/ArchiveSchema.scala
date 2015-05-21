package coinffeine.peer.market.orders.archive.h2

import java.sql.Connection

private object ArchiveSchema {

  private val Statements = Seq(
    """
      |create table if not exists `order`(
      |   id varchar(64) not null,
      |   order_type varchar(3) not null,
      |   amount decimal(16,8) not null,
      |   price decimal(128,64),
      |   currency char(3) not null,
      |   primary key(id)
      |);
    """.stripMargin,
    """
      |create table if not exists order_log(
      |  id long not null auto_increment,
      |  owner_id varchar(64) not null,
      |  timestamp datetime not null,
      |  event varchar(20) not null,
      |  primary key(id),
      |  foreign key(owner_id) references `order`(id) on delete cascade
      |);
    """.stripMargin,
    """
      |create table if not exists exchange(
      |  id varchar(64) not null,
      |  order_id varchar(64) not null,
      |  role varchar(16) not null,
      |  buyer_bitcoin decimal(16, 8) not null,
      |  seller_bitcoin decimal(16, 8) not null,
      |  buyer_fiat decimal(32, 6) not null,
      |  seller_fiat decimal(32, 6) not null,
      |  counterpart varchar(64) not null,
      |  lock_time long not null,
      |  buyer_progress decimal(16, 8) not null,
      |  seller_progress decimal(16, 8) not null,
      |  primary key(id),
      |  foreign key(order_id) references `order`(id) on delete cascade
      |);
    """.stripMargin,
    """
      |create table if not exists exchange_log(
      |  id long not null auto_increment,
      |  owner_id varchar(64) not null,
      |  timestamp datetime not null,
      |  event varchar(1024) not null,
      |  primary key(id),
      |  foreign key(owner_id) references exchange(id) on delete cascade
      |);
    """.stripMargin
  )

  def ensure(conn: Connection): Unit = {
    Statements.foreach(conn.createStatement().execute)
  }
}
