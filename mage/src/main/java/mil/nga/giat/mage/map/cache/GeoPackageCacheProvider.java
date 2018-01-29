package mil.nga.giat.mage.map.cache;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.util.LongSparseArray;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.GeoPackageCache;
import mil.nga.geopackage.GeoPackageManager;
import mil.nga.geopackage.extension.link.FeatureTileTableLinker;
import mil.nga.geopackage.factory.GeoPackageFactory;
import mil.nga.geopackage.features.index.FeatureIndexManager;
import mil.nga.geopackage.features.user.FeatureCursor;
import mil.nga.geopackage.features.user.FeatureDao;
import mil.nga.geopackage.features.user.FeatureRow;
import mil.nga.geopackage.geom.GeoPackageGeometryData;
import mil.nga.geopackage.geom.map.GoogleMapShape;
import mil.nga.geopackage.geom.map.GoogleMapShapeConverter;
import mil.nga.geopackage.projection.Projection;
import mil.nga.geopackage.tiles.features.FeatureTiles;
import mil.nga.geopackage.tiles.features.MapFeatureTiles;
import mil.nga.geopackage.tiles.features.custom.NumberFeaturesTile;
import mil.nga.geopackage.tiles.overlay.BoundedOverlay;
import mil.nga.geopackage.tiles.overlay.FeatureOverlay;
import mil.nga.geopackage.tiles.overlay.FeatureOverlayQuery;
import mil.nga.geopackage.tiles.overlay.GeoPackageOverlayFactory;
import mil.nga.geopackage.tiles.user.TileDao;
import mil.nga.geopackage.validate.GeoPackageValidate;
import mil.nga.giat.mage.R;
import mil.nga.giat.mage.sdk.utils.MediaUtility;
import mil.nga.giat.mage.sdk.utils.StorageUtility;
import mil.nga.wkb.geom.Geometry;
import mil.nga.wkb.geom.GeometryType;

public class GeoPackageCacheProvider implements CacheProvider {

    private static final String LOG_NAME = GeoPackageCacheProvider.class.getName();
    private static final float Z_INDEX_TILE_TABLE = -2.0f;
    private static final float Z_INDEX_FEATURE_TABLE = -1.0f;

    /**
     * Get a cache name for the cache file
     *
     * @param manager
     * @param cacheFile
     * @return cache name
     */
    private static String makeUniqueCacheName(GeoPackageManager manager, File cacheFile) {
        String cacheName = MediaUtility.getFileNameWithoutExtension(cacheFile);
        final String baseCacheName = cacheName;
        int nameCount = 0;
        while (manager.exists(cacheName)) {
            cacheName = baseCacheName + "_" + (++nameCount);
        }
        return cacheName;
    }

    private final Context context;
    private final GeoPackageManager geoPackageManager;
    private final GeoPackageCache geoPackageCache;

    public GeoPackageCacheProvider(Context context) {
        this.context = context;
        geoPackageManager = GeoPackageFactory.getManager(context);
        geoPackageCache = new GeoPackageCache(geoPackageManager);
    }

    @Override
    public boolean isCacheFile(File cacheFile) {
        // Handle GeoPackage files by linking them to their current location
        return GeoPackageValidate.hasGeoPackageExtension(cacheFile);
    }

    @Override
    public MapCache importCacheFromFile(File cacheFile) throws CacheImportException {
        String cacheName = getOrImportGeoPackageDatabase(cacheFile);
        return createCache(cacheFile, cacheName);
    }

    @Override
    public Set<MapCache> refreshCaches(Set<MapCache> existingCaches) {
        Set<MapCache> refreshed = new HashSet<>(existingCaches.size());
        for (MapCache cache : existingCaches) {
            File dbFile = geoPackageManager.getFile(cache.getName());
            if (!dbFile.exists() || !dbFile.canRead()) {
                cache = null;
            }
            else if (dbFile.lastModified() > cache.getRefreshTimestamp()) {
                cache = createCache(dbFile, cache.getName());
            }
            else {
                cache.updateRefreshTimestamp();
            }

            if (cache != null) {
                refreshed.add(cache);
            }
        }

        return refreshed;

        // TODO: test getting rid of this in favor of above to keep records of
        // unavailable databases along with a persistent database name that
        // can be stored in preferences to persist z-order.  otherwise, there's
        // no guarantee that the database/cache name will be the same across
        // different imports because of makeUniqueCacheName() above
//        Set<CacheOverlay> overlays = new HashSet<>();
//        geoPackageManager.deleteAllMissingExternal();
//        List<String> externalDatabases = geoPackageManager.externalDatabases();
//        for (String database : externalDatabases) {
//            GeoPackageCacheOverlay cacheOverlay = createCache(database);
//            if (cacheOverlay != null) {
//                overlays.add(cacheOverlay);
//            }
//        }
//        return overlays;
    }

