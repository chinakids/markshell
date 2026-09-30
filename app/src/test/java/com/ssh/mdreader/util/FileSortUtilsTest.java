package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import com.ssh.mdreader.model.RemoteFile;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** FileSortUtils 纯函数排序单测：目录恒在前、三种模式、不改动入参。 */
public class FileSortUtilsTest {

    private static RemoteFile dir(String name, long mtime) {
        return new RemoteFile(name, "/" + name, true, 0, 493, mtime);
    }

    private static RemoteFile file(String name, long size, long mtime) {
        return new RemoteFile(name, "/" + name, false, size, 420, mtime);
    }

    private static List<String> names(List<RemoteFile> files) {
        List<String> out = new ArrayList<>();
        for (RemoteFile f : files) out.add(f.getName());
        return out;
    }

    @Test
    public void dirsAlwaysBeforeFilesInAllModes() {
        List<RemoteFile> input = Arrays.asList(
                file("zeta.txt", 10, 5),
                dir("aaa", 1),
                dir("zzz", 2));
        for (int mode : new int[]{FileSortUtils.SORT_NAME, FileSortUtils.SORT_MTIME, FileSortUtils.SORT_SIZE}) {
            List<RemoteFile> sorted = FileSortUtils.sort(input, mode);
            assertEquals("mode=" + mode, 2, countDirs(sorted.subList(0, 2)));
            assertTrue("mode=" + mode, sorted.get(2).getName().equals("zeta.txt"));
        }
    }

    private static int countDirs(List<RemoteFile> files) {
        int n = 0;
        for (RemoteFile f : files) if (f.isDirectory()) n++;
        return n;
    }

    @Test
    public void nameModeSortsCaseInsensitiveAscending() {
        List<RemoteFile> input = Arrays.asList(
                file("b.txt", 10, 1),
                file("A.txt", 10, 1),
                file("c.txt", 10, 1));
        List<RemoteFile> sorted = FileSortUtils.sort(input, FileSortUtils.SORT_NAME);
        assertEquals(Arrays.asList("A.txt", "b.txt", "c.txt"), names(sorted));
    }

    @Test
    public void mtimeModeSortsNewestFirstThenName() {
        List<RemoteFile> input = Arrays.asList(
                file("old.txt", 10, 100),
                file("new.txt", 10, 300),
                file("mid.txt", 10, 200));
        List<RemoteFile> sorted = FileSortUtils.sort(input, FileSortUtils.SORT_MTIME);
        assertEquals(Arrays.asList("new.txt", "mid.txt", "old.txt"), names(sorted));

        // 相同 mtime 回退名称序（保证确定性）
        List<RemoteFile> tied = Arrays.asList(
                file("b.txt", 10, 200),
                file("a.txt", 10, 200));
        assertEquals(Arrays.asList("a.txt", "b.txt"), names(FileSortUtils.sort(tied, FileSortUtils.SORT_MTIME)));
    }

    @Test
    public void sizeModeSortsLargestFirstThenName() {
        List<RemoteFile> input = Arrays.asList(
                file("small.md", 10, 1),
                file("big.md", 5000, 1),
                file("mid.md", 100, 1));
        List<RemoteFile> sorted = FileSortUtils.sort(input, FileSortUtils.SORT_SIZE);
        assertEquals(Arrays.asList("big.md", "mid.md", "small.md"), names(sorted));

        List<RemoteFile> tied = Arrays.asList(
                file("b.md", 100, 1),
                file("a.md", 100, 1));
        assertEquals(Arrays.asList("a.md", "b.md"), names(FileSortUtils.sort(tied, FileSortUtils.SORT_SIZE)));
    }

    @Test
    public void invalidModeFallsBackToName() {
        List<RemoteFile> input = Arrays.asList(
                file("b.txt", 10, 1),
                file("a.txt", 10, 1));
        List<RemoteFile> sorted = FileSortUtils.sort(input, 99);
        assertEquals(Arrays.asList("a.txt", "b.txt"), names(sorted));
    }

    @Test
    public void sortDoesNotMutateInputList() {
        List<RemoteFile> input = new ArrayList<>(Arrays.asList(
                file("b.txt", 10, 1),
                file("a.txt", 10, 1)));
        List<RemoteFile> originalOrder = new ArrayList<>(input);
        List<RemoteFile> sorted = FileSortUtils.sort(input, FileSortUtils.SORT_NAME);
        assertNotSame(input, sorted);
        assertEquals(originalOrder, input);
    }

    @Test
    public void emptyAndSingleElementAreSafe() {
        assertTrue(FileSortUtils.sort(new ArrayList<>(), FileSortUtils.SORT_MTIME).isEmpty());
        List<RemoteFile> one = Arrays.asList(file("only.md", 10, 1));
        assertEquals(Arrays.asList("only.md"), names(FileSortUtils.sort(one, FileSortUtils.SORT_SIZE)));
    }
}
