package com.chrionline.security;

// Path traversal via filename parameter is mitigated architecturally:
// generateServerManaged() uses server-generated UUID filenames.
// No user input reaches the output path. See InvoicePdfGenerator.java.

import com.chrionline.ui.invoice.InvoicePdfGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// [SECURITY TEST]
class InvoicePdfGeneratorTest {

    private static final Path INVOICES_DIR = Paths.get("invoices").toAbsolutePath().normalize();

    @AfterEach
    void cleanup() throws Exception {
        if (Files.exists(INVOICES_DIR)) {
            try (Stream<Path> walk = Files.walk(INVOICES_DIR)) {
                walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (Exception ignored) {
                                // best-effort cleanup for tests
                            }
                        });
            }
        }
    }

    @Nested
    class GenerateServerManagedTests {
        @Test
        void generateServerManaged_createsPdfInInvoicesDirectory() throws Exception {
            File out = InvoicePdfGenerator.generateServerManaged(
                    "12345",
                    "Alice",
                    "alice@example.com",
                    LocalDateTime.now(),
                    List.of(new InvoicePdfGenerator.InvoiceLine("Item A", 1, 9.99)),
                    1.5,
                    11.49
            );
            assertTrue(out.exists());
            assertTrue(out.toPath().toAbsolutePath().normalize().startsWith(INVOICES_DIR));
        }

        @Test
        void generateServerManaged_usesUuidFilenamePattern() throws Exception {
            File out = InvoicePdfGenerator.generateServerManaged(
                    "12345", "Alice", "alice@example.com", LocalDateTime.now(), List.of(), 0.0, 0.0
            );
            String name = out.getName();
            assertTrue(name.matches("[0-9a-f\\-]{36}\\.pdf"));
            assertDoesNotThrow(() -> UUID.fromString(name.replace(".pdf", "")));
        }

        @Test
        void generateServerManaged_calledTwice_producesDifferentFilenames() throws Exception {
            File out1 = InvoicePdfGenerator.generateServerManaged(
                    "12345", "Alice", "alice@example.com", LocalDateTime.now(), List.of(), 0.0, 0.0
            );
            File out2 = InvoicePdfGenerator.generateServerManaged(
                    "12345", "Alice", "alice@example.com", LocalDateTime.now(), List.of(), 0.0, 0.0
            );
            assertNotEquals(out1.getName(), out2.getName());
        }

        @Test
        void generateServerManaged_createsParentDirectoryIfMissing() throws Exception {
            cleanup();
            File out = InvoicePdfGenerator.generateServerManaged(
                    "12345", "Alice", "alice@example.com", LocalDateTime.now(), List.of(), 0.0, 0.0
            );
            assertTrue(Files.exists(INVOICES_DIR));
            assertTrue(out.exists());
        }
    }
}
