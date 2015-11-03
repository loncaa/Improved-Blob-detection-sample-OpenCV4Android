package org.opencv.samples.colorblobdetect;

import java.util.List;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.FpsMeter;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.widget.Toast;

public class ColorBlobDetectionActivity extends Activity implements OnTouchListener, CvCameraViewListener2 {
    private static final String  TAG              = "OCVSample::Activity";
  
    private Mat mRgba; 
    private ColorBlobDetector mDetector;
    private FpsMeter meter;
    private CameraBridgeViewBase mOpenCvCameraView;

    private boolean mIsColorSelected = false;
    private boolean contourLost = true;
    
    private int debugMode = 0;
    private MenuItem mPrewievSpectrum;
    private MenuItem mPrewievAverageColor;
    private MenuItem mPrewievRoi;
    private MenuItem mPrewievNothing;

    
    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.setOnTouchListener(ColorBlobDetectionActivity.this);
                    //mOpenCvCameraView.setResolution(720, 480);
                    
                    //SMANJITIT REZOLUCIJU
                    
                    meter = new FpsMeter();
                    meter.init();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public ColorBlobDetectionActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.color_blob_detection_surface_view);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.color_blob_detection_activity_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mDetector = new ColorBlobDetector();
    }

    public void onCameraViewStopped() {
        mRgba.release();
    }

    public boolean onTouch(View v, MotionEvent event) {
		
		//nije najbolji nacin pretvaranja koordinata
		int cols = mRgba.cols();
		int rows = mRgba.rows();  

	    int xpos = (int) (event.getX() * cols) / mOpenCvCameraView.getWidth();
	    int ypos = (int) (event.getY() * rows) / mOpenCvCameraView.getHeight();
		
		if ((xpos < 0) || (ypos < 0) || (xpos > cols) || (ypos > rows)) return false;
		//

		if(!mIsColorSelected)
		{
			//kvadrat 8*8
			Rect roiRect = new Rect();
			roiRect.x = (xpos>4) ? xpos-4 : 0;
			roiRect.y = (ypos>4) ? ypos-4 : 0;
			
			//da ne uzima roi izvan slike
			roiRect.width = (xpos+4 < cols) ? xpos + 4 - roiRect.x : cols - roiRect.x;
			roiRect.height = (ypos+4 < rows) ? ypos + 4 - roiRect.y : rows - roiRect.y;
			
			mDetector.setHsvColor(mRgba, roiRect);
			
			//pronalazi najvecu konturu na clijeom frameu
			double mInitialContureArea = mDetector.findMaxContour(mRgba);
			
			if(mInitialContureArea != -1)
			{
				mDetector.findInitialRoi(mRgba); //pronalazi pocetni roi, koji se u odnosu sa koordinatama cijelog framea
				
				mIsColorSelected = true;
				contourLost = false;
			}
			else
			{
				Toast.makeText(getApplicationContext(), "Conture Area can't be calculated!", Toast.LENGTH_SHORT).show();
			}
		}
		else
		{
			mIsColorSelected = false;
		}
		
        return false;
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

    	mRgba = inputFrame.rgba();
    	
    	if(mIsColorSelected && !contourLost)
    	{
    		//trazi najvecu konturu na roi-u
    		double mContureArea = mDetector.findMaxContour(mDetector.getRoi());
    		
    		//DEBUG MODES
    		if(debugMode == 0)
    		{
    			//nema ništa
    		}
    		else if(debugMode == 1)
    		{
    			Scalar BlobRgba = mDetector.getBlobColorRgba();
        		Mat colorLabel = mRgba.submat(4, 36, 4, 36);
                colorLabel.setTo(BlobRgba);
    		}
    		else if(debugMode == 2)
    		{
    			 Mat mSpectrum = mDetector.getSpectrum();
    	         Mat spectrumLabel = mRgba.submat(4, 4 +mSpectrum.rows(), 4, 4 + mSpectrum.cols());
    	         mSpectrum.copyTo(spectrumLabel);
    		}
    		else if(debugMode == 3)
    		{
    			Mat roi = mDetector.getRoi();
                Mat roiLabel = mRgba.submat(4, 4 + roi.rows(), 4, 4 + roi.cols());
                roi.copyTo(roiLabel);
    		}
    		//

    		if(mContureArea != -1)
    		{
    			mDetector.findNewRoi(mRgba); //pronalazi roi na prosirenom roiu i onda apdejta globalni roi
    			Core.circle(mRgba, mDetector.getCentroid(), 10, new Scalar(255, 0, 0)); 
    		}
    		else{
    			contourLost = true; //ako se izgubi
    		}
    	}
    	else if(contourLost && mIsColorSelected) // ako je odreðen roi i ako je izgubljena kontura, pretrazuje se cijeli frame
    	{
    		double area = mDetector.findMaxContour(mRgba);
    		if(area != -1)
			{
    			mDetector.findInitialRoi(mRgba);
    			contourLost = false;
			}
    	}

    	//meter.measure();
    	//Core.putText(mRgba, meter.getFPSString(), new Point(300, 40), 3, 1, new Scalar(255, 0, 0, 255), 2);
        return mRgba;
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	// TODO Auto-generated method stub

    	mPrewievAverageColor = menu.add("Prewiev Acerage Color");
    	mPrewievRoi = menu.add("Prewiev Roi");
    	mPrewievSpectrum = menu.add("Prewiev Spectrum");
    	mPrewievNothing = menu.add("No Debug Mode");
    	return super.onCreateOptionsMenu(menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	// TODO Auto-generated method stub
    	if(item == mPrewievNothing)
    	{
    		debugMode = 0;
    	}
    	else if(item == mPrewievAverageColor)
    	{
    		debugMode = 1;
    		Toast.makeText(getApplicationContext(), "Debug mode " + debugMode, Toast.LENGTH_SHORT).show();
    	}
    	else if(item == mPrewievSpectrum)
    	{
    		debugMode = 2;
    		Toast.makeText(getApplicationContext(), "Debug mode " + debugMode, Toast.LENGTH_SHORT).show();
    	}
    	else if(item == mPrewievRoi)
    	{
    		debugMode = 3;
    		Toast.makeText(getApplicationContext(), "Debug mode " + debugMode, Toast.LENGTH_SHORT).show();
    	}
    	return super.onOptionsItemSelected(item);
    }
}
