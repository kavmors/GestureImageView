package com.kavmors.view.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.OverScroller;
import android.widget.Scroller;

public class GestureImageView extends ImageView {
//	private static final String TAG = "GestureImageView";
	
	public static final int NO_LIMIT = 4;
	public static final int LIMIT = 2;
	public static final int SPRING_BACK = 1;

	//State that decide whether the view can transform over limit
	//UNABLE: when it reach limit, it cannot react event that will make it over limit
	//OVER: do nothing when it goes over limit
	//SPRING_BACK: when it goes over limit, the view will spring back to the last state by animation 
	private enum OverlimitFlag {UNABLE, OVER, SPRING_BACK};
	private OverlimitFlag mCanZoomOverLimit = OverlimitFlag.SPRING_BACK;
	private OverlimitFlag mCanDragOverLimit = OverlimitFlag.SPRING_BACK;
	
	private Matrix mMatrix = new Matrix();
	private ScaleType mScaleType = ScaleType.CENTER;
	
	private OnDoubleClickListener mDblListener;
	private View.OnLongClickListener mLongListener;
	private OnZoomListener mZoomListener;
	private OnDragListener mDragListener;

	private boolean mQuickZoomable = true;
	private boolean mLongClickable = true;
	private boolean mDblClickable = true;
	private boolean mZoomable = true;
	private boolean mDraggable = true;
	
	private Matrix mMatrixOrigin;			//Matrix that set after setScaleType
	private int mImgHeight, mImgWidth;
	private float mMinScale = 1f/2f, mMaxScale = 2f;
	
	private Handler mHandler;
	private Message mMsg;
	private static final int MSG_AFTER_DRAG = 2;
	
	private static final float DEFAULT_ZOOM_SCALE = 1.5f;
	private static final long ANIMATE_DURATION = 500;
	private static final float SLIDE_ZOOM_WEIGHT = 192f;		//weight in slide zooming
	private static final float SCALE_ZOOM_WEIGHT = 480f;		//weight in scale zooming
	
	public interface OnDoubleClickListener {
		public void onDoubleClick(View view);
	}
	
	public interface OnZoomListener {
		/**
		 * Call when a zoom event start.
		 * @param byGesture True if the event is created by user gesture
		 * */
		public void onZoomStart(boolean byGesture);
		/**
		 * Call after a zoom event.
		 * @param byGesture True if the event is created by user gesture
		 * @param scale The scale that has been zoom of last event, NOT the whole scale
		 * @param centerX The pointX of center point of the last zoom
		 * @param centerY The pointY of center point of the last zoom
		 */
		public void onZoomEnd(boolean byGesture, float scale, int centerX, int centerY);
		
	}
	public interface OnDragListener {
		/**
		 * Call when a drag event start.
		 * @param byGesture True if the event is created by user gesture
		 * */
		public void onDragStart(boolean byGesture);
		/**
		 * Call after a drag event.
		 * @param byGesture True if the event is created by user gesture
		 * @param dx The distance along the X axis that has been scrolled of the last event
		 * @param dy The distance along the Y axis that has been scrolled of the last event
		 */
		public void onDragEnd(boolean byGesture, int dx, int dy);
	}
	
	public GestureImageView(Context context) {
		super(context);
		privateConstructor(context);
	}
	