    /**
     * Import the GeoPackage file as an external link if it does not exist
     *
     * @param cacheFile
     * @return cache name when imported, null when not imported
     */
    @NonNull
    private String getOrImportGeoPackageDatabase(File cacheFile) throws CacheImportException {
        String databaseName = geoPackageManager.getDatabaseAtExternalFile(cacheFile);
        if (databaseName != null) {
            return databaseName;
        }

        databaseName = makeUniqueCacheName(geoPackageManager, cacheFile);
        CacheImportException fail;
        try {
            // import the GeoPackage as a linked file
            if (geoPackageManager.importGeoPackageAsExternalLink(cacheFile, databaseName)) {
                return databaseName;
            }
            fail = new CacheImportException(cacheFile, "GeoPackage import failed: " + cacheFile.getName());
        }
        catch (Exception e) {
            Log.e(LOG_NAME, "Failed to import file as GeoPackage. path: " + cacheFile.getAbsolutePath() + ", name: " + databaseName + ", error: " + e.getMessage());
            fail = new CacheImportException(cacheFile, "GeoPackage import threw exception", e);
        }

        if (cacheFile.canWrite()) {
            try {
                cacheFile.delete();
            }
            catch (Exception deleteException) {
                Log.e(LOG_NAME, "Failed to delete file: " + cacheFile.getAbsolutePath() + ", error: " + deleteException.getMessage());
            }
        }

        throw fail;
    }

    /**
     * Get the GeoPackage database as a cache overlay
     *
     * @param database
     * @return cache overlay
     */
    private MapCache createCache(File sourceFile, String database) {

        GeoPackage geoPackage = null;

        // Add the GeoPackage overlay
        try {
            geoPackage = geoPackageManager.open(database);
            Set<CacheOverlay> tables = new HashSet<>();

            // GeoPackage tile tables, build a mapping between table name and the created cache overlays
            Map<String, GeoPackageTileTableCacheOverlay> tileCacheOverlays = new HashMap<>();
            List<String> tileTables = geoPackage.getTileTables();
            for (String tableName : tileTables) {
                TileDao tileDao = geoPackage.getTileDao(tableName);
                int count = tileDao.count();
                int minZoom = (int) tileDao.getMinZoom();
                int maxZoom = (int) tileDao.getMaxZoom();
                GeoPackageTileTableCacheOverlay tableCache = new GeoPackageTileTableCacheOverlay(database, tableName, count, minZoom, maxZoom);
                tileCacheOverlays.put(tableName, tableCache);
            }

            // Get a linker to find tile tables linked to features
            FeatureTileTableLinker linker = new FeatureTileTableLinker(geoPackage);
            Map<String, GeoPackageTileTableCacheOverlay> linkedTileCacheOverlays = new HashMap<>();

            // GeoPackage feature tables
            List<String> featureTables = geoPackage.getFeatureTables();
            for (String tableName : featureTables) {
                FeatureDao featureDao = geoPackage.getFeatureDao(tableName);
                int count = featureDao.count();
                FeatureIndexManager indexer = new FeatureIndexManager(context, geoPackage, featureDao);
                boolean indexed = indexer.isIndexed();
                List<GeoPackageTileTableCacheOverlay> linkedTileTableCaches = new ArrayList<>();
                int minZoom = 0;
                if (indexed) {
                    minZoom = featureDao.getZoomLevel() + context.getResources().getInteger(R.integer.geopackage_feature_tiles_min_zoom_offset);
                    minZoom = Math.max(minZoom, 0);
                    minZoom = Math.min(minZoom, GeoPackageFeatureTableCacheOverlay.MAX_ZOOM);
                    List<String> linkedTileTables = linker.getTileTablesForFeatureTable(tableName);
                    for (String linkedTileTable : linkedTileTables) {
                        // Get the tile table cache overlay
                        GeoPackageTileTableCacheOverlay tileCacheOverlay = tileCacheOverlays.get(linkedTileTable);
                        if (tileCacheOverlay != null) {
                            // Remove from tile cache overlays so the tile table is not added as stand alone, and add to the linked overlays
                            tileCacheOverlays.remove(linkedTileTable);
                            linkedTileCacheOverlays.put(linkedTileTable, tileCacheOverlay);
                        }
                        else {
                            // Another feature table may already be linked to this table, so check the linked overlays
                            tileCacheOverlay = linkedTileCacheOverlays.get(linkedTileTable);
                        }

                        if (tileCacheOverlay != null) {
                            linkedTileTableCaches.add(tileCacheOverlay);
                        }
                    }
                }

                GeoPackageFeatureTableCacheOverlay tableCache = new GeoPackageFeatureTableCacheOverlay(
                    database, tableName, count, minZoom, indexed, linkedTileTableCaches);
                tables.add(tableCache);
            }

            // Add stand alone tile tables that were not linked to feature tables
            tables.addAll(tileCacheOverlays.values());

            return new MapCache(database, this.getClass(), sourceFile, tables);
        }
        catch (Exception e) {
            Log.e(LOG_NAME, "error creating GeoPackage cache", e);
        }
        finally {
            if (geoPackage != null) {
                geoPackage.close();
            }
        }

        return null;
    }

