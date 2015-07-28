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

Packaging issues
----------------

Under some unknown circumstances, JavaFx Packager Tool fails to calculate the
correct classpath to be set in the native application bundle. That causes the
app launcher to define an empty classpath, and therefore the app fails to
launch. This affects up to JDK version 8u51. It is expected to be fixed for
8u60.

When that happens, the following workaround could be applied to obtain a valid
package.

For the sake of legibility, from now on we will refer to the target directory
`coinffeine-gui/target/scala-x.y` as `$TARGET_DIR`.

First of all, launch the sbt task to generate the package as explained above.
That will generate all the files needed to build the bundle, and it will
generate a file in `$TARGET_DIR/build.xml`. That's an Ant build file that uses
JavaFX Packager Tool tasks to generate the bundle.

At this point, we have all the library jars in `$TARGET_DIR/lib`. We have to
make a space-separated list of files from `$TARGET_DIR` directory perspective.
To do so, go to `$TARGET_DIR` and execute the following command:

    for f in lib/*; do echo -n "$f "; done; echo ""

Copy the file list from the output into your clipboard. Now, open the Ant file
`$TARGET_DIR/build.xml` and place the following new XML element under
`<fx::deploy>`:

    <fx:deploy ...>
        ...
        <fx:bundleArgument arg="classpath" value="<content-from-your-clipboard>" />
        ...
    </fx:deploy>

Where `<content-from-your-clipboard>` should be replaced by the file list from
your clipboard. Save the file and execute `ant` from `$TARGET` directory.

If Ant executes successfully, the new bundle will be generated with the
appropriate classpath defined.
