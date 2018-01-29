package mil.nga.giat.mage.cache;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import mil.nga.giat.mage.map.cache.CacheManager;
import mil.nga.giat.mage.sdk.utils.MediaUtility;

/**
 * Task for copying a cache file Uri stream to the cache folder location
 */
public class CopyCacheStreamTask extends AsyncTask<Void, Void, String> {

    /**
     * Context
     */
    private Context context;

    /**
     * Intent Uri used to launch MAGE
     */
    private Uri uri;

    /**
     * Cache file to create
     */
    private File cacheFile;

    /**
     * Cache name
     */
    private String cacheName;

    /**
     * Constructor
     *
     * @param context
     * @param uri       Uri containing stream
     * @param cacheFile copy to cache file location
     * @param cacheName cache name
     */
    public CopyCacheStreamTask(Context context, Uri uri, File cacheFile, String cacheName) {
        this.context = context;
        this.uri = uri;
        this.cacheFile = cacheFile;
        this.cacheName = cacheName;
    }

    /**
     * Copy the cache stream to cache file location
     *
     * @param params
     * @return
     */
    @Override
    protected String doInBackground(Void... params) {

        String error = null;

        final ContentResolver resolver = context.getContentResolver();
        try {
            InputStream stream = resolver.openInputStream(uri);
            MediaUtility.copyStream(stream, cacheFile);
        } catch (IOException e) {
            error = e.getMessage();
        }

        return error;
    }

    /**
     * Enable the new cache file and refresh the overlays
     *
     * @param result
     */
    @Override
    protected void onPostExecute(String result) {
        if (result == null) {
            CacheManager.getInstance().refreshAndEnableOverlay(cacheName);
        }
    }

}