    class TileTableOnMap extends OverlayOnMapManager.OverlayOnMap {

        private final GoogleMap map;
        private final GeoPackageTileTableCacheOverlay cache;
        private final TileOverlayOptions options;
        /**
         * Used to query the backing feature tables
         */
        private final List<FeatureOverlayQuery> featureOverlayQueries = new ArrayList<>();
        private TileOverlay tileOverlay;


        private TileTableOnMap(OverlayOnMapManager manager, GeoPackageTileTableCacheOverlay cache, TileOverlayOptions overlayOptions) {
            manager.super();
            this.map = manager.getMap();
            this.cache = cache;
            this.options = overlayOptions;
        }

        @Override
        public void addToMapWithVisibility(boolean visible) {
            if (tileOverlay == null) {
                options.visible(visible);
                tileOverlay = map.addTileOverlay(options);
            }
        }

        @Override
        public void removeFromMap() {
            if (tileOverlay != null) {
                tileOverlay.remove();
                tileOverlay = null;
            }
        }

        @Override
        public void zoomMapToBoundingBox() {
        }

        @Override
        public void show() {
            if (tileOverlay != null) {
                tileOverlay.setVisible(true);
            }
        }

        @Override
        public void hide() {
            if (tileOverlay != null) {
                tileOverlay.setVisible(false);
            }
        }

        @Override
        public boolean isOnMap() {
            return tileOverlay != null;
        }

        @Override
        public boolean isVisible() {
            return tileOverlay != null && tileOverlay.isVisible();
        }

        @Override
        public String onMapClick(LatLng latLng, MapView mapView) {
            StringBuilder message = new StringBuilder();
            for(FeatureOverlayQuery featureOverlayQuery: featureOverlayQueries) {
                String overlayMessage = featureOverlayQuery.buildMapClickMessage(latLng, mapView, map);
                if (overlayMessage != null) {
                    if (message.length() > 0) {
                        message.append("\n\n");
                    }
                    message.append(overlayMessage);
                }
            }
            return message.length() > 0 ? message.toString() : null;
        }

        void addFeatureOverlayQuery(FeatureOverlayQuery featureOverlayQuery){
            featureOverlayQueries.add(featureOverlayQuery);
        }

        void clearFeatureOverlayQueries(){
            Iterator<FeatureOverlayQuery> queryIter = featureOverlayQueries.iterator();
            while (queryIter.hasNext()) {
                FeatureOverlayQuery query = queryIter.next();
                queryIter.remove();
                query.close();
            }
        }

