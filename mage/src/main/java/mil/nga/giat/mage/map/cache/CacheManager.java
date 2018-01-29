package mil.nga.giat.mage.map.cache;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.GeoPackageManager;
import mil.nga.geopackage.extension.link.FeatureTileTableLinker;
import mil.nga.geopackage.factory.GeoPackageFactory;
import mil.nga.geopackage.features.index.FeatureIndexManager;
import mil.nga.geopackage.features.user.FeatureDao;
import mil.nga.geopackage.tiles.user.TileDao;
import mil.nga.geopackage.validate.GeoPackageValidate;
import mil.nga.giat.mage.R;
import mil.nga.giat.mage.cache.CacheUtils;
import mil.nga.giat.mage.cache.GeoPackageCacheUtils;
import mil.nga.giat.mage.sdk.utils.StorageUtility;
import mil.nga.wkb.geom.GeometryType;

/**
 * Created by wnewman on 2/11/16.
 */
public class CacheManager {

    public interface OnCacheOverlayListener {
        void onCacheOverlay(List<CacheOverlay> cacheOverlays);
    }

    private static final String LOG_NAME = CacheManager.class.getName();

    private static CacheManager instance = null;

    public static synchronized void initializeWithAppContext(Application context) {
        if (instance == null) {
            instance = new CacheManager(context);
            return;
        }
        throw new Error("attempt to initialize " + CacheManager.class + " singleton more than once");
    }

    public static CacheManager getInstance() {
        return instance;
    }

    private Application context;
    private List<CacheOverlay> cacheOverlays;
    private Map<CacheOverlay, Long> cacheIds = new HashMap<>();
    private Collection<OnCacheOverlayListener> cacheOverlayListeners = new ArrayList<>();

    private CacheManager(Application context) {
        this.context = context;
    }

    public void tryImportCacheFile(File cacheFile) {

        // Handle GeoPackage files by linking them to their current location
        if (GeoPackageValidate.hasGeoPackageExtension(cacheFile)) {

            String cacheName = GeoPackageCacheUtils.importGeoPackage(context, cacheFile);
            if (cacheName != null) {
                refreshAndEnableOverlay(cacheName);
            }
        }
    }

    public Long idOfCacheOverlay(CacheOverlay overlay) {
        return cacheIds.get(overlay);
    }

    public void registerCacheOverlayListener(OnCacheOverlayListener listener) {
        cacheOverlayListeners.add(listener);
        if (cacheOverlays != null)
            listener.onCacheOverlay(cacheOverlays);
    }

    public void removeCacheOverlay(String name) {
        if (cacheOverlays == null) {
            return;
        }
        Iterator<CacheOverlay> iterator = cacheOverlays.iterator();
        while (iterator.hasNext()) {
            CacheOverlay cacheOverlay = iterator.next();
            if (cacheOverlay.getOverlayName().equalsIgnoreCase(name)) {
                iterator.remove();
                return;
            }
        }
    }

    public void unregisterCacheOverlayListener(OnCacheOverlayListener listener) {
        cacheOverlayListeners.remove(listener);
    }

    public void refreshTileOverlays() {
        FindCacheOverlaysTask task = new FindCacheOverlaysTask();
        task.execute();
    }

    public void refreshAndEnableOverlay(String enableOverlayName) {
        FindCacheOverlaysTask task = new FindCacheOverlaysTask(Collections.singleton(enableOverlayName));
        task.execute();
    }

    private void setCacheOverlays(List<CacheOverlay> update) {
        Set<CacheOverlay> updateSet = new HashSet<>(update);
        cacheOverlays.retainAll(updateSet);
        updateSet.removeAll(cacheOverlays);
        cacheOverlays.addAll(updateSet);
        cacheIds.keySet().retainAll(this.cacheOverlays);
        for (OnCacheOverlayListener listener : cacheOverlayListeners) {
            listener.onCacheOverlay(cacheOverlays);
        }
    }

    private class FindCacheOverlaysTask extends AsyncTask<Void, Void, List<CacheOverlay>> {

        private final Set<String> overlaysToEnable;

        FindCacheOverlaysTask() {
            this(Collections.<String>emptySet());
        }

        FindCacheOverlaysTask(Collection<String> overlaysToEnable) {
            this.overlaysToEnable = new TreeSet<>(overlaysToEnable);
        }

