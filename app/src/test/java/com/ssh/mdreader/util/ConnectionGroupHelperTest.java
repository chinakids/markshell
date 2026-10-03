package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ssh.mdreader.model.SshConfig;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * {@link ConnectionGroupHelper} 连接分组纯函数层单测：
 * 组名规范化/去重、按组分桶、重命名/删除组的连接变换、展示组名合并。
 */
public class ConnectionGroupHelperTest {

    private static SshConfig conn(String alias, String group) {
        SshConfig c = new SshConfig(alias, "h" + alias, 22, "u", "p", "/");
        c.setGroup(group);
        return c;
    }

    // ── 组名规范化 ───────────────────────────────────────────────────────────

    @Test
    public void normalizeGroupName_trimsAndTreatsBlankAsUngrouped() {
        assertEquals("", ConnectionGroupHelper.normalizeGroupName(null));
        assertEquals("", ConnectionGroupHelper.normalizeGroupName(""));
        assertEquals("", ConnectionGroupHelper.normalizeGroupName("   "));
        assertEquals("生产", ConnectionGroupHelper.normalizeGroupName("  生产  "));
        // 组名内部空白保留（「生产 环境」是合法组名）
        assertEquals("生产 环境", ConnectionGroupHelper.normalizeGroupName(" 生产 环境 "));
    }

    @Test
    public void groupOf_and_isUngrouped() {
        assertTrue(ConnectionGroupHelper.isUngrouped(conn("a", null)));
        assertTrue(ConnectionGroupHelper.isUngrouped(conn("a", "  ")));
        assertFalse(ConnectionGroupHelper.isUngrouped(conn("a", "生产")));
        assertEquals("生产", ConnectionGroupHelper.groupOf(conn("a", " 生产 ")));
        // null config 视为未分组（防御）
        assertEquals("", ConnectionGroupHelper.groupOf(null));
        assertTrue(ConnectionGroupHelper.isUngrouped(null));
    }

    // ── 去重 ─────────────────────────────────────────────────────────────────

    @Test
    public void dedupeGroupNames_dedupesKeepingFirstOrder() {
        List<String> result = ConnectionGroupHelper.dedupeGroupNames(
                Arrays.asList(" 生产 ", "测试", "生产", " ", "测试", null));
        assertEquals(Arrays.asList("生产", "测试"), result);
    }

    @Test
    public void dedupeGroupNames_emptyAndNullInputs() {
        assertEquals(Collections.emptyList(), ConnectionGroupHelper.dedupeGroupNames(null));
        assertEquals(Collections.emptyList(), ConnectionGroupHelper.dedupeGroupNames(
                Arrays.asList("", "  ")));
    }

    // ── 分桶 ─────────────────────────────────────────────────────────────────

    @Test
    public void bucketByGroup_groupsByNormalizedGroupKeepingOrder() {
        List<SshConfig> list = Arrays.asList(
                conn("a", "生产"),
                conn("b", null),
                conn("c", " 生产 "),
                conn("d", "测试"));
        Map<String, List<SshConfig>> buckets = ConnectionGroupHelper.bucketByGroup(list);
        assertEquals(3, buckets.size());
        assertEquals(2, buckets.get("生产").size());
        assertSame(list.get(0), buckets.get("生产").get(0));
        assertSame(list.get(2), buckets.get("生产").get(1));
        assertEquals(1, buckets.get("测试").size());
        // 未分组桶 key 为空串
        assertEquals(1, buckets.get(ConnectionGroupHelper.UNGROUPED).size());
        assertSame(list.get(1), buckets.get(ConnectionGroupHelper.UNGROUPED).get(0));
    }

    @Test
    public void bucketByGroup_nullOrEmpty_returnsEmptyBuckets() {
        assertTrue(ConnectionGroupHelper.bucketByGroup(null).isEmpty());
        assertTrue(ConnectionGroupHelper.bucketByGroup(Collections.emptyList()).isEmpty());
        // 只有连接才建桶：不存在「空组」桶
        Map<String, List<SshConfig>> buckets = ConnectionGroupHelper.bucketByGroup(
                Collections.singletonList(conn("a", "生产")));
        assertFalse(buckets.containsKey(""));
        assertEquals(1, buckets.size());
    }