        @Override
        protected void dispose() {
            removeFromMap();
            clearFeatureOverlayQueries();
        }
    }

    class FeatureTableOnMap extends OverlayOnMapManager.OverlayOnMap {

        private final GoogleMap map;
        private final List<TileTableOnMap> linkedTiles;
        private final TileOverlayOptions tileOptions;
        private final FeatureOverlayQuery query;
        /**
         * keys are feature IDs from GeoPackage table
         * // TODO: initialize this on the background thread and pass it to GeoPackageFeatureTableCacheOverlay
         */
        private final LongSparseArray<GoogleMapShape> shapeOptions;
        private final LongSparseArray<GoogleMapShape> shapesOnMap;
        private TileOverlay overlay;
        private boolean visible;
        private boolean onMap;

        FeatureTableOnMap(OverlayOnMapManager manager, List<TileTableOnMap> linkedTiles, TileOverlayOptions tileOptions, FeatureOverlayQuery query) {
            manager.super();
            this.map = manager.getMap();
            this.linkedTiles = linkedTiles;
            this.tileOptions = tileOptions;
            this.query = query;
            shapeOptions = new LongSparseArray<>(0);
            shapesOnMap = new LongSparseArray<>(0);
        }

        FeatureTableOnMap(OverlayOnMapManager manager, List<TileTableOnMap> linkedTiles, LongSparseArray<GoogleMapShape> shapeOptions) {
            manager.super();
            this.map = manager.getMap();
            this.linkedTiles = linkedTiles;
            this.shapeOptions = shapeOptions;
            this.shapesOnMap = new LongSparseArray<>(shapeOptions.size());
            tileOptions = null;
            query = null;
        }

        @Override
        protected void addToMapWithVisibility(boolean visible) {
            for (TileTableOnMap linkedTileTable : linkedTiles){
                linkedTileTable.addToMapWithVisibility(visible);
            }
            if (tileOptions != null) {
                if (overlay == null) {
                    tileOptions.visible(visible);
                    overlay = map.addTileOverlay(tileOptions);
                }
            }
            else if (visible) {
                addShapes();
            }
            this.visible = visible;
            onMap = true;
        }

        @Override
        protected void removeFromMap() {
            removeShapes();
            if (overlay != null) {
                overlay.remove();
                overlay = null;
            }
            for (TileTableOnMap linkedTileTable : linkedTiles){
                linkedTileTable.removeFromMap();
            }
            visible = false;
            onMap = false;
        }

        @Override
        protected void zoomMapToBoundingBox() {
            // TODO
        }

        @Override
        protected void show() {
            if (visible) {
                return;
            }
            for (TileTableOnMap linkedTileTable : linkedTiles) {
                linkedTileTable.show();
            }
            if (overlay != null) {
                overlay.setVisible(true);
            }
            else {
                // TODO: GoogleMapShape needs to support visibility flag
                addShapes();
            }
            visible = true;
        }

        @Override
        protected void hide() {
            if (!visible) {
                return;
            }
            for (TileTableOnMap linkedTileTable : linkedTiles) {
                linkedTileTable.hide();
            }
            if (overlay != null) {
                overlay.setVisible(false);
            }
            else {
                removeShapes();
            }
            visible = false;
        }

        @Override
        protected String onMapClick(LatLng latLng, MapView mapView) {
            String message = null;
            if (query != null) {
                message = query.buildMapClickMessage(latLng, mapView, map);
            }
            return message;
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
        protected void dispose() {
            removeFromMap();
            for (TileTableOnMap tilesOnMap : linkedTiles) {
                tilesOnMap.dispose();
            }
            if (query != null) {
                query.close();
            }
            shapeOptions.clear();
        }

        private void addShapes() {
            for (int i = 0; i < shapeOptions.size(); i++) {
                GoogleMapShape shape = shapeOptions.valueAt(i);
                GoogleMapShape shapeOnMap = GoogleMapShapeConverter.addShapeToMap(map, shape);
                shapesOnMap.put(shapeOptions.keyAt(i), shapeOnMap);
            }
        }

        private void removeShapes() {
            for (int i = 0; i < shapesOnMap.size(); i++) {
                GoogleMapShape shape = shapesOnMap.valueAt(i);
                shape.remove();
            }
            shapesOnMap.clear();
        }
    }

