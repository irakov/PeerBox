package org.peerbox.watchservice.filetree;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.peerbox.watchservice.FileLeaf;
import org.peerbox.watchservice.FolderComposite;
import org.peerbox.watchservice.PathUtils;


public class FileLeafTest {
	
	private static Path basePath;
	private FileLeaf leaf;
	private Path file;
	private String fileName;
	private String content;
	
	// structure: rootBase -> parentA -> file
	private FolderComposite rootBase;
	private FolderComposite parentA;
	private FolderComposite parentB;
	
	private static Path folderA;
	private static Path folderB;
	
	private static final boolean cleanupFolder = true;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		basePath = Paths.get("PeerBox_Test_Tree");
		if(!Files.exists(basePath)) {
			Files.createDirectory(basePath);
		}
		
		folderA = basePath.resolve("folderA");
		if(!Files.exists(folderA)) {
			Files.createDirectory(folderA);
		}
		
		folderB = basePath.resolve("folderB");
		if(!Files.exists(folderB)) {
			Files.createDirectory(folderB);
		}
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		if(cleanupFolder) {
			FileUtils.deleteDirectory(basePath.toFile());
		}
	}

	@Before
	public void setUp() throws Exception {
		
		// create file with content in folderA
		fileName = "file1.txt";
		file = folderA.resolve(fileName);
		content = RandomStringUtils.randomAlphanumeric(1000);
		Files.write(file, content.getBytes());
		leaf = new FileLeaf(file, true);
		
		
		rootBase = new FolderComposite(basePath, true, true); // root
		parentA = new FolderComposite(folderA, true, false); // not root
		parentB = new FolderComposite(folderB, true, false); // not root
		
		rootBase.putComponent(parentA.getPath().toString(), parentA);
		rootBase.putComponent(parentB.getPath().toString(), parentB);
		rootBase.putComponent(leaf.getPath().toString(), leaf);
		
		assertNull(rootBase.getParent());
		assertEquals(parentA.getParent(), rootBase);
		assertEquals(parentB.getParent(), rootBase);
		assertEquals(leaf.getParent(), parentA);
		assertNotEquals(leaf.getParent(), parentB);
	}

	@After
	public void tearDown() throws Exception {
		leaf = null;
		content = null;
		file = null;
	}

	@Test
	public void testFileLeafCtr() throws IOException {
		String hash = PathUtils.computeFileContentHash(file);
		assertNotNull(hash);
		assertFalse(hash.isEmpty());
		
		FileLeaf leafA = new FileLeaf(file, true);
		assertEquals(hash, leafA.getContentHash());
		
		FileLeaf leafB = new FileLeaf(file, false);
		assertEquals("", leafB.getContentHash());
	}

	@Test
	public void testIsFile() {
		assertTrue(leaf.isFile());
	}

	@Test
	public void testIsFolder() {
		assertFalse(leaf.isFolder());
	}

	@Test
	public void testGetAndSetPath() {
		assertNotNull(leaf.getPath());
		assertEquals(file, leaf.getPath());
		
		// "move" file to folderB by setting the path
		Path fileB = folderB.resolve(file.getFileName());
		leaf.setPath(fileB);
		assertEquals(fileB, leaf.getPath());
		
		// "rename" file
		Path newName = fileB.resolveSibling("newFileName.txt");
		leaf.setPath(newName);
		assertEquals(newName, leaf.getPath());
	}

	@Test
	public void testGetAction() {
		assertNotNull(leaf.getAction());
	}

	@Test
	public void testGetAndSetActionIsUploaded() {
		boolean isUploaded = leaf.getActionIsUploaded(); // initial expetced false
		assertFalse(leaf.getActionIsUploaded());
		assertEquals(leaf.getActionIsUploaded(), leaf.getAction().getIsUploaded());
		
		isUploaded = !isUploaded; // toggle and test
		leaf.setActionIsUploaded(isUploaded);
		assertTrue(leaf.getActionIsUploaded());
		assertEquals(leaf.getActionIsUploaded(), leaf.getAction().getIsUploaded());
		
		isUploaded = !isUploaded; // toggle and test
		leaf.setActionIsUploaded(isUploaded);
		assertFalse(leaf.getActionIsUploaded());
		assertEquals(leaf.getActionIsUploaded(), leaf.getAction().getIsUploaded());
		
		// use action to set it
		isUploaded = !isUploaded; // toggle and test
		leaf.getAction().setIsUploaded(isUploaded);
		assertEquals(isUploaded, leaf.getActionIsUploaded());
		assertEquals(leaf.getActionIsUploaded(), leaf.getAction().getIsUploaded());
		
		isUploaded = !isUploaded; // toggle and test
		leaf.getAction().setIsUploaded(isUploaded);
		assertEquals(isUploaded, leaf.getActionIsUploaded());
		assertEquals(leaf.getActionIsUploaded(), leaf.getAction().getIsUploaded());
	}
	
	@Test
	public void testGetAndUpdateContentHash() throws IOException {
		String hash = PathUtils.computeFileContentHash(file);
		assertEquals(hash, leaf.getContentHash());
		
		for(int i = 0; i < 10; ++i) {
			// update file and test
			String s = RandomStringUtils.randomAlphanumeric(1000);
			Files.write(file, s.getBytes());
			hash = PathUtils.computeFileContentHash(file);
			boolean changed = leaf.updateContentHash();
			assertTrue(changed);
			assertEquals(hash, leaf.getContentHash());
		}
		
		// always same content and test
		String s = RandomStringUtils.randomAlphanumeric(1000);
		Files.write(file, s.getBytes());
		hash = PathUtils.computeFileContentHash(file);
		assertTrue(leaf.updateContentHash());
		for(int i = 0; i < 10; ++i) {
			Files.write(file, s.getBytes());
			boolean changed = leaf.updateContentHash();
			assertFalse(changed);
			assertEquals(hash, leaf.getContentHash());
		}
	}
	
	@Test
	public void testBubbleContentHashUpdate() throws IOException {
		String rootHash = rootBase.getContentHash();
		assertNotNull(rootHash);
		assertFalse(rootHash.isEmpty());
		
		String parentHash = parentA.getContentHash();
		assertNotNull(parentHash);
		assertFalse(parentHash.isEmpty());
		
		String s = RandomStringUtils.randomAlphanumeric(1000);
		Files.write(file, s.getBytes());
		leaf.updateContentHash();
		
		assertNotEquals(rootHash, rootBase.getContentHash());
		assertNotEquals(parentHash, parentA.getContentHash());
	}

	@Test
	public void testSetParentPath() {
		assertEquals(leaf.getPath(), file);
		assertEquals(leaf.getPath().getFileName().toString(), fileName);
		assertTrue(leaf.getPath().toString().endsWith(fileName));
		assertTrue(leaf.getPath().toString().startsWith(folderA.toString()));
		
		leaf.setParentPath(folderB);
		
		Path fileB = folderB.resolve(fileName);
		assertEquals(leaf.getPath(), fileB);
		assertEquals(leaf.getPath().getFileName().toString(), fileName);
		assertTrue(leaf.getPath().toString().endsWith(fileName));
		assertTrue(leaf.getPath().toString().startsWith(folderB.toString()));
	}

	@Test
	public void testGetAndSetParent() {
		assertEquals(leaf.getParent(), parentA);
		
		leaf.setParent(parentB);
		
		assertEquals(leaf.getParent(), parentB);
	}

	@Test
	public void testIsReady() {
		// root is always "ready"
		assertTrue(rootBase.isReady());
		// because root is ready, parent should also be ready
		assertTrue(parentA.isReady());
		// parent is not yet uploaded -> file should not be ready
		assertFalse(leaf.isReady());
		
		// parent uploaded -> leaf is ready now
		parentA.setActionIsUploaded(true);
		assertTrue(leaf.isReady());
		
		// parent uploaded -> leaf is ready now
		parentA.setActionIsUploaded(false);
		assertFalse(leaf.isReady());
	}

	@Test
	public void testGetAndSetStructureHash() {
		assertNotNull(leaf.getStructureHash());
		assertTrue(leaf.getStructureHash().isEmpty());
		
		leaf.setStructureHash("abc");
		assertNotNull(leaf.getStructureHash());
		assertEquals("abc", leaf.getStructureHash());
	}

	@Test
	public void testGetComponent() {
		assertNull(leaf.getComponent("aComponent"));
	}

	@Test
	public void testPutComponent() {
		try {
			leaf.putComponent(null, null);
		} catch(Exception e) {
			fail("Exception occurred but expected a no op method call.");
		}
	}

	@Test
	public void testDeleteComponent() {
		assertNull(leaf.deleteComponent("aComponent"));
	}

}
