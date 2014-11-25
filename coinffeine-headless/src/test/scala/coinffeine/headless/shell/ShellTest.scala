package coinffeine.headless.shell

import java.io._

import org.apache.commons.io.input.ReaderInputStream
import org.apache.commons.io.output.WriterOutputStream

import coinffeine.common.test.UnitTest

class ShellTest extends UnitTest {

  "A shell" should "prompt for commands" in new Fixture {
    inputs("", "")
    shell.run()
    out.toString shouldBe "1> \n2> \n3> "
  }

  it should "execute commands on demand" in new Fixture {
    inputs("command-a")
    shell.run()
    commandA shouldBe 'executed
    commandB should not be 'executed
  }

  it should "ignore whitespace at the beginning of the line" in new Fixture {
    inputs(" \t command-a")
    shell.run()
    commandA shouldBe 'executed
  }

  it should "inform about unrecognized commands" in new Fixture {
    inputs("unknown-command irrelevant args")
    shell.run()
    out.toString should include("Unknown command 'unknown-command'")
  }

  it should "ignore empty lines" in new Fixture {
    inputs("", " \t ", "  ")
    shell.run()
    out.toString should not include "Unknown command"
  }

  it should "execute commands until the input ends" in new Fixture {
    inputs("command-a", "command-b")
    shell.run()
    commandA shouldBe 'executed
    commandB shouldBe 'executed
  }

  it should "execute commands until 'exit' is typed" in new Fixture {
    inputs("  exit  ", "command-a")
    shell.run()
    commandA should not be 'executed
  }

  it should "pass to the command the rest of the line" in new Fixture {
    inputs("command-a", "command-a arg", "command-a   arg1 arg2")
    shell.run()
    commandA.invocations shouldBe Seq("", "arg", "arg1 arg2")
  }

  trait Fixture {
    private val pipeIn = new PipedWriter()
    private val pipeOut = new PipedReader(pipeIn)
    protected val in = new PrintWriter(pipeIn)
    protected val out = new StringWriter()
    val commandA = new SpyCommand("command-a")
    val commandB = new SpyCommand("command-b")
    val shell = new Shell(
      prompt = new AutoIncrementPrompt,
      commands = Seq(commandA, commandB),
      input = new ReaderInputStream(pipeOut),
      output = new WriterOutputStream(out)
    )

    def inputs(lines: String*): Unit = {
      lines.foreach(in.println)
      in.close()
    }
  }

  class SpyCommand(override val keyword: String) extends Command {
    private var _invocations = Seq.empty[String]

    def invocations = _invocations
    def executed = _invocations.nonEmpty

    override def apply(output: PrintWriter, args: String): Unit =
      synchronized { _invocations :+= args }

    override def toString = keyword
  }

  class AutoIncrementPrompt extends Prompt {

    private var promptCount = 0

    override def value: String = synchronized {
      promptCount += 1
      s"$promptCount> "
    }
  }
}
