package com.kavmors.view.widget;

import android.content.Context;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;

/**
 * Implement of {@link View.OnTouchListener} that can detect more events after a touch of singleTap, doubleTap and multiTap. 
 * @author KavMors
 *
 */
public class OnTouchGestureListener implements View.OnTouchListener {
	public static final String TAG = "OnTouchGestureListener";
	
	private static final int LONGPRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();
    private static final int TAP_TIMEOUT = ViewConfiguration.getTapTimeout();
    
    private static final int DBL_LONG_PRESS = 1;
    private static final int MULTI_LONG_PRESS = 2;
    
    private boolean mLongClickable = true;
    private boolean mDblLongClickable = true;
    private boolean mMultiLongClickable = true;
    private OnTouchListener mUserTouchListener = null;
	
	private enum Mode { NONE, DOWN, MOVE, UP, DBL_DOWN, DBL_MOVE, DBL_UP, MULTI_DOWN, MULTI_MOVE, MULTI_UP};
	private Mode mMode = Mode.NONE;
	
	private OnDetectSingle mOnSingle = defaultOnDetectSingle();
	private OnDetectDouble mOnDouble = defaultOnDetectDouble();
	private OnDetectMulti mOnMulti = defaultOnDetectMulti();

	/**
	 * Detect single tap events
	 */
	public interface OnDetectSingle {
		public void onDown(PointF point);
		public void onMove(PointF from, PointF to, float dx, float dy);
		public void onFling(PointF from, PointF to, float vx, float vy);
		public void onUp(PointF point);
		public void onClick(PointF point);
		public void onLongClick(PointF point);
	}
	
	/**
	 * Detect double tap events
	 */
	public interface OnDetectDouble {
		public void onDblDown(PointF point);
		public void onDblMove(PointF from, PointF to, float dx, float dy);
		public void onDblUp(PointF point);
		public void onDblClick(PointF point);
		public void onDblLongClick(PointF point);
	}
	
	/**
	 * Detect multi-pointer events
	 */
	public interface OnDetectMulti {
		public void onMultiDown(PointF p0, PointF p1);
		public void onMultiMove(PointF oldPoint0, PointF oldPoint1, PointF newPoint0, PointF newPoint1);
		public void onMultiUp(PointF p0, PointF p1);
		public void onMultiClick(PointF p0, PointF p1);
		public void onMultiLongClick(PointF p0, PointF p1);
	}
	
	/**
	 * Creates a new Listener with the supplied {@link android.os.Handler}
	 * @param context The application's context
	 */
	public OnTouchGestureListener(Context context) {
		mContext = context;
		mSingleDetectorGesture = new SingleGestureListener();
		mSingleDetector = new GestureDetector(mContext, mSingleDetectorGesture);
		mMultiDetector = new MultiGestureDetector();
		mHandler = new Handler(new GestureHandler());
	}
	
	/**
	 * Set a single-tap listener
	 * @param listener The listener implements OnDetectSingle. It will be set to default if listener is null.
	 */
	public void setOnDetectSingle(OnDetectSingle listener) {
		mOnSingle = listener==null? defaultOnDetectSingle(): listener;
	}
	
	/**
	 * Set a double-tap listener
	 * @param listener The listener implements OnDetectDouble. It will be set to default if listener is null.
	 */
	public void setOnDetectDouble(OnDetectDouble listener) {
		mOnDouble = listener==null? defaultOnDetectDouble(): listener;
	}
	
	/**
	 * Set a multi-tap listener
	 * @param listener The listener implements OnDetectMulti. It will be set to default if listener is null.
	 */
	public void setOnDetectMulti(OnDetectMulti listener) {
		mOnMulti = listener==null? defaultOnDetectMulti(): listener;
	}
	
	/**
	 * Enables or disables long click events for this listener.
	 * @param longClickable True to make it long clickable, false otherwise
	 */
	public void setLongClickable(boolean longClickable) {
		mLongClickable = longClickable;
	}

	/**
	 * Enables or disables double-tap long click events for this listener.
	 * @param longClickable True to make it double-tap long clickable, false otherwise
	 */
	public void setDblLongClickable(boolean dblLongClickable) {
		mDblLongClickable = dblLongClickable;
	}

	/**
	 * Enables or disables multi-tap long click events for this listener.
	 * @param longClickable True to make it multi-tap long clickable, false otherwise
	 */
	public void setMultiLongClickable(boolean multiLongClickable) {
		mMultiLongClickable = multiLongClickable;
	}
	
