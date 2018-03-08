package mil.nga.giat.mage.map.cache;


import android.app.Application;
import android.os.AsyncTask;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class MapDataManagerTest {

    // had to do this to make Mockito generate a different class for
    // two mock providers, because it uses the same class for two
    // separate mock instances of MapDataProvider directly, which is
    // a collision for MapLayerDescriptor.getDataType()
    static abstract class CatProvider implements MapDataProvider {}
    static abstract class DogProvider implements MapDataProvider {}

    static class TestDirMapDataRepository implements MapDataRepository {

        private final File dir;

        TestDirMapDataRepository(File dir) {
            this.dir = dir;
        }

        @Override
        public Set<MapDataResource> retrieveMapDataResources() {
            Set<MapDataResource> resources = new HashSet<>();
            File[] files = dir.listFiles();
            for (File file : files) {
                resources.add(new MapDataResource(file.toURI()));
            }
            return resources;
        }
    }

    static class TestLayerDescriptor extends MapLayerDescriptor {

        TestLayerDescriptor(String overlayName, String cacheName, Class<? extends MapDataProvider> type) {
            super(overlayName, cacheName, type);
        }
    }

    private static Set<MapDataResource> cacheSetWithCaches(MapDataResource... caches) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(caches)));
    }

    @Rule
    public TemporaryFolder testRoot = new TemporaryFolder();

    @Rule
    public TestName testName = new TestName();

    private File cacheDir1;
    private File cacheDir2;
    private MapDataManager mapDataManager;
    private Executor executor;
    private MapDataProvider catProvider;
    private MapDataProvider dogProvider;
    private MapDataManager.MapDataListener listener;
    private ArgumentCaptor<MapDataManager.MapDataUpdate> updateCaptor = ArgumentCaptor.forClass(MapDataManager.MapDataUpdate.class);

    @Before
    public void configureCacheManager() throws Exception {

        Application context = Mockito.mock(Application.class);

        List<File> cacheDirs = Arrays.asList(
            cacheDir1 = testRoot.newFolder("cache1"),
            cacheDir2 = testRoot.newFolder("cache2")
        );

        TestDirMapDataRepository repo1 = new TestDirMapDataRepository(cacheDir1);
        TestDirMapDataRepository repo2 = new TestDirMapDataRepository(cacheDir2);

        assertTrue(cacheDir1.isDirectory());
        assertTrue(cacheDir2.isDirectory());

        executor = mock(Executor.class);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) {
                Runnable task = invocationOnMock.getArgument(0);
                AsyncTask.SERIAL_EXECUTOR.execute(task);
                return null;
            }
        }).when(executor).execute(any(Runnable.class));

        catProvider = mock(CatProvider.class);
        dogProvider = mock(DogProvider.class);

        when(catProvider.canHandleResource(any(URI.class))).thenAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocationOnMock) {
                URI file = invocationOnMock.getArgument(0);
                return file.getPath().toLowerCase().endsWith(".cat");
            }
        });
        when(dogProvider.canHandleResource(any(URI.class))).thenAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocationOnMock) {
                URI file = invocationOnMock.getArgument(0);
                return file.getPath().toLowerCase().endsWith(".dog");
            }
        });

        MapDataManager.Config config = new MapDataManager.Config()
            .context(context)
            .executor(executor)
            .providers(catProvider, dogProvider)
            .repositories(repo1, repo2)
            .updatePermission(new MapDataManager.CreateUpdatePermission(){});

        listener = mock(MapDataManager.MapDataListener.class);
        mapDataManager = new MapDataManager(config);
        mapDataManager.addUpdateListener(listener);
    }

    @Test
    public void isMockable() {
        mock(MapDataManager.class, withSettings()
            .useConstructor(new MapDataManager.Config().updatePermission(new MapDataManager.CreateUpdatePermission(){})));
    }

    @Test(expected = IllegalArgumentException.class)
    public void requiresUpdatePermission() {
        MapDataManager manager = new MapDataManager(new MapDataManager.Config()
            .repositories()
            .executor(executor)
            .providers()
            .updatePermission(null));
    }

    @Test(expected = Error.class)
    public void cannotCreateUpdateWithoutPermission() {
        mapDataManager.new MapDataUpdate(new MapDataManager.CreateUpdatePermission() {}, null, null, null);
    }

    @Test
    public void importsCacheWithCapableProvider() throws Exception {
        File cacheFile = new File(cacheDir1, "big_cache.dog");

        assertTrue(cacheFile.createNewFile());

        mapDataManager.tryImportResource(cacheFile.toURI());

        verify(dogProvider, timeout(1000)).importResource(cacheFile.toURI());
        verify(catProvider, never()).importResource(any(URI.class));
    }

    @Test
    public void addsImportedCacheOverlayToCacheOverlaySet() throws Exception {
        File cacheFile = new File(cacheDir2, "data.cat");
        MapDataResource catCache = new MapDataResource(cacheFile.toURI(), cacheDir2.getName(), catProvider.getClass(), Collections.<MapLayerDescriptor>emptySet());
        when(catProvider.importResource(cacheFile.toURI())).thenReturn(catCache);

        assertTrue(cacheFile.createNewFile());

        mapDataManager.tryImportResource(cacheFile.toURI());

        verify(listener, timeout(1000)).onMapDataUpdated(updateCaptor.capture());

        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
        Set<MapDataResource> caches = mapDataManager.getMapData();

        assertThat(caches.size(), is(1));
        assertThat(caches, hasItem(catCache));
        assertThat(update.added.size(), is(1));
        assertThat(update.added, hasItem(catCache));
        assertTrue(update.updated.isEmpty());
        assertTrue(update.removed.isEmpty());
        assertThat(update.source, sameInstance(mapDataManager));
    }

    @Test
    public void refreshingFindsCachesInProvidedLocations() throws Exception {
        File cache1File = new File(cacheDir1, "pluto.dog");
        File cache2File = new File(cacheDir2, "figaro.cat");
        MapDataResource cache1 = new MapDataResource(cache1File.toURI(), cache1File.getName(), dogProvider.getClass(), Collections.<MapLayerDescriptor>emptySet());
        MapDataResource cache2 = new MapDataResource(cache2File.toURI(), cache2File.getName(), catProvider.getClass(), Collections.<MapLayerDescriptor>emptySet());
        when(dogProvider.importResource(cache1File.toURI())).thenReturn(cache1);
        when(catProvider.importResource(cache2File.toURI())).thenReturn(cache2);

        assertTrue(cache1File.createNewFile());
        assertTrue(cache2File.createNewFile());

        mapDataManager.refreshAvailableCaches();

        verify(listener, timeout(1000)).onMapDataUpdated(updateCaptor.capture());

        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
        Set<MapDataResource> caches = mapDataManager.getMapData();

        assertThat(caches.size(), is(2));
        assertThat(caches, hasItems(cache1, cache2));
        assertThat(update.added.size(), is(2));
        assertThat(update.added, hasItems(cache1, cache2));
        assertTrue(update.updated.isEmpty());
        assertTrue(update.removed.isEmpty());
        assertThat(update.source, sameInstance(mapDataManager));
    }

    @Test
    public void refreshingGetsAvailableCachesFromProviders() {
        MapDataResource dogCache1 = new MapDataResource(new File(cacheDir1, "dog1.dog").toURI(), "dog1", dogProvider.getClass(), Collections.<MapLayerDescriptor>emptySet());
        MapDataResource dogCache2 = new MapDataResource(new File(cacheDir1, "dog2.dog").toURI(), "dog2", dogProvider.getClass(), Collections.<MapLayerDescriptor>emptySet());
        MapDataResource catCache = new MapDataResource(new File(cacheDir1, "cat1.cat").toURI(), "cat1", catProvider.getClass(), Collections.<MapLayerDescriptor>emptySet());

        when(dogProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(cacheSetWithCaches(dogCache1, dogCache2));
        when(catProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(cacheSetWithCaches(catCache));

        mapDataManager.refreshAvailableCaches();

        verify(listener, timeout(1000)).onMapDataUpdated(updateCaptor.capture());
        verify(dogProvider).refreshResources(eq(Collections.<MapDataResource>emptySet()));
        verify(catProvider).refreshResources(eq(Collections.<MapDataResource>emptySet()));

        MapDataManager.MapDataUpdate update = updateCaptor.getValue();
        Set<MapDataResource> caches = mapDataManager.getMapData();

        assertThat(caches.size(), is(3));
        assertThat(caches, hasItems(dogCache1, dogCache2, catCache));
        assertThat(update.added.size(), is(3));
        assertThat(update.added, hasItems(dogCache1, dogCache2, catCache));
        assertTrue(update.updated.isEmpty());
        assertTrue(update.removed.isEmpty());
        assertThat(update.source, sameInstance(mapDataManager));
    }

    @Test
    public void refreshingRemovesCachesNoLongerAvailable() {
        MapDataResource dogCache1 = new MapDataResource(new File(cacheDir1, "dog1.dog").toURI(), "dog1", dogProvider.getClass(), Collections.<MapLayerDescriptor>emptySet());
        MapDataResource dogCache2 = new MapDataResource(new File(cacheDir1, "dog2.dog").toURI(), "dog2", dogProvider.getClass(), Collections.<MapLayerDescriptor>emptySet());
        MapDataResource catCache = new MapDataResource(new File(cacheDir1, "cat1.cat").toURI(), "cat1", catProvider.getClass(), Collections.<MapLayerDescriptor>emptySet());

        when(dogProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(cacheSetWithCaches(dogCache1, dogCache2));
        when(catProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(cacheSetWithCaches(catCache));

        mapDataManager.refreshAvailableCaches();

        verify(listener, timeout(1000)).onMapDataUpdated(updateCaptor.capture());
        verify(dogProvider).refreshResources(eq(Collections.<MapDataResource>emptySet()));
        verify(catProvider).refreshResources(eq(Collections.<MapDataResource>emptySet()));

        Set<MapDataResource> caches = mapDataManager.getMapData();

        assertThat(caches.size(), is(3));
        assertThat(caches, hasItems(dogCache1, dogCache2, catCache));

        when(dogProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(cacheSetWithCaches(dogCache2));
        when(catProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(Collections.<MapDataResource>emptySet());

        mapDataManager.refreshAvailableCaches();

        verify(listener, timeout(1000).times(2)).onMapDataUpdated(updateCaptor.capture());

        verify(dogProvider).refreshResources(eq(cacheSetWithCaches(dogCache1, dogCache2)));
        verify(catProvider).refreshResources(eq(cacheSetWithCaches(catCache)));

        caches = mapDataManager.getMapData();
        MapDataManager.MapDataUpdate update = updateCaptor.getValue();

        assertThat(caches.size(), is(1));
        assertThat(caches, hasItem(dogCache2));
        assertThat(update.added, empty());
        assertThat(update.updated, empty());
        assertThat(update.removed, hasItems(dogCache1, catCache));
        assertThat(update.source, sameInstance(mapDataManager));
    }

    @Test
    public void refreshingUpdatesExistingCachesThatChanged() {
        MapDataResource dogOrig = new MapDataResource(new File(cacheDir1, "dog1.dog").toURI(), "dog1", dogProvider.getClass(), Collections.<MapLayerDescriptor>emptySet());

        when(dogProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(cacheSetWithCaches(dogOrig));

        mapDataManager.refreshAvailableCaches();

        verify(listener, timeout(1000)).onMapDataUpdated(updateCaptor.capture());

        Set<MapDataResource> caches = mapDataManager.getMapData();
        MapDataManager.MapDataUpdate update = updateCaptor.getValue();

        assertThat(caches.size(), is(1));
        assertThat(caches, hasItem(dogOrig));
        assertThat(update.added.size(), is(1));
        assertThat(update.added, hasItem(dogOrig));
        assertThat(update.updated, empty());
        assertThat(update.removed, empty());

        MapDataResource dogUpdated = new MapDataResource(new File(cacheDir1, "dog1.dog").toURI(), "dog1", dogProvider.getClass(), Collections.<MapLayerDescriptor>emptySet());

        when(dogProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(cacheSetWithCaches(dogUpdated));

        mapDataManager.refreshAvailableCaches();

        verify(listener, timeout(1000).times(2)).onMapDataUpdated(updateCaptor.capture());

        Set<MapDataResource> overlaysRefreshed = mapDataManager.getMapData();
        update = updateCaptor.getValue();

        assertThat(overlaysRefreshed, not(sameInstance(caches)));
        assertThat(overlaysRefreshed.size(), is(1));
        assertThat(overlaysRefreshed, hasItem(sameInstance(dogUpdated)));
        assertThat(overlaysRefreshed, hasItem(dogOrig));
        assertThat(update.added, empty());
        assertThat(update.updated.size(), is(1));
        assertThat(update.updated, hasItem(sameInstance(dogUpdated)));
        assertThat(update.removed, empty());
        assertThat(update.source, sameInstance(mapDataManager));
    }

    @Test
    public void immediatelyBeginsRefreshOnExecutor() {
        final boolean[] overrodeMock = new boolean[]{false};
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) {
                // make sure this answer overrides the one in the setup method
                overrodeMock[0] = true;
                return null;
            }
        }).when(executor).execute(any(Runnable.class));

        mapDataManager.refreshAvailableCaches();

        verify(executor).execute(any(Runnable.class));
        assertTrue(overrodeMock[0]);
    }

    @Test
    public void cannotRefreshMoreThanOnceConcurrently() throws Exception {
        final CyclicBarrier taskBegan = new CyclicBarrier(2);
        final CyclicBarrier taskCanProceed = new CyclicBarrier(2);
        final AtomicReference<Runnable> runningTask = new AtomicReference<>();

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) {
                final Runnable task = invocation.getArgument(0);
                final Runnable blocked = new Runnable() {
                    @Override
                    public void run() {
                        if (runningTask.get() == this) {
                            try {
                                taskBegan.await();
                                taskCanProceed.await();
                            }
                            catch (Exception e) {
                                fail(e.getMessage());
                                throw new IllegalStateException(e);
                            }
                        }
                        task.run();
                    }
                };
                runningTask.compareAndSet(null, blocked);
                AsyncTask.SERIAL_EXECUTOR.execute(blocked);
                return null;
            }
        }).when(executor).execute(any(Runnable.class));

        when(catProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(Collections.<MapDataResource>emptySet());
        when(dogProvider.refreshResources(ArgumentMatchers.<MapDataResource>anySet())).thenReturn(Collections.<MapDataResource>emptySet());

        mapDataManager.refreshAvailableCaches();

        verify(executor, times(1)).execute(any(Runnable.class));

        // wait for the background task to start, then try to start another refresh
        // and verify no new tasks were submitted to executor
        taskBegan.await();

        mapDataManager.refreshAvailableCaches();

        verify(executor, times(1)).execute(any(Runnable.class));

        taskCanProceed.await();

        verify(listener, timeout(1000)).onMapDataUpdated(updateCaptor.capture());
    }
}
