package space.protasov.tuner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNELS, ENCODING);
    private static final int PITCH_UPDATE_INTERVAL = 100; // milliseconds
    private static final double PITCH_THRESHOLD = 10; // hertz
    private static final double A4_FREQUENCY = 440; // hertz

    private TextView mPitchText;
    private Button mTuneButton;
    private EditText mCustomTuningEditText;

    private Tuner mTuner;
    private Handler mHandler;
    private boolean mRecording;
    private AudioRecord mAudioRecord;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPitchText = findViewById(R.id.tuningTextView);
        mTuneButton = findViewById(R.id.tune_button);
        mCustomTuningEditText = findViewById(R.id.custom_tuning_edit_text);

        mTuner = new Tuner();
        mHandler = new Handler(Looper.getMainLooper());

        mTuneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mRecording) {
                    startRecording();
                    mTuneButton.setText(R.string.stop_tuning);
                } else {
                    stopRecording();
                    mTuneButton.setText(R.string.start_tuning);
                }
            }
        });

        // Request permission to record audio
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mAudioRecord != null) {
            mAudioRecord.release();
        }
    }

    private void startRecording() {
        mRecording = true;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNELS, ENCODING, BUFFER_SIZE);

        // Start recording
        mAudioRecord.startRecording();

        // Start pitch detection
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mRecording) {
                    double pitch = mTuner.getPitch(mAudioRecord);
                    updatePitchText(pitch);
                    highlightPitch(pitch);
                    mHandler.postDelayed(this, PITCH_UPDATE_INTERVAL);
                }
            }
        }, PITCH_UPDATE_INTERVAL);
    }

    private void stopRecording() {
        mRecording = false;

        // Stop pitch detection
        mHandler.removeCallbacksAndMessages(null);

        // Stop recording
        if (mAudioRecord != null) {
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord = null;
        }
    }

    private void updatePitchText(double pitch) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPitchText.setText(String.format("%.2f Hz", pitch));
            }
        });
    }

    private void highlightPitch(double pitch) {
        double deviation = pitch - A4_FREQUENCY;
        if (Math.abs(deviation) < PITCH_THRESHOLD) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mPitchText.setTextColor(getResources().getColor(R.color.green1));
                }
            });
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mPitchText.setTextColor(getResources().getColor(R.color.black));
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION && grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "You need to grant permission to record audio in order to use this app", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private class Tuner {
        private static final double[] STANDARD_TUNING = {E2, A2, D3, G3, B3, E4}; // E A D G B E
        private static final double E2 = 82.41; // hertz
        private static final double A2 = 110.00; // hertz
        private static final double D3 = 146.83; // hertz
        private static final double G3 = 196.00; // hertz
        private static final double B3 = 246.94; // hertz
        private static final double E4 = 329.63; // hertz

        private double[] mTuning;
        private String[] mTuningNames;
        private Object PitchDetection;

        public Tuner() {
            mTuning = STANDARD_TUNING;
            mTuningNames = getResources().getStringArray(R.array.standard_tuning_names);
        }

        public double getPitch(AudioRecord audioRecord) {
            short[] buffer = new short[BUFFER_SIZE];
            audioRecord.read(buffer, 0, BUFFER_SIZE);

            double[] audioData = new double[BUFFER_SIZE];
            for (int i = 0; i < BUFFER_SIZE; i++) {
                audioData[i] = (double) buffer[i] / Short.MAX_VALUE;
            }

            double sampleRate = (double) SAMPLE_RATE;
            double fundamentalFrequency = PitchDetection.getFundamentalFrequency(audioData, sampleRate);

            return fundamentalFrequency;
        }

        public void setTuning(int position) {
            switch (position) {
                case 0:
                    mTuning = STANDARD_TUNING;
                    mTuningNames = getResources().getStringArray(R.array.standard_tuning_names);
                    break;
                // Add additional tunings here
                default:
                    break;
            }
        }

        public String[] getTuningNames() {
            return mTuningNames;
        }

        public void setCustomTuning(String tuning) {
            String[] tuningStrings = tuning.split(",");
            double[] customTuning = new double[tuningStrings.length];
            for (int i = 0; i < tuningStrings.length; i++) {
                try {
                    customTuning[i] = Double.parseDouble(tuningStrings[i]);
                } catch (NumberFormatException e) {
                    Toast.makeText(MainActivity.this, "Invalid tuning string: " + tuning, Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            mTuning = customTuning;
            mTuningNames = new String[tuningStrings.length];
            for (int i = 0; i < tuningStrings.length; i++) {
                mTuningNames[i] = "String " + (i + 1);
            }
        }

        public String getCustomTuning() {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < mTuning.length; i++) {
                builder.append(mTuning[i]);
                if (i != mTuning.length - 1) {
                    builder.append(",");
                }
            }
            return builder.toString();
        }

        public int getClosestStringIndex(double pitch) {
            int closestIndex = -1;
            double closestDeviation = Double.MAX_VALUE;
            for (int i = 0; i < mTuning.length; i++) {
                double deviation = Math.abs(pitch - mTuning[i]);
                if (deviation < closestDeviation) {
                    closestIndex = i;
                    closestDeviation = deviation;
                }
            }
            return closestIndex;
        }
    }
}