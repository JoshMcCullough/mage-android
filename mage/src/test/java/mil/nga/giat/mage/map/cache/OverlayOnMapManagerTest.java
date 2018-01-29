package mil.nga.giat.mage.map.cache;

import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class OverlayOnMapManagerTest implements CacheManager.CreateUpdatePermission {

    static class TestOverlayOnMap extends OverlayOnMapManager.OverlayOnMap {

        boolean visible;
        boolean onMap;
        boolean disposed;

        TestOverlayOnMap(OverlayOnMapManager manager) {
            manager.super();
        }

        @Override
        protected void addToMapWithVisibility(boolean x) {
            onMap = true;
            visible = x;
        }

        @Override
        protected void removeFromMap() {
            onMap = visible = false;
        }

        @Override
        protected void show() {
            onMap = visible = true;
        }

        @Override
        protected void hide() {
            visible = false;
        }

        @Override
        protected void zoomMapToBoundingBox() {

        }

        @Override
        protected boolean isOnMap() {
            return onMap;
        }

        @Override
        protected boolean isVisible() {
            return visible;
        }

        @Override
        protected String onMapClick(LatLng latLng, MapView mapView) {
            return null;
        }

        @Override
        protected void dispose() {
            disposed = true;
        }

        OverlayOnMapManager.OverlayOnMap visible(boolean x) {
            if (x) {
                show();
            }
            else {
                hide();
            }
            return this;
        }

        OverlayOnMapManager.OverlayOnMap onMap(boolean x) {
            if (x) {
                addToMapWithVisibility(this.isVisible());
            }
            else {
                removeFromMap();
            }
            return this;
        }
    }

    private static OverlayOnMapManager.OverlayOnMap mockOverlayOnMap(OverlayOnMapManager overlayManager) {
        return mock(OverlayOnMapManager.OverlayOnMap.class, withSettings().useConstructor(overlayManager));
    }

    @SafeVarargs
    private static <T> Set<T> setOf(T... things) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(things)));
    }


    private CacheManager cacheManager;
    private CacheOverlayTest.TestCacheProvider1 provider1;
    private CacheOverlayTest.TestCacheProvider2 provider2;
    private List<CacheProvider> providers;

    @Before
    public void setup() {

        provider1 = mock(CacheOverlayTest.TestCacheProvider1.class);
        provider2 = mock(CacheOverlayTest.TestCacheProvider2.class);
        cacheManager = mock(CacheManager.class, withSettings().useConstructor(new CacheManager.Config().updatePermission(this)));
        providers = Arrays.asList(provider1, provider2);
    }

    @Test
    public void listensToCacheManagerUpdates() {

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(cacheManager, providers, null);

        verify(cacheManager).addUpdateListener(overlayManager);
    }

    @Test
    public void addsOverlaysFromAddedCaches() {

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(cacheManager, providers, null);
        CacheOverlay overlay1 = new CacheOverlayTest.TestCacheOverlay1("test overlay 1", "test cache", provider1.getClass());
        CacheOverlay overlay2 = new CacheOverlayTest.TestCacheOverlay1("test overlay 2", "test cache", provider1.getClass());
        MapCache mapCache = new MapCache("test cache", provider1.getClass(), new File("test"), setOf(overlay1, overlay2));
        Set<MapCache> added = setOf(mapCache);
        CacheManager.CacheOverlayUpdate update = cacheManager.new CacheOverlayUpdate(this, added, Collections.<MapCache>emptySet(), Collections.<MapCache>emptySet());

        when(provider1.createOverlayOnMapFromCache(overlay1, overlayManager)).thenReturn(mockOverlayOnMap(overlayManager));
        when(provider1.createOverlayOnMapFromCache(overlay2, overlayManager)).thenReturn(mockOverlayOnMap(overlayManager));

        overlayManager.onCacheOverlaysUpdated(update);

        assertThat(overlayManager.getOverlays().size(), is(2));
        assertThat(overlayManager.getOverlays(), hasItems(overlay1, overlay2));
    }

    @Test
    public void removesOverlaysFromRemovedCaches() {

        CacheOverlay overlay1 = new CacheOverlayTest.TestCacheOverlay1("test overlay 1", "test cache", provider1.getClass());
        CacheOverlay overlay2 = new CacheOverlayTest.TestCacheOverlay1("test overlay 2", "test cache", provider1.getClass());
        MapCache mapCache = new MapCache("test cache", provider1.getClass(), new File("test"), setOf(overlay1, overlay2));

        when(cacheManager.getCaches()).thenReturn(setOf(mapCache));

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(cacheManager, providers, null);

        assertThat(overlayManager.getOverlays().size(), is(2));
        assertThat(overlayManager.getOverlays(), hasItems(overlay1, overlay2));

        CacheManager.CacheOverlayUpdate update = cacheManager.new CacheOverlayUpdate(this, Collections.<MapCache>emptySet(), Collections.<MapCache>emptySet(), setOf(mapCache));
        overlayManager.onCacheOverlaysUpdated(update);

        assertTrue(overlayManager.getOverlays().isEmpty());
    }

    @Test
    public void removesOverlaysFromUpdatedCaches() {

        CacheOverlay overlay1 = new CacheOverlayTest.TestCacheOverlay1("test overlay 1", "test cache", provider1.getClass());
        CacheOverlay overlay2 = new CacheOverlayTest.TestCacheOverlay1("test overlay 2", "test cache", provider1.getClass());
        MapCache mapCache = new MapCache("test cache", provider1.getClass(), new File("test"), setOf(overlay1, overlay2));

        when(cacheManager.getCaches()).thenReturn(setOf(mapCache));

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(cacheManager, providers, null);

        List<CacheOverlay> overlays = overlayManager.getOverlays();
        assertThat(overlays.size(), is(2));
        assertThat(overlays, hasItems(overlay1, overlay2));

        overlay2 = new CacheOverlayTest.TestCacheOverlay1(overlay2.getOverlayName(), overlay2.getCacheName(), overlay2.getCacheType());
        mapCache = new MapCache(mapCache.getName(), mapCache.getType(), mapCache.getSourceFile(), setOf(overlay2));
        Set<MapCache> updated = setOf(mapCache);
        CacheManager.CacheOverlayUpdate update = cacheManager.new CacheOverlayUpdate(this,
            Collections.<MapCache>emptySet(), updated, Collections.<MapCache>emptySet());

        when(provider1.createOverlayOnMapFromCache(overlay2, overlayManager)).thenReturn(mockOverlayOnMap(overlayManager));

        overlayManager.onCacheOverlaysUpdated(update);

        overlays = overlayManager.getOverlays();
        assertThat(overlays.size(), is(1));
        assertThat(overlays, hasItem(overlay2));
    }

    @Test
    public void addsOverlaysFromUpdatedCaches() {

        CacheOverlay overlay1 = new CacheOverlayTest.TestCacheOverlay1("test overlay 1", "test cache", provider1.getClass());
        MapCache cache = new MapCache("test cache", provider1.getClass(), null, setOf(overlay1));

        when(cacheManager.getCaches()).thenReturn(setOf(cache));

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(cacheManager, providers, null);

        assertThat(overlayManager.getOverlays().size(), is(1));
        assertThat(overlayManager.getOverlays(), hasItem(overlay1));


        CacheOverlay overlay2 = new CacheOverlayTest.TestCacheOverlay1("test overlay 2", "test cache", provider1.getClass());
        cache = new MapCache(cache.getName(), cache.getType(), null, setOf(overlay1, overlay2));
        CacheManager.CacheOverlayUpdate update = cacheManager.new CacheOverlayUpdate(
            this, Collections.<MapCache>emptySet(), setOf(cache), Collections.<MapCache>emptySet());
        overlayManager.onCacheOverlaysUpdated(update);

        assertThat(overlayManager.getOverlays().size(), is(2));
        assertThat(overlayManager.getOverlays(), hasItems(overlay1, overlay2));
    }

    @Test
    public void addsAndRemovesOverlaysFromUpdatedCachesWhenOverlayCountIsUnchanged() {

        CacheOverlay overlay1 = new CacheOverlayTest.TestCacheOverlay1("overlay 1", "test cache", provider1.getClass());
        MapCache cache = new MapCache("test cache", provider1.getClass(), null, setOf(overlay1));

        when(cacheManager.getCaches()).thenReturn(setOf(cache));

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(cacheManager, providers, null);

        assertThat(overlayManager.getOverlays().size(), is(1));
        assertThat(overlayManager.getOverlays(), hasItem(overlay1));

        CacheOverlay overlay2 = new CacheOverlayTest.TestCacheOverlay1("overlay 2", "test cache", provider1.getClass());
        cache = new MapCache(cache.getName(), cache.getType(), null, setOf(overlay2));
        CacheManager.CacheOverlayUpdate update = cacheManager.new CacheOverlayUpdate(
            this, Collections.<MapCache>emptySet(), setOf(cache), Collections.<MapCache>emptySet());
        overlayManager.onCacheOverlaysUpdated(update);

        assertThat(overlayManager.getOverlays().size(), is(1));
        assertThat(overlayManager.getOverlays(), hasItems(overlay2));
    }

    @Test
    public void replacesLikeOverlaysFromUpdatedCaches() {

        CacheOverlay overlay1 = new CacheOverlayTest.TestCacheOverlay1("overlay 1", "test cache", provider1.getClass());
        MapCache cache = new MapCache("test cache", provider1.getClass(), null, setOf(overlay1));

        when(cacheManager.getCaches()).thenReturn(setOf(cache));

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(cacheManager, providers, null);

        assertThat(overlayManager.getOverlays().size(), is(1));
        assertThat(overlayManager.getOverlays(), hasItem(overlay1));

        CacheOverlay overlay1Updated = new CacheOverlayTest.TestCacheOverlay1(overlay1.getOverlayName(), overlay1.getCacheName(), overlay1.getCacheType());
        cache = new MapCache(cache.getName(), cache.getType(), null, setOf(overlay1Updated));
        CacheManager.CacheOverlayUpdate update = cacheManager.new CacheOverlayUpdate(
            this, Collections.<MapCache>emptySet(), setOf(cache), Collections.<MapCache>emptySet());
        overlayManager.onCacheOverlaysUpdated(update);

        assertThat(overlayManager.getOverlays().size(), is(1));
        assertThat(overlayManager.getOverlays(), hasItem(overlay1));
        assertThat(overlayManager.getOverlays(), not(hasItem(sameInstance(overlay1))));
        assertThat(overlayManager.getOverlays(), hasItem(sameInstance(overlay1Updated)));
    }

    @Test
    public void createsOverlaysOnMapLazily() {

        CacheOverlay overlay1 = new CacheOverlayTest.TestCacheOverlay1("overlay 1", "test cache", provider1.getClass());
        MapCache mapCache = new MapCache("test cache", provider1.getClass(), new File("test"), setOf(overlay1));
        Set<MapCache> added = setOf(mapCache);
        CacheManager.CacheOverlayUpdate update = cacheManager.new CacheOverlayUpdate(this, added, Collections.<MapCache>emptySet(), Collections.<MapCache>emptySet());
        OverlayOnMapManager overlayManager = new OverlayOnMapManager(cacheManager, providers, null);

        overlayManager.onCacheOverlaysUpdated(update);

        assertThat(overlayManager.getOverlays().size(), is(1));
        assertThat(overlayManager.getOverlays(), hasItems(overlay1));
        assertFalse(overlayManager.isOverlayVisible(overlay1));
        verify(provider1, never()).createOverlayOnMapFromCache(any(CacheOverlay.class), Mockito.same(overlayManager));

        when(provider1.createOverlayOnMapFromCache(overlay1, overlayManager)).thenReturn(new TestOverlayOnMap(overlayManager));

        overlayManager.showOverlay(overlay1);

        assertTrue(overlayManager.isOverlayVisible(overlay1));
        verify(provider1).createOverlayOnMapFromCache(overlay1, overlayManager);
    }


    @Test
    public void refreshesVisibleOverlayOnMapWhenUpdated() {

        CacheOverlay overlay1 = new CacheOverlayTest.TestCacheOverlay1("overlay 1", "test cache", provider1.getClass());
        MapCache cache = new MapCache("test cache", provider1.getClass(), new File("test"), setOf(overlay1));

        when(cacheManager.getCaches()).thenReturn(setOf(cache));

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(cacheManager, providers, null);

        assertThat(overlayManager.getOverlays().size(), is(1));
        assertThat(overlayManager.getOverlays(), hasItem(overlay1));
        assertFalse(overlayManager.isOverlayVisible(overlay1));

        OverlayOnMapManager.OverlayOnMap onMap =  mockOverlayOnMap(overlayManager);
        when(provider1.createOverlayOnMapFromCache(overlay1, overlayManager)).thenReturn(onMap);

        overlayManager.showOverlay(overlay1);

        verify(provider1).createOverlayOnMapFromCache(overlay1, overlayManager);
        verify(onMap).addToMapWithVisibility(true);

        overlay1 = new CacheOverlayTest.TestCacheOverlay1(overlay1.getOverlayName(), overlay1.getCacheName(), overlay1.getCacheType());
        cache = new MapCache(cache.getName(), cache.getType(), null, setOf(overlay1));
        CacheManager.CacheOverlayUpdate update = cacheManager.new CacheOverlayUpdate(this, Collections.<MapCache>emptySet(), setOf(cache), Collections.<MapCache>emptySet());

        OverlayOnMapManager.OverlayOnMap onMapUpdated = mockOverlayOnMap(overlayManager);
        when(provider1.createOverlayOnMapFromCache(overlay1, overlayManager)).thenReturn(onMapUpdated);
        when(onMap.isOnMap()).thenReturn(true);
        when(onMap.isVisible()).thenReturn(true);

        overlayManager.onCacheOverlaysUpdated(update);

        verify(onMap).removeFromMap();
        verify(provider1).createOverlayOnMapFromCache(Mockito.same(overlay1), Mockito.same(overlayManager));
        verify(onMapUpdated).addToMapWithVisibility(true);
    }

    @Test
    public void doesNotRefreshHiddenOverlayOnMapWhenUpdated() {

        CacheOverlay overlay1 = new CacheOverlayTest.TestCacheOverlay1("overlay 1", "test cache", provider1.getClass());
        MapCache cache = new MapCache("test cache", provider1.getClass(), new File("test"), setOf(overlay1));

        when(cacheManager.getCaches()).thenReturn(setOf(cache));

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(cacheManager, providers, null);

        assertThat(overlayManager.getOverlays().size(), is(1));
        assertThat(overlayManager.getOverlays(), hasItem(overlay1));
        assertFalse(overlayManager.isOverlayVisible(overlay1));

        OverlayOnMapManager.OverlayOnMap onMap =  mockOverlayOnMap(overlayManager);
        when(provider1.createOverlayOnMapFromCache(overlay1, overlayManager)).thenReturn(onMap);

        overlayManager.showOverlay(overlay1);

        verify(provider1).createOverlayOnMapFromCache(overlay1, overlayManager);
        verify(onMap).addToMapWithVisibility(true);

        overlay1 = new CacheOverlayTest.TestCacheOverlay1(overlay1.getOverlayName(), overlay1.getCacheName(), overlay1.getCacheType());
        cache = new MapCache(cache.getName(), cache.getType(), null, setOf(overlay1));
        CacheManager.CacheOverlayUpdate update = cacheManager.new CacheOverlayUpdate(this, Collections.<MapCache>emptySet(), setOf(cache), Collections.<MapCache>emptySet());

        OverlayOnMapManager.OverlayOnMap onMapUpdated = mockOverlayOnMap(overlayManager);
        when(provider1.createOverlayOnMapFromCache(overlay1, overlayManager)).thenReturn(onMapUpdated);
        when(onMap.isOnMap()).thenReturn(true);
        when(onMap.isVisible()).thenReturn(false);

        overlayManager.onCacheOverlaysUpdated(update);

        verify(onMap).removeFromMap();
        verify(provider1, never()).createOverlayOnMapFromCache(Mockito.same(overlay1), Mockito.same(overlayManager));
        verify(onMapUpdated, never()).addToMapWithVisibility(anyBoolean());
    }

    @Test
    public void doesNotRefreshUnchangedVisibleOverlaysFromUpdatedCaches() {

        CacheOverlay overlay1 = new CacheOverlayTest.TestCacheOverlay1("overlay 1", "test cache", provider1.getClass());
        MapCache cache = new MapCache("test cache", provider1.getClass(), new File("test"), setOf(overlay1));

        when(cacheManager.getCaches()).thenReturn(setOf(cache));

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(cacheManager, providers, null);

        assertThat(overlayManager.getOverlays().size(), is(1));
        assertThat(overlayManager.getOverlays(), hasItem(overlay1));
        assertFalse(overlayManager.isOverlayVisible(overlay1));

        OverlayOnMapManager.OverlayOnMap onMap =  mockOverlayOnMap(overlayManager);
        when(provider1.createOverlayOnMapFromCache(overlay1, overlayManager)).thenReturn(onMap);

        overlayManager.showOverlay(overlay1);

        verify(provider1).createOverlayOnMapFromCache(overlay1, overlayManager);
        verify(onMap).addToMapWithVisibility(true);

        CacheOverlay overlay2 = new CacheOverlayTest.TestCacheOverlay1("overlay 2", cache.getName(), cache.getType());
        cache = new MapCache(cache.getName(), cache.getType(), null, setOf(overlay1, overlay2));
        CacheManager.CacheOverlayUpdate update = cacheManager.new CacheOverlayUpdate(this, Collections.<MapCache>emptySet(), setOf(cache), Collections.<MapCache>emptySet());

        OverlayOnMapManager.OverlayOnMap onMapUpdated = mockOverlayOnMap(overlayManager);

        overlayManager.onCacheOverlaysUpdated(update);

        verify(onMap, never()).removeFromMap();
        verify(provider1, times(1)).createOverlayOnMapFromCache(Mockito.same(overlay1), Mockito.same(overlayManager));
        verify(onMapUpdated, never()).addToMapWithVisibility(anyBoolean());
    }

    @Test
    public void removesOverlayOnMapWhenOverlayIsRemovedFromCache() {

        CacheOverlay overlay1 = new CacheOverlayTest.TestCacheOverlay1("overlay 1", "test cache", provider1.getClass());
        CacheOverlay overlay2 = new CacheOverlayTest.TestCacheOverlay1("overlay 2", "test cache", provider1.getClass());
        MapCache cache = new MapCache("test cache", provider1.getClass(), new File("test"), setOf(overlay1, overlay2));

        when(cacheManager.getCaches()).thenReturn(setOf(cache));

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(cacheManager, providers, null);

        assertThat(overlayManager.getOverlays().size(), is(2));
        assertThat(overlayManager.getOverlays(), hasItems(overlay1, overlay2));

        OverlayOnMapManager.OverlayOnMap onMap =  mockOverlayOnMap(overlayManager);
        when(provider1.createOverlayOnMapFromCache(overlay1, overlayManager)).thenReturn(onMap);

        overlayManager.showOverlay(overlay1);

        verify(provider1).createOverlayOnMapFromCache(overlay1, overlayManager);
        verify(onMap).addToMapWithVisibility(true);

        cache = new MapCache(cache.getName(), cache.getType(), null, setOf(overlay2));
        CacheManager.CacheOverlayUpdate update = cacheManager.new CacheOverlayUpdate(this, Collections.<MapCache>emptySet(), setOf(cache), Collections.<MapCache>emptySet());

        overlayManager.onCacheOverlaysUpdated(update);

        verify(onMap).removeFromMap();
    }

    @Test
    public void removesOverlayOnMapWhenCacheIsRemoved() {

        CacheOverlay overlay1 = new CacheOverlayTest.TestCacheOverlay1("overlay 1", "test cache", provider1.getClass());
        CacheOverlay overlay2 = new CacheOverlayTest.TestCacheOverlay1("overlay 2", "test cache", provider1.getClass());
        MapCache cache = new MapCache("test cache", provider1.getClass(), new File("test"), setOf(overlay1, overlay2));

        when(cacheManager.getCaches()).thenReturn(setOf(cache));

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(cacheManager, providers, null);

        assertThat(overlayManager.getOverlays().size(), is(2));
        assertThat(overlayManager.getOverlays(), hasItems(overlay1, overlay2));

        OverlayOnMapManager.OverlayOnMap onMap =  mockOverlayOnMap(overlayManager);
        when(provider1.createOverlayOnMapFromCache(overlay1, overlayManager)).thenReturn(onMap);

        overlayManager.showOverlay(overlay1);

        verify(provider1).createOverlayOnMapFromCache(overlay1, overlayManager);
        verify(onMap).addToMapWithVisibility(true);

        CacheManager.CacheOverlayUpdate update = cacheManager.new CacheOverlayUpdate(this, Collections.<MapCache>emptySet(), Collections.<MapCache>emptySet(), setOf(cache));

        overlayManager.onCacheOverlaysUpdated(update);

        verify(onMap).removeFromMap();
    }

    @Test
    public void behavesWhenTwoCachesHaveOverlaysWithTheSameName() {

        CacheOverlay overlay1 = new CacheOverlayTest.TestCacheOverlay1("overlay1", "cache1", provider1.getClass());
        MapCache cache1 = new MapCache("cache1", provider1.getClass(), null, setOf(overlay1));

        CacheOverlay overlay2 = new CacheOverlayTest.TestCacheOverlay2("overlay1", "cache2", provider1.getClass());
        MapCache cache2 = new MapCache("cache2", provider1.getClass(), null, setOf(overlay2));

        when(cacheManager.getCaches()).thenReturn(setOf(cache1, cache2));

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(cacheManager, providers, null);

        assertThat(overlayManager.getOverlays().size(), is(2));
        assertThat(overlayManager.getOverlays(), hasItems(overlay1, overlay2));

        OverlayOnMapManager.OverlayOnMap onMap1 = mockOverlayOnMap(overlayManager);
        OverlayOnMapManager.OverlayOnMap onMap2 = mockOverlayOnMap(overlayManager);
        when(provider1.createOverlayOnMapFromCache(overlay1, overlayManager)).thenReturn(onMap1);
        when(provider1.createOverlayOnMapFromCache(overlay2, overlayManager)).thenReturn(onMap2);

        overlayManager.showOverlay(overlay1);

        verify(provider1).createOverlayOnMapFromCache(overlay1, overlayManager);
        verify(provider1, never()).createOverlayOnMapFromCache(overlay2, overlayManager);
        verify(onMap1).addToMapWithVisibility(true);

        overlayManager.showOverlay(overlay2);

        verify(provider1).createOverlayOnMapFromCache(overlay1, overlayManager);
        verify(provider1).createOverlayOnMapFromCache(overlay2, overlayManager);
        verify(onMap1).addToMapWithVisibility(true);
        verify(onMap2).addToMapWithVisibility(true);

        when(onMap2.isVisible()).thenReturn(true);

        overlayManager.hideOverlay(overlay2);

        verify(onMap2).hide();
        verify(onMap1, never()).hide();
    }

    @Test
    public void behavesWhenTwoOverlaysFromDifferentCachesHaveTheSameName() {
        fail("unimplemented");
    }

    @Test
    public void behavesWhenTwoOverlaysAndTheirCachesHaveTheSameNames() {
        fail("unimplemented");
    }

    @Test
    public void maintainsOrderOfUpdatedCacheOverlays() {

        fail("unimplemented");
    }

    @Test
    public void disposeStopsListeningToCacheManager() {

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(cacheManager, providers, null);
        overlayManager.dispose();

        verify(cacheManager).removeUpdateListener(overlayManager);
    }

    @Test
    public void disposeRemovesAndDisposesAllOverlaysFromMap() {

        CacheOverlay overlay1 = new CacheOverlayTest.TestCacheOverlay1("overlay1", "cache1", provider1.getClass());
        CacheOverlay overlay2 = new CacheOverlayTest.TestCacheOverlay1("overlay2", "cache1", provider1.getClass());
        CacheOverlay overlay3 = new CacheOverlayTest.TestCacheOverlay2("overlay3", "cache2", provider2.getClass());
        MapCache cache1 = new MapCache("cache1", provider1.getClass(), null, setOf(overlay1, overlay2));
        MapCache cache2 = new MapCache("cache1", provider2.getClass(), null, setOf(overlay3));

        when(cacheManager.getCaches()).thenReturn(setOf(cache1, cache2));

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(cacheManager, providers, null);

        OverlayOnMapManager.OverlayOnMap onMap1 = mockOverlayOnMap(overlayManager);
        OverlayOnMapManager.OverlayOnMap onMap2 = mockOverlayOnMap(overlayManager);
        OverlayOnMapManager.OverlayOnMap onMap3 = mockOverlayOnMap(overlayManager);

        when(provider1.createOverlayOnMapFromCache(overlay1, overlayManager)).thenReturn(onMap1);
        when(provider1.createOverlayOnMapFromCache(overlay2, overlayManager)).thenReturn(onMap2);
        when(provider2.createOverlayOnMapFromCache(overlay3, overlayManager)).thenReturn(onMap3);

        overlayManager.showOverlay(overlay1);
        overlayManager.showOverlay(overlay2);
        overlayManager.showOverlay(overlay3);

        when(onMap1.isOnMap()).thenReturn(true);
        when(onMap2.isOnMap()).thenReturn(true);
        when(onMap3.isOnMap()).thenReturn(true);

        overlayManager.dispose();

        verify(onMap1).removeFromMap();
        verify(onMap1).dispose();
        verify(onMap2).removeFromMap();
        verify(onMap2).dispose();
        verify(onMap3).removeFromMap();
        verify(onMap3).dispose();
    }
}
