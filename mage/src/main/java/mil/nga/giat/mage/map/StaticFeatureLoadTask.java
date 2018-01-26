package mil.nga.giat.mage.map;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.Map;

import mil.nga.giat.mage.map.marker.StaticGeometryCollection;
import mil.nga.giat.mage.sdk.datastore.layer.Layer;
import mil.nga.giat.mage.sdk.datastore.staticfeature.StaticFeature;
import mil.nga.giat.mage.sdk.datastore.staticfeature.StaticFeatureProperty;

public class StaticFeatureLoadTask extends AsyncTask<Layer, Object, Void> {

	private static final String LOG_NAME = StaticFeatureLoadTask.class.getName();

	private StaticGeometryCollection staticGeometryCollection;
	private GoogleMap map;
	private Context context;

	public StaticFeatureLoadTask(Context context, StaticGeometryCollection staticGeometryCollection, GoogleMap map) {
		this.context = context;
		this.staticGeometryCollection = staticGeometryCollection;
		this.map = map;
	}

	@Override
	protected Void doInBackground(Layer... layers) {
		Layer layer = layers[0];
		String layerId = layer.getId().toString();

		Log.d(LOG_NAME, "static feature layer: " + layer.getName() + " is enabled, it has " + layer.getStaticFeatures().size() + " features");

		Iterator<StaticFeature> features = layer.getStaticFeatures().iterator();
		while (features.hasNext() && !isCancelled()) {
			StaticFeature feature = features.next();
			Geometry geometry = feature.getGeometry();
			Map<String, StaticFeatureProperty> properties = feature.getPropertiesMap();

			StringBuilder content = new StringBuilder();
			if (properties.get("name") != null) {
				content.append("<h5>").append(properties.get("name").getValue()).append("</h5>");
			}
			if (properties.get("description") != null) {
				content.append("<div>").append(properties.get("description").getValue()).append("</div>");
			}
			String type = geometry.getGeometryType();
			if ("Point".equals(type)) {
				MarkerOptions options = new MarkerOptions().position(new LatLng(geometry.getCoordinate().y, geometry.getCoordinate().x)).snippet(content.toString());

				// check to see if there's an icon
				String iconPath = feature.getLocalPath();
				if (iconPath != null) {
					File iconFile = new File(iconPath);
					if (iconFile.exists()) {
						BitmapFactory.Options o = new BitmapFactory.Options();
						o.inDensity = 480;
						o.inTargetDensity = context.getResources().getDisplayMetrics().densityDpi;
						try {
							options.icon(BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeStream(new FileInputStream(iconFile), null, o)));
						} catch (FileNotFoundException fnfe) {
							Log.e(LOG_NAME, "Could not set icon.", fnfe);
						}
					}
				}

				publishProgress(options, layerId, content.toString());
			} else if ("LineString".equals(type)) {
				PolylineOptions options = new PolylineOptions();

				StaticFeatureProperty property = properties.get("stylelinestylecolorrgb");
				if (property != null) {
					String color = property.getValue();
					options.color(Color.parseColor(color));
				}
				for (Coordinate coordinate : geometry.getCoordinates()) {
					options.add(new LatLng(coordinate.y, coordinate.x));
				}
				publishProgress(options, layerId, content.toString());
			} else if ("Polygon".equals(type)) {
				PolygonOptions options = new PolygonOptions();

				Integer color = null;
				StaticFeatureProperty property = properties.get("stylelinestylecolorrgb");
				if (property != null) {
					String colorProperty = property.getValue();
					color = Color.parseColor(colorProperty);
					options.strokeColor(color);
				} else {
					property = properties.get("stylepolystylecolorrgb");
					if (property != null) {
					    String colorProperty = property.getValue();
						color = Color.parseColor(colorProperty);
						options.strokeColor(color);
					}
				}
				
                property = properties.get("stylepolystylefill");
                if (property != null) {
                    String fill = property.getValue();
                    if ("1".equals(fill) && color != null) {
                        options.fillColor(color);
                    }
                }

				for (Coordinate coordinate : geometry.getCoordinates()) {
					options.add(new LatLng(coordinate.y, coordinate.x));
				}
				publishProgress(options, layerId, content.toString());
			}
		}
		return null;
	}

	@Override
	protected void onProgressUpdate(Object... para) {
		Object options = para[0];
		String layerId = para[1].toString();
		String content = para[2].toString();
		if (options instanceof MarkerOptions) {
			Marker m = map.addMarker((MarkerOptions) options);
			staticGeometryCollection.addMarker(layerId, m);
		} else if (options instanceof PolylineOptions) {
			Polyline p = map.addPolyline((PolylineOptions) options);
			staticGeometryCollection.addPolyline(layerId, p, content);
		} else if (options instanceof PolygonOptions) {
			Polygon p = map.addPolygon((PolygonOptions) options);
			staticGeometryCollection.addPolygon(layerId, p, content);
		}
	}
}