Coinffeine headless
===================

This module provides a Coinffeine console intended for text-only environments.
You are able to keep the application running on a tmux or screen space in a
dedicated server and attach to it to perform remote administration.

Configuration
-------------

Configuration is performed using the same files at the same locations as with the coinffeine-gui.
It is recommended to run the graphical configuration wizard first and then, copy the configurations
to the server in the case of wanting it to be used in a different host.

Alternatively, you can create/edit a configuration file at the application user settings directory
(`~/.coinffeine` in Linux, `~/Library/Application Support/Coinffeine` in OS X) with at least the
following attributes:

    coinffeine {
        okpay {
            id="your id here"
            token="your token here"
        }
    }

To configure manual port forwarding, you should add another configuration value after configuring
your router.

    coinffeine {
        ...

        peer.externalForwardedPort="the port you configured here"
    }


How to run it
-------------

To run it from the sources:

    > sbt headless/run

How to create a standalone JAR:

    $ sbt headless/release

Then you can find the JAR at `target/scala-2.x/coinffeine-headless-standalone.jar`
(JARs with other suffixes are not standalone so don't accept imitations) and
use it as:

    java -jar <jar path>