    // ── 重命名/删除变换 ──────────────────────────────────────────────────────

    @Test
    public void renameGroupInList_renamesMatchingAndKeepsOthers() {
        List<SshConfig> list = Arrays.asList(conn("a", "生产"), conn("b", "测试"), conn("c", null));
        List<SshConfig> result = ConnectionGroupHelper.renameGroupInList(list, "生产", "生产环境");
        assertEquals("生产环境", result.get(0).getGroup());
        assertEquals("测试", result.get(1).getGroup());
        assertNull(result.get(2).getGroup());
        // 不修改入参
        assertEquals("生产", list.get(0).getGroup());
    }

    @Test
    public void renameGroupInList_targetBlank_ungrouped() {
        List<SshConfig> result = ConnectionGroupHelper.renameGroupInList(
                Collections.singletonList(conn("a", "生产")), "生产", " ");
        assertNull(result.get(0).getGroup());
    }

    @Test
    public void renameGroupInList_sameOrBlankOld_isNoopCopy() {
        List<SshConfig> list = Collections.singletonList(conn("a", "生产"));
        List<SshConfig> same = ConnectionGroupHelper.renameGroupInList(list, "生产", "生产");
        assertEquals(1, same.size());
        assertSame(list.get(0), same.get(0));
        // old 为空（未分组区）不做变换
        List<SshConfig> oldBlank = ConnectionGroupHelper.renameGroupInList(list, "", "新组");
        assertSame(list.get(0), oldBlank.get(0));
        // null 入参
        assertNotNull(ConnectionGroupHelper.renameGroupInList(null, "生产", "新组"));
        assertTrue(ConnectionGroupHelper.renameGroupInList(null, "生产", "新组").isEmpty());
    }

    @Test
    public void ungroupAllInList_movesMatchingToUngrouped() {
        List<SshConfig> list = Arrays.asList(conn("a", "生产"), conn("b", "测试"), conn("c", "生产"));
        List<SshConfig> result = ConnectionGroupHelper.ungroupAllInList(list, "生产");
        assertNull(result.get(0).getGroup());
        assertEquals("测试", result.get(1).getGroup());
        assertNull(result.get(2).getGroup());
        // 不修改入参
        assertEquals("生产", list.get(0).getGroup());
    }

    @Test
    public void ungroupAllInList_blankOrNoneMatch_isNoopCopy() {
        List<SshConfig> list = Collections.singletonList(conn("a", "生产"));
        List<SshConfig> blank = ConnectionGroupHelper.ungroupAllInList(list, " ");
        assertSame(list.get(0), blank.get(0));
        List<SshConfig> none = ConnectionGroupHelper.ungroupAllInList(list, "不存在");
        assertSame(list.get(0), none.get(0));
    }

    // ── 展示组名合并 ──────────────────────────────────────────────────────────

    @Test
    public void mergeGroupNames_metadataFirst_thenDerived() {
        List<String> result = ConnectionGroupHelper.mergeGroupNames(
                Arrays.asList("生产", "测试", "备份"),  // 备份=空组（元数据里有，连接没有）
                Arrays.asList(conn("a", "生产"), conn("b", "生产环境")));
        assertEquals(Arrays.asList("生产", "测试", "备份", "生产环境"), result);
    }

    @Test
    public void mergeGroupNames_dedupesAndFiltersBlank() {
        List<String> result = ConnectionGroupHelper.mergeGroupNames(
                Arrays.asList(" 生产 ", "生产", "", null),
                Collections.singletonList(conn("a", "测试")));
        assertEquals(Arrays.asList("生产", "测试"), result);
        assertTrue(ConnectionGroupHelper.mergeGroupNames(null, null).isEmpty());
    }
}
