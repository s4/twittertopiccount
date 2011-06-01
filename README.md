S4 Meter - A Distributed Performance Evaluation Framework for S4
================================================================ 

S4 Meter is a framework for running automatic end-to-end
performance tests on S4 clusters.

S4 Meter manages multiple remote event generators from a central process. The remote event generators are hosted by a container whose only function is to communicate with the controller, pass commands to the generator, and manage connections to the S4 Client Adapter using the S4 Client Driver. In preparation for a load test, the controller uploads a preconfigured event generator object to the remote generator containers. An instance of event generator can only be used once. To run another test, create a new instance and redeploy. To start a test, the controller sends a command to the remote generators using a REST interface. The event generation logic is implemented by extending the abstract EventGenerator class. S4 Meter provides a reference implementation in the package `io.s4.meter.controller.plugin.randomdoc`. Once a test is completed, the results are sent back to the remote generators and from there back to the controller which can aggregate and produce final reports for each test.

![S4 Meter Architecture](https://github.com/leoneu/s4-meter/raw/master/etc/s4-meter.jpg)

Running Custom Tests
--------------------

To run a custom test, you need to do the following:

* Write a plugin similar to the one in this package: `io.s4.meter.controller.plugin.randomdoc`
  * Extend `EventGenerator` and implement the `init` and `getDocument` methods.
  * Implement dependency injection in Guice using a configuration module similar to `RandomDocModule`.
  * Implement the PEs for your S4 application.
  * Create an S4 configuration file similar to the one in `s4-meter-controller/src/main/resources/s4-meter-controller-conf.xml`.
  * Create an S4 Meter properties file similar to the one in `s4-meter-controller/src/main/resources/s4-meter.properties`. 
* Run the test using the instruction at the bottom of this document. 

Implementation
--------------

The code is organized in three packages:

* `io.s4.meter.common`: common classes to both the controller and the remote generators.
* `io.s4.meter.controller`: functionality to run the controller, configure generator instances for a given plugin, serialization of the generator instances, and communication with the remote generator containers.
* `io.s4.meter.generator`: functionality to install and run generators in remote hosts, designed as a zero-configuration service that always running in the background. Even if you run a custom generator, the generator service never has to be restarted or managed. Install as a service on a host and forget about it.

Reference Implementation: Random Doc Test
------------------------------------------

* Each event contains a document and a unique ID (0 - N-1) where N is the total number of events to be generated.
* The document is a sequence of random words.
* Each word is a sequence of random characters generated on the fly. (So we don't need to store data.)
* Num words, word length, and alphabet are configurable.
* When initiated with the same seed, event generators generate exactly the
  same sequence of events. The source sequence is deterministic.
* The S4 application parses the document, and emits Word Events.
* Each unique word is counted.

NOTE: The source sequence is deterministic. (It is based on the `java.util.Random` with identical seed). We know ahead of time the exact count for each unique word. If we ran N identical generators, we expect the word count modulo N to be zero.

Any discrepancy in counts is due to errors which may be caused by:

* Package loss
* Queue overflow
* Bugs in the code

For low event rates, the first two sources should be negligible. Order and time of arrival shouldn't affect the final count. 

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
git clone git://github.com/leoneu/s4-meter.git
cd s4-meter
gradlew install
</pre>

> The command `gradlew` will download a wrapper for the build tool `gradle` and make it available for this project. You may want to [install](http://www.gradle.org/downloads.html) `gradle` directly instead and use the command `gradle` directly.  

Deploy
------

This will install the S4-Meter reference application in the $S4_IMAGE.
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

# Start a generator listening on port 8182.
./s4-meter-generator/build/install/s4-meter-generator/bin/s4-meter-generator 8182

# Start a generator listening on port 8183.
./s4-meter-generator/build/install/s4-meter-generator/bin/s4-meter-generator 8183

# Start the controller.
./s4-meter-controller/build/install/s4-meter-controller/bin/s4-meter-controller
</pre>

What happened? The controller uploaded an event generator to the two generators we 
started and instructed the event generators to start. As a result the event generators
sent events. Each event contains words with random characters. The events are 
sent using the S4 Java client to an adaptor service. If you look at the terminal where 
you started the S4 server, you will see two sets of each document.

To change the configuration, change the properties file:

<pre>
cat s4-meter-controller/src/main/resources/s4-meter.properties
</pre>

To change the S4 application, edit the S4 configuration file.

<pre>
cat s4-meter-controller/src/main/resources/s4-meter-controller-conf.xml
</pre>

Other useful commands:

Create an three eclipse projects (common, generator, controller):
<pre>
gradlew eclipse
</pre>

Create javadocs for the three projects:
<pre>
gradlew javadoc
</pre>

Implementation Details
----------------------

Generators are controlled using a REST API. To implement the interface we used
the [Restlet](http://www.restlet.org) framework because it is lightweight and easy to use. The concrete
classes used to generate events are loaded into the generators by the controller 
every time a test starts. To write a new event generator follow the pattern in:

<pre>
ls -l s4-meter-controller/src/main/java/io/s4/meter/controller/plugin/randomdoc
</pre>

In this project we used [Guice](http://code.google.com/p/google-guice) for dependency injection. All the configuration 
logic is implemented using Guice modules. 

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

