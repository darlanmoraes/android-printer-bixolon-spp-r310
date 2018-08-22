package com.darlan.bixolon;


import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Set;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import jpos.JposException;
import jpos.POSPrinter;
import jpos.POSPrinterConst;
import jpos.config.JposEntry;
import com.bxl.config.editor.BXLConfigLoader;

import static com.bxl.config.editor.BXLConfigLoader.*;

public class MainActivity extends Activity implements View.OnClickListener,
        AdapterView.OnItemClickListener, SeekBar.OnSeekBarChangeListener, OnCheckedChangeListener {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_BLUETOOTH = 1;
    private static final int REQUEST_CODE_ACTION_PICK = 2;

    private static final String DEVICE_ADDRESS_START = " (";
    private static final String DEVICE_ADDRESS_END = ")";

    private final ArrayList<CharSequence> bondedDevices = new ArrayList<>();
    private ArrayAdapter<CharSequence> arrayAdapter;

    private TextView pathTextView;
    private TextView progressTextView;
    private RadioGroup openRadioGroup;
    private Button openFromDeviceStorageButton;
    private BXLConfigLoader bxlConfigLoader;
    private POSPrinter posPrinter;
    private String logicalName;
    private int brightness = 50;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        onActivityAction();
        setBondedDevices();
        showPairedDevices();
        loadBXLConfig();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closePrinter();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.action_settings) {
            final Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            startActivityForResult(intent, REQUEST_CODE_BLUETOOTH);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_BLUETOOTH:
                setBondedDevices();
                break;

            case REQUEST_CODE_ACTION_PICK:
                onPickSomeAction(data);
                break;
        }
    }

    @Override
    public void onClick(final View v) {
        switch (v.getId()) {
            case R.id.buttonOpenFromDeviceStorage:
                openFromDeviceStorage();
                break;

            case R.id.buttonOpenPrinter:
                openPrinter();
                break;

            case R.id.buttonPrint:
                print();
                break;

            case R.id.buttonClosePrinter:
                closePrinter();
                break;
        }
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
        final String device = ((TextView) view).getText().toString();
        final String name = device.substring(0, device.indexOf(DEVICE_ADDRESS_START));
        final String address = device.substring(device.indexOf(DEVICE_ADDRESS_START)
                        + DEVICE_ADDRESS_START.length(), device.indexOf(DEVICE_ADDRESS_END));

        try {
            for (final Object entry : bxlConfigLoader.getEntries()) {
                final JposEntry jposEntry = (JposEntry) entry;
                bxlConfigLoader.removeEntry(jposEntry.getLogicalName());
            }
        } catch (final Exception e) {
            Log.e(TAG, "Error removing entry.", e);
        }

        try {
            logicalName = setProductName(name);
            bxlConfigLoader.addEntry(logicalName,
                    BXLConfigLoader.DEVICE_CATEGORY_POS_PRINTER,
                    logicalName,
                    BXLConfigLoader.DEVICE_BUS_BLUETOOTH, address);

            bxlConfigLoader.saveFile();
        } catch (final Exception e) {
            Log.e(TAG, "Error saving file.", e);
        }
    }

    private void onActivityAction() {
        pathTextView = findViewById(R.id.textViewPath);
        progressTextView = findViewById(R.id.textViewProgress);

        openRadioGroup = findViewById(R.id.radioGroupOpen);
        openRadioGroup.setOnCheckedChangeListener(this);

        openFromDeviceStorageButton = findViewById(R.id.buttonOpenFromDeviceStorage);
        openFromDeviceStorageButton.setOnClickListener(this);

        findViewById(R.id.buttonOpenPrinter).setOnClickListener(this);
        findViewById(R.id.buttonPrint).setOnClickListener(this);
        findViewById(R.id.buttonClosePrinter).setOnClickListener(this);

        final SeekBar seekBar = findViewById(R.id.seekBarBrightness);
        seekBar.setOnSeekBarChangeListener(this);
    }

    private void showPairedDevices() {
        arrayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_single_choice, bondedDevices);
        final ListView listView = findViewById(R.id.listViewPairedDevices);
        listView.setAdapter(arrayAdapter);

        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        listView.setOnItemClickListener(this);
    }

    private void loadBXLConfig() {
        bxlConfigLoader = new BXLConfigLoader(this);
        try {
            bxlConfigLoader.openFile();
        } catch (Exception e) {
            e.printStackTrace();
            bxlConfigLoader.newFile();
        }
        posPrinter = new POSPrinter(this);
    }

    private void onPickSomeAction(final Intent data) {
        if (data != null) {
            final Uri uri = data.getData();
            if (uri != null) {
                final ContentResolver cr = getContentResolver();
                final Cursor c = cr.query(uri, new String[]{ MediaStore.Images.Media.DATA },
                        null, null, null);
                if (c == null || c.getCount() == 0) {
                    return;
                }

                c.moveToFirst();
                final int columnIndex = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                final String text = c.getString(columnIndex);
                c.close();

                pathTextView.setText(text);
            }
        }
    }

    private String setProductName(final String name) {
        if ((name.contains("SPP-R200II"))) {
            if (name.length() > 10) {
                if (name.substring(10, 11).equals("I")) {
                    return PRODUCT_NAME_SPP_R200III;
                }
            }
        } else if ((name.contains("SPP-R210"))) {
            return PRODUCT_NAME_SPP_R210;
        } else if ((name.contains("SPP-R310"))) {
            return PRODUCT_NAME_SPP_R310;
        } else if ((name.contains("SPP-R300"))) {
            return PRODUCT_NAME_SPP_R300;
        } else if ((name.contains("SPP-R400"))) {
            return PRODUCT_NAME_SPP_R400;
        }
        return PRODUCT_NAME_SPP_R200II;
    }


    @Override
    @SuppressLint("SetTextI18n")
    public void onProgressChanged(final SeekBar seekBar, final int progress, final boolean fromUser) {
        progressTextView.setText(Integer.toString(progress));
        brightness = progress;
    }

    @Override
    public void onStartTrackingTouch(final SeekBar seekBar) { }

    @Override
    public void onStopTrackingTouch(final SeekBar seekBar) { }

    @Override
    public void onCheckedChanged(final RadioGroup group, final int checkedId) {
        switch (checkedId) {
            case R.id.radioDeviceStorage:
                openFromDeviceStorageButton.setEnabled(true);
                break;

            case R.id.radioProjectResources:
                openFromDeviceStorageButton.setEnabled(false);
                break;
        }
    }

    private void setBondedDevices() {
        logicalName = null;
        bondedDevices.clear();
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            final Set<BluetoothDevice> bondedDeviceSet = bluetoothAdapter.getBondedDevices();
            for (final BluetoothDevice device : bondedDeviceSet) {
                bondedDevices.add(device.getName() + DEVICE_ADDRESS_START
                        + device.getAddress() + DEVICE_ADDRESS_END);
            }
            if (arrayAdapter != null) {
                arrayAdapter.notifyDataSetChanged();
            }
        }
    }

    private void openFromDeviceStorage() {
        final String externalStorageState = Environment.getExternalStorageState();
        if (externalStorageState.equals(Environment.MEDIA_MOUNTED)) {
            final Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType(MediaStore.Images.Media.CONTENT_TYPE);
            startActivityForResult(intent, REQUEST_CODE_ACTION_PICK);
        }
    }

    private void openPrinter() {
        try {
            posPrinter.open(logicalName);
            posPrinter.claim(0);
            posPrinter.setDeviceEnabled(true);
        } catch (final JposException e) {
            Log.e(TAG, "Error opening printer.", e);
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            closePrinter();
        }
    }

    private void closePrinter() {
        try {
            posPrinter.close();
        } catch (JposException e) {
            Log.e(TAG, "Error closing printer.", e);
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void print() {
        InputStream is = null;
        try {
            final ByteBuffer buffer = buildBuffer();
            switch (openRadioGroup.getCheckedRadioButtonId()) {
                case R.id.radioDeviceStorage:
                    posPrinter.printBitmap(buffer.getInt(0), pathTextView.getText().toString(),
                            posPrinter.getRecLineWidth(), POSPrinterConst.PTR_BM_LEFT);
                    break;

                case R.id.radioProjectResources:
                    is = getResources().openRawResource(R.raw.bixolon_logo_black_500);
                    final Bitmap bitmap = BitmapFactory.decodeStream(is);
                    posPrinter.printBitmap(buffer.getInt(0), bitmap,
                            posPrinter.getRecLineWidth(), POSPrinterConst.PTR_BM_LEFT);

                    break;
            }
        } catch (final JposException e) {
            Log.e(TAG, "Error printing.", e);
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            closeInputStream(is);
        }
    }

    private ByteBuffer buildBuffer() {
        final ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.put((byte) POSPrinterConst.PTR_S_RECEIPT);
        buffer.put((byte) brightness);
        int compress = 1;
        buffer.put((byte) compress);
        buffer.put((byte) 0x00);
        return buffer;
    }

    private void closeInputStream(final InputStream is) {
        if (is != null) {
            try {
                is.close();
            } catch (final IOException e) {
                Log.e(TAG, "Error closing input stream.", e);
            }
        }
    }

}
