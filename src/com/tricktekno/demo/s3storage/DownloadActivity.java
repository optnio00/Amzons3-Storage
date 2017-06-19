/*
 * Copyright 2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.tricktekno.demo.s3storage;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.SimpleAdapter;
import android.widget.SimpleAdapter.ViewBinder;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferType;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DownloadActivity extends ListActivity {
    private static final String TAG = "DownloadActivity";

    private static final int DOWNLOAD_SELECTION_REQUEST_CODE = 1;

    private static final int INDEX_NOT_CHECKED = -1;

    private Button btnDownload;
    private Button btnPause;
    private Button btnResume;
    private Button btnCancel;
    private Button btnDelete;
    private Button btnPauseAll;
    private Button btnCancelAll;

    private TransferUtility transferUtility;


    private SimpleAdapter simpleAdapter;

    private List<TransferObserver> observers;

    private ArrayList<HashMap<String, Object>> transferRecordMaps;
    private int checkedIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download);
        // Initializes TransferUtility, always do this before using it.
        transferUtility = Util.getTransferUtility(this);
        checkedIndex = INDEX_NOT_CHECKED;
        transferRecordMaps = new ArrayList<HashMap<String, Object>>();
        initUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        initData();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (observers != null && !observers.isEmpty()) {
            for (TransferObserver observer : observers) {
                observer.cleanTransferListener();
            }
        }
    }

    private void initData() {
        transferRecordMaps.clear();
        // Uses TransferUtility to get all previous download records.
        observers = transferUtility.getTransfersWithType(TransferType.DOWNLOAD);
        TransferListener listener = new DownloadListener();
        for (TransferObserver observer : observers) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            Util.fillMap(map, observer, false);
            transferRecordMaps.add(map);


            if (TransferState.WAITING.equals(observer.getState())
                    || TransferState.WAITING_FOR_NETWORK.equals(observer.getState())
                    || TransferState.IN_PROGRESS.equals(observer.getState())) {
                observer.setTransferListener(listener);
            }
        }
        simpleAdapter.notifyDataSetChanged();
    }

    private void initUI() {
        simpleAdapter = new SimpleAdapter(this, transferRecordMaps,
                R.layout.record_item, new String[] {
                        "checked", "fileName", "progress", "bytes", "state", "percentage"
                },
                new int[] {
                        R.id.radioButton1, R.id.textFileName, R.id.progressBar1, R.id.textBytes,
                        R.id.textState, R.id.textPercentage
                });
        simpleAdapter.setViewBinder(new ViewBinder() {
            @Override
            public boolean setViewValue(View view, Object data,
                    String textRepresentation) {
                switch (view.getId()) {
                    case R.id.radioButton1:
                        RadioButton radio = (RadioButton) view;
                        radio.setChecked((Boolean) data);
                        return true;
                    case R.id.textFileName:
                        TextView fileName = (TextView) view;
                        fileName.setText((String) data);
                        return true;
                    case R.id.progressBar1:
                        ProgressBar progress = (ProgressBar) view;
                        progress.setProgress((Integer) data);
                        return true;
                    case R.id.textBytes:
                        TextView bytes = (TextView) view;
                        bytes.setText((String) data);
                        return true;
                    case R.id.textState:
                        TextView state = (TextView) view;
                        state.setText(((TransferState) data).toString());
                        return true;
                    case R.id.textPercentage:
                        TextView percentage = (TextView) view;
                        percentage.setText((String) data);
                        return true;
                }
                return false;
            }
        });
        setListAdapter(simpleAdapter);


        getListView().setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long id) {
                if (checkedIndex != pos) {
                    transferRecordMaps.get(pos).put("checked", true);
                    if (checkedIndex >= 0) {
                        transferRecordMaps.get(checkedIndex).put("checked", false);
                    }
                    checkedIndex = pos;
                    updateButtonAvailability();
                    simpleAdapter.notifyDataSetChanged();
                }
            }
        });

        btnDownload = (Button) findViewById(R.id.buttonDownload);
        btnPause = (Button) findViewById(R.id.buttonPause);
        btnResume = (Button) findViewById(R.id.buttonResume);
        btnCancel = (Button) findViewById(R.id.buttonCancel);
        btnDelete = (Button) findViewById(R.id.buttonDelete);
        btnPauseAll = (Button) findViewById(R.id.buttonPauseAll);
        btnCancelAll = (Button) findViewById(R.id.buttonCancelAll);

        // Launches an activity for the user to select an object in their S3
        // bucket to download
        btnDownload.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DownloadActivity.this, DownloadSelectionActivity.class);
                startActivityForResult(intent, DOWNLOAD_SELECTION_REQUEST_CODE);
            }
        });

        btnPause.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                // Make sure the user has selected a transfer
                if (checkedIndex >= 0 && checkedIndex < observers.size()) {
                    Boolean paused = transferUtility.pause(observers.get(checkedIndex)
                            .getId());
                    if (!paused) {
                        Toast.makeText(
                                DownloadActivity.this,
                                "Cannot Pause transfer.  You can only pause transfers in a WAITING or IN_PROGRESS state.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        btnResume.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                // Make sure the user has selected a transfer
                if (checkedIndex >= 0 && checkedIndex < observers.size()) {
                    TransferObserver resumed = transferUtility.resume(observers.get(checkedIndex)
                            .getId());

                    observers.get(checkedIndex).setTransferListener(new DownloadListener());


                    if (resumed == null) {
                        Toast.makeText(
                                DownloadActivity.this,
                                "Cannot resume transfer.  You can only resume transfers in a PAUSED state.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        btnCancel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Make sure a transfer is selected
                if (checkedIndex >= 0 && checkedIndex < observers.size()) {
                    Boolean canceled = transferUtility.cancel(observers.get(checkedIndex).getId());
                    /**
                     * If cancel returns false, it is likely because the
                     * transfer is already canceled
                     */
                    if (!canceled) {
                        Toast.makeText(
                                DownloadActivity.this,
                                "Cannot cancel transfer.  You can only resume transfers in a PAUSED, WAITING, or IN_PROGRESS state.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        btnDelete.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Make sure a transfer is selected
                if (checkedIndex >= 0 && checkedIndex < observers.size()) {
                    // Deletes a record but the file is not deleted.
                    transferUtility.deleteTransferRecord(observers.get(checkedIndex).getId());
                    observers.remove(checkedIndex);
                    transferRecordMaps.remove(checkedIndex);
                    checkedIndex = INDEX_NOT_CHECKED;
                    updateButtonAvailability();
                    updateList();
                }
            }
        });

        btnPauseAll.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                transferUtility.pauseAllWithType(TransferType.DOWNLOAD);
            }
        });

        btnCancelAll.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                transferUtility.cancelAllWithType(TransferType.DOWNLOAD);
            }
        });

        updateButtonAvailability();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == DOWNLOAD_SELECTION_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                String key = data.getStringExtra("key");
                beginDownload(key);
            }
        }
    }

    private void beginDownload(String key) {

        File file = new File(Environment.getExternalStorageDirectory().toString() + "/" + key);

        TransferObserver observer = transferUtility.download(Constants.BUCKET_NAME, key, file);

    }

    private void updateList() {
        TransferObserver observer = null;
        HashMap<String, Object> map = null;
        for (int i = 0; i < observers.size(); i++) {
            observer = observers.get(i);
            map = transferRecordMaps.get(i);
            Util.fillMap(map, observer, i == checkedIndex);
        }
        simpleAdapter.notifyDataSetChanged();
    }

    /*
     * Enables or disables buttons according to checkedIndex.
     */
    private void updateButtonAvailability() {
        boolean availability = checkedIndex >= 0;
        btnPause.setEnabled(availability);
        btnResume.setEnabled(availability);
        btnCancel.setEnabled(availability);
        btnDelete.setEnabled(availability);
    }


    private class DownloadListener implements TransferListener {

        @Override
        public void onError(int id, Exception e) {
            Log.e(TAG, "onError: " + id, e);
            updateList();
        }

        @Override
        public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
            Log.d(TAG, String.format("onProgressChanged: %d, total: %d, current: %d",
                    id, bytesTotal, bytesCurrent));
            updateList();
        }

        @Override
        public void onStateChanged(int id, TransferState state) {
            Log.d(TAG, "onStateChanged: " + id + ", " + state);
            updateList();
        }
    }
}
