package mil.nga.giat.mage.map;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.content.res.AppCompatResources;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.CancelableCallback;
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.GoogleMap.OnMapLongClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.GoogleMap.OnMyLocationButtonClickListener;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.LocationSource;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.TileProvider;
import com.google.common.collect.Sets;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mil.nga.geopackage.BoundingBox;
import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.GeoPackageCache;
import mil.nga.geopackage.GeoPackageManager;
import mil.nga.geopackage.core.contents.Contents;
import mil.nga.geopackage.core.contents.ContentsDao;
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
import mil.nga.geopackage.projection.ProjectionConstants;
import mil.nga.geopackage.projection.ProjectionFactory;
import mil.nga.geopackage.projection.ProjectionTransform;
import mil.nga.geopackage.tiles.TileBoundingBoxUtils;
import mil.nga.geopackage.tiles.features.FeatureTiles;
import mil.nga.geopackage.tiles.features.MapFeatureTiles;
import mil.nga.geopackage.tiles.features.custom.NumberFeaturesTile;
import mil.nga.geopackage.tiles.overlay.BoundedOverlay;
import mil.nga.geopackage.tiles.overlay.FeatureOverlay;
import mil.nga.geopackage.tiles.overlay.FeatureOverlayQuery;
import mil.nga.geopackage.tiles.overlay.GeoPackageOverlayFactory;
import mil.nga.geopackage.tiles.user.TileDao;
import mil.nga.giat.mage.MAGE;
import mil.nga.giat.mage.R;
import mil.nga.giat.mage.filter.DateTimeFilter;
import mil.nga.giat.mage.filter.Filter;
import mil.nga.giat.mage.filter.FilterActivity;
import mil.nga.giat.mage.map.GoogleMapWrapper.OnMapPanListener;
import mil.nga.giat.mage.map.cache.CacheOverlay;
import mil.nga.giat.mage.map.cache.CacheOverlayType;
import mil.nga.giat.mage.map.cache.CacheProvider;
import mil.nga.giat.mage.map.cache.CacheProvider.OnCacheOverlayListener;
import mil.nga.giat.mage.map.cache.GeoPackageCacheOverlay;
import mil.nga.giat.mage.map.cache.GeoPackageFeatureTableCacheOverlay;
import mil.nga.giat.mage.map.cache.GeoPackageTileTableCacheOverlay;
import mil.nga.giat.mage.map.cache.XYZDirectoryCacheOverlay;
import mil.nga.giat.mage.map.marker.LocationMarkerCollection;
import mil.nga.giat.mage.map.marker.MyHistoricalLocationMarkerCollection;
import mil.nga.giat.mage.map.marker.ObservationMarkerCollection;
import mil.nga.giat.mage.map.marker.PointCollection;
import mil.nga.giat.mage.map.marker.StaticGeometryCollection;
import mil.nga.giat.mage.observation.ObservationEditActivity;
import mil.nga.giat.mage.sdk.Temporal;
import mil.nga.giat.mage.sdk.datastore.layer.Layer;
import mil.nga.giat.mage.sdk.datastore.layer.LayerHelper;
import mil.nga.giat.mage.sdk.datastore.location.LocationHelper;
import mil.nga.giat.mage.sdk.datastore.location.LocationProperty;
import mil.nga.giat.mage.sdk.datastore.observation.Observation;
import mil.nga.giat.mage.sdk.datastore.observation.ObservationHelper;
import mil.nga.giat.mage.sdk.datastore.staticfeature.StaticFeatureHelper;
import mil.nga.giat.mage.sdk.datastore.user.EventHelper;
import mil.nga.giat.mage.sdk.datastore.user.User;
import mil.nga.giat.mage.sdk.datastore.user.UserHelper;
import mil.nga.giat.mage.sdk.event.ILocationEventListener;
import mil.nga.giat.mage.sdk.event.IObservationEventListener;
import mil.nga.giat.mage.sdk.event.IStaticFeatureEventListener;
import mil.nga.giat.mage.sdk.exceptions.LayerException;
import mil.nga.giat.mage.sdk.exceptions.UserException;
import mil.nga.giat.mage.sdk.location.LocationService;
import mil.nga.wkb.geom.GeometryType;

