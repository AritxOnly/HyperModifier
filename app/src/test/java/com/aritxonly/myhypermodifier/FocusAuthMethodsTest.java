package com.aritxonly.myhypermodifier;
import org.junit.Test;
import static org.junit.Assert.*;

public class FocusAuthMethodsTest {
    static class Error {}
    static class Result {}
    static class Supported {
        final Result a(Error error) { return new Result(); }
        final Result b() { return new Result(); }
        final Result other(String unrelated) { return new Result(); }
        static final Result global() { return new Result(); }
    }
    static class Ambiguous {
        final Result a(Error error) { return new Result(); }
        final Result b() { return new Result(); }
        final Result c() { return new Result(); }
    }
    static class Unrelated {
        final Result a(String error) { return new Result(); }
        final Result b() { return new Result(); }
    }
    @Test public void selectsExactErrorAndSuccessDespiteObfuscation() throws Exception {
        FocusAuthMethods methods = FocusAuthMethods.find(Supported.class, Error.class, Result.class);
        assertEquals("a", methods.failure.getName());
        assertEquals("b", methods.success.getName());
        assertTrue(methods.success.invoke(new Supported()) instanceof Result);
    }
    @Test(expected = NoSuchMethodException.class) public void ambiguousShapeDisablesHook() throws Exception {
        FocusAuthMethods.find(Ambiguous.class, Error.class, Result.class);
    }
    @Test(expected = NoSuchMethodException.class) public void unrelatedOneArgumentMethodIsNotAuthFailure() throws Exception {
        FocusAuthMethods.find(Unrelated.class, Error.class, Result.class);
    }
}
