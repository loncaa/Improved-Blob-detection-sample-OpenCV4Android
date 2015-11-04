package org.opencv.samples.colorblobdetect;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import android.util.Log;

public class ColorBlobDetector {
	
	private int maxContourPosition = 0;
	private int offset = 50;
    // lower i upper granica za HSV
    private Scalar mLowerBound;
    private Scalar mUpperBound;
    private Scalar mColorRadius; // Color radius for range checking in HSV color space
    private Scalar mBlobColorRgba;
    
    // Minimum contour area in percent for contours filtering
    private double mMaxContourArea;
    
    private List<MatOfPoint> mContours;
    private List<MatOfPoint> contours;
    
    private Size SPECTRUM_SIZE;
    private Size ROI_SIZE;
    
    //Rects
    private Rect rect;
    private Rect globalRect;
    
    // Mats
    private Mat mSpectrum;
    private Mat mRoi;
    private Mat mPyrDownMat;
    private Mat mHsvMat;
    private Mat mMask;
    private Mat mDilatedMask;
    private Mat mHierarchy;
    
    private MatOfPoint mBiggestContour;
    private MatOfPoint2f approxCurve;
    
    private Point centroid;
    
    public ColorBlobDetector()
    {
    	mLowerBound = new Scalar(0);
        mUpperBound = new Scalar(0);
		mBlobColorRgba = new Scalar(255);
		mColorRadius = new Scalar(25,50,50,0);
		
		mSpectrum = new Mat();
		mRoi = new Mat();
		mPyrDownMat = new Mat();
		mHsvMat = new Mat();
		mMask = new Mat();
		mDilatedMask = new Mat();
		mHierarchy = new Mat();
		
		mContours = new ArrayList<MatOfPoint>();
		contours = new ArrayList<MatOfPoint>();
		mBiggestContour = new MatOfPoint();

		centroid = new Point();
		
		approxCurve = new MatOfPoint2f();
		
	    ROI_SIZE = new Size(32, 32);
	    SPECTRUM_SIZE = new Size(100, 32);
    }
    
    public void setColorRadius(Scalar radius) {
        mColorRadius = radius;
    }

    /**After calculating average value of Roi, 
     * this function finds min and max value for each channel.
     * +-25 hue, sat and value +-50
     */
    private void calculateUpperAndLowerBound(Scalar hsvColor) {
    	
        double minH = (hsvColor.val[0] >= mColorRadius.val[0]) ? hsvColor.val[0] - mColorRadius.val[0] : 0;
        double maxH = (hsvColor.val[0] + mColorRadius.val[0] <= 255) ? hsvColor.val[0] + mColorRadius.val[0] : 255;

        mLowerBound.val[0] = minH;
        mUpperBound.val[0] = maxH;

        mLowerBound.val[1] = hsvColor.val[1] - mColorRadius.val[1];
        mUpperBound.val[1] = hsvColor.val[1] + mColorRadius.val[1];

        mLowerBound.val[2] = hsvColor.val[2] - mColorRadius.val[2];
        mUpperBound.val[2] = hsvColor.val[2] + mColorRadius.val[2];

        mLowerBound.val[3] = 0;
        mUpperBound.val[3] = 255;

        Mat spectrumHsv = new Mat(1, (int)(maxH-minH), CvType.CV_8UC3);

        //Making spectrum.
        for (int j = 0; j < maxH-minH; j++) {
            byte[] tmp = {(byte)(minH+j), (byte)255, (byte)255};
            spectrumHsv.put(0, j, tmp);
        }

        Imgproc.cvtColor(spectrumHsv, mSpectrum, Imgproc.COLOR_HSV2RGB_FULL, 4);
    }
    
    /**Sets HSV color of selected roi*/
    public void setHsvColor(Mat image, Rect roi) {
    	
    	Mat touchedRgb = image.submat(roi);
		Mat touchedHsv = new Mat();
		Scalar mBlobColorHsv = new Scalar(255);
        
		Imgproc.cvtColor(touchedRgb, touchedHsv, Imgproc.COLOR_RGB2HSV_FULL);

		/*Calculating average value for each channel
		 *8 x 8  pixels in roi for each channel
		 *sum values of channel and divide with (8x8)*/
        mBlobColorHsv = Core.sumElems(touchedHsv);
        int pointCount = roi.width*roi.height;
        for (int i = 0; i < mBlobColorHsv.val.length; i++)
            mBlobColorHsv.val[i] /= pointCount;
        
        mBlobColorRgba = converScalarHsv2Rgba(mBlobColorHsv);
        calculateUpperAndLowerBound(mBlobColorHsv);
        
        Imgproc.resize(mSpectrum, mSpectrum, SPECTRUM_SIZE);
        Imgproc.resize(touchedRgb, mRoi, ROI_SIZE);
        
        touchedHsv.release();
        touchedRgb.release();
    }

    /**Function that finds biggest countour ind image (rgbaImage)
     * @param rgbaImage Mat on which is located object.
     * @return returns -1 if contour isn't found, size of contour if it is found.*/
    public double findMaxContour(Mat rgbaImage)
    {
    	mMaxContourArea = -1;

    	//Scale it down 2 times for making it faster
        Imgproc.pyrDown(rgbaImage, mPyrDownMat); 
        Imgproc.pyrDown(mPyrDownMat, mPyrDownMat); 
        Imgproc.cvtColor(mPyrDownMat, mHsvMat, Imgproc.COLOR_RGB2HSV_FULL);

        Core.inRange(mHsvMat, mLowerBound, mUpperBound, mMask);
        Imgproc.dilate(mMask, mDilatedMask, new Mat());

        contours.clear();
        Imgproc.findContours(mDilatedMask, contours, mHierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE); //cost!
        
    	for(int i = 0; i < contours.size(); i++)
    	{
    		double area = Imgproc.contourArea(contours.get(i)); //area of contoure, cost!
    		if(area > mMaxContourArea)
    		{
    			mMaxContourArea = area;
    			maxContourPosition = i;
    		}
    	}
    	
        return mMaxContourArea;
    }
       