	public GestureImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		obtainAttributes(context, attrs);
		privateConstructor(context);
	}
	
	public GestureImageView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		obtainAttributes(context, attrs);
		privateConstructor(context);
	}
	
	private void privateConstructor(Context context) {
		mMatrix.set(getImageMatrix());
		super.setScaleType(ScaleType.MATRIX);
		super.setLongClickable(false);		//Mask View.LongClick
		setClickable(true);
	}
	
	private void obtainAttributes(Context context, AttributeSet attrs) {
		int[] styleable = new int[]{android.R.attr.scaleType};
		TypedArray a = context.obtainStyledAttributes(attrs, styleable);
		int index = a.getInteger(0, ScaleType.CENTER.ordinal());
		mScaleType = ScaleType.values()[index];
		a.recycle();
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		Drawable drawable = getDrawable();
		if (drawable!=null) {
			mImgHeight = drawable.getIntrinsicHeight();
			mImgWidth = drawable.getIntrinsicWidth();
		}
		setScaleType(mScaleType);
		super.setOnTouchListener(mOnTouchListener);
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}
	
	/**
	 * Set a value to enable or disable a quick zoom gesture(a double-click to zoom in and a multi-click to zoom out). 
	 * @param quickZoomable True for enable the gesture, false otherwise
	 */
	public void setQuickZoomable(boolean quickZoomable) {
		mQuickZoomable = quickZoomable;
	}
	
	/**
	 * Return whether a quick zoom gesture(a double-click to zoom in and a multi-click to zoom out) is supported. 
	 * @return If the gesture is supported, return true
	 */
	public boolean isQuickZoomable() {
		return mQuickZoomable;
	}
	
	@Override
	public boolean performClick() {
		return super.performClick();
	}

	@Override
	public boolean isLongClickable() {
		return mLongClickable;
	}
	
	@Override
	public void setLongClickable(boolean longClickable) {
		mLongClickable = longClickable;
	}
	
	@Override
	public boolean performLongClick() {
		if (mLongListener!=null) {
			return mLongListener.onLongClick(this);
		}
		return false;
	}
	
	@Override
	public void setOnLongClickListener(View.OnLongClickListener listener) {
		if (!isLongClickable()) {
			setLongClickable(true);
		}
		mLongListener = listener;
	}

	/**
	 * Indicates whether this view reacts to double click events or not.
	 * @return True if the view is double clickable, false otherwise
	 */
	public boolean isDoubleClickable() {
		return mDblClickable;
	}
	
	/**
	 * Enables or disables double click events for this view.
	 * @param dblClickable True to make the view long double clickable, false otherwise
	 */
	public void setDoubleClickable(boolean dblClickable) {
		mDblClickable = dblClickable;
	}
	
	/**
	 * Call this view's OnDoubleClickListener if it is defined.
	 * @return True if OnDoubleClickListener is called, false otherwise
	 */
	public boolean performDoubleClick() {
		if (mDblListener!=null) {
			mDblListener.onDoubleClick(this);
			return true;
		}
		return false;
	}
	
	/**
	 * 	Register a callback to be invoked when this view is double clicked and held. If this view is not double clickable, it becomes double clickable.
	 *	@param listener The callback that will run when this view is double clicked
	 */
	public void setOnDoubleClickListener(OnDoubleClickListener listener) {
		if (!isDoubleClickable()) {
			setDoubleClickable(true);
		}
		mDblListener = listener;
	}
	
	/**
	 * Indicates whether this view reacts to zoom events or not.
	 * @return True if the view is draggable, false otherwise
	 */
	public boolean isDraggable() {
		return mDraggable;
	}
	
	/**
	 * Indicates whether this view reacts to zoom events or not.
	 * @return True if the view is zoomable, false otherwise
	 */
	public boolean isZoomable() {
		return mZoomable;
	}

	/**
	 * Enables or disables zoom events for this view.
	 * @param zoomable True to make this view draggable, false otherwise
	 */
	public void setDraggable(boolean draggable) {
		mDraggable = draggable;
	}
	
	/**
	 * Enables or disables zoom events for this view.
	 * @param zoomable True to make this view zoomable, false otherwise
	 */
	public void setZoomable(boolean zoomable) {
		mZoomable = zoomable;
	}
	
	/**
	 * Perform a drag event, and will call {@link OnDragListener#onDragEnd} if it is moved.
	 * @param dx The distance of X axis that should be translate
	 * @param dy The distance of Y axis that should be translate
	 * @return True if this event has been performed, false otherwise
	 */
	public boolean performDrag(int dx, int dy) {
		if (mDragListener!=null) {
			mDragListener.onDragStart(false);
		}
		return privatePerformDrag(false, dx, dy, true);
	}
	
	private boolean privatePerformDrag(boolean byGesture, int dx, int dy, boolean shouldCallback) {
		int[] realDistance = checkRealDistance(dx, dy);
		//cannot drag anymore
		if (realDistance[0]==0 && realDistance[1]==0) {
			if (mDragListener!=null) {
				fitTranslate();
				mDragListener.onDragEnd(byGesture, 0, 0);
			}
			return false;
		}
		if (mFling!=null) {
			mFling.cancel();
		}
		dragAnimated(byGesture, realDistance[0], realDistance[1], shouldCallback);
		return true;
	}
	
	/**
	 * Reset drag the initialized state.
	 */
	public void resetDrag() {
		int[] trans = new int[]{getImageTranslateX(), getImageTranslateY()};
		if (trans[0]!=0 && trans[1]!=0) {
			performDrag(-trans[0], -trans[1]);
		}
	}
	
	/**
	 * Perform a zoom event, and will call {@link OnZoomListener#onZoomEnd} if it is zoomed.
	 * @param scale The scale that should be zoom
	 * @param centerX The pointX of center point of this zoom
	 * @param centerY The pointY of center point of this zoom
	 * @return True if zoom event has been performed, false otherwise(may reach the min or max scale)
	 */
	public boolean performZoom(float scale, int centerX, int centerY) {
		if (mZoomListener!=null) {
			mZoomListener.onZoomStart(false);
		}
		return privatePerformZoom(false, scale, centerX, centerY, true);
	}
	
	//private
	private boolean privatePerformZoom(boolean byGesture, float scale, int centerX, int centerY, boolean shouldCallback) {
		float realScale = checkRealScale(scale);
		//cannot zoom anymore
		if (realScale==1f) {
			if (mZoomListener!=null) {
				fitTranslate();
				mZoomListener.onZoomEnd(byGesture, 1, centerX, centerY);
			}
			return false;
		}
		zoomAnimated(byGesture, realScale, centerX, centerY, shouldCallback);
		return true;
	}
	
	/**
	 * Reset scale the initialized state.
	 */
	public void resetZoom() {
		float scaled = getImageScale();
		if (scaled!=1f) {
			privatePerformZoom(false, 1f/scaled, getImageLeft(), getImageTop(), false);
		}
	}
	
	/**
	 * Reset scale and translation the initialized state.
	 */
	public void reset() {
		if (isZoomed()) {
			resetZoom();
			postDelayed(new Runnable() {
				@Override
				public void run() {
					resetDrag();
				}
			}, ANIMATE_DURATION);
		} else {
			resetDrag();
		}
	}

	/**
	 * Register a callback to be invoked when this view is zoomed. 
	 * If this view is not zoomable, it becomes zoomable.
	 * @param listener The callback that will run
	 */
	public void setOnZoomListener(OnZoomListener listener) {
		if (!isZoomable()) {
			setZoomable(true);
		}
		mZoomListener = listener;
	}
	
	/**
	 * Register a callback to be invoked when this view is dragged. 
	 * If this view is not draggable, it becomes draggable.
	 * @param listener The callback that will run
	 */
	public void setOnDragListener(OnDragListener listener) {
		if (!isDraggable()) {
			setZoomable(true);
		}
		mDragListener = listener;
	}
	
	/**
	 * Set scale to this ImageView. 
	 * @param scale The scale that should be set
	 * @param centerX The pointX of center point of this zoom
	 * @param centerY The pointY of center point of this zoom
	 * @return True if it has been scaled, false otherwise(will reach the min or max scale after setScale so it is prevented)
	 */
	public boolean postImageScale(float scale, int centerX, int centerY) {
		//cannot zoom by this scale
		if (checkRealScale(scale)!=scale) {
			return false;
		}
		mMatrix.postScale(scale, scale, centerX, centerY);
		setImageMatrix(mMatrix);
		return true;
	}
	
	/**
	 * Set translate to this ImageView. 
	 * @param dx The distance of X axis that should be translate
	 * @param dy The distance of Y axis that should be translate
	 * @return True if this event has been performed, false otherwise(will reach border after setTranslate so it is prevented)
	 */
	public boolean postImageTranslate(int dx, int dy) {
		int[] realDistance = checkRealDistance(dx, dy);
		//cannot translate by this distance
		if (realDistance[0]-dx!=0 || realDistance[1]-dy!=0) {
			return false;
		}
		mMatrix.postTranslate(dx, dy);
		setImageMatrix(mMatrix);
		return true;
	}
	
	/**
	 * Indicate whether the image has been zoomed.
	 * @return If image has been zoomed, return true
	 */
	public boolean isZoomed() {
		return getImageScale()!=1f;
	}
	
	/**
	 * Get the scale value that has been zoomed.
	 * If the view has not been zoomed, it returns 1
	 * @return Scaling that has been zoomed
	 */
	public float getImageScale() {
		float[] valuesCurrent = new float[9];
		mMatrix.getValues(valuesCurrent);
		float[] valueOrigin = new float[9];
		mMatrixOrigin.getValues(valueOrigin);
		return valuesCurrent[Matrix.MSCALE_X]/valueOrigin[Matrix.MSCALE_X];
	}
	
	/**
	 * Indicate whether the image has been dragged.
	 * @return If image has been dragged, return true
	 */
	public boolean isDragged() {
		return getImageTranslateX()!=1 || getImageTranslateY()!=1;
	}

	/**
	 * Get the value on X axis that has been translated.
	 * If the view has not been translate on X axis, it returns 0
	 * @return Translated value on X axis
	 */
	public int getImageTranslateX() {
		float[] values = new float[9];
		mMatrixOrigin.getValues(values);
		return getImageLeft() - (int)values[Matrix.MTRANS_X];
	}
	
	/**
	 * Get the value on Y axis that has been translated.
	 * If the view has not been translate on Y axis, it returns 0
	 * @return Translated value on Y axis
	 */
	public int getImageTranslateY() {
		float[] values = new float[9];
		mMatrixOrigin.getValues(values);
		return getImageTop() - (int)values[Matrix.MTRANS_Y];
	}
	
	/**
	 * Set minimal value of zooming.
	 * @param minScale minimal scale
	 */
	public void setMinScale(float minScale) {
		mMinScale = minScale;
	}
	
	/**
	 * Set maximal value of zooming
	 * @param maxScale maximal scale
	 */
	public void setMaxScale(float maxScale) {
		mMaxScale = maxScale;
	}
	
	/**
	 * Get minimal value of zooming that set by {@link #setMinScale}.
	 * @param The value
	 */
	public float getMinScale() {
		return mMinScale;
	}

	/**
	 * Get maximal value of zooming that set by {@link #setMaxScale}.
	 * @param The value
	 */
	public float getMaxScale() {
		return mMaxScale;
	}
	
	/**
	 * Set whether this view can be zoom over max or min scale.
	 * @param mode zoom mode. Available value includes {@link #NO_LIMIT}, {@link #LIMIT}, {@link #SPRING_BACK}.
	 * Values is multi-selectable with OR, but {@link #NO_LIMIT} and {@link #LIMIT} cannot be selected at same time.
	 */
	public void setZoomMode(int mode) {
		if (mode >= 6) {
			throw new UnsupportedOperationException("NO_LIMIT and LIMIT cannot be selected at same time");
		} else if (mode < 2) {
			throw new UnsupportedOperationException("NO_LIMIT or LIMIT must be selected either");
		}
		boolean canZoom = false, springBack = true;
		switch (mode) {
		case 2:
			canZoom = false;
			springBack = false;
			break;
		case 3:
			canZoom = false;
			springBack = true;
			break;
		case 4:
		case 5:
			canZoom = true;
			springBack = false;
			break;
		default:
			break;
		}
		if (canZoom) {
			mCanZoomOverLimit = OverlimitFlag.OVER;
		} else {
			mCanZoomOverLimit = springBack? OverlimitFlag.SPRING_BACK: OverlimitFlag.UNABLE;
		}
	}
	
	/**
	 * Returns the value that has been set in the last call of {@link #setZoomMode}.
	 * @return the value
	 */
	public boolean canZoomOverLimit() {
		return mCanZoomOverLimit == OverlimitFlag.OVER;
	}
	
	/**
	 * Set whether this view can be drag over bound.
	 * @param mode drag mode. Available value includes {@link #NO_LIMIT}, {@link #LIMIT}, {@link #SPRING_BACK}.
	 * Values is multi-selectable with OR, but {@link #NO_LIMIT} and {@link #LIMIT} cannot be select at same time.
	 */
	public void setDragMode(int mode) {
		if (mode >= 6) {
			throw new UnsupportedOperationException("NO_LIMIT and LIMIT cannot be selected at same time");
		} else if (mode < 2) {
			throw new UnsupportedOperationException("NO_LIMIT or LIMIT must be selected either");
		}
		boolean canDrag = false, springBack = true;
		switch (mode) {
		case 2:
			canDrag = false;
			springBack = false;
			break;
		case 3:
			canDrag = false;
			springBack = true;
			break;
		case 4:
		case 5:
			canDrag = true;
			springBack = false;
			break;
		default:
			break;
		}
		if (canDrag) {
			mCanDragOverLimit = OverlimitFlag.OVER;
		} else {
			mCanDragOverLimit = springBack? OverlimitFlag.SPRING_BACK: OverlimitFlag.UNABLE;
		}
	}
	
	/**
	 * Returns the value that has been set in the last call of {@link #setDragMode}.
	 * @return the value
	 */
	public boolean canDragOverLimit() {
		return mCanDragOverLimit == OverlimitFlag.OVER;
	}
	
	@Override
	public void setOnTouchListener(OnTouchListener l) {
		mOnTouchListener.setUserTouchListener(l);
	}
	
	@Override
	public ScaleType getScaleType() {
		return mScaleType;
	}
	
	@Override
	public void setScaleType(ScaleType type) {
		mMatrix = new Matrix();
		float bW = mImgWidth;
		float bH = mImgHeight;
		float vW = this.getWidth();
		float vH = this.getHeight();
		float sW = vW/bW;
		float sH = vH/bH;
		
		if (type == null) {
            throw new NullPointerException("ScaleType cannot be null");
        }
		mScaleType = type;
		if (type==ScaleType.CENTER) {
			mMatrix.postTranslate((int)(vW-bW)/2, (int)(vH-bH)/2);
		} else if (type==ScaleType.CENTER_CROP) {
			mMatrix.postTranslate((int)(vW-bW)/2, (int)(vH-bH)/2);
			float scale = sW>sH? sW: sH;		//switch the max
			mMatrix.postScale(scale, scale, vW/2, vH/2);
		} else if (type==ScaleType.CENTER_INSIDE) {
			mMatrix.postTranslate((int)(vW-bW)/2, (int)(vH-bH)/2);
			float scale = sW<sH? sW: sH;		//switch the min
			if (scale<1) {			//scale only when too large
				mMatrix.postScale(scale, scale, vW/2, vH/2);
			}
		} else if (type==ScaleType.FIT_CENTER) {
			mMatrix.postTranslate((int)(vW-bW)/2, (int)(vH-bH)/2);
			float scale = sW<sH? sW: sH;		//switch the min
			mMatrix.postScale(scale, scale, vW/2, vH/2);
		} else if (type==ScaleType.FIT_END) {
			mMatrix.postTranslate((int)(vW-bW), (int)(vH-bH));
			setImageMatrix(mMatrix);
			float scale = sW<sH? sW: sH;		//switch the min
			mMatrix.postScale(scale, scale, vW, vH);
		} else if (type==ScaleType.FIT_START) {
			float scale = sW<sH? sW: sH;		//switch the min
			mMatrix.postScale(scale, scale, 0, 0);
		} else if (type==ScaleType.FIT_XY) {
			mMatrix.postScale(sW, sH, 0, 0);
		} else if (type==ScaleType.MATRIX) {
		} else {
			//never
    		throw new UnsupportedOperationException("Unsupported ScaleType");
		}
		mMatrixOrigin = new Matrix(mMatrix);
		setImageMatrix(mMatrix);
		invalidate();
	}

	/**
	 * Get the relative position of top border of the image after translated or zoomed, on Y axis.
	 * @return Top border position
	 */
	public int getImageTop() {
		float[] values = new float[9];
		mMatrix.getValues(values);
		return (int)values[Matrix.MTRANS_Y];
	}
	
	/**
	 * Get the relative position of left border of the image after translated or zoomed, on X axis.
	 * @return Left border position
	 */
	public int getImageLeft() {
		float[] values = new float[9];
		mMatrix.getValues(values);
		return (int)values[Matrix.MTRANS_X];
		
	}
	
	/**
	 * Get the relative position of bottom border of the image after translated or zoomed, on Y axis.
	 * @return Bottom border position
	 */
	public int getImageBottom() {
		float[] values = new float[9];
		mMatrix.getValues(values);
		return (int)(values[Matrix.MTRANS_Y] + values[Matrix.MSCALE_Y]*mImgHeight);
	}
	
	/**
	 * Get the relative position of right border of the image after translated or zoomed, on X axis.
	 * @return Right border position
	 */
	public int getImageRight() {
		float[] values = new float[9];
		mMatrix.getValues(values);
		return (int)(values[Matrix.MTRANS_X] + values[Matrix.MSCALE_X]*mImgWidth);
	}
	
	/**
	 * Get the width of image after zoomed.
	 * @return Width value
	 */
	public int getImageWidth() {
		float[] values = new float[9];
		mMatrix.getValues(values);
		return (int)(values[Matrix.MSCALE_X]*mImgWidth);
	}
	
	/**
	 * Get the height of image after zoomed.
	 * @return height value
	 */
	public int getImageHeight() {
		float[] values = new float[9];
		mMatrix.getValues(values);
		return (int)(values[Matrix.MSCALE_Y]*mImgHeight);
	}

	private void zoomAnimated(final boolean byGesture, float scale, final int centerX, final int centerY, boolean shouldCallback) {
		mAnimator = CompatAnimator.ofFloat(1, scale);
		mAnimator.setDuration(ANIMATE_DURATION);
		mAnimator.addUpdateListener(new CompatAnimator.AnimatorUpdateListener() {
			private float preValue = 1;
			@Override
			public void onAnimationUpdate(CompatAnimator animation) {
				float scale = (Float) animation.getAnimatedValue();
				mMatrix.postScale(scale/preValue, scale/preValue, centerX, centerY);
				setImageMatrix(mMatrix);
				preValue = scale;
			}
		});
		if (shouldCallback) {
			mAnimator.addListener(new CompatAnimator.AnimatorListener() {
				@Override
				public void onAnimationEnd(CompatAnimator animation) {
					float scale = (Float) animation.getAnimatedValue();
					safeCallAfterZoom(byGesture, new PointF(centerX, centerY), scale);
				}
			});
		}
		mAnimator.start(this);
	}
	
	private void dragAnimated(final boolean byGesture, final int dx, final int dy, boolean shouldCallback) {
		mAnimator = CompatAnimator.ofFloat(0, 1);
		mAnimator.setDuration(ANIMATE_DURATION);
		mAnimator.addUpdateListener(new CompatAnimator.AnimatorUpdateListener() {
			private float preFactor = 0;
			@Override
			public void onAnimationUpdate(CompatAnimator animation) {
				float factor = (Float) animation.getAnimatedValue();
				mMatrix.postTranslate((factor-preFactor)*dx, (factor-preFactor)*dy);
				setImageMatrix(mMatrix);
				preFactor = factor;
			}
		});
		if (shouldCallback) {
			mAnimator.addListener(new CompatAnimator.AnimatorListener() {
				@Override
				public void onAnimationEnd(CompatAnimator animation) {
					float factor = (Float) animation.getAnimatedValue();
					safeCallAfterDrag(byGesture, (int)(factor*dx), (int)(factor*dy));
				}
			});
		}
		mAnimator.start(this);
	}
	
	private int[] checkRealDistance(int dx, int dy) {
		if (mCanDragOverLimit==OverlimitFlag.OVER) {
			return new int[]{dx, dy};
		}
		int realX, realY;
		if (getImageWidth() > getWidth()) {
			if (dx > 0) {
				realX = Math.min(dx, 0 - getImageLeft());
			} else {
				realX = Math.max(dx, getWidth() - getImageRight());
			}
		} else {
			if (dx > 0) {
				realX = Math.min(dx, getWidth() - getImageRight());
			} else {
				realX = Math.max(dx, 0 - getImageLeft());
			}
		}
		
		if (getImageHeight() > getHeight()) {
			if (dy > 0) {
				realY = Math.min(dy, 0 - getImageTop());
			} else {
				realY = Math.max(dy, getHeight() - getImageBottom());
			}
		} else {
			if (dy > 0) {
				realY = Math.min(dy, getHeight() - getImageBottom());
			} else {
				realY = Math.max(dy, 0 - getImageTop());
			}
		}
		return new int[]{realX, realY};
	}
	
	private float checkRealScale(float scale) {
		if (mCanZoomOverLimit==OverlimitFlag.OVER) {
			return scale;
		}
		float realScale;
		if (scale>1f) {
			float limitScale = ((float)Math.round(mMaxScale/getImageScale()*1000))/1000f;
			realScale = Math.min(scale, limitScale);
		} else {
			float limitScale = ((float)Math.round(mMinScale/getImageScale()*1000))/1000f;
			realScale = Math.max(scale, limitScale);
		}
		return realScale;
	}
	
	private void beforeDragByUser() {
		if (mDragListener!=null) {
			mDragListener.onDragStart(true);
		}
	}
	
	private void afterDragByUser(int movedX, int movedY) {
		if (mCanDragOverLimit==OverlimitFlag.OVER) {
			safeCallAfterDrag(true, movedX, movedY);
		} else if (mCanDragOverLimit==OverlimitFlag.SPRING_BACK) {
			int fixX = 0, fixY = 0;
			if (getImageWidth() > getWidth()) {
				if (movedX > 0 && getImageLeft() > 0) {
					fixX = 0 - getImageLeft();
				} else if (movedX < 0 && getImageRight() < getWidth()) {
					fixX = getWidth() - getImageRight();
				}
			} else {
				if (movedX > 0 && getImageRight() > getWidth()) {
					fixX = getWidth() - getImageRight();
				} else if (movedX < 0 && getImageLeft() < 0) {
					fixX = 0 - getImageLeft();
				}
			}
			
			if (getImageHeight() > getHeight()) {
				if (movedY > 0 && getImageTop() > 0) {
					fixY = 0 - getImageTop();
				} else if (movedY < 0 && getImageBottom() < getHeight()) {
					fixY = getHeight() - getImageBottom();
				}
			} else {
				if (movedY > 0 && getImageBottom() > getHeight()) {
					fixY = getHeight() - getImageBottom();
				} else if (movedY < 0 && getImageTop() < 0) {
					fixY = 0 - getImageTop();
				}
			}
			safeCallAfterDrag(true, movedX+fixX, movedY+fixY);
			
			if (fixX==0 && fixY==0) {
				return;
			}
			privatePerformDrag(false, fixX, fixY, false);
		} else if (mCanDragOverLimit==OverlimitFlag.UNABLE) {
			safeCallAfterDrag(true, movedX, movedY);
		}
	}

	private void safeCallAfterDrag(boolean byGesture, int movedX, int movedY) {
		if (mDragListener!=null) {
			fitTranslate();
			mDragListener.onDragEnd(byGesture, movedX, movedY);
		}
	}
	
	private void beforeZoomByUser() {
		if (mZoomListener!=null) {
			mZoomListener.onZoomStart(true);
		}
	}
	
	private void afterZoomByUser(PointF center, float scaled) {
		if (mCanZoomOverLimit==OverlimitFlag.OVER) {
			safeCallAfterZoom(true, center, scaled);
		} else if (mCanZoomOverLimit==OverlimitFlag.SPRING_BACK) {
			float fixScale = 1;
			//OverLimited
			if (mMinScale>0 && getImageScale()<mMinScale) {
				fixScale = mMinScale/getImageScale();
			}
			if (mMaxScale>0 && getImageScale()>mMaxScale) { 
				fixScale = mMaxScale/getImageScale();
			}
			safeCallAfterZoom(true, center, Math.round((scaled*fixScale)*1000)/1000);
			
			if (fixScale==1f) {
				return;
			}
			privatePerformZoom(false, fixScale, (int)center.x, (int)center.y, false);
		} else if (mCanZoomOverLimit==OverlimitFlag.UNABLE) {
			safeCallAfterZoom(true, center, scaled);
		}
	}
	
	private void safeCallAfterZoom(boolean byGesture, PointF center, float scaled) {
		if (mZoomListener!=null) {
			fitTranslate();
			mZoomListener.onZoomEnd(byGesture, scaled, (int)center.x, (int)center.y);
		}
	}
	
	private void fitTranslate() {
		float[] values = new float[9];
		mMatrix.getValues(values);
		values[Matrix.MTRANS_X] = (int)values[Matrix.MTRANS_X];
		values[Matrix.MTRANS_Y] = (int)values[Matrix.MTRANS_Y];
		mMatrix.setValues(values);
		setImageMatrix(mMatrix);
	}
	
	//Handle events that created by onTouch
	//Initialize statement
	private OnTouchGestureListener mOnTouchListener = new OnTouchGestureListener(getContext());
	{
		mOnTouchListener.setLongClickable(true);
		mOnTouchListener.setOnDetectSingle(new OnTouchGestureListener.OnDetectSingle() {
			boolean moved = false;	//if called onMove in once, it is set to true
			int movedX = 0;
			int movedY = 0;
			float overLimitDX = 0;	//record the distanceX after the drag reach limit
			float overLimitDY = 0;	//record the distanceY after the drag reach limit
			
			@Override
			public void onDown(PointF point) {
				if (isDraggable()) {
					if (mFling!=null) {
						mFling.cancel();
					}
				}
				moved = false;	//if called onMove in once, it is set to true
				movedX = 0;
				movedY = 0;
				overLimitDX = 0;	//record the distanceX after the drag reach limit
				overLimitDY = 0;	//record the distanceY after the drag reach limit
			}

			@Override
			public void onMove(PointF from, PointF to, float dxF, float dyF) {
				if (!isDraggable()) {
					return;
				}
				if (moved == false) {
					beforeDragByUser();
				}
				moved = true;
				int dx = (int)-dxF;
				int dy = (int)-dyF;
				int realDx = dx;
				int realDy = dy;
				if (mCanDragOverLimit == OverlimitFlag.UNABLE) {		//cannot drag over limit
					int[] r = checkRealDistance(dx, dy);
					realDx = r[0];
					if (realDx == 0) {
						overLimitDX += Math.abs(dx);
					} else if (overLimitDX > 0) {
						overLimitDX -= Math.abs(dx);
					}
					realDy = r[1];
					if (realDy == 0) {
						overLimitDY += Math.abs(dy);
					} else if (overLimitDY > 0) {
						overLimitDY -= Math.abs(dy);
					}
				}
				if (overLimitDX > 0) {
					realDx = 0;
				}
				if (overLimitDY > 0) {
					realDy = 0;
				}
				movedX += realDx;
				movedY += realDy;
				mMatrix.postTranslate(realDx, realDy);
				setImageMatrix(mMatrix);
			}

			@Override
			public void onUp(PointF point) {
				if (moved && isDraggable()) {
					mHandler = new Handler(new Handler.Callback() {
						@Override
						public boolean handleMessage(Message msg) {
							if (msg.what == MSG_AFTER_DRAG) {
								afterDragByUser(msg.arg1, msg.arg2);
								mMsg = null;
							}
							return true;
						}
					});
					mMsg = Message.obtain();
					mMsg.what = MSG_AFTER_DRAG;
					mMsg.arg1 = movedX;
					mMsg.arg2 = movedY;
					mHandler.sendMessageDelayed(mMsg, 10);
				}
			}

			@Override
			public void onClick(PointF point) {
				if (isClickable()) {
					performClick();
				}
			}

			@Override
			public void onLongClick(PointF point) {
				if (isLongClickable()) {
					performLongClick();
				}				
			}

			@Override
			public void onFling(PointF from, PointF to, float vx, float vy) {
				if (getImageWidth() > getWidth()) {
					if (vx > 0 && getImageLeft() > 0) {
						return;
					} else if (vx < 0 && getImageRight() < getWidth()) {
						return;
					}
				} else {
					if (vx > 0 && getImageRight() > getWidth()) {
						return;
					} else if (vx < 0 && getImageLeft() < 0) {
						return;
					}
				}
				
				if (getImageHeight() > getHeight()) {
					if (vy > 0 && getImageTop() > 0) {
						return;
					} else if (vy < 0 && getImageBottom() < getHeight()) {
						return;
					}
				} else {
					if (vy > 0 && getImageBottom() > getHeight()) {
						return;
					} else if (vy < 0 && getImageTop() < 0) {
						return;
					}
				}
				int movedX = 0, movedY = 0;
				if (mFling!=null) {
					mFling.cancel();
				}
				if (mHandler!=null) {
					movedX = mMsg.arg1;
					movedY = mMsg.arg2;
					mHandler.removeMessages(MSG_AFTER_DRAG);
					mMsg = null;
					mHandler = null;
				}
				mFling = new Fling((int)vx, (int)vy, movedX, movedY);
				compatPostOnAnimation(mFling);
			}
		});
		mOnTouchListener.setDblLongClickable(false);
		mOnTouchListener.setOnDetectDouble(new OnTouchGestureListener.OnDetectDouble() {
			PointF center;
			boolean moved = false;	//if called onDblMove in once, it is set to true
			float scaled = 1f;		//record the total scale in one dbl-move event
			float overLimitDY = 0;	//record the distanceY after the scale reach limit
			
			@Override
			public void onDblDown(PointF point) {
				center = point;
				moved = false;
				scaled = 1f;
				overLimitDY = 0;
			}
			
			@Override
			public void onDblMove(PointF from, PointF to, float dx, float dy) {
				if (!isZoomable()) {
					return;
				}
				if (moved == false) {
					beforeZoomByUser();
				}
				moved = true;
				float scale = (float) Math.pow(2, -dy/SLIDE_ZOOM_WEIGHT);
				float realScale = scale;
				if (mCanZoomOverLimit == OverlimitFlag.UNABLE) {		//cannot zoom over limit
					realScale = checkRealScale(scale);
					if (realScale == 1.0f) {
						overLimitDY += Math.abs(dy);
						return;
					} else if (overLimitDY>0) {
						overLimitDY -= Math.abs(dy);
						return;
					}
				}
				scaled *= realScale;
				mMatrix.postScale(realScale, realScale, center.x, center.y);
				setImageMatrix(mMatrix);
			}
			
			@Override
			public void onDblUp(PointF point) {
				if (moved && isZoomable()) {
					afterZoomByUser(center, scaled);
				}
			}
			
			@Override
			public void onDblClick(PointF point) {
				if (isQuickZoomable() && isZoomable()) {
					beforeZoomByUser();
					privatePerformZoom(true, DEFAULT_ZOOM_SCALE, (int)point.x, (int)point.y, true);
				}
				if (isDoubleClickable()) {
					performDoubleClick();
				}
			}

			@Override
			public void onDblLongClick(PointF point) {
			}
		});
		mOnTouchListener.setMultiLongClickable(false);
		mOnTouchListener.setOnDetectMulti(new OnTouchGestureListener.OnDetectMulti() {
			private PointF center;
			boolean moved = false;	//if called onMultiMove in once, it is set to true
			float scaled = 1f;		//record the total scale in one multi-move event
			float overLimitDis = 0;	//record the distance after the scale reach limit. When it returns 0, the view will react zoom event again

			@Override
			public void onMultiDown(PointF p0, PointF p1) {
				center = OnTouchGestureListener.Util.center(p0, p1);
				moved = false;
				scaled = 1f;
				overLimitDis = 0;
			}
			
			@Override
			public void onMultiClick(PointF p0, PointF p1) {
				if (isQuickZoomable() && isZoomable()) {
					beforeZoomByUser();
					privatePerformZoom(true, 1f/DEFAULT_ZOOM_SCALE, (int)center.x, (int)center.y, true);
				}
			}
			
			@Override
			public void onMultiLongClick(PointF p0, PointF p1) {
			}
			
			@Override
			public void onMultiUp(PointF p0, PointF p1) {
				if (moved && isZoomable()) {
					afterZoomByUser(center, scaled);
				}
			}
			
			@Override
			public void onMultiMove(PointF oldPoint0, PointF oldPoint1, PointF newPoint0, PointF newPoint1) {
				if (!isZoomable()) {
					return;
				}
				if (moved == false) {
					beforeZoomByUser();
				}
				moved = true;
				float distance = OnTouchGestureListener.Util.distance(newPoint0, newPoint1) - OnTouchGestureListener.Util.distance(oldPoint0, oldPoint1);
				float scale = (float) Math.pow(2, distance/SCALE_ZOOM_WEIGHT);
				float realScale = scale;
				if (mCanZoomOverLimit == OverlimitFlag.UNABLE) {		//cannot zoom over limit
					realScale = checkRealScale(scale);
					if (realScale - scale != 0 || realScale == 1.0f) {
						overLimitDis += Math.abs(distance);
						return;
					} else if (overLimitDis>0) {
						overLimitDis -= Math.abs(distance);
						return;
					}
				}
				scaled *= realScale;
				mMatrix.postScale(realScale, realScale, center.x, center.y);
				setImageMatrix(mMatrix);
			}
		});
	}
	
	private Fling mFling;
	private class Fling implements Runnable {
		CompatScroller scroller;
		int currX, currY;
		int prevMovedX, prevMovedY;
		
		Fling(int vx, int vy, int movedX, int movedY) {
			float[] values = new float[9];
			scroller = new CompatScroller(getContext());
			mMatrix.getValues(values);
			
			int startX = (int) values[Matrix.MTRANS_X];
			int startY = (int) values[Matrix.MTRANS_Y];
			int minX, maxX, minY, maxY;
			
			if (canDragOverLimit()) {
				minX = getImageLeft() - 2 * getWidth();
				maxX = getImageLeft() + 2 * getWidth();
				minY = getImageTop() - 2 * getHeight();
				maxY = getImageTop() + 2 * getHeight();
			} else {
				if (getImageWidth() > getWidth()) {
					maxX = 0;
					minX = getWidth() - getImageWidth();
				} else {
					minX = 0;
					maxX = getWidth() - getImageWidth();
				}
				
				if (getImageHeight() > getHeight()) {
					maxY = 0;
					minY = getHeight() - getImageHeight();
				} else {
					minY = 0;
					maxY = getHeight() - getImageHeight();
				}
			}
			scroller.fling(startX, startY, vx, vy, minX, maxX, minY, maxY);
			currX = startX;
			currY = startY;
			prevMovedX = movedX;
			prevMovedY = movedY;
		}
		
		void cancel() {
			if (scroller != null) {
				scroller.forceFinished(true);
			}
		}
		
		@Override
		public void run() {
			if (scroller.isFinished()) {
				callAfterFling();
				scroller = null;
				return;
			}
			if (scroller.computeScrollOffset()) {
				int newX = scroller.getCurrX();
				int newY = scroller.getCurrY();
				mMatrix.postTranslate(newX-currX, newY-currY);
				setImageMatrix(mMatrix);
				currX = newX;
				currY = newY;
				compatPostOnAnimation(this);
			} else {
				callAfterFling();
				scroller = null;
			}
		}
		
		private void callAfterFling() {
			afterDragByUser(prevMovedX + scroller.getDistanceX(), prevMovedY + scroller.getDistanceY());
		}
	}
	
	//Scroller or OverScroller
	@TargetApi(VERSION_CODES.GINGERBREAD)
	private static class CompatScroller {
		Scroller scroller;
		OverScroller overScroller;
		boolean isAdvancedApi;
		int startX, startY;
		
		CompatScroller(Context context) {
			if (VERSION.SDK_INT >= VERSION_CODES.GINGERBREAD) {
				isAdvancedApi = true;
				overScroller = new OverScroller(context);
			} else {
				isAdvancedApi = false;
				scroller = new Scroller(context);
			}
		}
		
		void fling(int startX, int startY, int velocityX, int velocityY, int minX, int maxX, int minY, int maxY) {
			if (isAdvancedApi) {
				overScroller.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY);
			} else {
				scroller.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY);
			}
			this.startX = startX;
			this.startY = startY;
		}
		
		boolean isFinished() {
			if (isAdvancedApi) {
				return overScroller.isFinished();
			} else {
				return scroller.isFinished();
			}
		}
		
		void forceFinished(boolean finished) {
			if (isAdvancedApi) {
				overScroller.forceFinished(finished);
			} else {
				scroller.forceFinished(finished);
			}
		}
		
		boolean computeScrollOffset() {
			if (isAdvancedApi) {
				return overScroller.computeScrollOffset();
			} else {
				return scroller.computeScrollOffset();
			}
		}
		
		int getCurrX() {
			if (isAdvancedApi) {
				return overScroller.getCurrX();
			} else {
				return scroller.getCurrX();
			}
		}
		
		int getCurrY() {
			if (isAdvancedApi) {
				return overScroller.getCurrY();
			} else {
				return scroller.getCurrY();
			}
		}

		int getDistanceX() {
			return getCurrX() - startX;
		}
		
		int getDistanceY() {
			return getCurrY() - startY;
		}
	}
	
	@TargetApi(VERSION_CODES.JELLY_BEAN)
	private void compatPostOnAnimation(Runnable runnable) {
		if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
			postOnAnimation(runnable);
		} else {
			postDelayed(runnable, 1000/60);
		}
	}
	
	private CompatAnimator mAnimator;
	static class CompatAnimator implements Runnable {
		private final long FRAME_DURATION = 1000/60;
		private View contextView;
		private long duration;
		private float start;
		private float end;
		private float animatedValue;
		private State state = State.STOP;
		
		private AnimatorUpdateListener updateListener;
		private AnimatorListener endListener;
		private long remainTime;
		
		public enum State {STOP, RUNNING, FINISHED, CANCELLED};
		
		public interface AnimatorUpdateListener {
			public void onAnimationUpdate(CompatAnimator animation);
		}
		
		public interface AnimatorListener {
			public void onAnimationEnd(CompatAnimator animation);
		}
		
		public static CompatAnimator ofFloat(float start, float end) {
			return new CompatAnimator(start, end);
		}
		
		private CompatAnimator(float start, float end) {
			this.start = start;
			this.end = end;
		}
		
		public void setDuration(long duration) {
			if (duration<0) {
				duration = -duration;
			}
			this.duration = duration;
			this.remainTime = duration;
		}
		
		public long getDuration() {
			return this.duration;
		}
		
		public boolean isRunning() {
			return this.state == State.RUNNING;
		}
		
		public boolean isStarted() {
			return this.state != State.STOP;
		}
		
		public void addUpdateListener(AnimatorUpdateListener listener) {
			this.updateListener = listener;
		}
		
		public void addListener(AnimatorListener listener) {
			this.endListener = listener;
		}
		
		public void start(View contextView) {
			animatedValue = start;
			this.contextView = contextView;
			state = State.RUNNING;
			contextView.postDelayed(this, FRAME_DURATION);
		}
		
		public void cancel() {
			if (state == State.RUNNING) {
				state = State.CANCELLED;
			}
		}
		
		public void end() {
			if (state == State.RUNNING) {
				state = State.FINISHED;
			}
		}
		
		public Object getAnimatedValue() {
			return Float.valueOf(animatedValue);
		}
		
		@Override
		public void run() {
			if (state == State.RUNNING) {
				animatedValue = animatorValueAt(duration-remainTime);
				remainTime -= FRAME_DURATION;
			} else if (state == State.FINISHED) {
				animatedValue = end;
				remainTime = 0;
			} else if (state == State.CANCELLED) {
				animatedValue = start;
				remainTime = 0;
			} else if (state == State.STOP) {
				remainTime = 0;
			}
			
			if (updateListener!=null) {
				updateListener.onAnimationUpdate(this);
			}
			
			if (state != State.RUNNING) {
				if (endListener!=null) {
					endListener.onAnimationEnd(this);
				}
				return;
			}
			
			//Last step to finish
			if (remainTime <= FRAME_DURATION) {
				state = State.FINISHED;
			}
			
			if (remainTime < 0) {
				remainTime = 0;
			}
			
			contextView.postDelayed(this, remainTime>=FRAME_DURATION? FRAME_DURATION: remainTime);
		}
		
		private float animatorValueAt(long t) {
			float dd = duration*duration;
			float a = (start-end)/dd;
			float x = (t-duration)*(t-duration);
			float c = end;
			return a * x + c;
		}
	}
}
