//package com.storm.abod3;
//
//import android.app.ProgressDialog;
//import android.graphics.Canvas;
//import android.graphics.Matrix;
//import android.graphics.Paint;
//import android.graphics.Rect;
//import android.graphics.Typeface;
//import android.hardware.camera2.CameraDevice;
//import android.os.Bundle;
//import android.support.annotation.NonNull;
//import android.support.annotation.Nullable;
//import android.util.Log;
//import android.view.SurfaceView;
//import android.widget.FrameLayout;
//import android.widget.LinearLayout;
//import android.widget.Toast;
//
//import com.storm.experiment1.R;
//
//import boofcv.alg.color.ColorFormat;
//import boofcv.android.ConvertBitmap;
//import boofcv.android.camera2.VisualizeCamera2Activity;
//import boofcv.core.image.ConvertImage;
//import boofcv.misc.MovingAverage;
//import boofcv.struct.image.GrayU8;
//import boofcv.struct.image.ImageBase;
//import boofcv.struct.image.ImageDataType;
//import boofcv.struct.image.ImageType;
//import boofcv.struct.image.Planar;
//import georegression.struct.point.Point2D_F64;
//
///**
// * Camera activity specifically designed for this demonstration. Image processing algorithms
// * can be swapped in and out
// *
// * useful variables and methods
// * visualizationPending
// */
//public abstract class Camera2Activity extends VisualizeCamera2Activity {
//
//    private static final String TAG = "Camera2Activity";
//
//    //######## Start variables owned by lock
//    protected final Object lockProcessor = new Object();
//    protected DemoProcessing processor;
//    // if false no processor functions will be called. Will not be set to true until the
//    // resolution is known and init has been called
//    protected boolean cameraInitialized;
//    // camera width and height when processor was initialized
//    protected int cameraWidth,cameraHeight,cameraOrientation;
//    //####### END
//
//    // If true it will show the processed image, otherwise it will
//    // display the input image
//    protected boolean showProcessed = true;
//
//    // Used to inform the user that its doing some calculations
//    ProgressDialog progressDialog;
//    protected final Object lockProgress = new Object();
//
//    //START Timing data structures locked on super.lockTiming
//    protected int totalFramesProcessed; // total frames processed for the specific processing algorithm
//    protected MovingAverage periodProcess = new MovingAverage(0.8); // milliseconds
//    protected MovingAverage periodRender = new MovingAverage(0.8); // milliseconds
//    //END
//
//    // If this is true then visualization data has not been rendered and the input image
//    // will not be processed. This ensures that visualization and the background image
//    // are rendered together with each other
//    // To use this feature set the variable to true inside the process(image) block after visuals
//    // have been made
//    protected volatile boolean visualizationPending = false;
//    // NOTE: The current approach isn't perfect. it's possible for an old do nothing visual to
//    //       mark this variable as false before the now one is passed in
//
//    // if a process is taking too long potentially trigger a change in resolution to sleep things up
//    protected boolean changeResolutionOnSlow = false;
//    protected boolean triggerSlow;
//    protected final static double TRIGGER_HORIBLY_SLOW = 2000.0;
//    protected final static double TRIGGER_SLOW = 400.0;
//
//    // Work variables for rendering performance
//    private Paint paintText = new Paint();
//    private Rect bounds0 = new Rect();
//    private Rect bounds1 = new Rect();
//    private Rect bounds2 = new Rect();
//    private final Matrix tempMatrix = new Matrix();
//
//
//    public Camera2Activity(Resolution resolution) {
//        super.targetResolution = resolutionToPixels(resolution);
//
//        super.showBitmap = true;
//        super.visualizeOnlyMostRecent = true;
//    }
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        // Assign app before letting the parent initialize because there's a chance the camera
//        // could be initialized before this gets assigned and generate a NPE
//        super.onCreate(savedInstanceState);
//
//        paintText.setStrokeWidth(3*displayMetrics.density);
//        paintText.setTextSize(24*displayMetrics.density);
//        paintText.setTextAlign(Paint.Align.LEFT);
//        paintText.setARGB(0xFF,0xFF,0xB0,0);
//        paintText.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
//    }
//
//    @Override
//    protected void onStop() {
//        super.onStop();
//        // this is an attempt to prevent the leaked window error.
//        synchronized (lockProgress) {
//            if( progressDialog != null ) {
//                progressDialog.dismiss();
//                progressDialog = null;
//            }
//        }
//    }
//
//    /**
//     * Creates a new process and calls setProcess(new ASDASDASD). This is invoked
//     * after a new camera device has been opened
//     */
//    public abstract void createNewProcessor();
//
//    protected void setControls(@Nullable LinearLayout controls ) {
//        setContentView(R.layout.activity_camera);
//        LinearLayout parent = findViewById(R.id.root_layout);
//        if( controls != null)
//            parent.addView(controls);
//
//        FrameLayout surfaceLayout = findViewById(R.id.camera_frame_layout);
//        startCamera(surfaceLayout,null);
//    }
//
//    @Override
//    protected void processImage(ImageBase image) {
//        // the previous visualization has yet to be rendered.
//        if( visualizationPending )
//            return;
//
//        if( !showProcessed) {
//            synchronized (bitmapLock) {
//                ConvertBitmap.boofToBitmap(image, bitmap, bitmapTmp);
//            }
//            return;
//        }
//        DemoProcessing processor;
//        synchronized (lockProcessor) {
//            if( !cameraInitialized || this.processor == null )
//                return;
//            processor = this.processor;
//        }
//
//        if( !processor.isThreadSafe() && threadPool.getMaximumPoolSize() > 1 )
//            throw new RuntimeException("Process is not thread safe but the pool is larger than 1!");
//
//        if( processor.getImageType().isSameType(image.getImageType())) {
//            long before = System.nanoTime();
//            try {
//                processor.process(image);
//            } catch( OutOfMemoryError e ) {
//                runOnUiThread(()->{
//                    finish(); // leave the activity
//                    Toast.makeText(this,"Out of Memory. Try lower resolution",Toast.LENGTH_LONG).show();
//                });
//                return;
//            }
//            long after = System.nanoTime();
//
//            double milliseconds = (after-before)*1e-6;
//
////            double timeProcess,timeConvert;
//            synchronized (lockTiming) {
//                totalFramesProcessed++;
//                // give it a few frames to warm up
//                if( totalFramesProcessed >= TIMING_WARM_UP ) {
//                    periodProcess.update(milliseconds);
//                    triggerSlow |= periodProcess.getAverage() > TRIGGER_SLOW;
//                } else {
//                    // if things are extremely slow right off the bat abort and change resolution
//                    triggerSlow |= milliseconds >= TRIGGER_HORIBLY_SLOW;
//                }
//
////                timeProcess = periodProcess.getAverage();
////                timeConvert = periodConvert.getAverage();
//            }
//
//            if( verbose ) {
////                Log.i("DemoTiming",String.format("Total Frames %4d curr %5.1f ave process %5.1f convert %5.1f at %dx%d",
////                        totalFramesProcessed,milliseconds,timeProcess,timeConvert,image.width,image.height));
//            }
//
//        }        else{
//            long before = System.nanoTime();
//            try {
//
//                GrayU8 grayU8Image = new GrayU8(image.getWidth(),image.getHeight());
//                ConvertImage.average((Planar) image , grayU8Image);
//
//                processor.process(grayU8Image);
//            } catch( OutOfMemoryError e ) {
//                runOnUiThread(()->{
//                    finish(); // leave the activity
//                    Toast.makeText(this,"Out of Memory. Try lower resolution",Toast.LENGTH_LONG).show();
//                });
//                return;
//            }
//            long after = System.nanoTime();
//
//            double milliseconds = (after-before)*1e-6;
//
////            double timeProcess,timeConvert;
//            synchronized (lockTiming) {
//                totalFramesProcessed++;
//                // give it a few frames to warm up
//                if( totalFramesProcessed >= TIMING_WARM_UP ) {
//                    periodProcess.update(milliseconds);
//                    triggerSlow |= periodProcess.getAverage() > TRIGGER_SLOW;
//                } else {
//                    // if things are extremely slow right off the bat abort and change resolution
//                    triggerSlow |= milliseconds >= TRIGGER_HORIBLY_SLOW;
//                }
//
////                timeProcess = periodProcess.getAverage();
////                timeConvert = periodConvert.getAverage();
//            }
//
//            if( verbose ) {
////                Log.i("DemoTiming",String.format("Total Frames %4d curr %5.1f ave process %5.1f convert %5.1f at %dx%d",
////                        totalFramesProcessed,milliseconds,timeProcess,timeConvert,image.width,image.height));
//            }
//        }
//
//    }
//
//    @Override
//    protected void onCameraOpened( @NonNull CameraDevice cameraDevice ) {
//        // Adding a delay before starting the process seems to allow things to run better
//        // An image can be displayed
//        new java.util.Timer().schedule(
//                new java.util.TimerTask() {
//                    @Override
//                    public void run() {
//                        runOnUiThread(()->createNewProcessor());
//                    }
//                },
//                500);
//    }
//
//    @Override
//    protected void onCameraResolutionChange(int width, int height, int cameraOrientation) {
//        Log.i("Demo","onCameraResolutionChange called. "+width+"x"+height);
//        super.onCameraResolutionChange(width,height,cameraOrientation);
//
//        triggerSlow = false;
//        DemoProcessing p;
//        synchronized ( lockProcessor) {
//            p = processor;
//            this.cameraWidth = width;
//            this.cameraHeight = height;
//            this.cameraOrientation = cameraOrientation;
//        }
//        if( p != null ) {
//            try {
//                p.initialize(cameraWidth, cameraHeight, cameraOrientation);
//            } catch( OutOfMemoryError e ) {
//                synchronized ( lockProcessor) {
//                    processor = null; // free memory
//                }
//                e.printStackTrace();
//                Log.e(TAG,"Out of memory. "+e.getMessage());
//                runOnUiThread(()->{
//                    finish(); // leave the activity
//                    Toast.makeText(this,"Out of Memory. Try lower resolution",Toast.LENGTH_LONG).show();
//                });
//            }
//        } else {
//            Log.i("Demo","  processor is null");
//        }
//        // Wait until initialize has been called to prevent it from being called twice immediately
//        // and to prevent process or visualize from being initialize has been called
//        synchronized (lockProcessor) {
//            this.cameraInitialized = true;
//        }
//
//    }
//
//    @Override
//    protected void onPause() {
//        super.onPause();
//        synchronized (lockProcessor) {
//            if( processor != null )
//                processor.stop();
//        }
//    }
//
//    /**
//     * Changes the processor used to process the video frames
//     */
//    public void setProcessing(DemoProcessing processor ) {
//        synchronized (lockProcessor) {
//            // shut down the previous processor
//            if( this.processor != null ) {
//                this.processor.stop();
//            }
//            // switch it over to the new one
//            //setImageType(processor.getImageType(),processor.getColorFormat());
//            setImageType(new ImageType(ImageType.Family.PLANAR, ImageDataType.U8,3), ColorFormat.RGB);
//            this.processor = processor;
//
//            // If the camera is not initialized then all these values are not known. It will be
//            // initialized when they are known
//            // This must also be called before leaving the lock to prevent process or visualize
//            // from being called on it before initialize has completed
//            if( cameraInitialized ) {
//                Log.i("Demo", "initializing processor " + cameraWidth + "x" + cameraHeight);
//                processor.initialize(cameraWidth, cameraHeight, cameraOrientation);
//            }
//        }
//        synchronized (lockTiming) {
//            totalFramesProcessed = 0;
//            periodProcess.reset();
//            periodRender.reset();
//        }
//    }
//
//    @Override
//    protected void onDrawFrame(SurfaceView view , Canvas canvas ) {
//        long startTime = System.nanoTime();
//        super.onDrawFrame(view,canvas);
//
//        if( !showProcessed ) {
//            if( !showBitmap ) { // if true then it has already been rendered
//                synchronized (bitmapLock) {
//                    canvas.drawBitmap(bitmap, imageToView, null);
//                }
//            }
//        } else {
//            DemoProcessing processor = null;
//            synchronized (lockProcessor) {
//                if( cameraInitialized )
//                    processor = this.processor;
//            }
//            if (processor != null)
//                processor.onDraw(canvas, imageToView);
//        }
//        long stopTime = System.nanoTime();
//
//        visualizationPending = false;
//
//        double processPeriod;
//        double renderPeriod;
//
//        synchronized (lockTiming) {
//            periodRender.update((stopTime-startTime)*1e-6);
//            renderPeriod = periodRender.getAverage();
//            processPeriod = periodProcess.getAverage();
//        }
//    }
//
//    final float pts[] = new float[2];
//
//    /**
//     * Applies the matrix to the specified point. Make sure only ONE thread uses this at any
//     * moment.
//     */
//    public void applyToPoint(Matrix matrix , double x , double y , Point2D_F64 out ) {
//        pts[0] = (float)x;
//        pts[1] = (float)y;
//        matrix.mapPoints(pts);
//        out.x = pts[0];
//        out.y = pts[1];
//    }
//
//    public static void applyToPoint(Matrix matrix , double x , double y , Point2D_F64 out, float pts[] ) {
//        pts[0] = (float)x;
//        pts[1] = (float)y;
//        matrix.mapPoints(pts);
//        out.x = pts[0];
//        out.y = pts[1];
//    }
//
//    public int resolutionToPixels(Resolution resolution) {
//        switch (resolution) {
//            case LOW:
//            case R320x240:
//                return 320*240;
//
//            case MEDIUM:
//            case R640x480:
//                return 640*480;
//
//            case HIGH:
//                return 1024*768;
//
//            case MAX:
//                return Integer.MAX_VALUE;
//
//            case R1920x1080:
//                return 1920*1080;
//
//            case R1600x900:
//                return 1600*900;
//
//            default:
//                throw new IllegalArgumentException("Unknown");
//        }
//    }
//
//    /**
//     * Algorithm which require an exact resolution should request
//     * a specific one. Algorithms produce better results with more
//     * resolution should choose a relative one.
//     */
//    public enum Resolution {
//        LOW,MEDIUM,HIGH,MAX,R320x240,R640x480,R1920x1080,R1600x900;
//    }
//}
