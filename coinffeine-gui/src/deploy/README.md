This directory contains misc data to generate the installable packages for 
Coinffeine.

In order to build such packages, please run `sbt gui/package-javafx` from the
root source directory.

**For Windows 8 users**. There is a known bug in JDK 8 that prevents
msvcp100.dll & msvcr100.dll libraries to be included in the bundle.
Nevertheless, these libraries are required by the Java runtime, so the app
will fail if they are not included in the bundle.

This bug is known and will be fixed in JDK 8u40, which will be released in
March 2015. Until then, the Inno Setup config file includes msvcp100.dll
by following these steps.

* Install MSVC 2010 Redistributable package in the system. You may obtain it 
from http://www.microsoft.com/en-US/download/confirmation.aspx?id=14632

* Copy the library from `C:\Windows\System32\msvcp100.dll` into 
`C:\Windows\Temp`. This step is needed because Inno Setup is unable to access
`C:\Windows\System32\msvcp100.dll` for some unknown reason.

* Copy the library from `C:\Windows\System32\msvcr100.dll` into
`C:\Windows\Temp` as well.

* Execute `sbt gui/package-javafx` normally to have a valid Windows installer.
