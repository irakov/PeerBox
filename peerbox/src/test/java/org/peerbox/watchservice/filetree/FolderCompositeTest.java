package org.peerbox.watchservice.filetree;

import static org.junit.Assert.*;

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

public class FolderCompositeTest {
	
	private static Path basePath;
	private FolderComposite rootBase;
	
	private Path folderPathA;
	private Path folderPathB;
	private FolderComposite folderA;
	private FolderComposite folderB;
	
	private Path filePathA1;
	private FileLeaf fileA1;
	
	private static Path filePathB1;
	private FileLeaf fileB1;
	
	private static final boolean cleanupFolder = true;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		basePath = Paths.get(FileUtils.getTempDirectoryPath(), "PeerBox_Test_Tree");
		if(!Files.exists(basePath)) {
			Files.createDirectory(basePath);
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
		rootBase = new FolderComposite(basePath, true, true);
		
		folderPathA = basePath.resolve("folderA");
		if(!Files.exists(folderPathA)) {
			Files.createDirectory(folderPathA);
		}
		folderA = createFolderComposite(folderPathA);
		
		folderPathB = basePath.resolve("folderB");
		if(!Files.exists(folderPathB)) {
			Files.createDirectory(folderPathB);
		}
		folderB = createFolderComposite(folderPathB);
		
		filePathA1 = folderPathA.resolve("fileA1.txt");
		fileA1 = createFileLeaf(filePathA1);
		
		filePathB1 = folderPathB.resolve("fileB1.txt");
		fileB1 = createFileLeaf(filePathB1);
	}
	
	private FolderComposite createFolderComposite(Path p) {
		int prevChildrenSize = rootBase.getChildren().size();
		FolderComposite fc = new FolderComposite(p, true);
//		rootBase.putComponent(p.toString(), fc);
//		assertTrue(rootBase.getChildren().containsKey(p.getFileName().toString()));
//		assertTrue(rootBase.getChildren().containsValue(fc));
//		assertEquals(fc, rootBase.getChildren().get(p.getFileName().toString()));
//		assertEquals(prevChildrenSize + 1, rootBase.getChildren().size());
		assertTrue(fc.getChildren().isEmpty());
		return fc;
	}
	
	private FileLeaf createFileLeaf(Path p) throws IOException {
		String content = RandomStringUtils.randomAlphanumeric(1000);
		Files.write(p, content.getBytes());
		FileLeaf fl = new FileLeaf(p, true);
		return fl;
	}

	@After
	public void tearDown() throws Exception {
		FileUtils.cleanDirectory(basePath.toFile());
	}

	@Test
	public void testIsFile() {
		assertFalse(rootBase.isFile());
		assertFalse(folderA.isFile());
		assertFalse(folderB.isFile());
	}

	@Test
	public void testIsFolder() {
		assertTrue(rootBase.isFolder());
		assertTrue(folderA.isFolder());
		assertTrue(folderB.isFolder());
	}

	@Test
	public void testSetActionIsUploaded() {
		fail("Not yet implemented");
	}

	@Test
	public void testUpdateContentHash() {
		fail("Not yet implemented");
	}

	@Test
	public void testGetComponent() {
		fail("Not yet implemented");
	}

	@Test
	public void testPutComponent() {
		fail("Not yet implemented");
	}

	@Test
	public void testDeleteComponent() {
		fail("Not yet implemented");
	}
	
	@Test 
	public void testFolderCompositeCtr() {
		fail("Not yet implemented");
	}
	
	@Test
	public void testIsRoot() {
		Path p = Paths.get("abc", "def");
		FolderComposite fc = new FolderComposite(p, true);

		assertEquals(fc.getPath(), p);
		assertFalse(fc.isRoot());
		
		FolderComposite fcRoot = new FolderComposite(p, true, true);
		assertEquals(fcRoot.getPath(), p);
		assertTrue(fcRoot.isRoot());
		
		FolderComposite fcNotRoot = new FolderComposite(p, true, false);
		assertEquals(fcNotRoot.getPath(), p);
		assertFalse(fcNotRoot.isRoot());
	}

	@Test
	public void testGetChildren() {
		fail("Not yet implemented");
	}

	@Test
	public void testIsReady() {
		fail("Not yet implemented");
	}

	@Test
	public void testAbstractFileComponent() {
		fail("Not yet implemented");
	}

	@Test
	public void testGetAction() {
		assertNotNull(folderA.getAction());
	}

	@Test
	public void testGetActionIsUploaded() {
		fail("Not yet implemented");
	}

	@Test
	public void testGetAndSetPath() {
		fail("Not yet implemented");
	}

	@Test
	public void testSetParentPath() {
		fail("Not yet implemented");
	}

	@Test
	public void testGetAndSetParent() {
		fail("Not yet implemented");
	}

	@Test
	public void testGetContentHash() {
		fail("Not yet implemented");
	}

	@Test
	public void testBubbleContentHashUpdate() {
		fail("Not yet implemented");
	}

	@Test
	public void testGetAndSetStructureHash() {
		fail("Not yet implemented");
	}
}
