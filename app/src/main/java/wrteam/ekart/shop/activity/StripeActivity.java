package wrteam.ekart.shop.activity;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.android.volley.Request;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import wrteam.ekart.shop.R;
import wrteam.ekart.shop.fragment.WalletTransactionFragment;
import wrteam.ekart.shop.helper.ApiConfig;
import wrteam.ekart.shop.helper.AppController;
import wrteam.ekart.shop.helper.Constant;
import wrteam.ekart.shop.helper.PaymentModelClass;
import wrteam.ekart.shop.helper.Session;
import wrteam.ekart.shop.helper.VolleyCallback;

public class StripeActivity extends AppCompatActivity {
    Toolbar toolbar;
    WebView webView;
    String url;
    PaymentModelClass paymentModelClass;
    boolean isTxnInProcess = true;
    String itemNo;
    Map<String, String> sendParams;
    String from;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(getString(R.string.stripe));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        paymentModelClass = new PaymentModelClass(StripeActivity.this);
        url = getIntent().getStringExtra("url");
        itemNo = getIntent().getStringExtra(Constant.ORDER_ID);
        sendParams = (Map<String, String>) getIntent().getSerializableExtra(Constant.PARAMS);
        from = getIntent().getStringExtra(Constant.FROM);

        webView = findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith(Constant.MAINBASEUrl)) {
                    GetTransactionResponse(url);
                    return true;
                } else
                    isTxnInProcess = true;
                return false;
            }
        });
        webView.loadUrl(url);
    }

    public void GetTransactionResponse(String url) {
        StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                response -> {
                    isTxnInProcess = false;
                    try {
                        JSONObject jsonObject = new JSONObject(response);
                        String status = jsonObject.getString("transaction_status");
                        AddTransaction(StripeActivity.this, itemNo, getString(R.string.stripe), itemNo, status, jsonObject.getString(Constant.MESSAGE), sendParams);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                },
                (VolleyError error) -> error.printStackTrace());
        AppController.getInstance().getRequestQueue().getCache().clear();
        AppController.getInstance().addToRequestQueue(stringRequest);

    }

    public void AddTransaction(Activity activity, String orderId, String paymentType, String txnid, final String status, String message, Map<String, String> sendparams) {
        Map<String, String> transparams = new HashMap<>();
        transparams.put(Constant.Add_TRANSACTION, Constant.GetVal);
        transparams.put(Constant.USER_ID, sendparams.get(Constant.USER_ID));
        transparams.put(Constant.ORDER_ID, orderId);
        transparams.put(Constant.TYPE, paymentType);
        transparams.put(Constant.TRANS_ID, txnid);
        transparams.put(Constant.AMOUNT, sendparams.get(Constant.FINAL_TOTAL));
        transparams.put(Constant.STATUS, status);
        transparams.put(Constant.MESSAGE, message);
        Date c = Calendar.getInstance().getTime();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        transparams.put("transaction_date", df.format(c));
        ApiConfig.RequestToVolley(new VolleyCallback() {
            @Override
            public void onSuccess(boolean result, String response) {
                if (result) {
                    try {
                        JSONObject objectbject = new JSONObject(response);
                        if (!objectbject.getBoolean(Constant.ERROR)) {

                            if (from.equals(Constant.WALLET)) {
                                onBackPressed();
                                new WalletTransactionFragment().AddWalletBalance(StripeActivity.this, new Session(StripeActivity.this), WalletTransactionFragment.amount, WalletTransactionFragment.msg, orderId);
                            } else if (from.equals(Constant.PAYMENT)) {
                                if (status.equals("capture") || status.equals("challenge") || status.equals("pending")) {
                                    finish();
                                    Intent intent = new Intent(activity, MainActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    intent.putExtra(Constant.FROM, "payment_success");
                                    activity.startActivity(intent);
                                } else if (status.equals("deny") || status.equals("expire") || status.equals("cancel")) {
                                    finish();
                                }
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }, StripeActivity.this, Constant.ORDERPROCESS_URL, transparams, false);
    }

    @Override
    public void onBackPressed() {
        if (isTxnInProcess)
            ProcessAlertDialog();
        else
            super.onBackPressed();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return super.onSupportNavigateUp();
    }

    public void ProcessAlertDialog() {
        final AlertDialog.Builder alertDialog = new AlertDialog.Builder(StripeActivity.this);
        // Setting Dialog Message
        alertDialog.setMessage(getString(R.string.txn_cancel_msg));
        alertDialog.setCancelable(false);
        final AlertDialog alertDialog1 = alertDialog.create();
        alertDialog.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                DeleteTransaction(StripeActivity.this, itemNo);
                alertDialog1.dismiss();
            }
        }).setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                alertDialog1.dismiss();
            }
        });
        // Showing Alert Message
        alertDialog.show();
    }


    public void DeleteTransaction(Activity activity, String orderId) {
        Map<String, String> transparams = new HashMap<>();
        transparams.put(Constant.DELETE_ORDER, Constant.GetVal);
        transparams.put(Constant.ORDER_ID, orderId);
        ApiConfig.RequestToVolley(new VolleyCallback() {
            @Override
            public void onSuccess(boolean result, String response) {
                if (result) {
                    StripeActivity.super.onBackPressed();
                }
            }
        }, activity, Constant.ORDERPROCESS_URL, transparams, false);
    }

}