package com.github.stefvanschie.quickskript.parsing;

import com.github.stefvanschie.quickskript.TestClassBase;
import com.github.stefvanschie.quickskript.psi.exception.ParseException;
import com.github.stefvanschie.quickskript.skript.Skript;
import org.junit.jupiter.api.Test;

/**
 * A test which asserts that all specified skript files
 * can be parsed without any exceptions being raised.
 */
class SkriptFileParseTest extends TestClassBase {

    @Test
    void test() {
        for (Skript skript : getSampleSkripts()) {
            try {
                skript.registerCommands();
                skript.registerEventExecutors();
                System.out.println("Successfully parsed: " + skript.getName());
            } catch (ParseException e) {
                throw new AssertionError("Error while parsing:" + e.getExtraInfo(skript.getName()), e);
            }
        }
    }
}
