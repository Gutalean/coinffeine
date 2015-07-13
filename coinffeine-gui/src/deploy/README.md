This directory contains misc data to generate the installable packages for 
Coinffeine.

In order to build such packages, please run `sbt gui/release` from the root
source directory.

**For Windows 8 users**. There is a known bug in JDK 8 that prevents
`msvcp100.dll` and `msvcr100.dll` libraries to be included in the bundle.
Nevertheless, these libraries are required by the Java runtime, so the app
will fail if they are not included in the bundle.

This bug is known and will be fixed in JDK 8u40, which will be released in
March 2015. Until then, the Inno Setup config file includes msvcp100.dll
by following these steps.

* Install MSVC 2010 Redistributable package in the system. You may obtain it 
from http://www.microsoft.com/en-US/download/confirmation.aspx?id=14632

* Copy the library from `C:\Windows\System32\msvcp100.dll` and 
`C:\Windows\System32\msvcr100.dll` into `C:\Windows\Temp`. This step is 
needed because Inno Setup is unable to access those libraries at 
`C:\Windows\System32` for some unknown reason.

* Execute `sbt gui/release` normally to have a valid Windows installer.