        @Override
        protected List<CacheOverlay> doInBackground(Void... params) {
            List<CacheOverlay> overlays = new ArrayList<>();

            // Add the existing external GeoPackage databases as cache overlays
            GeoPackageManager geoPackageManager = GeoPackageFactory.getManager(context);
            addGeoPackageCacheOverlays(context, overlays, geoPackageManager);

            // Get public external caches stored in /MapCache folder
            Map<StorageUtility.StorageType, File> storageLocations = StorageUtility.getReadableStorageLocations();
            for (File storageLocation : storageLocations.values()) {
                File root = new File(storageLocation, context.getString(R.string.overlay_cache_directory));
                if (root.exists() && root.isDirectory() && root.canRead()) {
                    for (File path : root.listFiles()) {
                        if (path.canRead()) {
                            if (path.isDirectory()) {
                                overlays.add(new XYZDirectoryCacheOverlay(path.getName(), path));
                            } else if (GeoPackageValidate.hasGeoPackageExtension(path)) {
                                GeoPackageCacheOverlay cacheOverlay = getGeoPackageCacheOverlay(context, path, geoPackageManager);
                                if (cacheOverlay != null) {
                                    overlays.add(cacheOverlay);
                                }
                            }
                        }
                    }
                }
            }

            // Check internal/external application storage
            File applicationCacheDirectory = CacheUtils.getApplicationCacheDirectory(context);
            if (applicationCacheDirectory != null && applicationCacheDirectory.exists()) {
                for (File cache : applicationCacheDirectory.listFiles()) {
                    if (GeoPackageValidate.hasGeoPackageExtension(cache)) {
                        GeoPackageCacheOverlay cacheOverlay = getGeoPackageCacheOverlay(context, cache, geoPackageManager);
                        if (cacheOverlay != null) {
                            overlays.add(cacheOverlay);
                        }
                    }
                }
            }

            // Set what should be enabled based on preferences.
            boolean update = false;
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            Set<String> updatedEnabledOverlays = new HashSet<>();
            updatedEnabledOverlays.addAll(preferences.getStringSet(context.getString(R.string.tileOverlaysKey), Collections.<String>emptySet()));
            Set<String> enabledOverlays = new HashSet<>();
            enabledOverlays.addAll(updatedEnabledOverlays);

            // Determine which caches are enabled
            for (CacheOverlay cacheOverlay : overlays) {

                // Check and enable the cache
                String cacheName = cacheOverlay.getOverlayName();
                if (enabledOverlays.remove(cacheName)) {
                    cacheOverlay.setEnabled(true);
                }

                // Check the child caches
                for (CacheOverlay childCache : cacheOverlay.getChildren()) {
                    if (enabledOverlays.remove(childCache.getOverlayName())) {
                        childCache.setEnabled(true);
                        cacheOverlay.setEnabled(true);
                    }
                }

                // Check for new caches to enable in the overlays and preferences
                if (overlaysToEnable.contains(cacheName)) {

                    update = true;
                    cacheOverlay.setEnabled(true);
                    cacheOverlay.setAdded(true);
                    if (cacheOverlay.isSupportsChildren()) {
                        for (CacheOverlay childCache : cacheOverlay.getChildren()) {
                            childCache.setEnabled(true);
                            updatedEnabledOverlays.add(childCache.getOverlayName());
                        }
                    } else {
                        updatedEnabledOverlays.add(cacheName);
                    }
                }

            }

            // Remove overlays in the preferences that no longer exist
            if (!enabledOverlays.isEmpty()) {
                updatedEnabledOverlays.removeAll(enabledOverlays);
                update = true;
            }

            // If new enabled cache overlays, update them in the preferences
            if (update) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putStringSet(context.getString(R.string.tileOverlaysKey), updatedEnabledOverlays);
                editor.apply();
            }

            return overlays;
        }

