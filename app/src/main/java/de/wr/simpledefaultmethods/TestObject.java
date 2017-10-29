package de.wr.simpledefaultmethods;

import de.wr.libsimpledefaultmethods.Default;
import de.wr.libsimpledefaultmethods.DefaultBool;
import de.wr.libsimpledefaultmethods.DefaultInt;
import de.wr.simpledefaultmethods.TestObjectDefaults;

/**
 * Created by wolfgangreithmeier on 29.10.17.
 */

public class TestObject implements TestObjectDefaults {

    @Default
    void testMethod(int i, @DefaultBool(true) boolean b, String s) {

    }

    void testMethod2(@DefaultInt(3) int i, boolean b, String s) {

    }

}
