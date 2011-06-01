S4 Twitter Topic Count - Sample S4 Application
==============================================

TODO: intro here

![S4 Twitter Topic Count](https://github.com/leoneu/s4-meter/raw/master/etc/s4-meter.jpg)

More details
--------------------

blah blah:

* bullet 1
  * bullet 2 

Implementation
--------------

The code is organized as follows:

* bullet
* bullet

Set up an S4 Image
------------------

<pre>
# Extract s4 image from tgz file.
tar xvzf  s4-0.3-SNAPSHOT-bin.tgz
rm -f  s4-0.3-SNAPSHOT-bin.tgz
cd s4-0.3-SNAPSHOT
export S4_IMAGE=`pwd`
</pre>

Build
-----

<pre>
cd myapps
git clone git://github.com/s4/twittertopiccount.git
cd twittertopiccount
gradlew install
</pre>

> The command `gradlew` will download a wrapper for the build tool `gradle` and make it available for this project. You may want to [install](http://www.gradle.org/downloads.html) `gradle` directly instead and use the command `gradle` directly.  

Deploy
------

This will install the twittertopiccount application in the $S4_IMAGE.
<pre>
gradlew deploy
</pre>

Run
---

Start each command in a different window to see the updates on the standard output.

<pre>
# Start S4 server.
$S4_IMAGE/scripts/s4-start.sh -r client-adapter

# Start S4 client adapter server.
$S4_IMAGE/scripts/run-client-adapter.sh -s client-adapter \
-g s4 -x -d $S4_IMAGE/s4-core/conf/default/client-stub-conf.xml

# Start a any other PROCESS.
./s4-meter-generator/build/install/twittertopiccount/bin/twittertopiccount
</pre>

Troubleshooting
---------------

* Make sure scripts have executable permissions (chmod u+x myscript).
* Make sure you set the environment variable S4_IMAGE in your shell. For this add the following to your .bashrc:

<pre>
# S4 Configuration
S4_IMAGE=/home/leo/Projects/s4project/s4image/s4-0.3-SNAPSHOT
export S4_IMAGE
</pre>

* To use the Gradle plugin for the Eclipse IDE, make sure that the S4_IMAGE environment variable is inherited 
by Eclipse. If you use the GUI to launch eclipse make sure to set it up. 
[Notes for MacOS](http://stackoverflow.com/questions/603785/environment-variables-in-mac-os-x).

