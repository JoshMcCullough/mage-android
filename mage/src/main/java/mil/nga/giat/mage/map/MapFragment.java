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
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
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
import com.google.common.collect.Sets;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import mil.nga.giat.mage.MAGE;
import mil.nga.giat.mage.R;
import mil.nga.giat.mage.filter.DateTimeFilter;
import mil.nga.giat.mage.filter.Filter;
import mil.nga.giat.mage.filter.FilterActivity;
import mil.nga.giat.mage.map.cache.CacheManager;
import mil.nga.giat.mage.map.cache.CacheManager.CacheOverlaysUpdateListener;
import mil.nga.giat.mage.map.cache.CacheOverlay;
import mil.nga.giat.mage.map.cache.MapCache;
import mil.nga.giat.mage.map.cache.OverlayOnMapManager;
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

public class MapFragment extends Fragment
	implements OnMapReadyCallback, OnMapClickListener, OnMapLongClickListener, OnMarkerClickListener,
	GoogleMap.OnCameraMoveStartedListener, GoogleMap.OnCameraIdleListener,
	OnInfoWindowClickListener, OnMyLocationButtonClickListener, OnClickListener,
	LocationSource, LocationListener, CacheOverlaysUpdateListener, SearchView.OnQueryTextListener,
	IObservationEventListener, ILocationEventListener, IStaticFeatureEventListener {

	private static final String LOG_NAME = MapFragment.class.getName();
	private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
	private static final int MARKER_REFRESH_INTERVAL_SECONDS = 300;
	private static final String STATE_OVERLAYS_EXPANDED = MapFragment.class.getSimpleName() + ".overlays_expanded";

	private class RefreshMarkersRunnable implements Runnable {
		private final PointCollection<?> points;

		private RefreshMarkersRunnable(PointCollection<?> points) {
			this.points = points;
		}

		public void run() {
			if (points.isVisible()) {
				points.refreshMarkerIcons();
			}
			scheduleMarkerRefresh(this);
		}
	}

	private MAGE mage;
	private SharedPreferences preferences;
	private ViewGroup container;
	private ViewGroup mapWrapper;
	private MapView mapView;
	private GoogleMap map;
	private View searchLayout;
	private SearchView searchView;
	private Location location;
	private User currentUser = null;
	private OnLocationChangedListener locationChangedListener;

	private PointCollection<Observation> observations;
	private PointCollection<Pair<mil.nga.giat.mage.sdk.datastore.location.Location, User>> locations;
	private PointCollection<Pair<mil.nga.giat.mage.sdk.datastore.location.Location, User>> historicLocations;
	private StaticGeometryCollection staticGeometryCollection;
	private List<Marker> searchMarkers = new ArrayList<>();
	private RefreshMarkersRunnable refreshLocationsTask;
	private RefreshMarkersRunnable refreshHistoricLocationsTask;

	private FloatingActionButton searchButton;
	private FloatingActionButton zoomToLocationButton;
	private FloatingActionButton overlaysButton;
	private FloatingActionButton newObservationButton;
	private LocationService locationService;


	private ConstraintLayout constraintLayout;
	private ConstraintSet layoutOverlaysCollapsed = new ConstraintSet();
	private ConstraintSet layoutOverlaysExpanded = new ConstraintSet();
	private OverlayOnMapManager mapOverlayManager;

	private boolean layersPanelVisible = false;
	private boolean searchInputVisible = false;
	private boolean followMe = false;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mage = (MAGE) getContext().getApplicationContext();
		preferences = PreferenceManager.getDefaultSharedPreferences(mage);
		locationService = mage.getLocationService();

		// creating the MapView here should preserve it across configuration/layout changes - onConfigurationChanged()
		// and avoid redrawing map and markers and whatnot
		setRetainInstance(true);
		GoogleMapOptions opts = new GoogleMapOptions()
			.rotateGesturesEnabled(false)
			.tiltGesturesEnabled(false)
			.compassEnabled(false);
		mapView = new MapView(getContext(), opts);
		mapView.onCreate(savedInstanceState);

		setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		this.container = new FrameLayout(getContext());
		this.container.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		loadLayoutToContainer(inflater);
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
		mapView.getMapAsync(this);

		((AppCompatActivity) getActivity()).getSupportActionBar().setSubtitle(getFilterTitle());

		searchView.setOnQueryTextListener(this);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		cleanUpForLayoutChange();
		LayoutInflater inflater = LayoutInflater.from(getContext());
		loadLayoutToContainer(inflater);
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

		ObservationHelper.getInstance(mage).removeListener(this);
		LocationHelper.getInstance(mage).removeListener(this);
		StaticFeatureHelper.getInstance(mage).removeListener(this);
		locationService.unregisterOnLocationListener(this);

		if (map != null) {
			saveMapPosition();
			map.setLocationSource(null);
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
			searchMarkers.clear();
		}

		map.setOnMapClickListener(null);
		map.setOnMarkerClickListener(null);
		map.setOnMapLongClickListener(null);
		map.setOnMyLocationButtonClickListener(null);
		map.setOnInfoWindowClickListener(null);
		map.setOnCameraMoveStartedListener(null);
		map.setOnCameraIdleListener(null);
		map.clear();

		mapOverlayManager.dispose();

		staticGeometryCollection.clear();
		staticGeometryCollection = null;
		currentUser = null;
		map = null;
		mapView.onDestroy();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	private void scheduleMarkerRefresh(RefreshMarkersRunnable task) {
		getView().postDelayed(task, MARKER_REFRESH_INTERVAL_SECONDS * 1000);
	}

	private void cleanUpForLayoutChange() {
	    searchInputVisible = isSearchInputVisible();
	    layersPanelVisible = isLayersPanelVisible();
		container.removeAllViews();
		mapWrapper.removeAllViews();
		zoomToLocationButton.setOnClickListener(null);
		searchView.setOnQueryTextListener(null);
		searchButton.setOnClickListener(null);
		overlaysButton.setOnClickListener(null);
		newObservationButton.setOnClickListener(null);
	}

	private View loadLayoutToContainer(LayoutInflater inflater) {
		constraintLayout = (ConstraintLayout) inflater.inflate(R.layout.fragment_map, container, false);
		layoutOverlaysCollapsed.clone(constraintLayout);
		layoutOverlaysExpanded.load(getContext(), R.layout.fragment_map_expanded_layers_panel);

		staticGeometryCollection = new StaticGeometryCollection();

		zoomToLocationButton = (FloatingActionButton) constraintLayout.findViewById(R.id.zoom_button);
		zoomToLocationButton.setOnClickListener(this);

		searchButton = (FloatingActionButton) constraintLayout.findViewById(R.id.map_search_button);
		Drawable drawable = DrawableCompat.wrap(searchButton.getDrawable());
		searchButton.setImageDrawable(drawable);
		DrawableCompat.setTintList(drawable, AppCompatResources.getColorStateList(getContext(), R.color.map_search_icon));
		searchButton.setOnClickListener(this);

		overlaysButton = (FloatingActionButton) constraintLayout.findViewById(R.id.map_layer_button);
		overlaysButton.setOnClickListener(this);

		newObservationButton = (FloatingActionButton) constraintLayout.findViewById(R.id.new_observation_button);
		newObservationButton.setOnClickListener(this);

		searchLayout = constraintLayout.findViewById(R.id.search_layout);
		searchView = (SearchView) constraintLayout.findViewById(R.id.search_view);
		searchView.setIconifiedByDefault(false);
		searchView.setIconified(false);
		searchView.clearFocus();

		mapWrapper = (FrameLayout) constraintLayout.findViewById(R.id.map_wrapper);
		mapWrapper.addView(mapView);

		if (layersPanelVisible) {
			showLayersPanel();
		}
		else if (searchInputVisible) {
		    showSearchInput();
        }

		container.addView(constraintLayout);
		return container;
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
		if (map == null) {
			map = googleMap;
			map.getUiSettings().setMyLocationButtonEnabled(false);
			map.setOnMapClickListener(this);
			map.setOnMarkerClickListener(this);
			map.setOnMapLongClickListener(this);
			map.setOnMyLocationButtonClickListener(this);
			map.setOnInfoWindowClickListener(this);
			map.setOnCameraMoveStartedListener(this);
			map.setOnCameraIdleListener(this);
			observations = new ObservationMarkerCollection(getActivity(), map);
			historicLocations = new MyHistoricalLocationMarkerCollection(getActivity(), map);
			locations = new LocationMarkerCollection(getActivity(), map);
			ObservationHelper.getInstance(mage).addListener(this);
			LocationHelper.getInstance(mage).addListener(this);
			StaticFeatureHelper.getInstance(mage).addListener(this);
			mapOverlayManager = CacheManager.getInstance().createMapManager(map);
		}

		ObservationLoadTask observationLoad = new ObservationLoadTask(getActivity(), observations);
		observationLoad.addFilter(getTemporalFilter("timestamp"));
		observationLoad.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

		HistoricLocationLoadTask myHistoricLocationLoad = new HistoricLocationLoadTask(getActivity(), historicLocations);
		myHistoricLocationLoad.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

		LocationLoadTask locationLoad = new LocationLoadTask(getActivity(), locations);
		locationLoad.setFilter(getTemporalFilter("timestamp"));
		locationLoad.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

		loadLastMapPosition();
		updateStaticFeatureLayers();

		// Set visibility on map markers as preferences may have changed
		observations.setVisibility(preferences.getBoolean(getResources().getString(R.string.showObservationsKey), true));
		locations.setVisibility(preferences.getBoolean(getResources().getString(R.string.showLocationsKey), true));
		historicLocations.setVisibility(preferences.getBoolean(getResources().getString(R.string.showMyLocationHistoryKey), false));

		// Check if any map preferences changed that I care about
		if (ContextCompat.checkSelfPermission(mage, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
			map.setMyLocationEnabled(true);
			map.setLocationSource(this);
			locationService.registerOnLocationListener(this);
		}
		else {
			map.setMyLocationEnabled(false);
			map.setLocationSource(null);
		}

		refreshLocationsTask = new RefreshMarkersRunnable(locations);
		refreshHistoricLocationsTask = new RefreshMarkersRunnable(historicLocations);
		scheduleMarkerRefresh(refreshLocationsTask);
		scheduleMarkerRefresh(refreshHistoricLocationsTask);
	}

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

	private void onZoom() {
		if (map == null) {
			return;
		}
		Location location = locationService.getLocation();
		LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
		CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 18);
		map.animateCamera(cameraUpdate);
	}

	private void onSearchToggled() {
		hideLayersPanel();
		if (isSearchInputVisible()) {
			hideSearchInput();
		}
		else {
			showSearchInput();
		}
	}

	private void onLayersPanelToggled() {
		if (isLayersPanelVisible()) {
			hideLayersPanel();
		}
		else {
			showLayersPanel();
		}
    }

    private boolean isSearchInputVisible() {
		return searchLayout.getVisibility() == View.VISIBLE;
	}

    private boolean isLayersPanelVisible() {
		return getChildFragmentManager().findFragmentById(R.id.map_overlays_container) != null;
	}

    private void showSearchInput() {
		hideLayersPanel();
		searchLayout.setVisibility(View.VISIBLE);
		searchView.requestFocus();
		InputMethodManager inputMethodManager = (InputMethodManager) getActivity().getSystemService(Activity.INPUT_METHOD_SERVICE);
		inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
	}

    private void hideSearchInput() {
		searchView.clearFocus();
		searchLayout.setVisibility(View.GONE);
	}

    private void showLayersPanel() {
		hideSearchInput();
		// TODO: animate layout change with ObjectAnimator on map padding
		layoutOverlaysExpanded.applyTo(constraintLayout);
		MapOverlaysFragment overlays = (MapOverlaysFragment) Fragment.instantiate(getActivity(), MapOverlaysFragment.class.getName());
		overlays.setOverlayManager(mapOverlayManager);
		FragmentManager fragmentManager = getChildFragmentManager();
		fragmentManager.beginTransaction().replace(R.id.map_overlays_container, overlays).commit();
	}

	private void hideLayersPanel() {
		layoutOverlaysCollapsed.applyTo(constraintLayout);
		FragmentManager fragmentManager = getChildFragmentManager();
		Fragment overlays = fragmentManager.findFragmentById(R.id.map_overlays_container);
		if (overlays != null) {
			fragmentManager.beginTransaction().remove(overlays).commit();
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

		// TODO: handle overlay clicks
		mapOverlayManager.onMapClick(latLng, mapView);
//		if(!overlays.isEmpty()) {
//			StringBuilder clickMessage = new StringBuilder();
//			for (CacheOverlay cacheOverlay : overlays.values()) {
//				String message = null; //cacheOverlay.onMapClick(latLng, mapView, map);
//				if(message != null){
//					if(clickMessage.length() > 0){
//						clickMessage.append("\n\n");
//					}
//					clickMessage.append(message);
//				}
//			}
//			if(clickMessage.length() > 0) {
//				new AlertDialog.Builder(getActivity())
//					.setMessage(clickMessage.toString())
//					.setPositiveButton(android.R.string.yes, null)
//					.show();
//			}
//		}
	}

	@Override
	public void onMapLongClick(LatLng point) {
		hideKeyboard();
		if (!UserHelper.getInstance(mage).isCurrentUserPartOfCurrentEvent()) {
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
				return;
			case R.id.map_search_button:
				onSearchToggled();
				return;
			case R.id.map_layer_button:
			    onLayersPanelToggled();
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
					followMe = true;
				}

				@Override
				public void onFinish() {
					followMe = true;
				}
			});
		}
		return true;
	}

	@Override
	public void onCameraMoveStarted(int reason) {
		if (reason == REASON_GESTURE) {
			followMe = false;
		}
	}

	@Override
	public void onCameraIdle() {
		// TODO: anything for followMe?
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
	public void onCacheOverlaysUpdated(CacheManager.CacheOverlayUpdate update) {
		if (update.added.size() != 1) {
			return;
		}
		MapCache explicitlyRequestedCache = update.added.iterator().next();
		for (CacheOverlay cacheOverlay : explicitlyRequestedCache.getCacheOverlays().values()) {
			mapOverlayManager.showOverlay(cacheOverlay);
		}
		LatLngBounds cacheBounds = explicitlyRequestedCache.getBounds();
		if (cacheBounds != null) {
			CameraUpdate showCache = CameraUpdateFactory.newLatLngBounds(cacheBounds, 0);
			map.animateCamera(showCache);
		}
	}

	private void updateStaticFeatureLayers() {
		removeStaticFeatureLayers();
		try {
			for (Layer l : LayerHelper.getInstance(mage).readByEvent(EventHelper.getInstance(mage).getCurrentEvent())) {
				onStaticFeatureLayer(l);
			}
		}
		catch (LayerException e) {
			Log.e(LOG_NAME, "error updating static features.", e);
		}
	}

	private void removeStaticFeatureLayers() {
		Set<String> selectedLayerIds = preferences.getStringSet(getResources().getString(R.string.staticFeatureLayersKey), Collections.<String> emptySet());

		Set<String> eventLayerIds = new HashSet<>();
		try {
			for (Layer layer : LayerHelper.getInstance(mage).readByEvent(EventHelper.getInstance(mage).getCurrentEvent())) {
				eventLayerIds.add(layer.getRemoteId());
			}
		}
		catch (LayerException e) {
			Log.e(LOG_NAME, "error reading static layers", e);
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

	private void loadLastMapPosition() {
		// Check the map type
		map.setMapType(preferences.getInt(getString(R.string.baseLayerKey), getResources().getInteger(R.integer.baseLayerDefaultValue)));

		// Check the map location and zoom
		String xyz = preferences.getString(getString(R.string.recentMapXYZKey), getString(R.string.recentMapXYZDefaultValue));
		if (xyz == null) {
			return;
		}

		String[] values = xyz.split(",");
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

	private void saveMapPosition() {
		CameraPosition position = map.getCameraPosition();
		String xyz = String.format(Locale.US, "%f,%f,%f", position.target.longitude, position.target.latitude, position.zoom);
		preferences.edit().putString(getResources().getString(R.string.recentMapXYZKey), xyz).apply();
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