package mil.nga.giat.mage.map.preference;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ListFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckBox;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import mil.nga.giat.mage.R;
import mil.nga.giat.mage.map.cache.CacheManager;
import mil.nga.giat.mage.map.cache.CacheManager.CacheOverlaysUpdateListener;
import mil.nga.giat.mage.map.cache.CacheOverlay;
import mil.nga.giat.mage.map.cache.GeoPackageCacheProvider;
import mil.nga.giat.mage.map.cache.XYZDirectoryCacheProvider;

public class TileOverlayPreferenceActivity extends AppCompatActivity  {

    private static final int PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 100;

    private OverlayListFragment overlayFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cache_overlay);

        overlayFragment = (OverlayListFragment) getSupportFragmentManager().findFragmentById(R.id.overlay_fragment);
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        intent.putStringArrayListExtra(MapPreferencesActivity.OVERLAY_EXTENDED_DATA_KEY, overlayFragment.getSelectedOverlays());
        setResult(Activity.RESULT_OK, intent);
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public static class OverlayListFragment extends ListFragment implements CacheOverlaysUpdateListener {

        private OverlayAdapter overlayAdapter;
        private ExpandableListView listView;
        private ProgressBar progressBar;
        private MenuItem refreshButton;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            setHasOptionsMenu(true);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_cache_overlay, container, false);
            listView = (ExpandableListView) view.findViewById(android.R.id.list);
            listView.setEnabled(true);

            progressBar = (ProgressBar) view.findViewById(R.id.overlay_progress_bar);
            progressBar.setVisibility(View.VISIBLE);

            if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    new AlertDialog.Builder(getActivity(), R.style.AppCompatAlertDialogStyle)
                            .setTitle(R.string.overlay_access_title)
                            .setMessage(R.string.overlay_access_message)
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
                                }
                            })
                            .create()
                            .show();

                } else {
                    requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
                }
            }

            return view;
        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            inflater.inflate(R.menu.tile_overlay_menu, menu);

            refreshButton = menu.findItem(R.id.tile_overlay_refresh);
            refreshButton.setEnabled(false);

            // This really should be done in the onResume, but I need to have my refreshButton
            // before I register as the call back will set it to enabled
            // the problem is that onResume gets called before this so my menu is
            // not yet setup and I will not have a handle on this button
            CacheManager.getInstance().registerCacheOverlayListener(this);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.tile_overlay_refresh:
                    item.setEnabled(false);
                    progressBar.setVisibility(View.VISIBLE);
                    listView.setEnabled(false);
                    CacheManager.getInstance().refreshAvailableCaches();
                    return true;
                default:
                    return super.onOptionsItemSelected(item);
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();

            CacheManager.getInstance().unregisterCacheOverlayListener(this);
        }

        @Override
        public void onCacheOverlaysUpdated(Set<CacheOverlay> overlaySet) {
            List<CacheOverlay> cacheOverlays = new ArrayList<>(overlaySet);
            overlayAdapter = new OverlayAdapter(getActivity(), cacheOverlays);
            listView.setAdapter(overlayAdapter);
            listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                    int itemType = ExpandableListView.getPackedPositionType(id);
                    if (itemType == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
                        int childPosition = ExpandableListView.getPackedPositionChild(id);
                        int groupPosition = ExpandableListView.getPackedPositionGroup(id);
                        // Handle child row long clicks here
                        return true;
                    } else if (itemType == ExpandableListView.PACKED_POSITION_TYPE_GROUP) {
                        int groupPosition = ExpandableListView.getPackedPositionGroup(id);
                        CacheOverlay cacheOverlay = (CacheOverlay) overlayAdapter.getGroup(groupPosition);
                        deleteCacheOverlayConfirm(cacheOverlay);
                        return true;
                    }
                    return false;
                }
            });

            refreshButton.setEnabled(true);
            listView.setEnabled(true);
            progressBar.setVisibility(View.INVISIBLE);
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);

            switch (requestCode) {
                case PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE: {
                    if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        CacheManager.getInstance().refreshAvailableCaches();
                    };

                    break;
                }
            }
        }

        /**
         * Get the selected cache overlays and child cache overlays
         *
         * @return
         */
        public ArrayList<String> getSelectedOverlays() {
            ArrayList<String> overlays = new ArrayList<>();
            if (overlayAdapter != null) {
                for (CacheOverlay cacheOverlay : overlayAdapter.getOverlays()) {

                    boolean childAdded = false;
                    for (CacheOverlay childCache : cacheOverlay.getChildren()) {
                        if (childCache.isEnabled()) {
                            overlays.add(childCache.getOverlayName());
                            childAdded = true;
                        }
                    }

                    if (!childAdded && cacheOverlay.isEnabled()) {
                        overlays.add(cacheOverlay.getOverlayName());
                    }
                }
            }
            return overlays;
        }

        /**
         * Delete the cache overlay
         * @param cacheOverlay
         */
        private void deleteCacheOverlayConfirm(final CacheOverlay cacheOverlay) {
            AlertDialog deleteDialog = new AlertDialog.Builder(getActivity())
                    .setTitle("Delete Cache")
                    .setMessage("Delete " + cacheOverlay.getOverlayName() + " Cache?")
                    .setPositiveButton("Delete",

                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    deleteCacheOverlay(cacheOverlay);
                                }
                            })

                    .setNegativeButton(getString(R.string.cancel),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    dialog.dismiss();
                                }
                            }).create();
            deleteDialog.show();
        }


        /**
         * Delete the cache overlay
         * @param cacheOverlay
         */
        private void deleteCacheOverlay(CacheOverlay cacheOverlay){

            progressBar.setVisibility(View.VISIBLE);
            listView.setEnabled(false);

            if (cacheOverlay.isTypeOf(XYZDirectoryCacheProvider.class)) {
                // TODO: moved to XYZDirectoryCacheProvider
//                    deleteXYZCacheOverlay((XYZDirectoryCacheOverlay)cacheOverlay);
            }
            else if (cacheOverlay.isTypeOf(GeoPackageCacheProvider.class)) {
                    // TODO: moved to GeoPackageCacheProvider
//                    deleteGeoPackageCacheOverlay((GeoPackageCacheOverlay)cacheOverlay);

            }

            CacheManager.getInstance().refreshAvailableCaches();
        }


    }

    /**
     * Cache Overlay Expandable list adapter
     */
    public static class OverlayAdapter extends BaseExpandableListAdapter {

        /**
         * Context
         */
        private Activity activity;

        /**
         * List of cache overlays
         */
        private List<CacheOverlay> overlays;

        /**
         * Constructor
         *
         * @param activity
         * @param overlays
         */
        public OverlayAdapter(Activity activity, List<CacheOverlay> overlays) {
            this.activity = activity;
            this.overlays = overlays;
        }

        /**
         * Get the overlays
         *
         * @return
         */
        public List<CacheOverlay> getOverlays() {
            return overlays;
        }

        @Override
        public int getGroupCount() {
            return overlays.size();
        }

        @Override
        public int getChildrenCount(int i) {
            return overlays.get(i).getChildren().size();
        }

        @Override
        public Object getGroup(int i) {
            return overlays.get(i);
        }

        @Override
        public Object getChild(int i, int j) {
            return overlays.get(i).getChildren().get(j);
        }

        @Override
        public long getGroupId(int i) {
            return i;
        }

        @Override
        public long getChildId(int i, int j) {
            return j;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getGroupView(int i, boolean isExpanded, View view,
                                 ViewGroup viewGroup) {
            if (view == null) {
                LayoutInflater inflater = LayoutInflater.from(activity);
                view = inflater.inflate(R.layout.cache_overlay_group, viewGroup, false);
            }

            ImageView imageView = (ImageView) view.findViewById(R.id.cache_overlay_group_image);
            TextView cacheName = (TextView) view.findViewById(R.id.cache_overlay_group_name);
            TextView childCount = (TextView) view.findViewById(R.id.cache_overlay_group_count);
            CheckBox checkBox = (CheckBox) view.findViewById(R.id.cache_overlay_group_checkbox);

            final CacheOverlay overlay = overlays.get(i);

            checkBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean checked = ((CheckBox) v).isChecked();

                    overlay.setEnabled(checked);

                    boolean modified = false;
                    for (CacheOverlay childCache : overlay.getChildren()) {
                        if (childCache.isEnabled() != checked) {
                            childCache.setEnabled(checked);
                            modified = true;
                        }
                    }

                    if (modified) {
                        notifyDataSetChanged();
                    }
                }
            });

            Integer imageResource = overlay.getIconImageResourceId();
            if(imageResource != null){
                imageView.setImageResource(imageResource);
            }else{
                imageView.setImageResource(-1);
            }
            cacheName.setText(overlay.getOverlayName());
            if (overlay.isSupportsChildren()) {
                childCount.setText("(" + getChildrenCount(i) + ")");
            }else{
                childCount.setText("");
            }
            checkBox.setChecked(overlay.isEnabled());

            return view;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {

            if (convertView == null) {
                LayoutInflater inflater = LayoutInflater.from(activity);
                convertView = inflater.inflate(R.layout.cache_overlay_child, parent, false);
            }

            final CacheOverlay overlay = overlays.get(groupPosition);
            final CacheOverlay childCache = overlay.getChildren().get(childPosition);

            ImageView imageView = (ImageView) convertView.findViewById(R.id.cache_overlay_child_image);
            TextView tableName = (TextView) convertView.findViewById(R.id.cache_overlay_child_name);
            TextView info = (TextView) convertView.findViewById(R.id.cache_overlay_child_info);
            CheckBox checkBox = (CheckBox) convertView.findViewById(R.id.cache_overlay_child_checkbox);

            convertView.findViewById(R.id.divider).setVisibility(isLastChild ? View.VISIBLE : View.INVISIBLE);

            checkBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean checked = ((CheckBox) v).isChecked();

                    childCache.setEnabled(checked);

                    boolean modified = false;
                    if (checked) {
                        if (!overlay.isEnabled()) {
                            overlay.setEnabled(true);
                            modified = true;
                        }
                    } else if (overlay.isEnabled()) {
                        modified = true;
                        for (CacheOverlay childCache : overlay.getChildren()) {
                            if (childCache.isEnabled()) {
                                modified = false;
                                break;
                            }
                        }
                        if (modified) {
                            overlay.setEnabled(false);
                        }
                    }

                    if (modified) {
                        notifyDataSetChanged();
                    }
                }
            });

            tableName.setText(childCache.getOverlayName());
            info.setText(childCache.getInfo());
            checkBox.setChecked(childCache.isEnabled());

            Integer imageResource = childCache.getIconImageResourceId();
            if (imageResource != null){
                imageView.setImageResource(imageResource);
            } else {
                imageView.setImageResource(-1);
            }

            return convertView;
        }

        @Override
        public boolean isChildSelectable(int i, int j) {
            return true;
        }
    }
}