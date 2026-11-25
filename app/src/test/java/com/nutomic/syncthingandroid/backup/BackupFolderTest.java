package com.nutomic.syncthingandroid.backup;

import static com.google.common.truth.Truth.assertThat;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.List;
import java.util.TimeZone;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 37)
public class BackupFolderTest {

    /** 2025-07-01T10:00:00Z, summer time in Europe/Berlin. */
    private static final long SUMMER = 1751364000000L;
    private static final long MINUTE = 60 * 1000L;
    private static final long HOUR = 60 * MINUTE;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File folder;
    private TimeZone savedZone;

    @Before
    public void setUp() throws Exception {
        folder = tmp.newFolder("auto");
        savedZone = TimeZone.getDefault();
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(savedZone);
    }

    static File create(File file) throws Exception {
        assertThat(file.createNewFile()).isTrue();
        return file;
    }

    // SPEC:auto-config-backup:R12
    @Test
    public void stamp_fixedClock() {
        assertThat(BackupFolder.stamp(1763900000000L)).isEqualTo("20251123T121320Z");
    }

    // SPEC:auto-config-backup:R12
    @Test
    public void nextFile_sameSecond_getsSuffixAndOrdersAfter() throws Exception {
        File first = create(BackupFolder.nextFile(folder, SUMMER));
        File second = BackupFolder.nextFile(folder, SUMMER);
        assertThat(second.getName()).isEqualTo("config-auto-20250701T100000Z-2.zip");
        create(second);

        assertThat(BackupFolder.list(folder)).containsExactly(second, first).inOrder();
    }

    // SPEC:auto-config-backup:R12
    @Test
    public void list_localClockBackAnHour_newestStillFirst() throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
        File older = create(BackupFolder.nextFile(folder, SUMMER));
        TimeZone.setDefault(TimeZone.getTimeZone("Etc/GMT-1"));
        File newer = create(BackupFolder.nextFile(folder, SUMMER + 30 * MINUTE));

        assertThat(BackupFolder.list(folder).get(0)).isEqualTo(newer);
        for (int i = 1; i <= 5; i++) {
            create(new File(folder, "config-auto-2025010" + i + "T000000Z.zip"));
        }
        assertThat(BackupFolder.prune(folder, SUMMER + HOUR)).isTrue();
        assertThat(newer.exists()).isTrue();
        assertThat(older.exists()).isTrue();
        assertThat(BackupFolder.list(folder)).hasSize(BackupFolder.KEEP);
    }

    // SPEC:auto-config-backup:R12
    @Test
    public void nameTime_clockBehindNewestBackup_usesNewestStamp() throws Exception {
        assertThat(BackupFolder.nameTime(folder, SUMMER)).isEqualTo(SUMMER);
        File existing = create(BackupFolder.nextFile(folder, SUMMER));

        long nameTime = BackupFolder.nameTime(folder, SUMMER - HOUR);
        assertThat(nameTime).isEqualTo(SUMMER);
        File next = create(BackupFolder.nextFile(folder, nameTime));
        assertThat(BackupFolder.list(folder)).containsExactly(next, existing).inOrder();

        assertThat(BackupFolder.nameTime(folder, SUMMER + HOUR)).isEqualTo(SUMMER + HOUR);
    }

    // SPEC:auto-config-backup:R11
    @Test
    public void prune_keepsNewestFive_andOnlyItsOwnFiles() throws Exception {
        long now = SUMMER + 10 * HOUR;
        File[] backups = new File[6];
        for (int i = 0; i < 6; i++) {
            backups[i] = create(BackupFolder.nextFile(folder, SUMMER + i * MINUTE));
        }
        File oldTmp = create(new File(folder, "config-auto-20250601T000000Z.zip.tmp"));
        assertThat(oldTmp.setLastModified(now - 25 * HOUR)).isTrue();
        File freshTmp = create(new File(folder, "config-auto-20250701T190000Z.zip.tmp"));
        assertThat(freshTmp.setLastModified(now - MINUTE)).isTrue();
        File manual = create(new File(folder, "config.zip"));
        File notes = create(new File(folder, "notes.txt"));
        File bad = create(new File(folder, "config-auto-bad.zip"));

        assertThat(BackupFolder.prune(folder, now)).isTrue();

        assertThat(backups[0].exists()).isFalse();
        for (int i = 1; i < 6; i++) {
            assertThat(backups[i].exists()).isTrue();
        }
        assertThat(oldTmp.exists()).isFalse();
        assertThat(freshTmp.exists()).isTrue();
        assertThat(manual.exists()).isTrue();
        assertThat(notes.exists()).isTrue();
        assertThat(bad.exists()).isTrue();
        List<File> listed = BackupFolder.list(folder);
        assertThat(listed).containsExactly(backups[5], backups[4], backups[3], backups[2], backups[1])
                .inOrder();
    }

    @Test
    public void list_missingFolder_isEmpty() {
        assertThat(BackupFolder.list(new File(folder, "missing"))).isEmpty();
    }
}
