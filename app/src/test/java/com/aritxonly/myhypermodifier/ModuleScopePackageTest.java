package com.aritxonly.myhypermodifier;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import static org.junit.Assert.*;

public class ModuleScopePackageTest {
    @Test public void modernSystemServerScopeMatchesPackagedDeclaration() throws Exception {
        assertEquals("system", ModuleScopePackage.SYSTEM_FRAMEWORK);
        try (InputStream input = getClass().getResourceAsStream("/META-INF/xposed/scope.list")) {
            assertNotNull("Packaged Modern Xposed scope list", input);
            Set<String> declared = Arrays.stream(new String(input.readAllBytes(),
                    StandardCharsets.UTF_8).split("\\R"))
                    .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
            assertEquals(ModuleScopePackage.PACKAGES, declared);
            assertFalse("Deadliner is no longer a declared target", declared.contains("com.aritxonly.deadliner"));
            assertFalse("android targets system dialogs, not system_server", declared.contains("android"));
        }
    }
}
