package de.wr.simpledefaultmethods;

import java.util.Arrays;
import java.util.List;

import de.wr.libsimpledefaultmethods.Default;
import de.wr.libsimpledefaultmethods.DefaultBool;
import de.wr.libsimpledefaultmethods.DefaultChar;
import de.wr.libsimpledefaultmethods.DefaultFloat;
import de.wr.libsimpledefaultmethods.DefaultInt;
import de.wr.libsimpledefaultmethods.DefaultString;

/**
 * Created by wolfgangreithmeier on 29.10.17.
 */

public class TestObject implements de.wr.simpledefaultmethods.TestObjectDefaults {

    @Default
    public void testMethod(int i, @DefaultBool(true) boolean b, String s, @DefaultChar('b') char charTest) {

    }

    public void testMethod2(int i, @DefaultBool(true) boolean b, @DefaultString("test") String s) {

    }

    public void testMethod3(@DefaultFloat(7.0f) float f) {

    }

    public void testMethod4(@DefaultInt(9) int i, @DefaultBool(true) boolean b, @DefaultString("test") String s) {

    }

    @Default
    public void testMethod5(@DefaultInt(8) int i, char c, @DefaultBool(true) boolean b, String s, @DefaultChar('b') char charTest) {

    }

    public String testMethod6(int i, char c, @DefaultBool(true) boolean b, @DefaultChar('b') char charTest) {
        return i + "," + c + "," + b + "," + charTest;
    }

    public List<List<TestObject>> testMethod7(int i, @Default List<TestObject> ... test) {
        return Arrays.asList(test);
    }

    public void testMethod8(int i, @DefaultFloat(7.0f) float f) {

    }

    public void testMethod9(int i, @Default Object o) {
    }
}
