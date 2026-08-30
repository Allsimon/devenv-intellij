package com.allsimon.intellij.processes;

import com.allsimon.intellij.core.DevenvProjectTestCase;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The bookkeeping the Services tool window rests on: one process manager per devenv.nix of the
 * project, kept across refreshes so that consoles and polling survive, and let go of with the root.
 * <p>
 * Nothing here runs devenv: a manager only spawns anything once something asks it for a snapshot.
 */
public class DevenvProcessRootsTest extends DevenvProjectTestCase {

    public void testOneManagerPerRoot() {
        VirtualFile first = moduleWithDevenvNix("first");
        VirtualFile second = moduleWithDevenvNix("second");

        List<DevenvProcessManager> managers = DevenvProcessRoots.getInstance(getProject()).managers();

        assertEquals(List.of(first, second), managers.stream().map(DevenvProcessManager::getRoot).toList());
    }

    public void testManagersSurviveALookup() {
        moduleWithDevenvNix("first");
        DevenvProcessRoots roots = DevenvProcessRoots.getInstance(getProject());

        assertSame(roots.managers().get(0), roots.managers().get(0));
    }

    public void testAManagerIsDisposedWithItsRoot() {
        VirtualFile root = moduleWithDevenvNix("first");
        DevenvProcessRoots roots = DevenvProcessRoots.getInstance(getProject());
        AtomicBoolean disposed = new AtomicBoolean();
        Disposer.register(roots.managers().get(0), () -> disposed.set(true));

        delete(root.findChild("devenv.nix"));

        assertEmpty(roots.managers());
        // The manager holds UI, so it is let go of on the EDT rather than under the roots' lock.
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        assertTrue(disposed.get());
    }
}