public class MapFragment extends Fragment
	implements OnMapReadyCallback, OnMapClickListener, OnMapLongClickListener, OnMarkerClickListener,
	OnInfoWindowClickListener, OnMapPanListener, OnMyLocationButtonClickListener, OnClickListener,
	LocationSource, LocationListener, OnCacheOverlayListener,
	IObservationEventListener, ILocationEventListener, IStaticFeatureEventListener {

	private static final String LOG_NAME = MapFragment.class.getName();
	private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
	private static final int MARKER_REFRESH_INTERVAL_SECONDS = 300;

	private class RefreshMarkersRunnable implements Runnable {
		private final PointCollection<?> points;

		private RefreshMarkersRunnable(PointCollection<?> points) {
			this.points = points;
		}

		public void run() {
			if (points.isVisible()) {
				points.refreshMarkerIcons();
			}
			beginMarkerRefresh(this);
		}
	}

	private MAGE mage;
	private ViewGroup container;
	private ViewGroup mapOverlaysContainer;
	private MapView mapView;
	private GoogleMap map;
	private boolean mapInitialized = false;
	private View searchLayout;
	private SearchView searchView;
	private Location location;
	private boolean followMe = false;
	private GoogleMapWrapper mapWrapper;
	private User currentUser = null;
	private OnLocationChangedListener locationChangedListener;

	private PointCollection<Observation> observations;
	private PointCollection<Pair<mil.nga.giat.mage.sdk.datastore.location.Location, User>> locations;
	private PointCollection<Pair<mil.nga.giat.mage.sdk.datastore.location.Location, User>> historicLocations;
	private StaticGeometryCollection staticGeometryCollection;
	private List<Marker> searchMarkers = new ArrayList<>();
	private RefreshMarkersRunnable refreshLocationsTask;
	private RefreshMarkersRunnable refreshHistoricLocationsTask;

	private Map<String, CacheOverlay> cacheOverlays = new HashMap<>();

	// GeoPackage cache of open GeoPackage connections
	private GeoPackageCache geoPackageCache;
	private BoundingBox addedCacheBoundingBox;

	private FloatingActionButton searchButton;
	private FloatingActionButton zoomToLocationButton;
	private FloatingActionButton overlaysButton;
	private FloatingActionButton newObservationButton;
	private LocationService locationService;

	private SharedPreferences preferences;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mage = (MAGE) getContext().getApplicationContext();

		// creating the MapView here should preserve it across configuration/layout changes - onConfigurationChanged()
		// and avoid redrawing map and markers and whatnot
		setRetainInstance(true);
		GoogleMapOptions opts = new GoogleMapOptions()
			.rotateGesturesEnabled(false)
			.tiltGesturesEnabled(false)
			.compassEnabled(false);
		mapView = new MapView(getContext(), opts);
		mapView.onCreate(savedInstanceState);
		mapView.getMapAsync(this);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		this.container = new FrameLayout(getContext());
		this.container.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		loadLayoutToContainer(inflater, savedInstanceState);
		return this.container;
	}

	@Override
	public void onStart() {
		super.onStart();
		mapView.onStart();
	}

	@Override
	public void onResume() {
		super.onResume();

		try {
			currentUser = UserHelper.getInstance(mage).readCurrentUser();
		} catch (UserException ue) {
			Log.e(LOG_NAME, "Could not find current user.", ue);
		}

		mapView.onResume();

		((AppCompatActivity) getActivity()).getSupportActionBar().setSubtitle(getFilterTitle());

		searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String query) {
				if (StringUtils.isNoneBlank(query)) {
					new GeocoderTask(getActivity(), map, searchMarkers).execute(query);
				}

				searchView.clearFocus();
				return true;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				if (StringUtils.isEmpty(newText)) {
					if (searchMarkers != null) {
						for (Marker m : searchMarkers) {
							m.remove();
						}
						searchMarkers.clear();
					}
				}

				return true;
			}
		});
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		cleanUpForLayoutChange();
		LayoutInflater inflater = LayoutInflater.from(getContext());
		loadLayoutToContainer(inflater, null);
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		mapView.onLowMemory();
	}

	@Override
	public void onPause() {
		super.onPause();
		mapView.onPause();

		CacheProvider.getInstance().unregisterCacheOverlayListener(this);
		StaticFeatureHelper.getInstance(mage).removeListener(this);

		if (map != null) {
			saveMapView();

			map.setLocationSource(null);
			if (locationService != null) {
				locationService.unregisterOnLocationListener(this);
			}

			mapInitialized = false;
		}

		getView().removeCallbacks(refreshLocationsTask);
		getView().removeCallbacks(refreshHistoricLocationsTask);
		refreshLocationsTask = null;
		refreshHistoricLocationsTask = null;
	}

	@Override
	public void onStop() {
		super.onStop();
		mapView.onStop();
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();

		ObservationHelper.getInstance(mage).removeListener(this);
		LocationHelper.getInstance(mage).removeListener(this);

		if (observations != null) {
			observations.clear();
			observations = null;
		}

		if (locations != null) {
			locations.clear();
			locations = null;
		}

		if (historicLocations != null) {
			historicLocations.clear();
			historicLocations = null;
		}

		if (searchMarkers != null) {
			for (Marker m : searchMarkers) {
				m.remove();
			}
			searchMarkers.clear();
		}

		// Close all open GeoPackages
		geoPackageCache.closeAll();

		cacheOverlays.clear();
		staticGeometryCollection = null;
		currentUser = null;
		map = null;
		mapInitialized = false;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		mapView.onSaveInstanceState(outState);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mapView.onDestroy();
	}

	private void beginMarkerRefresh(RefreshMarkersRunnable task) {
		getView().postDelayed(task, MARKER_REFRESH_INTERVAL_SECONDS * 1000);
	}

	private void cleanUpForLayoutChange() {
		container.removeAllViews();
		mapWrapper.removeAllViews();
		mapWrapper.setOnMapPanListener(null);
		zoomToLocationButton.setOnClickListener(null);
		searchButton.setOnClickListener(null);
		overlaysButton.setOnClickListener(null);
		newObservationButton.setOnClickListener(null);
	}

	private View loadLayoutToContainer(LayoutInflater inflater, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_map, container, true);

		setHasOptionsMenu(true);

		staticGeometryCollection = new StaticGeometryCollection();

		zoomToLocationButton = (FloatingActionButton) view.findViewById(R.id.zoom_button);
		zoomToLocationButton.setOnClickListener(this);

		searchButton = (FloatingActionButton) view.findViewById(R.id.map_search_button);
		Drawable drawable = DrawableCompat.wrap(searchButton.getDrawable());
		searchButton.setImageDrawable(drawable);
		DrawableCompat.setTintList(drawable, AppCompatResources.getColorStateList(getContext(), R.color.map_search_icon));
		searchButton.setOnClickListener(this);

		mapOverlaysContainer = (ViewGroup) view.findViewById(R.id.map_overlays_container);
		overlaysButton = (FloatingActionButton) view.findViewById(R.id.map_settings);
		overlaysButton.setOnClickListener(this);

		newObservationButton = (FloatingActionButton) view.findViewById(R.id.new_observation_button);
		newObservationButton.setOnClickListener(this);


		locationService = mage.getLocationService();

		preferences = PreferenceManager.getDefaultSharedPreferences(mage);

		searchLayout = view.findViewById(R.id.search_layout);
		searchView = (SearchView) view.findViewById(R.id.search_view);
		searchView.setIconifiedByDefault(false);
		searchView.setIconified(false);
		searchView.clearFocus();

		mapWrapper = (GoogleMapWrapper) view.findViewById(R.id.map_wrapper);
		mapWrapper.addView(mapView);

		GeoPackageManager geoPackageManager = GeoPackageFactory.getManager(mage);
		geoPackageCache = new GeoPackageCache(geoPackageManager);

		return view;
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.filter, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.filter_button:
				Intent intent = new Intent(getActivity(), FilterActivity.class);
				startActivity(intent);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onMapReady(GoogleMap googleMap) {
		map = googleMap;
		initializeMap();
	}

	private void initializeMap() {
		if (mapInitialized) {
			return;
		}

		mapInitialized = true;

		map.getUiSettings().setMyLocationButtonEnabled(false);
		map.setOnMapClickListener(this);
		map.setOnMarkerClickListener(this);
		map.setOnMapLongClickListener(this);
		map.setOnMyLocationButtonClickListener(this);
		map.setOnInfoWindowClickListener(this);

		historicLocations = new MyHistoricalLocationMarkerCollection(getActivity(), map);
		HistoricLocationLoadTask myHistoricLocationLoad = new HistoricLocationLoadTask(getActivity(), historicLocations);
		myHistoricLocationLoad.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

		if (observations != null) {
			observations.clear();
		}
		observations = new ObservationMarkerCollection(getActivity(), map);
		ObservationLoadTask observationLoad = new ObservationLoadTask(getActivity(), observations);
		observationLoad.addFilter(getTemporalFilter("timestamp"));
		observationLoad.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

		if (locations != null) {
			locations.clear();
		}
		locations = new LocationMarkerCollection(getActivity(), map);
		LocationLoadTask locationLoad = new LocationLoadTask(getActivity(), locations);
		locationLoad.setFilter(getTemporalFilter("timestamp"));
		locationLoad.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

		updateMapView();
		updateStaticFeatureLayers();

		// Set visibility on map markers as preferences may have changed
		observations.setVisibility(preferences.getBoolean(getResources().getString(R.string.showObservationsKey), true));
		locations.setVisibility(preferences.getBoolean(getResources().getString(R.string.showLocationsKey), true));
		historicLocations.setVisibility(preferences.getBoolean(getResources().getString(R.string.showMyLocationHistoryKey), false));

		// Check if any map preferences changed that I care about
		if (locationService != null && ContextCompat.checkSelfPermission(getActivity().getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
			map.setMyLocationEnabled(true);
			map.setLocationSource(this);
			locationService.registerOnLocationListener(this);
		} else {
			map.setMyLocationEnabled(false);
			map.setLocationSource(null);
		}

		ObservationHelper.getInstance(mage).addListener(this);
		LocationHelper.getInstance(mage).addListener(this);
		StaticFeatureHelper.getInstance(mage).addListener(this);
		CacheProvider.getInstance().registerCacheOverlayListener(this);

		refreshLocationsTask = new RefreshMarkersRunnable(locations);
		refreshHistoricLocationsTask = new RefreshMarkersRunnable(historicLocations);
		beginMarkerRefresh(refreshLocationsTask);
		beginMarkerRefresh(refreshHistoricLocationsTask);
	}

	private void onZoom() {
		if (!mapInitialized) {
			return;
		}
		Location location = locationService.getLocation();
		LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
		CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 18);
		map.animateCamera(cameraUpdate);
	}

	private void search() {
		boolean isVisible = searchLayout.getVisibility() == View.VISIBLE;
		searchLayout.setVisibility(isVisible ? View.GONE : View.VISIBLE);
		searchButton.setSelected(!isVisible);

		if (isVisible) {
			searchView.clearFocus();
		} else {
			searchView.requestFocus();
			InputMethodManager inputMethodManager = (InputMethodManager) getActivity().getSystemService(Activity.INPUT_METHOD_SERVICE);
			inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
		}
	}

	private void onNewObservation() {
		Intent intent = new Intent(mage, ObservationEditActivity.class);
		Location l = locationService.getLocation();

		// if there is not a location from the location service, then try to pull one from the database.
		if (l == null) {
			List<mil.nga.giat.mage.sdk.datastore.location.Location> tLocations = LocationHelper.getInstance(mage).getCurrentUserLocations(1, true);
			if (!tLocations.isEmpty()) {
				mil.nga.giat.mage.sdk.datastore.location.Location tLocation = tLocations.get(0);
				Geometry geo = tLocation.getGeometry();
				Map<String, LocationProperty> propertiesMap = tLocation.getPropertiesMap();
				if (geo instanceof Point) {
					Point point = (Point) geo;
					String provider = "manual";
					if (propertiesMap.get("provider").getValue() != null) {
						provider = propertiesMap.get("provider").getValue().toString();
					}
					l = new Location(provider);
					l.setTime(tLocation.getTimestamp().getTime());
					if (propertiesMap.get("accuracy").getValue() != null) {
						l.setAccuracy(Float.valueOf(propertiesMap.get("accuracy").getValue().toString()));
					}
					l.setLatitude(point.getY());
					l.setLongitude(point.getX());
				}
			}
		} else {
			l = new Location(l);
		}

		if (!UserHelper.getInstance(mage).isCurrentUserPartOfCurrentEvent()) {
			new AlertDialog.Builder(getActivity())
				.setTitle(getActivity().getResources().getString(R.string.location_no_event_title))
				.setMessage(getActivity().getResources().getString(R.string.location_no_event_message))
				.setPositiveButton(android.R.string.ok, null)
				.show();
		} else if (l != null) {
			intent.putExtra(ObservationEditActivity.LOCATION, l);
			intent.putExtra(ObservationEditActivity.INITIAL_LOCATION, map.getCameraPosition().target);
			intent.putExtra(ObservationEditActivity.INITIAL_ZOOM, map.getCameraPosition().zoom);
			startActivity(intent);
		} else {
			if (ContextCompat.checkSelfPermission(mage, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
				new AlertDialog.Builder(getActivity())
						.setTitle(getActivity().getResources().getString(R.string.location_missing_title))
						.setMessage(getActivity().getResources().getString(R.string.location_missing_message))
						.setPositiveButton(android.R.string.ok, null)
						.show();
			} else {
				new AlertDialog.Builder(getActivity())
						.setTitle(getActivity().getResources().getString(R.string.location_access_observation_title))
						.setMessage(getActivity().getResources().getString(R.string.location_access_observation_message))
						.setPositiveButton(android.R.string.ok, new Dialog.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
							}
						})
						.show();
			}
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		switch (requestCode) {
			case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
				// If request is cancelled, the result arrays are empty.
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					onNewObservation();
				}
				break;
			}
		}
	}

	@Override
	public void onObservationCreated(Collection<Observation> o, Boolean sendUserNotifcations) {
		if (observations != null) {
			ObservationTask task = new ObservationTask(getActivity(), ObservationTask.Type.ADD, observations);
			task.addFilter(getTemporalFilter("last_modified"));
			task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, o.toArray(new Observation[o.size()]));
		}
	}

	@Override
	public void onObservationUpdated(Observation o) {
		if (observations != null) {
			ObservationTask task = new ObservationTask(getActivity(), ObservationTask.Type.UPDATE, observations);
			task.addFilter(getTemporalFilter("last_modified"));
			task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, o);
		}
	}

	@Override
	public void onObservationDeleted(Observation o) {
		if (observations != null) {
			new ObservationTask(getActivity(), ObservationTask.Type.DELETE, observations).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, o);
		}
	}

	@Override
	public void onLocationCreated(Collection<mil.nga.giat.mage.sdk.datastore.location.Location> ls) {
		for (mil.nga.giat.mage.sdk.datastore.location.Location l : ls) {
			if (currentUser != null && !currentUser.getRemoteId().equals(l.getUser().getRemoteId())) {
				if (locations != null) {
					LocationTask task = new LocationTask(getActivity(), LocationTask.Type.ADD, locations);
					task.setFilter(getTemporalFilter("timestamp"));
					task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, l);
				}
			} else {
				if (historicLocations != null) {
					new LocationTask(getActivity(), LocationTask.Type.ADD, historicLocations).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, l);
				}
			}
		}
	}

	@Override
	public void onLocationUpdated(mil.nga.giat.mage.sdk.datastore.location.Location l) {
		if (currentUser != null && !currentUser.getRemoteId().equals(l.getUser().getRemoteId())) {
			if (locations != null) {
				LocationTask task = new LocationTask(getActivity(), LocationTask.Type.UPDATE, locations);
				task.setFilter(getTemporalFilter("timestamp"));
				task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, l);
			}
		} else {
			if (historicLocations != null) {
				new LocationTask(getActivity(), LocationTask.Type.UPDATE, historicLocations).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, l);
			}
		}
	}

	@Override
	public void onLocationDeleted(Collection<mil.nga.giat.mage.sdk.datastore.location.Location> l) {
		// this is slowing the app down a lot!  Moving the delete like code into the add methods of the collections
		/*
		if (currentUser != null && !currentUser.getRemoteId().equals(l.getUser().getRemoteId())) {
			if (locations != null) {
				new LocationTask(LocationTask.Type.DELETE, locations).execute(l);
			}
		} else {
			if (myHistoricLocations != null) {
				new LocationTask(LocationTask.Type.DELETE, myHistoricLocations).execute(l);
			}
		}
		*/
	}
	
	@Override
	public void onInfoWindowClick(Marker marker) {
		observations.onInfoWindowClick(marker);
		locations.onInfoWindowClick(marker);
	}

	@Override
	public boolean onMarkerClick(Marker marker) {
		hideKeyboard();
		// search marker
		if(searchMarkers != null) {
			for(Marker m :searchMarkers) {
				 if(marker.getId().equals(m.getId())) {
						m.showInfoWindow();
						return true;		 
				 }
			}
		}
		
		// You can only have one marker click listener per map.
		// Lets listen here and shell out the click event to all
		// my marker collections. Each one need to handle
		// gracefully if it does not actually contain the marker
		if (observations.onMarkerClick(marker)) {
			return true;
		}

		if (locations.onMarkerClick(marker)) {
			return true;
		}

		if (historicLocations.onMarkerClick(marker)) {
			return true;
		}

		// static layer
		if(marker.getSnippet() != null) {
			View markerInfoWindow = LayoutInflater.from(getActivity()).inflate(R.layout.static_feature_infowindow, null, false);
			WebView webView = ((WebView) markerInfoWindow.findViewById(R.id.static_feature_infowindow_content));
			webView.loadData(marker.getSnippet(), "text/html; charset=UTF-8", null);
			new AlertDialog.Builder(getActivity())
				.setView(markerInfoWindow)
				.setPositiveButton(android.R.string.yes, null)
				.show();
		}
		return true;
	}

	@Override
	public void onMapClick(LatLng latLng) {
		hideKeyboard();
		// remove old accuracy circle
		((LocationMarkerCollection) locations).offMarkerClick();

		staticGeometryCollection.onMapClick(map, latLng, getActivity());

		if(!cacheOverlays.isEmpty()) {
			StringBuilder clickMessage = new StringBuilder();
			for (CacheOverlay cacheOverlay : cacheOverlays.values()) {
				String message = cacheOverlay.onMapClick(latLng, mapView, map);
				if(message != null){
					if(clickMessage.length() > 0){
						clickMessage.append("\n\n");
					}
					clickMessage.append(message);
				}
			}
			if(clickMessage.length() > 0) {
				new AlertDialog.Builder(getActivity())
					.setMessage(clickMessage.toString())
					.setPositiveButton(android.R.string.yes, null)
					.show();
			}
		}
	}

	@Override
	public void onMapLongClick(LatLng point) {
		hideKeyboard();
		if(!UserHelper.getInstance(mage).isCurrentUserPartOfCurrentEvent()) {
			new AlertDialog.Builder(getActivity())
				.setTitle(getActivity().getResources().getString(R.string.location_no_event_title))
				.setMessage(getActivity().getResources().getString(R.string.location_no_event_message))
				.setPositiveButton(android.R.string.ok, null)
				.show();
		} else {
			Intent intent = new Intent(mage, ObservationEditActivity.class);
			Location l = new Location("manual");
			l.setAccuracy(0.0f);
			l.setLatitude(point.latitude);
			l.setLongitude(point.longitude);
			l.setTime(new Date().getTime());
			intent.putExtra(ObservationEditActivity.LOCATION, l);
			startActivity(intent);
		}
	}

	@Override
	public void onClick(View view) {
		// close keyboard
		hideKeyboard();
		int target = view.getId();

		switch (target) {
			case R.id.zoom_button:
				onZoom();
			case R.id.map_search_button:
				search();
				return;
			case R.id.map_settings:
				View overlays = getView().findViewById(R.id.map_overlays_container);
				overlays.setVisibility(overlays.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
				return;
			case R.id.new_observation_button:
				onNewObservation();
				return;

		}
	}

	@Override
	public boolean onMyLocationButtonClick() {
		if (location != null) {
			LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
			float zoom = map.getCameraPosition().zoom < 15 ? 15 : map.getCameraPosition().zoom;
			map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom), new CancelableCallback() {

				@Override
				public void onCancel() {
					mapWrapper.setOnMapPanListener(MapFragment.this);
					followMe = true;
				}

				@Override
				public void onFinish() {
					mapWrapper.setOnMapPanListener(MapFragment.this);
					followMe = true;
				}
			});
		}
		return true;
	}

	@Override
	public void activate(OnLocationChangedListener listener) {
		Log.i(LOG_NAME, "map location, activate");
		locationChangedListener = listener;
		if (location != null) {
			Log.i(LOG_NAME, "map location, activate we have a location, let our listener know");
			locationChangedListener.onLocationChanged(location);
		}
	}

	@Override
	public void deactivate() {
		Log.i(LOG_NAME, "map location, deactivate");
		locationChangedListener = null;
	}

	@Override
	public void onMapPan() {
		mapWrapper.setOnMapPanListener(null);
		followMe = false;
	}

	@Override
	public void onLocationChanged(Location location) {
		this.location = location;
		Log.d(LOG_NAME, "Map location updated.");
		if (locationChangedListener != null) {
			locationChangedListener.onLocationChanged(location);
		}

		if (followMe) {
			LatLngBounds bounds = map.getProjection().getVisibleRegion().latLngBounds;
			LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
			if (!bounds.contains(latLng)) {
				// Move the camera to the user's location once it's available!
				map.animateCamera(CameraUpdateFactory.newLatLng(latLng));
			}
		}
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
	}

	@Override
	public void onProviderEnabled(String provider) {
	}

	@Override
	public void onProviderDisabled(String provider) {
	}

	@Override
	public void onCacheOverlay(List<CacheOverlay> cacheOverlays) {

		// Add all overlays that are in the preferences

		// Track enabled cache overlays
		Map<String, CacheOverlay> enabledCacheOverlays = new HashMap<>();

		// Track enabled GeoPackages
		Set<String> enabledGeoPackages = new HashSet<>();

		// Reset the bounding box for newly added caches
		addedCacheBoundingBox = null;

		for (CacheOverlay cacheOverlay : cacheOverlays) {

			// If this cache overlay potentially replaced by a new version
			if(cacheOverlay.isAdded()){
				if(cacheOverlay.getType() == CacheOverlayType.GEOPACKAGE){
					geoPackageCache.close(cacheOverlay.getOverlayName());
				}
			}

			// The user has asked for this overlay
			if (cacheOverlay.isEnabled()) {

				// Handle each type of cache overlay
				switch(cacheOverlay.getType()) {

					case XYZ_DIRECTORY:
						addXYZDirectoryCacheOverlay(enabledCacheOverlays, (XYZDirectoryCacheOverlay) cacheOverlay);
						break;

					case GEOPACKAGE:
						addGeoPackageCacheOverlay(enabledCacheOverlays, enabledGeoPackages, (GeoPackageCacheOverlay)cacheOverlay);
						break;
				}
			}

			cacheOverlay.setAdded(false);
		}

		// Remove any overlays that are on the map but no longer selected in
		// preferences, update the tile overlays to the enabled tile overlays
		for (CacheOverlay cacheOverlay : this.cacheOverlays.values()) {
			cacheOverlay.removeFromMap();
		}
		this.cacheOverlays = enabledCacheOverlays;

		// Close GeoPackages no longer enabled
		geoPackageCache.closeRetain(enabledGeoPackages);

		// If a new cache was added, zoom to the bounding box area
		if(addedCacheBoundingBox != null){

			final LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
			boundsBuilder.include(new LatLng(addedCacheBoundingBox.getMinLatitude(), addedCacheBoundingBox
					.getMinLongitude()));
			boundsBuilder.include(new LatLng(addedCacheBoundingBox.getMinLatitude(), addedCacheBoundingBox
					.getMaxLongitude()));
			boundsBuilder.include(new LatLng(addedCacheBoundingBox.getMaxLatitude(), addedCacheBoundingBox
					.getMinLongitude()));
			boundsBuilder.include(new LatLng(addedCacheBoundingBox.getMaxLatitude(), addedCacheBoundingBox
					.getMaxLongitude()));

			try {
				map.animateCamera(CameraUpdateFactory.newLatLngBounds(
						boundsBuilder.build(), 0));
			} catch (Exception e) {
				Log.e(LOG_NAME, "Unable to move camera to newly added cache location", e);
			}
		}
	}

	/**
	 * Add XYZ Directory tile cache overlay
	 * @param enabledCacheOverlays
	 * @param xyzDirectoryCacheOverlay
	 */
	private void addXYZDirectoryCacheOverlay(Map<String, CacheOverlay> enabledCacheOverlays, XYZDirectoryCacheOverlay xyzDirectoryCacheOverlay){
		// Retrieve the cache overlay if it already exists (and remove from cache overlays)
		CacheOverlay cacheOverlay = cacheOverlays.remove(xyzDirectoryCacheOverlay.getOverlayName());
		if(cacheOverlay == null){
			// Create a new tile provider and add to the map
			TileProvider tileProvider = new FileSystemTileProvider(256, 256, xyzDirectoryCacheOverlay.getDirectory().getAbsolutePath());
			TileOverlayOptions overlayOptions = createTileOverlayOptions(tileProvider);
			// Set the tile overlay in the cache overlay
			TileOverlay tileOverlay = map.addTileOverlay(overlayOptions);
			xyzDirectoryCacheOverlay.setTileOverlay(tileOverlay);
			cacheOverlay = xyzDirectoryCacheOverlay;
		}
		// Add the cache overlay to the enabled cache overlays
		enabledCacheOverlays.put(cacheOverlay.getOverlayName(), cacheOverlay);
	}

	/**
	 * Add a GeoPackage cache overlay, which contains tile and feature tables
	 * @param enabledCacheOverlays
	 * @param enabledGeoPackages
	 * @param geoPackageCacheOverlay
	 */
	private void addGeoPackageCacheOverlay(Map<String, CacheOverlay> enabledCacheOverlays, Set<String> enabledGeoPackages, GeoPackageCacheOverlay geoPackageCacheOverlay){

		// Check each GeoPackage table
		for(CacheOverlay tableCacheOverlay: geoPackageCacheOverlay.getChildren()){
			// Check if the table is enabled
			if(tableCacheOverlay.isEnabled()){

				// Get and open if needed the GeoPackage
				GeoPackage geoPackage = geoPackageCache.getOrOpen(geoPackageCacheOverlay.getOverlayName());
				enabledGeoPackages.add(geoPackage.getName());

				// Handle tile and feature tables
				switch(tableCacheOverlay.getType()){
					case GEOPACKAGE_TILE_TABLE:
						addGeoPackageTileCacheOverlay(enabledCacheOverlays, (GeoPackageTileTableCacheOverlay)tableCacheOverlay, geoPackage, false);
						break;
					case GEOPACKAGE_FEATURE_TABLE:
						addGeoPackageFeatureCacheOverlay(enabledCacheOverlays, (GeoPackageFeatureTableCacheOverlay)tableCacheOverlay, geoPackage);
						break;
					default:
						throw new UnsupportedOperationException("Unsupported GeoPackage type: " + tableCacheOverlay.getType());
				}

				// If a newly added cache, update the bounding box for zooming
				if(geoPackageCacheOverlay.isAdded()){

					try {
						ContentsDao contentsDao = geoPackage.getContentsDao();
						Contents contents = contentsDao.queryForId(((GeoPackageTileTableCacheOverlay) tableCacheOverlay).getTableName());
						BoundingBox contentsBoundingBox = contents.getBoundingBox();
						Projection projection = ProjectionFactory
								.getProjection(contents.getSrs().getOrganizationCoordsysId());

						ProjectionTransform transform = projection.getTransformation(ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM);
						BoundingBox boundingBox = transform.transform(contentsBoundingBox);
						boundingBox = TileBoundingBoxUtils.boundWgs84BoundingBoxWithWebMercatorLimits(boundingBox);

						if (addedCacheBoundingBox == null) {
							addedCacheBoundingBox = boundingBox;
						} else {
							addedCacheBoundingBox = TileBoundingBoxUtils.union(addedCacheBoundingBox, boundingBox);
						}
					}catch(Exception e){
						Log.e(LOG_NAME, "Failed to retrieve GeoPackage Table bounding box. GeoPackage: "
								+ geoPackage.getName() + ", Table: " + tableCacheOverlay.getOverlayName(), e);
					}
				}
			}
		}
	}

	/**
	 * Add the GeoPackage Tile Table Cache Overlay
	 * @param enabledCacheOverlays
	 * @param tileTableCacheOverlay
	 * @param geoPackage
	 * @param linkedToFeatures
	 */
	private void addGeoPackageTileCacheOverlay(Map<String, CacheOverlay> enabledCacheOverlays, GeoPackageTileTableCacheOverlay tileTableCacheOverlay, GeoPackage geoPackage, boolean linkedToFeatures){
		// Retrieve the cache overlay if it already exists (and remove from cache overlays)
		CacheOverlay cacheOverlay = cacheOverlays.remove(tileTableCacheOverlay.getOverlayName());
		if(cacheOverlay != null){
			// If the existing cache overlay is being replaced, create a new cache overlay
			if(tileTableCacheOverlay.getParent().isAdded()){
				cacheOverlay = null;
			}
		}
		if(cacheOverlay == null){
			// Create a new GeoPackage tile provider and add to the map
			TileDao tileDao = geoPackage.getTileDao(tileTableCacheOverlay.getTableName());
			BoundedOverlay geoPackageTileProvider = GeoPackageOverlayFactory.getBoundedOverlay(tileDao);
			TileOverlayOptions overlayOptions = null;
			if(linkedToFeatures){
				overlayOptions = createFeatureTileOverlayOptions(geoPackageTileProvider);
			}else {
				overlayOptions = createTileOverlayOptions(geoPackageTileProvider);
			}
			TileOverlay tileOverlay = map.addTileOverlay(overlayOptions);
			tileTableCacheOverlay.setTileOverlay(tileOverlay);

			// Check for linked feature tables
			tileTableCacheOverlay.clearFeatureOverlayQueries();
			FeatureTileTableLinker linker = new FeatureTileTableLinker(geoPackage);
			List<FeatureDao> featureDaos = linker.getFeatureDaosForTileTable(tileDao.getTableName());
			for(FeatureDao featureDao: featureDaos){

				// Create the feature tiles
				FeatureTiles featureTiles = new MapFeatureTiles(getActivity(), featureDao);

				// Create an index manager
				FeatureIndexManager indexer = new FeatureIndexManager(getActivity(), geoPackage, featureDao);
				featureTiles.setIndexManager(indexer);

				// Add the feature overlay query
				FeatureOverlayQuery featureOverlayQuery = new FeatureOverlayQuery(getActivity(), geoPackageTileProvider, featureTiles);
				tileTableCacheOverlay.addFeatureOverlayQuery(featureOverlayQuery);
			}

			cacheOverlay = tileTableCacheOverlay;
		}
		// Add the cache overlay to the enabled cache overlays
		enabledCacheOverlays.put(cacheOverlay.getOverlayName(), cacheOverlay);
	}

	/**
	 * Add the GeoPackage Feature Table Cache Overlay
	 * @param enabledCacheOverlays
	 * @param featureTableCacheOverlay
	 * @param geoPackage
	 */
	private void addGeoPackageFeatureCacheOverlay(Map<String, CacheOverlay> enabledCacheOverlays, GeoPackageFeatureTableCacheOverlay featureTableCacheOverlay, GeoPackage geoPackage){
		// Retrieve the cache overlay if it already exists (and remove from cache overlays)
		CacheOverlay cacheOverlay = cacheOverlays.remove(featureTableCacheOverlay.getTableName());
		if(cacheOverlay != null){
			// If the existing cache overlay is being replaced, create a new cache overlay
			if(featureTableCacheOverlay.getParent().isAdded()){
				cacheOverlay = null;
			}
			for(GeoPackageTileTableCacheOverlay linkedTileTable: featureTableCacheOverlay.getLinkedTileTables()){
				if(cacheOverlay != null){
					// Add the existing linked tile cache overlays
					addGeoPackageTileCacheOverlay(enabledCacheOverlays, linkedTileTable, geoPackage, true);
				}
				cacheOverlays.remove(linkedTileTable.getTableName());
			}
		}
		if(cacheOverlay == null) {
			// Add the features to the map
			FeatureDao featureDao = geoPackage.getFeatureDao(featureTableCacheOverlay.getTableName());

			// If indexed, add as a tile overlay
			if(featureTableCacheOverlay.isIndexed()){
				FeatureTiles featureTiles = new MapFeatureTiles(getActivity(), featureDao);
				Integer maxFeaturesPerTile = null;
				if(featureDao.getGeometryType() == GeometryType.POINT){
					maxFeaturesPerTile = getResources().getInteger(R.integer.geopackage_feature_tiles_max_points_per_tile);
				}else{
					maxFeaturesPerTile = getResources().getInteger(R.integer.geopackage_feature_tiles_max_features_per_tile);
				}
				featureTiles.setMaxFeaturesPerTile(maxFeaturesPerTile);
				NumberFeaturesTile numberFeaturesTile = new NumberFeaturesTile(getActivity());
				// Adjust the max features number tile draw paint attributes here as needed to
				// change how tiles are drawn when more than the max features exist in a tile
				featureTiles.setMaxFeaturesTileDraw(numberFeaturesTile);
				featureTiles.setIndexManager(new FeatureIndexManager(getActivity(), geoPackage, featureDao));
				// Adjust the feature tiles draw paint attributes here as needed to change how
				// features are drawn on tiles
				FeatureOverlay featureOverlay = new FeatureOverlay(featureTiles);
				featureOverlay.setMinZoom(featureTableCacheOverlay.getMinZoom());

				FeatureTileTableLinker linker = new FeatureTileTableLinker(geoPackage);
				List<TileDao> tileDaos = linker.getTileDaosForFeatureTable(featureDao.getTableName());
				featureOverlay.ignoreTileDaos(tileDaos);

				FeatureOverlayQuery featureOverlayQuery = new FeatureOverlayQuery(getActivity(), featureOverlay);
				featureTableCacheOverlay.setFeatureOverlayQuery(featureOverlayQuery);
				TileOverlayOptions overlayOptions = createFeatureTileOverlayOptions(featureOverlay);
				TileOverlay tileOverlay = map.addTileOverlay(overlayOptions);
				featureTableCacheOverlay.setTileOverlay(tileOverlay);
			}
			// Not indexed, add the features to the map
			else {
				int maxFeaturesPerTable = 0;
				if(featureDao.getGeometryType() == GeometryType.POINT){
					maxFeaturesPerTable = getResources().getInteger(R.integer.geopackage_features_max_points_per_table);
				}else{
					maxFeaturesPerTable = getResources().getInteger(R.integer.geopackage_features_max_features_per_table);
				}
				Projection projection = featureDao.getProjection();
				GoogleMapShapeConverter shapeConverter = new GoogleMapShapeConverter(projection);
				FeatureCursor featureCursor = featureDao.queryForAll();
				try {
					final int totalCount = featureCursor.getCount();
					int count = 0;
					while (featureCursor.moveToNext()) {
						FeatureRow featureRow = featureCursor.getRow();
						GeoPackageGeometryData geometryData = featureRow.getGeometry();
						if (geometryData != null && !geometryData.isEmpty()) {
							mil.nga.wkb.geom.Geometry geometry = geometryData.getGeometry();
							if (geometry != null) {
								GoogleMapShape shape = shapeConverter.toShape(geometry);
								// Set the Shape Marker, PolylineOptions, and PolygonOptions here if needed to change color and style
								featureTableCacheOverlay.addShapeToMap(featureRow.getId(), shape, map);

								if(++count >= maxFeaturesPerTable){
									if(count < totalCount){
										Toast.makeText(mage, featureTableCacheOverlay.getTableName()
												+ "- added " + count + " of " + totalCount, Toast.LENGTH_LONG).show();
									}
									break;
								}
							}
						}
					}
				} finally {
					featureCursor.close();
				}
			}

			// Add linked tile tables
			for(GeoPackageTileTableCacheOverlay linkedTileTable: featureTableCacheOverlay.getLinkedTileTables()){
				addGeoPackageTileCacheOverlay(enabledCacheOverlays, linkedTileTable, geoPackage, true);
			}

			cacheOverlay = featureTableCacheOverlay;
		}

		// Add the cache overlay to the enabled cache overlays
		enabledCacheOverlays.put(cacheOverlay.getOverlayName(), cacheOverlay);
	}

	/**
	 * Create Feature Tile Overlay Options with the default z index for tile layers drawn from features
	 * @param tileProvider
	 * @return
	 */
	private TileOverlayOptions createFeatureTileOverlayOptions(TileProvider tileProvider){
		return createTileOverlayOptions(tileProvider, -1);
	}

	/**
	 * Create Tile Overlay Options with the default z index for tile layers
	 * @param tileProvider
	 * @return
	 */
	private TileOverlayOptions createTileOverlayOptions(TileProvider tileProvider){
		return createTileOverlayOptions(tileProvider, -2);
	}

	/**
	 * Create Tile Overlay Options for the Tile Provider using the z index
	 * @param tileProvider
	 * @param zIndex
	 * @return
	 */
	private TileOverlayOptions createTileOverlayOptions(TileProvider tileProvider, int zIndex){
		TileOverlayOptions overlayOptions = new TileOverlayOptions();
		overlayOptions.tileProvider(tileProvider);
		overlayOptions.zIndex(zIndex);
		return overlayOptions;
	}

	private void updateStaticFeatureLayers() {
		removeStaticFeatureLayers();

		try {
			for (Layer l : LayerHelper.getInstance(mage).readByEvent(EventHelper.getInstance(getActivity().getApplicationContext()).getCurrentEvent())) {
				onStaticFeatureLayer(l);
			}
		} catch (LayerException e) {
			Log.e(LOG_NAME, "Problem updating static features.", e);
		}
	}

	private void removeStaticFeatureLayers() {
		Set<String> selectedLayerIds = preferences.getStringSet(getResources().getString(R.string.staticFeatureLayersKey), Collections.<String> emptySet());

		Set<String> eventLayerIds = new HashSet<>();
		try {
			for (Layer layer : LayerHelper.getInstance(mage).readByEvent(EventHelper.getInstance(mage).getCurrentEvent())) {
				eventLayerIds.add(layer.getRemoteId());
			}
		} catch (LayerException e) {
			Log.e(LOG_NAME, "Problem reading static layers", e);
		}
		Set<String> layersNotInEvent = Sets.difference(selectedLayerIds, eventLayerIds);

		for (String layerId : staticGeometryCollection.getLayers()) {
			if (!selectedLayerIds.contains(layerId) || layersNotInEvent.contains(layerId)) {
				staticGeometryCollection.removeLayer(layerId);
			}
		}
	}

	@Override
	public void onStaticFeaturesCreated(final Layer layer) {
		getActivity().runOnUiThread(new Runnable() {

			@Override
			public void run() {
				onStaticFeatureLayer(layer);
			}
		});
	}

	private void onStaticFeatureLayer(Layer layer) {
		Set<String> layers = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext()).getStringSet(getString(R.string.staticFeatureLayersKey), Collections.<String> emptySet());

		// The user has asked for this feature layer
		String layerId = layer.getId().toString();
		if (layers.contains(layerId) && layer.isLoaded()) {
			new StaticFeatureLoadTask(mage, staticGeometryCollection, map).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, layer);
		}
	}

	private void updateMapView() {
		// Check the map type
		map.setMapType(preferences.getInt(getString(R.string.baseLayerKey), getResources().getInteger(R.integer.baseLayerDefaultValue)));

		// Check the map location and zoom
		String xyz = preferences.getString(getString(R.string.recentMapXYZKey), getString(R.string.recentMapXYZDefaultValue));
		if (xyz != null) {
			String[] values = StringUtils.split(xyz, ",");
			LatLng latLng = new LatLng(0.0, 0.0);
			if(values.length > 1) {
				try {
					latLng = new LatLng(Double.valueOf(values[1]), Double.valueOf(values[0]));
				} catch (NumberFormatException nfe) {
					Log.e(LOG_NAME, "Could not parse lon,lat: " + String.valueOf(values[1]) + ", " + String.valueOf(values[0]));
				}
			}
			float zoom = 1.0f;
			if(values.length > 2) {
				try {
					zoom = Float.valueOf(values[2]);
				} catch (NumberFormatException nfe) {
					Log.e(LOG_NAME, "Could not parse zoom level: " + String.valueOf(values[2]));
				}
			}
			map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));
		}
	}

	private void saveMapView() {
		CameraPosition position = map.getCameraPosition();

		String xyz = new StringBuilder().append(Double.valueOf(position.target.longitude).toString()).append(",").append(Double.valueOf(position.target.latitude).toString()).append(",").append(Float.valueOf(position.zoom).toString()).toString();
		preferences.edit().putString(getResources().getString(R.string.recentMapXYZKey), xyz).commit();
	}

	@Override
	public void onError(Throwable error) {
	}

	private int getTimeFilterId() {
		return preferences.getInt(getResources().getString(R.string.activeTimeFilterKey), getResources().getInteger(R.integer.time_filter_none));
	}

	private Filter<Temporal> getTemporalFilter(String columnName) {
		Filter<Temporal> filter = null;
		int filterId = getTimeFilterId();
		Calendar c = Calendar.getInstance();

		if (filterId == getResources().getInteger(R.integer.time_filter_last_month)) {
			c.add(Calendar.MONTH, -1);
		} else if (filterId == getResources().getInteger(R.integer.time_filter_last_week)) {
			c.add(Calendar.DAY_OF_MONTH, -7);
		} else if (filterId == getResources().getInteger(R.integer.time_filter_last_24_hours)) {
			c.add(Calendar.HOUR, -24);
		} else if (filterId == getResources().getInteger(R.integer.time_filter_today)) {
			c.set(Calendar.HOUR_OF_DAY, 0);
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.SECOND, 0);
			c.set(Calendar.MILLISECOND, 0);
		} else {
			// no filter
			c = null;
		}

		if (c != null) {
			filter = new DateTimeFilter(c.getTime(), null, columnName);
		}

		return filter;
	}

	private String getFilterTitle() {
		List<String> filters = new ArrayList<>();

		int filterId = getTimeFilterId();
		if (filterId == getResources().getInteger(R.integer.time_filter_last_month)) {
			filters.add("Last Month");
		} else if (filterId == getResources().getInteger(R.integer.time_filter_last_week)) {
			filters.add("Last Week");
		} else if (filterId == getResources().getInteger(R.integer.time_filter_last_24_hours)) {
			filters.add("Last 24 Hours");
		} else if (filterId == getResources().getInteger(R.integer.time_filter_today)) {
			filters.add("Since Midnight");
		}

		List<String> actionFilters = new ArrayList<>();
		if (preferences.getBoolean(getResources().getString(R.string.activeFavoritesFilterKey), false)) {
			actionFilters.add("Favorites");
		}

		if (preferences.getBoolean(getResources().getString(R.string.activeImportantFilterKey), false)) {
			actionFilters.add("Important");
		}

		if (!actionFilters.isEmpty()) {
			filters.add(StringUtils.join(actionFilters, " & "));
		}

		return StringUtils.join(filters, ", ");
	}

	private void hideKeyboard() {
		InputMethodManager inputMethodManager = (InputMethodManager) getActivity().getSystemService(Activity.INPUT_METHOD_SERVICE);
		if (getActivity().getCurrentFocus() != null) {
			inputMethodManager.hideSoftInputFromWindow(getActivity().getCurrentFocus().getWindowToken(), 0);
		}
	}
}