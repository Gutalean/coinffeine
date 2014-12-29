Coinffeine GUI
==============

This module is the graphical user interface of the Coinffeine network.

You can run it from sources using sbt:

    > sbt gui/run

To build installable bundles just run from the project root:

    > sbt gui/release

This will generate all native bundles that can be generated from your OS (a
DMG from OS X, exe/msi from Windows, etc) that you will find under
`coinffeine-gui/target/scala-X.YY`.


Running tests
-------------

The default `sbt` command `test` will only run non-graphical tests. Those are
considered integrated test and should only executed from graphics enabled
terminals by running `sbt gui/it:test`.