    /**Pronalazi roi na zadanoj slici, sluzi za pronalazenje roia na frameu*/
    public void initRoi(Mat rgbaImage)
    {
    	if(contours.size() > 0)
    	{
	    	mBiggestContour = contours.get(maxContourPosition);
	    	
	    	//Scale it up 4 times, 4 x X-axis and 4 x Y-axis
	    	Core.multiply(mBiggestContour, new Scalar(4,4), mBiggestContour); 
	    	
	    	//Finding coordinates of object centroid.
	        Moments m = Imgproc.moments(mBiggestContour);
	        centroid.x =  (m.get_m10() / m.get_m00());
	        centroid.y = (m.get_m01() / m.get_m00());
	    	 	
	        //Rect around contour.
	    	MatOfPoint2f contour2f = new MatOfPoint2f(mBiggestContour.toArray()); //cost
	    	double approxDistance = Imgproc.arcLength(contour2f, true)*0.01f;
	    	Imgproc.approxPolyDP(contour2f, approxCurve, approxDistance, true);
	    	globalRect = Imgproc.boundingRect(new MatOfPoint(approxCurve.toArray()));
	
	    	int newy = globalRect.y - offset < 0 ? 0 : globalRect.y - offset;
	    	int newx = globalRect.x - offset < 0 ? 0 : globalRect.x;
	    	int newheight = globalRect.y + globalRect.height + offset > rgbaImage.rows() ? globalRect.height : globalRect.height + offset;
	    	int newwidth = globalRect.x + globalRect.width + offset > rgbaImage.cols() ? globalRect.width : globalRect.width + offset;
	    	
	    	globalRect.x = newx;
	    	globalRect.y = newy;
	    	globalRect.width = newwidth;
	    	globalRect.height = newheight;
	    	mRoi = rgbaImage.submat(globalRect);
	    	    	
	    	contour2f.release();
    	}
    }
    
    public void updateRoi(Mat rgbaImage)
    {
    	mBiggestContour = contours.get(maxContourPosition);
    	Core.multiply(mBiggestContour, new Scalar(4,4), mBiggestContour);	
    
    	MatOfPoint2f contour2f = new MatOfPoint2f(mBiggestContour.toArray());
    	double approxDistance = Imgproc.arcLength(contour2f, true)*0.01f;
    	Imgproc.approxPolyDP(contour2f, approxCurve, approxDistance, true);
    	rect = Imgproc.boundingRect(new MatOfPoint(approxCurve.toArray())); 

    	//KOD NAGLOG POMICANJA DODE DO VELIKOG ŠUMA!
    	if(rect.x > offset + 10)
    		globalRect.x = globalRect.x + (rect.x - offset) < rgbaImage.cols() ? globalRect.x + (rect.x - offset) : globalRect.x;
    	else if(rect.x < offset - 10)
    		globalRect.x = globalRect.x - (offset - rect.x) > 0 ? globalRect.x - (offset - rect.x) : globalRect.x;
    	
    	if(rect.y > offset + 10)
    		globalRect.y = globalRect.y + (rect.y - offset) < rgbaImage.rows() ? globalRect.y + (rect.y - offset) : globalRect.y;
    	else if(rect.y < offset - 10)
    		globalRect.y = globalRect.y - (offset - rect.y) > 0 ? globalRect.y - (offset - rect.y) : globalRect.y;
    	
    	globalRect.width = globalRect.x + (2*offset + rect.width) < rgbaImage.cols() ? 2*offset + rect.width : (rgbaImage.cols() - globalRect.x);
    	globalRect.height = globalRect.y + (2*offset + rect.height) < rgbaImage.rows() ? 2*offset + rect.height : (rgbaImage.rows() - globalRect.y);
    	
    	mRoi = rgbaImage.submat(globalRect);
    	
        Moments m = Imgproc.moments(mBiggestContour); //cost
        centroid.x = globalRect.x + (m.get_m10() / m.get_m00());
        centroid.y = globalRect.y + (m.get_m01() / m.get_m00());
    	
    	contour2f.release();
    }
       
    /**pretvara skalar u kojemu su vrijednosti hue, sat i value u skalar rgb-a,
     * tako da napravit jedan piksel hsv, pretvori ga u bgr i onda napravi skalar RGB*/
    private Scalar converScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }
    
    public void release()
    {
    	mRoi.release();
    	mSpectrum.release();
        mPyrDownMat.release();
        mHsvMat.release();
        mMask.release();
        mDilatedMask.release();
        mHierarchy.release();
        
        approxCurve.release();
        
        contours.clear();
        mContours.clear();
    }

    public double getMaxContourArea()
    {
    	return mMaxContourArea;
    }

    public Mat getSpectrum() {
        return mSpectrum;
    }
    
    public Scalar getBlobColorRgba()
    {
    	return mBlobColorRgba;
    }
    
    public Mat getRoi()
    {
    	return mRoi;	
    }
    
    public Point getCentroid()
    {
    	return centroid;
    }
    
    public MatOfPoint getBiggestContour()
    {
    	return mBiggestContour;
    }
}