	/**
	 * Set an OnTouchListener for user
	 * @param l The listener
	 */
	public void setUserTouchListener(OnTouchListener l) {
		mUserTouchListener = l;
	}
	
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		if (event.getPointerCount() < 2) {
			if (mSingleDetector.onTouchEvent(event)) {
				return true;
			} else {
				mSingleDetectorGesture.continueEvent(event);
			}
		} else {
			mMultiDetector.onTouch(event);
		}
		if (mUserTouchListener!=null) {
			mUserTouchListener.onTouch(v, event);
		}
		return true;
	}
	
	public static class Util {
		/**
		 * Calculate the distance of two points.
		 * @param p0
		 * @param p1
		 * @return Distance in float
		 */
		public static float distance(PointF p0, PointF p1) {
			float dx = p0.x - p1.x;
			float dy = p0.y - p1.y;
			return (float) Math.sqrt(dx*dx + dy*dy);
		}
		
		/**
		 * Calculate the center point of two points.
		 * @param p0
		 * @param p1
		 * @return Coordinate of center point in PointF
		 */
		public static PointF center(PointF p0, PointF p1) {
			float x = (p0.x + p1.x) / 2;
			float y = (p0.y + p1.y) / 2;
			return new PointF(x, y);
		}
	}
	
	private class GestureHandler implements Handler.Callback {
		GestureHandler() {
			super();
		}
		
		@Override
		public boolean handleMessage(Message msg) {
			switch (msg.what) {
			case DBL_LONG_PRESS:
				mMode = Mode.DBL_UP;
				mOnDouble.onDblLongClick((PointF) msg.obj);
				break;
			case MULTI_LONG_PRESS:
				mMode = Mode.MULTI_UP;
				PointF[] points = (PointF[]) msg.obj;
				mOnMulti.onMultiLongClick(points[0], points[1]);
				break;
			}
			return true;
		}
	}
	
	class SingleGestureListener extends SimpleOnGestureListener {
		//For all returns: true if the event is consumed, else false
		//The result will be returned to onTouch
		//False returned means that the event will continue in GestureImageView#onTouchListener after handled in GestureDetector
		
		//Single: Click, LongClick, DoubleCLick, Drag
		
		@Override
		public void onShowPress(MotionEvent e) {
		}
		
		@Override
		public boolean onDown(MotionEvent e) {
			if (mMode == Mode.NONE) {
				mHandler.removeMessages(DBL_LONG_PRESS);
				mHandler.removeMessages(MULTI_LONG_PRESS);
				mMode = Mode.DOWN;
				mOnSingle.onDown(new PointF(e.getX(), e.getY()));
			}
			return true;
		}
		
		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
			if (mMode == Mode.DOWN) {
				mMode = Mode.MOVE;
			}
			if (mMode == Mode.MOVE) {
				mOnSingle.onMove(new PointF(e1.getX(), e1.getY()), new PointF(e2.getX(), e2.getY()), dx, dy);
			}
			return false;
		}
		
		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			if (mMode == Mode.MOVE) {
				mMode = Mode.UP;
				mOnSingle.onUp(new PointF(e1.getX(), e1.getY()));
				mOnSingle.onFling(new PointF(e1.getX(), e2.getY()), new PointF(e2.getX(), e2.getY()), velocityX, velocityY);
				mMode = Mode.NONE;
			}
			return true;
	    }
		
		@Override
		public boolean onSingleTapConfirmed(MotionEvent e) {
			if (mMode == Mode.UP) {
				mOnSingle.onClick(new PointF(e.getX(), e.getY()));
				mMode = Mode.NONE;
			}
			return true;
		}
		
		@Override
		public void onLongPress(MotionEvent e) {
			if (mMode == Mode.DOWN) {
				if (mLongClickable) {
					mOnSingle.onLongClick(new PointF(e.getX(), e.getY()));
					mMode = Mode.UP;
				}
			}
			//will return false after touch-up
		}
		
		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			if (mMode == Mode.DOWN) {
				mMode = Mode.UP;
				mOnSingle.onUp(new PointF(e.getX(), e.getY()));
			}
			return true;
		}
		
		@Override
		public boolean onDoubleTap(MotionEvent e) {
			return true;
		}
		
		private PointF prePoint = new PointF();
		
		@Override
		public boolean onDoubleTapEvent(MotionEvent e) {
			switch (e.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				if (mMode == Mode.UP) {
					mMode = Mode.DBL_DOWN;
					mSingleDetector.setIsLongpressEnabled(false);	//Cannot react longClick while dblclick-moving
					prePoint.set(e.getX(), e.getY());
					mOnDouble.onDblDown(new PointF(e.getX(), e.getY()));
					
					if (mDblLongClickable) {
						Message msg = Message.obtain();
						msg.what = DBL_LONG_PRESS;
						msg.obj = prePoint;
						mHandler.sendMessageAtTime(msg, e.getDownTime()+TAP_TIMEOUT+LONGPRESS_TIMEOUT);
					}
				}
				break;
			case MotionEvent.ACTION_MOVE:
				if (mMode == Mode.DBL_DOWN && (Math.abs(e.getY()-prePoint.y)>10f || Math.abs(e.getX()-prePoint.x)>10f)) {
					mMode = Mode.DBL_MOVE;
					mHandler.removeMessages(DBL_LONG_PRESS);
				}
				if (mMode == Mode.DBL_MOVE) {
					mOnDouble.onDblMove(new PointF(prePoint.x, prePoint.y), new PointF(e.getX(), e.getY()), e.getX()-prePoint.x, e.getY()-prePoint.y);
					prePoint.set(e.getX(), e.getY());
				}
				break;
			case MotionEvent.ACTION_UP:
				mSingleDetector.setIsLongpressEnabled(true);
				mHandler.removeMessages(DBL_LONG_PRESS);
				if (mMode == Mode.DBL_DOWN) {	//for click
					mMode = Mode.DBL_UP;
					mOnDouble.onDblClick(new PointF(prePoint.x, prePoint.y));
					mOnDouble.onDblUp(new PointF(prePoint.x, prePoint.y));
				} else if (mMode == Mode.DBL_MOVE) {	//for up after move
					mMode = Mode.DBL_UP;
					mOnDouble.onDblUp(new PointF(e.getX(), e.getY()));
				} else if (mMode == Mode.DBL_UP) {		//for long click
					mOnDouble.onDblUp(new PointF(e.getX(), e.getY()));
				}
				mMode = Mode.NONE;
				break;
			default:
				break;
			}
			return true;
		}
		
		public void continueEvent(MotionEvent e) {
			if (e.getActionMasked() == MotionEvent.ACTION_UP) {
				if (mMode == Mode.DOWN || mMode == Mode.MOVE || mMode == Mode.UP) {
					mOnSingle.onUp(new PointF(e.getX(), e.getY()));
					mMode = Mode.NONE;
				}
			}
		}
	}
	
	class MultiGestureDetector {
		private PointF pre0 = new PointF();
		private PointF pre1 = new PointF();
		
		public boolean onTouch(MotionEvent e) {
			switch (e.getActionMasked()) {
			case MotionEvent.ACTION_POINTER_DOWN:
				if (mMode == Mode.DOWN || mMode == Mode.NONE) {
					mMode = Mode.MULTI_DOWN;
					mSingleDetector.setIsLongpressEnabled(false);
					pre0.set(e.getX(0), e.getY(0));
					pre1.set(e.getX(1), e.getY(1));
					mOnMulti.onMultiDown(new PointF(e.getX(0), e.getY(0)), new PointF(e.getX(1), e.getY(1)));
					
					if (mMultiLongClickable) {
						Message msg = Message.obtain();
						msg.what = MULTI_LONG_PRESS;
						msg.obj = new PointF[]{new PointF(e.getX(0), e.getY(0)), new PointF(e.getX(1), e.getY(1))};
						mHandler.sendMessageAtTime(msg, e.getDownTime()+TAP_TIMEOUT+LONGPRESS_TIMEOUT);
					}
				}
				break;
			case MotionEvent.ACTION_MOVE:
				if (mMode == Mode.MULTI_DOWN && isTouchMove(e)) {
					mMode = Mode.MULTI_MOVE;
					mHandler.removeMessages(MULTI_LONG_PRESS);
				}
				if (mMode == Mode.MULTI_MOVE) {
					mOnMulti.onMultiMove(pre0, pre1, new PointF(e.getX(0), e.getY(0)), new PointF(e.getX(1), e.getY(1)));
					pre0.set(e.getX(0), e.getY(0));
					pre1.set(e.getX(1), e.getY(1));
				}
				break;
			case MotionEvent.ACTION_POINTER_UP:
				mSingleDetector.setIsLongpressEnabled(true);
				mHandler.removeMessages(MULTI_LONG_PRESS);
				if (mMode == Mode.MULTI_DOWN) {		//for click
					mMode = Mode.MULTI_UP;
					mOnMulti.onMultiClick(new PointF(pre0.x, pre0.y), new PointF(pre1.x, pre1.y));
					mOnMulti.onMultiUp(new PointF(pre0.x, pre0.y), new PointF(pre1.x, pre1.y));
				} else if (mMode == Mode.MULTI_MOVE) {	//for up after move
					mMode = Mode.MULTI_UP;
					mOnMulti.onMultiUp(new PointF(e.getX(0), e.getY(0)), new PointF(e.getX(1), e.getY(1)));
				} else if (mMode == Mode.MULTI_UP) {		//for long click
					mOnMulti.onMultiUp(new PointF(e.getX(0), e.getY(0)), new PointF(e.getX(1), e.getY(1)));
				}
				mMode = Mode.NONE;
				break;
			default:
				break;
			}
			return true;
		}
		
		private boolean isTouchMove(MotionEvent e) {
			if (Math.abs(e.getX(0) - pre0.x) > 10f || Math.abs(e.getY(0) - pre0.y) > 10f) {
				return true;
			}
			if (Math.abs(e.getX(1) - pre1.x) > 10f || Math.abs(e.getY(1) - pre1.y) > 10f) {
				return true;
			}
			return false;
		}
	}

	private SingleGestureListener mSingleDetectorGesture;
	private GestureDetector mSingleDetector;
	private MultiGestureDetector mMultiDetector;
	
	private Context mContext;
	private Handler mHandler;
	
	//default gesture listener
	private static OnDetectSingle defaultOnDetectSingle() {
		return new OnDetectSingle() {
			public void onDown(PointF point) { Log.i(TAG, System.currentTimeMillis() + "--- onDown"); }
			public void onMove(PointF from, PointF to, float dx, float dy) { Log.i(TAG, System.currentTimeMillis() + "--- onMove"); }
			public void onFling(PointF from, PointF to, float vx, float vy) { Log.i(TAG, System.currentTimeMillis() + "--- onFling"); }
			public void onUp(PointF point) { Log.i(TAG, System.currentTimeMillis() + "--- onUp"); }
			public void onClick(PointF point) { Log.i(TAG, System.currentTimeMillis() + "--- onClick"); }
			public void onLongClick(PointF point) { Log.i(TAG, System.currentTimeMillis() + "--- onLongClick"); }
		};
	}
	
	private static OnDetectDouble defaultOnDetectDouble() {
		return new OnDetectDouble() {
			public void onDblDown(PointF point) { Log.i(TAG, System.currentTimeMillis() + "--- onDblDown"); }
			public void onDblMove(PointF from, PointF to, float dx, float dy) { Log.i(TAG, System.currentTimeMillis() + "--- onDblMove"); }
			public void onDblUp(PointF point) { Log.i(TAG, System.currentTimeMillis() + "--- onDblUp"); }
			public void onDblClick(PointF point) { Log.i(TAG, System.currentTimeMillis() + "--- onDblClick"); }
			public void onDblLongClick(PointF point) { Log.i(TAG, System.currentTimeMillis() + "--- onDblLongClick"); }
		};
	}
	
	private static OnDetectMulti defaultOnDetectMulti() {
		return new OnDetectMulti() {
			public void onMultiDown(PointF p0, PointF p1) { Log.i(TAG, System.currentTimeMillis() + "--- onMultiDown"); }
			public void onMultiMove(PointF oldPoint0, PointF oldPoint1, PointF newPoint0, PointF newPoint1) { Log.i(TAG, System.currentTimeMillis() + "--- onMultiMove"); }
			public void onMultiUp(PointF p0, PointF p1) { Log.i(TAG, System.currentTimeMillis() + "--- onMultiUp"); }
			public void onMultiClick(PointF p0, PointF p1) { Log.i(TAG, System.currentTimeMillis() + "--- onMultiClcik"); }
			public void onMultiLongClick(PointF p0, PointF p1) { Log.i(TAG, System.currentTimeMillis() + "--- onMultiLongClcik"); }
		};
	}
}