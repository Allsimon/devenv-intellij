package com.allsimon.intellij.nixlang;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * A light code insight fixture for JUnit 5 tests, set up and torn down around each test on the EDT. Its files are made
 * in the light project, where the daemon highlights them, as {@code BasePlatformTestCase}'s are.
 */
public final class NixFixture implements BeforeEachCallback, AfterEachCallback {
    private final boolean onDisk;
    private CodeInsightTestFixture fixture;
    private VirtualFile root;

    public NixFixture() {
        this(false);
    }

    /**
     * With {@code onDisk}, its files are on the local file system, in a content root of the project, as affogato needs
     * them to follow an {@code import} from one to another.
     */
    public NixFixture(boolean onDisk) {
        this.onDisk = onDisk;
    }

    public CodeInsightTestFixture get() {
        return fixture;
    }

    public interface Body {
        void run(CodeInsightTestFixture fixture) throws Exception;
    }

    /** Runs {@code body} on the EDT, as the platform's tests do. */
    public void run(Body body) {
        EdtTestUtil.runInEdtAndWait(() -> {
            try {
                body.run(fixture);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        var factory = IdeaTestFixtureFactory.getFixtureFactory();
        fixture = factory.createCodeInsightFixture(factory.createLightFixtureBuilder(new LightProjectDescriptor(), context.getDisplayName())
                .getFixture(), onDisk ? new TempDirTestFixtureImpl() : new LightTempDirTestFixtureImpl(true));
        EdtTestUtil.runInEdtAndWait(() -> {
            fixture.setUp();
            if (onDisk) {
                root = LocalFileSystem.getInstance().refreshAndFindFileByPath(fixture.getTempDirPath());
                PsiTestUtil.addContentRoot(fixture.getModule(), root);
            }
        });
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        EdtTestUtil.runInEdtAndWait(() -> {
            if (root != null) {
                PsiTestUtil.removeContentEntry(fixture.getModule(), root);
            }
            fixture.tearDown();
        });
    }
}