    @Override
    public OverlayOnMapManager.OverlayOnMap createOverlayOnMapFromCache(CacheOverlay cache, OverlayOnMapManager mapManager) {
        if (cache instanceof GeoPackageTileTableCacheOverlay) {
            return createOverlayOnMap((GeoPackageTileTableCacheOverlay) cache, mapManager);
        }
        else if (cache instanceof GeoPackageFeatureTableCacheOverlay) {
            return createOverlayOnMap((GeoPackageFeatureTableCacheOverlay) cache, mapManager);
        }

        throw new IllegalArgumentException(getClass().getSimpleName() + " does not support " + cache + " of type " + cache.getCacheType() );
    }

    private TileTableOnMap createOverlayOnMap(GeoPackageTileTableCacheOverlay tableCache, OverlayOnMapManager mapManager) {
        GeoPackage geoPackage = geoPackageCache.getOrOpen(tableCache.getGeoPackage());
        TileDao tileDao = geoPackage.getTileDao(tableCache.getTableName());
        BoundedOverlay geoPackageTileProvider = GeoPackageOverlayFactory.getBoundedOverlay(tileDao);
        TileOverlayOptions overlayOptions = new TileOverlayOptions()
            .tileProvider(geoPackageTileProvider)
            .zIndex(Z_INDEX_TILE_TABLE);
        TileTableOnMap onMap = new TileTableOnMap(mapManager, tableCache, overlayOptions);
        // Check for linked feature tables
        FeatureTileTableLinker linker = new FeatureTileTableLinker(geoPackage);
        List<FeatureDao> featureDaos = linker.getFeatureDaosForTileTable(tileDao.getTableName());
        for (FeatureDao featureDao: featureDaos) {
            FeatureTiles featureTiles = new MapFeatureTiles(context, featureDao);
            FeatureIndexManager indexer = new FeatureIndexManager(context, geoPackage, featureDao);
            featureTiles.setIndexManager(indexer);
            FeatureOverlayQuery featureOverlayQuery = new FeatureOverlayQuery(context, geoPackageTileProvider, featureTiles);
            onMap.addFeatureOverlayQuery(featureOverlayQuery);
        }
        return onMap;
    }

