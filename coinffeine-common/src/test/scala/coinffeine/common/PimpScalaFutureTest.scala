package coinffeine.common

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NoStackTrace
import scala.util.{Failure, Success}

import coinffeine.common.test.UnitTest

class PimpScalaFutureTest extends UnitTest with ScalaFutureImplicits {

  val cause = new Exception("injected error") with NoStackTrace

  "A future" should "be materialized to Success(value) when successful" in {
    Future.successful("ok").materialize.futureValue shouldBe Success("ok")
  }

  it should "be materialized to Failure(cause) when unsuccessful" in {
    Future.failed(cause).materialize.futureValue shouldBe Failure(cause)
  }

  it should "have a failure action executed on future failure" in {
    val action = new FailureAction
    val future = Future.failed(cause).failureAction(action.apply())
    the [Exception] thrownBy future.futureValue shouldBe cause
    action shouldBe 'executed
  }

  it should "have no failure action executed on future success" in {
    val action = new FailureAction
    val future = Future.successful("ok").failureAction(action.apply())
    future.futureValue shouldBe "ok"
    action should not be 'executed
  }

  class FailureAction {
    private val executedFlag = new AtomicBoolean(false)
    def apply() = Future.successful(executedFlag.set(true))
    def executed: Boolean = executedFlag.get
  }
}
