package org.opencv.samples.colorblobdetect;

import org.opencv.android.JavaCameraView;

import android.content.Context;
import android.util.AttributeSet;

public class CameraClass extends JavaCameraView{

	//pazi na konstruktor zbog layouta!
	public CameraClass(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Auto-generated constructor stub
	}

	public void setResolution(int width, int height)
	{
		disconnectCamera();
		mMaxHeight = width;
		mMaxWidth = height;
		connectCamera(getWidth(), getHeight());		
	}

}
