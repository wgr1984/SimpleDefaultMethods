[![Build Status](https://travis-ci.org/wgr1984/SimpleDefaultMethods.svg?branch=master)](https://travis-ci.org/wgr1984/SimpleDefaultMethods)
[ ![Download](https://api.bintray.com/packages/wgr1984/SimpleDefaultMethods/SimpleDefaultMethodsProcessor/images/download.svg) ](https://bintray.com/wgr1984/SimpleDefaultMethods/SimpleDefaultMethodsProcessor/_latestVersion)

# SimpleDefaultMethods
This projects provides an annotation processor to provide
default parameters for methods inside java

#### Disclaimer
```
private methods are not supported!
```

# How to use
As project currenlty not published to major maven repos please add:
```Groovy
repositories {
   maven { url "https://dl.bintray.com/wgr1984/SimpleDefaultMethods"}
}
```
and the following two dependecies:
```Groovy
annotationProcessor "de.wr.simpledefaultmethods:simpleDefaultMethodsProcessor:0.1"
provided "de.wr.simpledefaultmethods:libSimpleDefaultMethods:0.1"
```

Now you can simply use the ```@Default``` annotations in order
to have generated an java8-style interface enhancing the
class with missing default methods:

```Java
public class TestObject implements de.wr.simpledefaultmethods.TestObjectDefaults {

    @Default
    public void testMethod(int i, @DefaultBool(true) boolean b, String s, @DefaultChar('b') char charTest) {

    }

    public void testMethod2(int i, @DefaultBool(true) boolean b, @DefaultString("test") String s) {

    }
}
```
this will allow you to use methods without the need of passing
parameters annotated by default values

Note: Using ```@Default``` annotating the method itself will
use default values ```zero / null / array[0]``` for non annotated
parameters.