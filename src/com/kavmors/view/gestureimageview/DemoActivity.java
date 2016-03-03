package com.kavmors.view.gestureimageview;

import com.kavmors.view.widget.GestureImageView;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;

public class DemoActivity extends Activity implements OnClickListener {
	private int[] images = {R.drawable.cat, R.drawable.dog};
	private int imageIndex = 0;
	private View up;
	private View left;
	private View down;
	private View right;
	private View zoomin;
	private View zoomout;
	private GestureImageView v;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_demo);
		
		up = findViewById(R.id.up);
		left = findViewById(R.id.left);
		down = findViewById(R.id.down);
		right = findViewById(R.id.right);
		zoomin = findViewById(R.id.zoomin);
		zoomout = findViewById(R.id.zoomout);
		v = (GestureImageView) findViewById(R.id.v);
		
		v.setZoomMode(GestureImageView.LIMIT|GestureImageView.SPRING_BACK);
		v.setDragMode(GestureImageView.LIMIT|GestureImageView.SPRING_BACK);
		
		up.setOnClickListener(this);
		left.setOnClickListener(this);
		down.setOnClickListener(this);
		right.setOnClickListener(this);
		zoomin.setOnClickListener(this);
		zoomout.setOnClickListener(this);
		v.setOnClickListener(this);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.up: moveUp(); break;
		case R.id.down: moveDown(); break;
		case R.id.left: moveLeft(); break;
		case R.id.right: moveRight(); break;
		case R.id.zoomin: zoomIn(); break;
		case R.id.zoomout: zoomOut(); break;
		case R.id.v: changeImage(); break;
		}
	}
	
	public void zoomIn() { v.performZoom(1.5f, v.getWidth()/2, v.getHeight()/2); }
	public void zoomOut() { v.performZoom(1f/1.5f, v.getWidth()/2, v.getHeight()/2); }
	public void moveUp() { v.performDrag(0, -200); }
	public void moveLeft() { v.performDrag(-200, 0); }
	public void moveDown() { v.performDrag(0, 200); }
	public void moveRight() { v.performDrag(200, 0); }
	public void changeImage() {
		imageIndex++;
		v.setImageResource(images[imageIndex%2]);
	}
}