    private FeatureTableOnMap createOverlayOnMap(GeoPackageFeatureTableCacheOverlay featureTableCache, OverlayOnMapManager mapManager) {
        List<TileTableOnMap> linkedTiles = new ArrayList<>(featureTableCache.getLinkedTileTables().size());
        for (GeoPackageTileTableCacheOverlay linkedTileTable : featureTableCache.getLinkedTileTables()) {
            TileTableOnMap tiles = createOverlayOnMap(linkedTileTable, mapManager);
            linkedTiles.add(tiles);
        }

        GeoPackage geoPackage = geoPackageCache.getOrOpen(featureTableCache.getGeoPackage());
        // Add the features to the map
        FeatureDao featureDao = geoPackage.getFeatureDao(featureTableCache.getTableName());
        // If indexed, add as a tile overlay
        if (featureTableCache.isIndexed()) {
            FeatureTiles featureTiles = new MapFeatureTiles(context, featureDao);
            Integer maxFeaturesPerTile = null;
            if (featureDao.getGeometryType() == GeometryType.POINT) {
                maxFeaturesPerTile = context.getResources().getInteger(R.integer.geopackage_feature_tiles_max_points_per_tile);
            }
            else {
                maxFeaturesPerTile = context.getResources().getInteger(R.integer.geopackage_feature_tiles_max_features_per_tile);
            }
            featureTiles.setMaxFeaturesPerTile(maxFeaturesPerTile);
            NumberFeaturesTile numberFeaturesTile = new NumberFeaturesTile(context);
            // Adjust the max features number tile draw paint attributes here as needed to
            // change how tiles are drawn when more than the max features exist in a tile
            featureTiles.setMaxFeaturesTileDraw(numberFeaturesTile);
            featureTiles.setIndexManager(new FeatureIndexManager(context, geoPackage, featureDao));
            // Adjust the feature tiles draw paint attributes here as needed to change how
            // features are drawn on tiles
            FeatureOverlay tileProvider = new FeatureOverlay(featureTiles);
            tileProvider.setMinZoom(featureTableCache.getMinZoom());
            FeatureTileTableLinker linker = new FeatureTileTableLinker(geoPackage);
            List<TileDao> tileDaos = linker.getTileDaosForFeatureTable(featureDao.getTableName());
            tileProvider.ignoreTileDaos(tileDaos);
            TileOverlayOptions overlayOptions = new TileOverlayOptions()
                .zIndex(Z_INDEX_FEATURE_TABLE)
                .tileProvider(tileProvider);
            FeatureOverlayQuery featureQuery = new FeatureOverlayQuery(context, tileProvider);
            return new FeatureTableOnMap(mapManager, linkedTiles, overlayOptions, featureQuery);
        }
        // Not indexed, add the features to the map
        else {
            int maxFeaturesPerTable = 0;
            if (featureDao.getGeometryType() == GeometryType.POINT) {
                maxFeaturesPerTable = context.getResources().getInteger(R.integer.geopackage_features_max_points_per_table);
            }
            else {
                maxFeaturesPerTable = context.getResources().getInteger(R.integer.geopackage_features_max_features_per_table);
            }
            LongSparseArray<GoogleMapShape> shapes = new LongSparseArray<>(maxFeaturesPerTable);
            Projection projection = featureDao.getProjection();
            GoogleMapShapeConverter shapeConverter = new GoogleMapShapeConverter(projection);
            FeatureCursor featureCursor = featureDao.queryForAll();
            final int numFeaturesInTable;
            try {
                numFeaturesInTable = featureCursor.getCount();
                while (featureCursor.moveToNext() && shapes.size() < maxFeaturesPerTable) {
                    FeatureRow featureRow = featureCursor.getRow();
                    GeoPackageGeometryData geometryData = featureRow.getGeometry();
                    if (geometryData != null && !geometryData.isEmpty()) {
                        Geometry geometry = geometryData.getGeometry();
                        if (geometry != null) {
                            GoogleMapShape shape = shapeConverter.toShape(geometry);
                            // Set the Shape Marker, PolylineOptions, and PolygonOptions here if needed to change color and style
                            shapes.put(featureRow.getId(), shape);
                        }
                    }
                }
            }
            finally {
                featureCursor.close();
            }

            if (shapes.size() < numFeaturesInTable) {
                // TODO: don't really like doing any UI stuff here
                Toast.makeText(context, featureTableCache.getTableName()
                    + "- added " + shapes.size() + " of " + numFeaturesInTable, Toast.LENGTH_LONG).show();
            }

            return new FeatureTableOnMap(mapManager, linkedTiles, shapes);
        }
    }

    /**
     * Delete the GeoPackage cache overlay
     *
     * TODO: this was originally in TileOverlayPreferenceActivity to handle deleting on long press
     * this logic to go searching through directories to delete the cache file should be reworked
     */
    private void deleteGeoPackageCacheOverlay(MapCache cache){

        String database = cache.getName();

        // Get the GeoPackage file
        GeoPackageManager manager = GeoPackageFactory.getManager(context);
        File path = manager.getFile(database);

        // Delete the cache from the GeoPackage manager
        manager.delete(database);

        // Attempt to delete the cache file if it is in the cache directory
        File pathDirectory = path.getParentFile();
        if (path.canWrite() && pathDirectory != null) {
            // TODO: this should be in CacheManager
            Map<StorageUtility.StorageType, File> storageLocations = StorageUtility.getWritableStorageLocations();
            for (File storageLocation : storageLocations.values()) {
                File root = new File(storageLocation, context.getString(R.string.overlay_cache_directory));
                if (root.equals(pathDirectory)) {
                    path.delete();
                    break;
                }
            }
        }

        // Check internal/external application storage
        File applicationCacheDirectory = DefaultCacheLocationProvider.getApplicationCacheDirectory(context);
        if (applicationCacheDirectory != null && applicationCacheDirectory.exists()) {
            for (File cacheFile : applicationCacheDirectory.listFiles()) {
                if (cacheFile.equals(path)) {
                    path.delete();
                    break;
                }
            }
        }
    }
}
