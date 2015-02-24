package coinffeine.alarms

sealed trait Severity

object Severity {
  case object Low extends Severity { override def toString = "low" }
  case object Normal extends Severity { override def toString = "normal" }
  case object High extends Severity { override def toString = "high" }
}
