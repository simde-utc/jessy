package fr.utc.simde.payutc;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.w3c.dom.Text;

import java.io.IOException;

import fr.utc.simde.payutc.articles.GridAdapter;
import fr.utc.simde.payutc.articles.ListAdapater;
import fr.utc.simde.payutc.tools.HTTPRequest;

/**
 * Created by Samy on 29/10/2017.
 */

public class BuyerInfoActivity extends BaseActivity {
    private static final String LOG_TAG = "_BuyerInfoActivity";

    private String badgeId;
    private String username;
    private String lastname;
    private String firstname;
    private ArrayNode lastPurchaseList;
    private ArrayNode lastArticleList;

    private TextView textBuyerName;
    private TextView textSolde;
    private LinearLayout linearLayout;
    private ListView listView;

    private ListAdapater listAdapater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_buyer_info);

        this.textBuyerName = findViewById(R.id.text_buyer_name);
        this.textSolde = findViewById(R.id.text_solde);
        this.linearLayout = findViewById(R.id.layout_articles);

        try {
            JsonNode buyerInfo = new ObjectMapper().readTree(getIntent().getExtras().getString("buyerInfo"));
            this.badgeId = getIntent().getExtras().getString("badgeId");

            if (!buyerInfo.has("lastname") || !buyerInfo.has("username") || !buyerInfo.has("firstname") || !buyerInfo.has("solde") || !buyerInfo.has("last_purchases") || !buyerInfo.get("last_purchases").isArray())
                throw new Exception("Unexpected JSON");

            this.username = buyerInfo.get("username").textValue();
            this.lastname = buyerInfo.get("lastname").textValue();
            this.firstname = buyerInfo.get("firstname").textValue();
            this.lastPurchaseList = (ArrayNode) buyerInfo.get("last_purchases");

            this.textBuyerName.setText(this.firstname + " " + this.lastname);
            this.textSolde.setText("Solde: " + String.format("%.2f", new Float(buyerInfo.get("solde").intValue()) / 100.00f) + "€");

            generatePurchases();
        } catch (Exception e) {
            Log.e(LOG_TAG, "error: " + e.getMessage());
            dialog.errorDialog(this, getResources().getString(R.string.information_collection), getResources().getString(R.string.error_view), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int id) {
                    finish();
                }
            });
        }
    }

    @Override
    public void onRestart() {
        super.onRestart();

        startBuyerInfoActivity(BuyerInfoActivity.this, this.badgeId);
    }

    @Override
    protected void onIdentification(final String badgeId) { }

    protected void generatePurchases() throws Exception {
        if (this.lastPurchaseList.size() == 0) {
            String foundationName = nemopaySession.getFoundationName();
            TextView noPurchase = new TextView(this);
            noPurchase.setText(getString(R.string.no_purchases) + (foundationName.equals("") ? "" : "\n(" + foundationName + ")"));
            noPurchase.setTextSize(24);
            noPurchase.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,  LinearLayout.LayoutParams.MATCH_PARENT);
            layoutParams.setMargins(100, 100, 100, 100);
            noPurchase.setLayoutParams(layoutParams);

            this.linearLayout.addView(noPurchase);
        }
        else {
            generateArticleList();
            this.listView = new ListView(this);

            this.linearLayout.addView(listView);

            this.listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView parent, View view, int position, long id) {
                    final JsonNode article = lastArticleList.get(position);

                    final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(BuyerInfoActivity.this);
                    alertDialogBuilder
                            .setTitle(R.string.cancel_transaction)
                            .setMessage(getString(R.string.ask_cancel_transaction) + " " + Integer.toString(article.get("quantity").intValue()) + "x " + article.get("name").textValue() + " (total: " + String.format("%.2f", new Float(article.get("price").intValue()) / 100.00f) + "€) ?")
                            .setCancelable(true)
                            .setPositiveButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialogInterface, int id) {
                                    new Thread() {
                                        @Override
                                        public void run() {
                                            if (nemopaySession.getFoundationId() != -1) {
                                                try {
                                                    nemopaySession.cancelTransaction(nemopaySession.getFoundationId(), article.get("purchase_id").intValue());
                                                    Thread.sleep(100);

                                                    runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            dialog.stopLoading();

                                                            final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(BuyerInfoActivity.this);
                                                            alertDialogBuilder
                                                                    .setTitle(R.string.cancel_transaction)
                                                                    .setMessage(getString(R.string.transaction_canceled))
                                                                    .setCancelable(true)
                                                                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                                                        public void onClick(DialogInterface dialogInterface, int id) {
                                                                            try {
                                                                                startBuyerInfoActivity(BuyerInfoActivity.this, badgeId);
                                                                            } catch (Exception e) {
                                                                                Log.e(LOG_TAG, "error: " + e.getMessage());
                                                                                dialog.errorDialog(BuyerInfoActivity.this, getResources().getString(R.string.information_collection), getResources().getString(R.string.error_view), new DialogInterface.OnClickListener() {
                                                                                    @Override
                                                                                    public void onClick(DialogInterface dialogInterface, int id) {
                                                                                        finish();
                                                                                    }
                                                                                });
                                                                            }
                                                                        }
                                                                    });

                                                            dialog.createDialog(alertDialogBuilder);
                                                        }
                                                    });
                                                } catch (final Exception e) {
                                                    Log.e(LOG_TAG, "error: " + e.getMessage());

                                                    try {
                                                        final JsonNode response = nemopaySession.getRequest().getJSONResponse();

                                                        if (response.has("error") && response.get("error").has("message")) {
                                                            runOnUiThread(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    dialog.stopLoading();
                                                                    dialog.errorDialog(BuyerInfoActivity.this, getString(R.string.cancel_transaction), response.get("error").get("message").textValue());
                                                                }
                                                            });
                                                        }
                                                        else
                                                            throw new Exception("");
                                                    } catch (Exception e1) {
                                                        runOnUiThread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                dialog.stopLoading();
                                                                dialog.errorDialog(BuyerInfoActivity.this, getString(R.string.cancel_transaction), e.getMessage());
                                                            }
                                                        });
                                                    }
                                                }
                                            }
                                        }
                                    }.start();
                                }
                            })
                            .setNegativeButton(R.string.do_nothing, null);

                    dialog.createDialog(alertDialogBuilder);
                }
            });

            this.listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {
                    listAdapater.toast(position, Toast.LENGTH_LONG);

                    return true;
                }
            });
        }
    }

    public void generateArticleList() {
        dialog.startLoading(this, getString(R.string.information_collection), getString(R.string.article_list_collecting));

        new Thread() {
            @Override
            public void run() {
                ArrayNode articleFoundationList = new ObjectMapper().createArrayNode();
                if (nemopaySession.getFoundationId() != -1) {
                    try {
                        int responseCode = nemopaySession.getArticles();
                        Thread.sleep(100);

                        // Toute une série de vérifications avant de lancer l'activité
                        final HTTPRequest request = nemopaySession.getRequest();
                        articleFoundationList = (ArrayNode) request.getJSONResponse();

                        for (final JsonNode article : articleFoundationList) {
                            if (!article.has("id") || !article.has("price") || !article.has("name") || !article.has("active") || !article.has("cotisant") || !article.has("alcool") || !article.has("categorie_id") || !article.has("image_url") || !article.has("fundation_id") || article.get("fundation_id").intValue() != nemopaySession.getFoundationId())
                                throw new Exception("Unexpected JSON");
                        }
                    } catch (final Exception e) {
                        Log.e(LOG_TAG, "error: " + e.getMessage());

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                dialog.stopLoading();
                                dialog.errorDialog(BuyerInfoActivity.this, getString(R.string.article_list_collecting), e.getMessage());
                            }
                        });
                    }
                }

                final ArrayNode articleList = new ObjectMapper().createArrayNode();
                Boolean hasRight = true; //
                for (JsonNode purchase : lastPurchaseList) {
                    int articleId = purchase.get("obj_id").intValue();

                    Boolean isIn = false;
                    for (JsonNode article : articleFoundationList) {
                        if (article.get("id").intValue() == articleId) {
                            ((ObjectNode) article).put("info", getString(R.string.realized) + " " + purchase.get("pur_date").textValue().substring(purchase.get("pur_date").textValue().length() - 8));
                            ((ObjectNode) article).put("quantity", Math.round(purchase.get("pur_qte").floatValue()));
                            ((ObjectNode) article).put("purchase_id", purchase.get("pur_id").intValue());
                            articleList.add(article);

                            isIn = true;
                            break;
                        }
                    }

                    if (!isIn) {
                        try {
                            articleList.add(new ObjectMapper().readTree("{" +
                                "\"name\":\"" + "N°: " + Integer.toString(purchase.get("obj_id").intValue()) + "\", " +
                                "\"price\":" + Integer.toString(purchase.get("pur_price").intValue()) + ", " +
                                "\"quantity\":" + Math.round(purchase.get("pur_qte").floatValue()) + ", " +
                                "\"purchase_id\":" + purchase.get("pur_id").intValue() + ", " +
                                "\"info\":\"" + getString(R.string.realized_by_other) + "\", " +
                                "\"image_url\":\"\"}"
                            ));
                        }
                        catch (Exception e) {
                            Log.e(LOG_TAG, "error: " + e.getMessage());

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    dialog.stopLoading();
                                    dialog.errorDialog(BuyerInfoActivity.this, getResources().getString(R.string.information_collection), getResources().getString(R.string.error_view), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int id) {
                                            finish();
                                        }
                                    });
                                }
                            });
                        }
                    }
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            lastArticleList = articleList;
                            listAdapater = new ListAdapater(BuyerInfoActivity.this, articleList);
                            listView.setAdapter(listAdapater);
                            dialog.stopLoading();
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "error: " + e.getMessage());

                            dialog.errorDialog(BuyerInfoActivity.this, getResources().getString(R.string.information_collection), getResources().getString(R.string.error_view), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int id) {
                                    finish();
                                }
                            });
                        }
                    }
                });

            }
        }.start();
    }
}
