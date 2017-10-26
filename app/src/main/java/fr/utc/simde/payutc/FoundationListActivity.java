package fr.utc.simde.payutc;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import fr.utc.simde.payutc.tools.HTTPRequest;

/**
 * Created by Samy on 26/10/2017.
 */

public class FoundationListActivity extends BaseActivity {
    private static final String LOG_TAG = "_FoundationListActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_foundation_list);

        dialog.startLoading(FoundationListActivity.this, getString(R.string.nemopay_connection), getString(R.string.nemopay_authentification));
        new Thread() {
            @Override
            public void run() {
                try {
                    nemopaySession.getFoundations();
                    Thread.sleep(100);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "error: " + e.getMessage());
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        HTTPRequest request = nemopaySession.getRequest();
                        dialog.stopLoading();

                        try {

                            if (request.getResponseCode() == 200 && request.isJsonResponse())
                                Log.d(LOG_TAG, "Liste acquise");
                                // setFoundationList(request.getJsonResponse();
                            else
                                dialog.errorDialog(getString(R.string.information_collection), getString(R.string.foundation_error_get_list));
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "error: " + e.getMessage());
                        }
                    }
                });
            }
        }.start();
    }

    @Override
    protected void onIdentification(String idBadge) {}

    @Override
    protected void onDestroy() {
        super.onDestroy();

        disconnect();
    }
}
