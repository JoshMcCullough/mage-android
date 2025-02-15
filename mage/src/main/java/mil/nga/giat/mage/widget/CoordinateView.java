package mil.nga.giat.mage.widget;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;

import com.google.android.gms.maps.model.LatLng;

import mil.nga.giat.mage.coordinate.CoordinateFormatter;

public class CoordinateView extends android.support.v7.widget.AppCompatTextView {

    private CoordinateFormatter coordinateFormatter;

    public CoordinateView(Context context) {
        super(context, null);
        coordinateFormatter = new CoordinateFormatter(context);
    }

    public CoordinateView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        coordinateFormatter = new CoordinateFormatter(context);
    }

    public CoordinateView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        coordinateFormatter = new CoordinateFormatter(context);
    }

    public void setLatLng(LatLng latLng) {
        setText(coordinateFormatter.format(latLng));
    }
}