        @Override
        protected void onPostExecute(List<CacheOverlay> result) {
            setCacheOverlays(result);
        }
    }

    /**
     * Add GeoPackage Cache Overlay for the existing databases
     *
     * @param context
     * @param overlays
     * @param geoPackageManager
     */
    private void addGeoPackageCacheOverlays(Context context, List<CacheOverlay> overlays, GeoPackageManager geoPackageManager) {

        // Delete any GeoPackages where the file is no longer accessible
        geoPackageManager.deleteAllMissingExternal();

        // Add each existing database as a cache
        List<String> externalDatabases = geoPackageManager.externalDatabases();
        for (String database : externalDatabases) {
            GeoPackageCacheOverlay cacheOverlay = getGeoPackageCacheOverlay(context, geoPackageManager, database);
            if (cacheOverlay != null) {
                overlays.add(cacheOverlay);
            }
        }
    }

    /**
     * Get GeoPackage Cache Overlay for the database file
     *
     * @param context
     * @param cache
     * @param geoPackageManager
     * @return cache overlay
     */
    private GeoPackageCacheOverlay getGeoPackageCacheOverlay(Context context, File cache, GeoPackageManager geoPackageManager) {

        GeoPackageCacheOverlay cacheOverlay = null;

        // Import the GeoPackage if needed
        String cacheName = GeoPackageCacheUtils.importGeoPackage(geoPackageManager, cache);
        if(cacheName != null){
            // Get the GeoPackage overlay
            cacheOverlay = getGeoPackageCacheOverlay(context, geoPackageManager, cacheName);
        }

        return cacheOverlay;
    }

    /**
     * Get the GeoPackage database as a cache overlay
     *
     * @param context
     * @param geoPackageManager
     * @param database
     * @return cache overlay
     */
    private GeoPackageCacheOverlay getGeoPackageCacheOverlay(Context context, GeoPackageManager geoPackageManager, String database) {

        GeoPackageCacheOverlay cacheOverlay = null;
        GeoPackage geoPackage = null;

        // Add the GeoPackage overlay
        try {
            geoPackage = geoPackageManager.open(database);
            List<GeoPackageTableCacheOverlay> tables = new ArrayList<>();

            // GeoPackage tile tables, build a mapping between table name and the created cache overlays
            Map<String, GeoPackageTileTableCacheOverlay> tileCacheOverlays = new HashMap<>();
            List<String> tileTables = geoPackage.getTileTables();
            for (String tableName : tileTables) {
                String tableCacheName = CacheOverlay.buildChildCacheName(database, tableName);
                TileDao tileDao = geoPackage.getTileDao(tableName);
                int count = tileDao.count();
                int minZoom = (int) tileDao.getMinZoom();
                int maxZoom = (int) tileDao.getMaxZoom();
                GeoPackageTileTableCacheOverlay tableCache = new GeoPackageTileTableCacheOverlay(tableCacheName, database, tableName, count, minZoom, maxZoom);
                tileCacheOverlays.put(tableName, tableCache);
            }

            // Get a linker to find tile tables linked to features
            FeatureTileTableLinker linker = new FeatureTileTableLinker(geoPackage);
            Map<String, GeoPackageTileTableCacheOverlay> linkedTileCacheOverlays = new HashMap<>();

            // GeoPackage feature tables
            List<String> featureTables = geoPackage.getFeatureTables();
            for (String tableName : featureTables) {
                String tableCacheName = CacheOverlay.buildChildCacheName(database, tableName);
                FeatureDao featureDao = geoPackage.getFeatureDao(tableName);
                int count = featureDao.count();
                GeometryType geometryType = featureDao.getGeometryType();
                FeatureIndexManager indexer = new FeatureIndexManager(context, geoPackage, featureDao);
                boolean indexed = indexer.isIndexed();
                int minZoom = 0;
                if (indexed) {
                    minZoom = featureDao.getZoomLevel() + context.getResources().getInteger(R.integer.geopackage_feature_tiles_min_zoom_offset);
                    minZoom = Math.max(minZoom, 0);
                    minZoom = Math.min(minZoom, GeoPackageFeatureTableCacheOverlay.MAX_ZOOM);
                }
                GeoPackageFeatureTableCacheOverlay tableCache = new GeoPackageFeatureTableCacheOverlay(tableCacheName, database, tableName, count, minZoom, indexed, geometryType);

                // If indexed, check for linked tile tables
                if(indexed){
                    List<String> linkedTileTables = linker.getTileTablesForFeatureTable(tableName);
                    for(String linkedTileTable: linkedTileTables){
                        // Get the tile table cache overlay
                        GeoPackageTileTableCacheOverlay tileCacheOverlay = tileCacheOverlays.get(linkedTileTable);
                        if(tileCacheOverlay != null){
                            // Remove from tile cache overlays so the tile table is not added as stand alone, and add to the linked overlays
                            tileCacheOverlays.remove(linkedTileTable);
                            linkedTileCacheOverlays.put(linkedTileTable, tileCacheOverlay);
                        }else{
                            // Another feature table may already be linked to this table, so check the linked overlays
                            tileCacheOverlay = linkedTileCacheOverlays.get(linkedTileTable);
                        }

                        // Add the linked tile table to the feature table
                        if(tileCacheOverlay != null){
                            tableCache.addLinkedTileTable(tileCacheOverlay);
                        }
                    }
                }

                tables.add(tableCache);
            }

            // Add stand alone tile tables that were not linked to feature tables
            for(GeoPackageTileTableCacheOverlay tileCacheOverlay: tileCacheOverlays.values()){
                tables.add(tileCacheOverlay);
            }

            // Create the GeoPackage overlay with child tables
            cacheOverlay = new GeoPackageCacheOverlay(database, tables);
        } catch (Exception e) {
            Log.e(LOG_NAME, "Could not get geopackage cache", e);
        } finally {
            if (geoPackage != null) {
                geoPackage.close();
            }
        }

        return cacheOverlay;
    }

}
