package com.applicationzero.android;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import com.google.mediapipe.formats.proto.LandmarkProto;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList;
import com.google.mediapipe.components.CameraHelper;
import com.google.mediapipe.components.CameraXPreviewHelper;
import com.google.mediapipe.components.ExternalTextureConverter;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.framework.PacketGetter;
import com.google.mediapipe.glutil.EglManager;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String BINARY_GRAPH_NAME = "multihandtrackinggpu.binarypb";
    private static final String INPUT_VIDEO_STREAM_NAME = "input_video";
    private static final String OUTPUT_VIDEO_STREAM_NAME = "output_video";
    private static final String OUTPUT_LANDMARKS_STREAM_NAME = "multi_hand_landmarks";
    private static final CameraHelper.CameraFacing CAMERA_FACING = CameraHelper.CameraFacing.FRONT;
    private static final boolean FLIP_FRAMES_VERTICALLY = true;
    static {
        System.loadLibrary("mediapipe_jni");
        System.loadLibrary("opencv_java3");
    }
    private SurfaceTexture previewFrameTexture;
    private SurfaceView previewDisplayView;
    private EglManager eglManager;
    private FrameProcessor processor;
    private ExternalTextureConverter converter;
    private CameraXPreviewHelper cameraHelper;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        previewDisplayView = new SurfaceView(this);
        setPreD();
        frBi();
        AndroidAssetUtil.initializeNativeAssetManager(this);
        eglManager = new EglManager(null);
        processor = new FrameProcessor(this, eglManager.getNativeContext(), BINARY_GRAPH_NAME, INPUT_VIDEO_STREAM_NAME, OUTPUT_VIDEO_STREAM_NAME);
        processor.getVideoSurfaceOutput().setFlipY(FLIP_FRAMES_VERTICALLY);

        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setCornerRadius(25f);
        gradientDrawable.setColor(Color.parseColor("#33000000"));
        findViewById(R.id.nodPane).setBackground(gradientDrawable);
        findViewById(R.id.landmarkPane).setBackground(gradientDrawable);
        GradientDrawable gradientDrawable3 = new GradientDrawable();
        gradientDrawable3.setCornerRadius(15f);
        gradientDrawable3.setStroke(3, Color.parseColor("#5CA08E"));
        findViewById(R.id.languageControl).setBackground(gradientDrawable3);
        findViewById(R.id.languageControl).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GradientDrawable gradientDrawable = new GradientDrawable();
                gradientDrawable.setCornerRadius(15f);
                if(asl) {
                    asl = false;
                    gradientDrawable.setStroke(3, Color.parseColor("#FFFFFF"));
                    ((TextView)findViewById(R.id.textViewLanguage)).setText("BIT");
                    ((TextView)findViewById(R.id.textViewLanguage)).setTextColor(Color.parseColor("#FFFFFF"));
                    v.setBackground(gradientDrawable);
                    Toast.makeText(MainActivity.this, "BIT", Toast.LENGTH_SHORT).show();
                } else {
                    asl = true;
                    gradientDrawable.setStroke(3, Color.parseColor("#5CA08E"));
                    ((TextView)findViewById(R.id.textViewLanguage)).setText("ASL");
                    ((TextView)findViewById(R.id.textViewLanguage)).setTextColor(Color.parseColor("#5CA08E"));
                    v.setBackground(gradientDrawable);
                    Toast.makeText(MainActivity.this, "ASL", Toast.LENGTH_SHORT).show();
                }
            }
        });
        processor.addPacketCallback(
                OUTPUT_LANDMARKS_STREAM_NAME,
                (packet) -> {
                    List<NormalizedLandmarkList> multiHandLandmarks = PacketGetter.getProtoVector(packet, NormalizedLandmarkList.parser());
                    List<LandmarkProto.LandmarkList> landmarkList = PacketGetter.getProtoVector(packet, LandmarkProto.LandmarkList.parser());
                            initPathPreNorm(multiHandLandmarks);
                    String sysInfo = "[Frame Rate:"+fPer(0)+" fps]\n" + "[System Epoch (s):"+ (System.currentTimeMillis()/1000)  + "]\n";
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ((TextView)findViewById(R.id.textViewBeta)).setText(sysInfo);
                        }
                    });
                });

        PermissionHelper.checkAndRequestCameraPermissions(this);
    }
    @Override
    protected void onResume() {
        super.onResume();
        converter = new ExternalTextureConverter(eglManager.getContext());
        converter.setFlipY(FLIP_FRAMES_VERTICALLY);
        converter.setConsumer(processor);
        if (PermissionHelper.cameraPermissionsGranted(this)) {
            startCamera();
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
        converter.close();
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void setPreD() {
        previewDisplayView.setVisibility(View.GONE);
        ViewGroup viewGroup = findViewById(R.id.preview_display_layout);
        viewGroup.addView(previewDisplayView);
        previewDisplayView
                .getHolder()
                .addCallback(
                        new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(holder.getSurface());
                            }
                            @Override
                            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                                Size viewSize = new Size(width, height);
                                Size displaySize = cameraHelper.computeDisplaySizeFromViewSize(viewSize);
                                converter.setSurfaceTextureAndAttachToGLContext(
                                        previewFrameTexture, displaySize.getWidth(), displaySize.getHeight());
                            }
                            @Override
                            public void surfaceDestroyed(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(null);
                            }
                        });
    }
    private void startCamera() {
        cameraHelper = new CameraXPreviewHelper();
        cameraHelper.setOnCameraStartedListener(
                surfaceTexture -> {
                    previewFrameTexture = surfaceTexture;
                    previewDisplayView.setVisibility(View.VISIBLE);
                });
        cameraHelper.startCamera(this, CAMERA_FACING, null);
    }

    boolean asl = true;
    int bF_S = 7;
    int Cb_1 = 0;
    float sF = 0.05f;
    boolean motion = false;
    int senL = 15;
    float[] cD1 = new float[bF_S];
    String alp = "";
    String pAlp = "";
    String fE1 = "";
    String sE = "";
    FrameLandmark[] Lf_1 = new FrameLandmark[bF_S];
    String initPathPreNorm(List<NormalizedLandmarkList> stateList) {
        if (stateList.isEmpty()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    findViewById(R.id.relParent).setVisibility(View.GONE);
                    findViewById(R.id.predictionPane).setVisibility(View.GONE);
                    findViewById(R.id.nodPane).setVisibility(View.VISIBLE);
                }
            });
            return "";
        }
        String lStr = "";
        fE1 = "";
        int Li = 0;
        int pFm = gPfm();
        float[] D_01 = new float[21];
        float[] D_02 = new float[21];
        float[] D_1 = Lf_1[pFm].getxDimension();
        float[] D_2 = Lf_1[pFm].getyDimension();
        float n1 = 0;
        float d1 = 0;
        float d2 = 0;
        NormalizedLandmarkList landmarkStateZero = stateList.get(0);
        for(NormalizedLandmark landmark : landmarkStateZero.getLandmarkList()) {
            float X = landmark.getX();
            float Y = landmark.getY();
            lStr += "L`["+Li+"] = {"+landmark.getX()+", "+landmark.getY()+"}\n";
            D_01[Li] = X;
            D_02[Li] = Y;
            n1 = n1 + (X * D_1[Li]) + (Y * D_2[Li]);
            d1 = d1 + (X * X) + (Y * Y);
            d2 = d2 + (D_1[Li] * D_1[Li]) + (D_2[Li] * D_2[Li]);
            ++Li;
        }
        d1 = (float) Math.pow(d1, 0.5);
        d2 = (float) Math.pow(d2, 0.5);
        float c = n1 / (d1 * d2);
        if((float) Math.pow(2 * (1 - c), 0.5) > sF) cD1[Cb_1] = 1;
        else cD1[Cb_1] = 0;
        motion = false;
        for(int e = 0; e < bF_S; e++) if(cD1[e] == 1) motion = true;
        float[] pPV_1 = {Lf_1[Cb_1].yDimension[6], Lf_1[Cb_1].yDimension[10], Lf_1[Cb_1].yDimension[14], Lf_1[Cb_1].yDimension[18]};
        float[] pPV_2 = {Lf_1[Cb_1].xDimension[6], Lf_1[Cb_1].xDimension[10], Lf_1[Cb_1].xDimension[14], Lf_1[Cb_1].xDimension[18]};
        float[] mV_1 = {Lf_1[Cb_1].yDimension[5], Lf_1[Cb_1].yDimension[9], Lf_1[Cb_1].yDimension[13], Lf_1[Cb_1].yDimension[17]};
        float[] mV_2 = {Lf_1[Cb_1].xDimension[5], Lf_1[Cb_1].xDimension[9], Lf_1[Cb_1].xDimension[13], Lf_1[Cb_1].xDimension[17]};
        float[] dPV_1 = {Lf_1[Cb_1].yDimension[8], Lf_1[Cb_1].yDimension[12], Lf_1[Cb_1].yDimension[16], Lf_1[Cb_1].yDimension[20]};
        float[] dPV_2 = {Lf_1[Cb_1].xDimension[8], Lf_1[Cb_1].xDimension[12], Lf_1[Cb_1].xDimension[16], Lf_1[Cb_1].xDimension[20]};
        float[][] dP_21 = {dPV_2, dPV_1};
        float mD_1 = Math.max(Math.max(mV_1[0], mV_1[1]), Math.max(mV_1[2], mV_1[3])) - Math.min(Math.min(mV_1[0], mV_1[1]), Math.min(mV_1[2], mV_1[3]));
        float mD_2 = Math.max(Math.max(mV_2[0], mV_2[1]), Math.max(mV_2[2], mV_2[3])) - Math.min(Math.min(mV_2[0], mV_2[1]), Math.min(mV_2[2], mV_2[3]));
        float[] pPM = {pPV_2[2], pPV_1[2]};
        float[] pPT = {Lf_1[Cb_1].xDimension[2], Lf_1[Cb_1].yDimension[2]};
        float[] dPT = {Lf_1[Cb_1].xDimension[4], Lf_1[Cb_1].yDimension[4]};
        float[] cC = {Lf_1[Cb_1].xDimension[0], Lf_1[Cb_1].yDimension[0]};
        float delM = mD_2 - mD_1;
        float c_1 = cC[1] - mV_1[2];
        float[] tF_11 = {(dPT[0] - (Math.min(Math.min(mV_2[0], mV_2[1]), Math.min(mV_2[2], mV_2[3])))) / mD_2, (dPT[1]) / mD_1};
        if(!motion) {
            int aZ = pCom(delM, c_1);
            setC(c_1);
            alp = comD(dP_21, pPM, aZ);
            comDc(dPV_2[0], dPV_2[1], mV_2[0], mV_2[1]);
            comP(dPT[1], dPV_1[0], dPV_2[0], dPV_2[1], dPV_1[1]);
            comTp(aZ, dPT, pPT);
            if(asl) alp = cL_1(Integer.parseInt(alp, 2), aZ, tF_11);
            else alp = cL_2();
        }

        Lf_1[Cb_1].setxDimension(D_01);
        Lf_1[Cb_1].setyDimension(D_02);
        uCB();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                findViewById(R.id.relParent).setVisibility(View.VISIBLE);
                int[] relArray = {R.id.rel0, R.id.rel1, R.id.rel2, R.id.rel3, R.id.rel4, R.id.rel5, R.id.rel6};
                for(int e = 0; e < bF_S; e++) {
                    if(cD1[e] == 1) findViewById(relArray[e]).setVisibility(View.VISIBLE);
                    else findViewById(relArray[e]).setVisibility(View.GONE);
                }
                findViewById(R.id.nodPane).setVisibility(View.GONE);
                findViewById(R.id.predictionPane).setVisibility(View.VISIBLE);
                if(!(pAlp.equals(alp))) {
                    sE += alp;
                    if(sE.length() > senL) sE = sE.substring(1);
                    ((TextView)findViewById(R.id.textViewSentence)).setText(sE);
                }
                pAlp = alp;
                ((TextView)findViewById(R.id.textViewPrediction)).setText(alp);
            }
        });

        return lStr;
    }
    void frBi() {
        Arrays.fill(Lf_1, new FrameLandmark());
    }
    class FrameLandmark {
        private int landmarkCount = 21;
        float[] xDimension = new float[landmarkCount];
        float[] yDimension = new float[landmarkCount];

        FrameLandmark() {
            Arrays.fill(xDimension, 0.5f);
            Arrays.fill(yDimension, 0.5f);
        }
        float[] getxDimension() {
            return xDimension;
        }
        float[] getyDimension() {
            return yDimension;
        }
        void setxDimension(float[] xDimension) {
            this.xDimension = Arrays.copyOf(xDimension, landmarkCount);
        }
        void setyDimension(float[] yDimension) {
            this.yDimension = Arrays.copyOf(yDimension, landmarkCount);
        }
    }
    int gPfm() {
        if(Cb_1 == 0) return bF_S - 1;
        else return Cb_1 - 1;
    }
    void uCB() {
        ++Cb_1;
        if(Cb_1 >= bF_S) {
            Cb_1 = 0;
        }
    }
    int pCom(float delM, float Cv) {
        if(Math.abs(delM) < 0.06d) {
            fE1 += "00";
            return 3;
        }
        if(delM > 0) {
            fE1 += "10";
            if(Cv > 0.15) {
                return 1;
            }
            return 4;
        } else {
            fE1 += "01";
            return 2;
        }
    }
    void setC(float Cv) {
        if(Cv > 0.15f) fE1 += "1";
        else fE1 += "0";
    }
    void comDc(float dP8X, float dP12X, float mC5X, float mC9X) {
        if(Math.signum(dP8X - dP12X) == Math.signum(mC5X - mC9X)) fE1 += "0";
        else fE1 += "1";
    }
    void comP(float dP4Y, float dP8Y, float dP8X, float dp12X, float dP12Y) {
        if(Math.abs(dP4Y - dP8Y) < 0.1f) fE1 += "1";
        else fE1 += "0";
        if(Math.abs(dP8Y - dP12Y) < 0.1f) {
            if(Math.abs(dP8X - dp12X) < 0.1f) fE1 += "1";
            else fE1 += "0";
        } else fE1 += "0";
    }
    void comTp(int p1, float[] d1, float[] p2) {
        int o2_t = 0;
        if(p1 == 2) o2_t = 1;
        if(d1[o2_t] < p2[o2_t]) fE1 += "1";
        else fE1 += "0";
    }
    String comD(float[][] d3, float[] m3, int p1) {
        int o1_t = 1;
        if(p1 == 2) o1_t = 0;
        String dpString = "";
        if(d3[o1_t][0] < m3[o1_t]) dpString += "1";
        else dpString += "0";
        if(d3[o1_t][1] < m3[o1_t]) dpString += "1";
        else dpString += "0";
        if(d3[o1_t][2] < m3[o1_t]) dpString += "1";
        else dpString += "0";
        if(d3[o1_t][3] < m3[o1_t]) dpString += "1";
        else dpString += "0";
        fE1 += dpString;
        return dpString;
    }
    int ph_1(String s) {
        if (s.equals("10")) return 1;
        else if (s.equals("01")) return 2;
        else if (s.equals("00")) return 3;
        else return 4;
    }

    String cL_2() {
        String b1 = ""+fE1.charAt(6)+fE1.charAt(5)+fE1.charAt(4)+fE1.charAt(3)+fE1.charAt(10);
        String[] fr = {"E", "T", "A", "O", "P", "K", "N", "R", "X", "J", "!", "Q", "W", "B", "I", "S", "H", "L", "D", "F", "V", "C", "M", "U", "Z", ".", "$", "#", "G", "Y", "?", "  "};
        int a1id = Integer.parseInt(b1, 2);
        if(a1id < 32) return fr[a1id];
        return "";
    }
    String cL_1(int value, int p2, float[] tFv) {
        int[] vCt = {ph_1(""+fE1.charAt(0)+fE1.charAt(1)),
                fE1.charAt(2),
                Integer.parseInt(""+fE1.charAt(3)+fE1.charAt(4)+fE1.charAt(5)+fE1.charAt(6), 2),
                fE1.charAt(7),
                fE1.charAt(8),
                fE1.charAt(9),
                fE1.charAt(10)};
        if(vCt[0] == 1) {
            float tF = Math.abs(tFv[0]);
            if(value == 15) {
                if(tF > 1) return "  ";
                else return "B";
            }
            else if(value == 14) return "W";
            else if(value == 12) {
                if(fE1.charAt(7) == '1') return "R";
                else if(fE1.charAt(9) == '1') return "U";
                else return "K/V";
            }
            else if(value == 7) return "F";
            else if(value == 8) {
                if(tF > 1) return "L";
                else return "D";
            }
            else if(value == 1) {
                if(tF > 1) return "Y";
                else return "I";
            }
            else if(value == 0) {
                if(fE1.charAt(10) == '1') return "A";
                else return "E/..";
            }
        } else if(vCt[0] == 2) {
            if(value == 12 || value == 3) return "H";
            else if(value == 8 || value == 7) return "G";
        } else if(vCt[0] == 3) {
            if(fE1.charAt(8) == '1')return "O";
            else return "C";
        } else if(p2 == 4) {
            if(fE1.charAt(10) == '1') return "P";
            else return "Q;";
        }
        return "PHASE="+p2;
    }

    long sED = 0;
    long pFSE = 0;
    long cM() {
        long cFSE = (System.currentTimeMillis());
        sED = cFSE - pFSE;
        pFSE = cFSE;
        return sED;
    }
    long fPer(int metricIndex) {
        if(metricIndex == 0) return (1000/cM());
        else return -1;
    }
}