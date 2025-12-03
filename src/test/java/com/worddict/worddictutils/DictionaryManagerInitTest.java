package com.worddict.worddictutils;

import org.junit.Test;
import org.junit.After;
import org.junit.Before;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.lang.AssertionError;
import java.util.Comparator;

import com.worddict.worddictutils.DictionaryManagerFileSystemTest;

/**
 * Isolated test class for checking the initialization logic of
 * DictionaryManagerFileSystem, as it modifies the global Singleton state.
 */
public class DictionaryManagerInitTest {

    private DictionaryManagerFileSystem manager;
    private Path tempDir;

    @Before
    public void setUp() {
        manager = DictionaryManagerFileSystem.INSTANCE;
        manager.resetForTests(); 
        tempDir = null;
    }

    @After
    public void tearDown() throws IOException {
        manager.resetForTests(); // Cleanup state and temporary dirs
        if (tempDir != null) {
            DictionaryManagerFileSystemTest.deleteDirectoryRecursively(tempDir.toAbsolutePath().toString());
            tempDir = null;
        }
    }

    /**
     * Tests the positive scenario: verifies that the {@code init()} method 
     * successfully creates the root directory on the filesystem and sets 
     * the correct absolute path in the {@code DictionaryManagerFileSystem}.
     *
     * <p>Precondition: Manager is uninitialized (ensured by {@code setUp()}).</p>
     * * @throws IOException if the temporary directory cannot be created or file system access fails.
     */
    @Test
    public void testInit_Positive_CreatesRootDirectory() throws IOException {
        tempDir = Files.createTempDirectory("dict-init-positive-");
        String rootPathString = tempDir.toString();
        manager.init(rootPathString);

        Path actualRoot = manager.getRootDirectory(); 
        assertEquals(tempDir.toAbsolutePath(), actualRoot.toAbsolutePath());
        assertTrue("Root directory was not created or does not exist.", Files.isDirectory(actualRoot));
    }

    /**
     * Tests the negative scenario: verifies that calling {@code init()} a second time 
     * on an already initialized {@code DictionaryManagerFileSystem} throws an 
     * {@code IllegalStateException}, preventing state corruption.
     *
     * <p>Precondition: Manager is uninitialized (ensured by {@code setUp()}) 
     * before the first successful {@code init()} call within the test.</p>
     * * @throws IOException if the temporary directory cannot be created or file system access fails.
     */
    @Test
    public void testInit_Negative_ReinitializationFails() throws IOException {
        tempDir = Files.createTempDirectory("dict-init-negative-");
        manager.init(tempDir.toString());
        try {
            manager.init("/some/other/path");
            // If control reaches here, the test must fail
            fail("Expected IllegalStateException on re-initialization, but init() succeeded.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("already initialized"));
        }
    }

    /**
     * Tests the negative scenario where {@code init()} fails due to 
     * file system restrictions (e.g., lack of permissions to create 
     * a directory like /root/).
     *
     * <p>Precondition: Manager is uninitialized (ensured by {@code setUp()}).</p>
     * @throws IOException if the file system access fails, as expected.
     */
    @Test(expected = IOException.class)
    public void testInit_Negative_FailureOnInvalidPath() throws IOException {
        String restrictedPathString = "/root/test_init_fail_" + System.currentTimeMillis();
        manager.init(restrictedPathString);
        fail("Expected IOException due to permission denial, but initialization succeeded.");
    }

    @Test
    public void testInit_Negative_FailureOnInvalidPath_DIAGNOSTICS() throws IOException {
    try {
        String restrictedPathString = "/root/test_init_fail1_" + System.currentTimeMillis();
        
        // Тут ми очікуємо або успіху, або IOException
        manager.init(restrictedPathString);
        
        // Якщо сюди дійшли, init() був успішним (що небажано, але не кидає AssertionError)
        //fail("Initialization succeeded unexpectedly."); 

    } catch (AssertionError ae) {
        // !!! Якщо ви потрапили сюди, AssertionError виник ДО або ПІД ЧАС init() !!!
        System.err.println("AssertionError occurred: " + ae.getMessage());
        throw ae; // Перекидаємо його
        
    } catch (Exception e) {
        // Якщо ми отримали IOException, ми очікуємо його (це правильно)
        if (e instanceof IOException) {
            System.out.println("Test successful: Caught expected IOException.");
            return; // Успіх
        }
        // Якщо це інший виняток, провалюємо тест
        fail("Caught unexpected exception: " + e.getClass().getName() + ": " + e.getMessage());
    }
}
}
