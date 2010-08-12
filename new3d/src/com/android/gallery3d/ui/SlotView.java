/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.ui;

import android.content.Context;
import android.graphics.Rect;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;

import com.android.gallery3d.anim.Animation;
import com.android.gallery3d.ui.PositionRepository.Position;
import com.android.gallery3d.util.Utils;

import java.util.LinkedHashMap;

public class SlotView extends GLView {
    private static final int MAX_VELOCITY = 2500;
    private static final int INDEX_NONE = -1;

    public static interface Listener {
        public void onLayoutChanged(int width, int height);
        public void onScrollPositionChanged(int position);
    }

    public interface SlotTapListener {
        public void onSingleTapUp(int index);
        public void onLongTap(int index);
    }

    private final GestureDetector mGestureDetector;
    private final ScrollerHelper mScroller;
    private final PositionRepository mPositions;

    private SlotTapListener mSlotTapListener;
    private Listener mListener;

    private int mTransitionOffsetX;

    // Use linked hash map to keep the rendering order
    private LinkedHashMap<Long, ItemEntry> mItems =
            new LinkedHashMap<Long, ItemEntry>();

    private MyAnimation mAnimation = null;
    private final Position mTempPosition = new Position();
    private final Layout mLayout = new Layout();

    public SlotView(Context context, PositionRepository repository) {
        mPositions = repository;
        mGestureDetector =
                new GestureDetector(context, new MyGestureListener());
        mScroller = new ScrollerHelper(context, new DecelerateInterpolator(1));
    }

    public void setSlotSize(int slotWidth, int slotHeight) {
        mLayout.setSlotSize(slotWidth, slotHeight);
    }

    public void setSlotGaps(int horizontalGap, int verticalGap) {
        mLayout.setSlotGaps(horizontalGap, verticalGap);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    public void addComponent(GLView view) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeComponent(GLView view) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void onLayout(boolean changeSize, int l, int t, int r, int b) {
        if (!changeSize) return;
        mLayout.setSize(r - l, b - t);
        if (mListener != null) mListener.onLayoutChanged(r - l, b - t);
    }

    public void startTransition() {
        mAnimation = new MyAnimation();
        mAnimation.start();
        mTransitionOffsetX = mScrollX;
        if (mItems.size() != 0) invalidate();
    }

    public void savePositions() {
        mPositions.clear();
        for (ItemEntry entry : mItems.values()) {
            Position position = entry.target.clone();
            position.x -= mScrollX;
            mPositions.putPosition(entry.item.getIdentity(), position);
        }
    }

    private void setScrollPosition(int position, boolean force) {
        position = Utils.clamp(position, 0, mLayout.mContentLength);
        if (!force && position == mScrollX) return;
        mScrollX = position;
        mLayout.setScrollPosition(position);
        if (mListener != null) mListener.onScrollPositionChanged(position);
    }

    public void putDisplayItem(Position target, DisplayItem item) {
        Long identity = Long.valueOf(item.getIdentity());
        Position source = mPositions.get(identity);
        if (source == null) {
            source = target.clone();
            source.alpha = 0f;
        } else {
            source = source.clone();
            source.x += mTransitionOffsetX;
        }
        mItems.put(identity, new ItemEntry(item, source, target));
    }

    public Rect getSlotRect(int slotIndex) {
        return mLayout.getSlotRect(slotIndex);
    }

    public void removeDisplayItem(DisplayItem item) {
        mItems.remove(item.getIdentity());
    }

    @Override
    protected boolean onTouch(MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mScroller.forceFinished();
                break;
        }
        return true;
    }


    public void setSlotTapListener(SlotTapListener listener) {
        mSlotTapListener = listener;
    }

    @Override
    protected void render(GLCanvas canvas) {
        super.render(canvas);
        long currentTimeMillis = canvas.currentAnimationTimeMillis();
        boolean more = mScroller.computeScrollOffset(currentTimeMillis);
        setScrollPosition(mScroller.getCurrentPosition(), false);
        float interpolate = 1f;
        if (mAnimation != null) {
            more |= mAnimation.calculate(currentTimeMillis);
            interpolate = mAnimation.value;
        }
        canvas.translate(-mScrollX, 0, 0);
        for (ItemEntry entry : mItems.values()) {
            renderItem(canvas, entry, interpolate);
        }
        canvas.translate(mScrollX, 0, 0);

        if (more) invalidate();
    }

    private void renderItem(
            GLCanvas canvas, ItemEntry entry, float interpolate) {
        canvas.save(GLCanvas.SAVE_FLAG_ALPHA | GLCanvas.SAVE_FLAG_MATRIX);
        Position position = mTempPosition;
        Position.interpolate(entry.source, entry.target, position, interpolate);
        canvas.multiplyAlpha(position.alpha);
        canvas.translate(position.x, position.y, position.z);
        canvas.rotate(position.theta, 0, 0, 1);
        entry.item.render(canvas);
        canvas.restore();
    }

    public static class MyAnimation extends Animation {
        public float value;

        public MyAnimation() {
            setInterpolator(new DecelerateInterpolator(4));
        }

        @Override
        protected void onCalculate(float progress) {
            value = progress;
        }
    }

    private static class ItemEntry {
        public DisplayItem item;
        public Position source;
        public Position target;

        public ItemEntry(DisplayItem item, Position source, Position target) {
            this.item = item;
            this.source = source;
            this.target = target;
        }
    }

    public static class Layout {

        private int mVisibleStart;
        private int mVisibleEnd;

        private int mSlotCount;
        private int mSlotWidth;
        private int mSlotHeight;

        private int mWidth;
        private int mHeight;

        private int mVerticalGap;
        private int mHorizontalGap;

        private int mRowCount;
        private int mContentLength;
        private int mScrollPosition;

        public void setSlotSize(int slotWidth, int slotHeight) {
            mSlotWidth = slotWidth;
            mSlotHeight = slotHeight;
        }

        public void setSlotCount(int slotCount) {
            mSlotCount = slotCount;
            initLayoutParameters();
        }

        public void setSlotGaps(int horizontalGap, int verticalGap) {
            mHorizontalGap = horizontalGap;
            mVerticalGap = verticalGap;
            initLayoutParameters();
        }

        public Rect getSlotRect(int index) {
            int col = index / mRowCount;
            int row = index - col * mRowCount;

            int x = col * (mHorizontalGap + mSlotWidth) + mHorizontalGap;
            int y = row * (mVerticalGap + mSlotHeight) + mVerticalGap;
            return new Rect(x, y, x + mSlotWidth, y + mSlotHeight);
        }

        public int getContentLength() {
            return mContentLength;
        }

        private void initLayoutParameters() {
            int rowCount = (mHeight - mVerticalGap) / (mVerticalGap + mSlotHeight);
            if (rowCount == 0) rowCount = 1;
            mRowCount = rowCount;
            mContentLength = ((mSlotCount + rowCount - 1) / rowCount)
                    * (mHorizontalGap + mSlotWidth) + mHorizontalGap - mWidth;
            if (mContentLength < 0) mContentLength = 0;
            updateVisibleSlotRange();
        }

        public void setSize(int width, int height) {
            mWidth = width;
            mHeight = height;
            initLayoutParameters();
        }

        private void updateVisibleSlotRange() {
            int position = mScrollPosition;
            int colWidth = mHorizontalGap + mSlotWidth;
            int rowHeight = mVerticalGap + mSlotHeight;
            int startColumn = position / colWidth;
            int endColumn = (position + mWidth + mSlotWidth - 1) / colWidth;

            setVisibleRange(startColumn * mRowCount,
                    Math.min(mSlotCount, endColumn * mRowCount));
        }

        public void setScrollPosition(int position) {
            if (mScrollPosition == position) return;
            mScrollPosition = position;
            updateVisibleSlotRange();
        }

        private void setVisibleRange(int start, int end) {
            if (start == mVisibleStart && end == mVisibleEnd) return;
            mVisibleStart = start;
            mVisibleEnd = end;
        }

        public int getVisibleStart() {
            return mVisibleStart;
        }

        public int getVisibleEnd() {
            return mVisibleEnd;
        }

        public int getSlotIndexByPosition(float x, float y) {
            int columnWidth = mHorizontalGap + mSlotWidth;
            float absoluteX = x + mScrollPosition;
            int columnIdx = (int) (absoluteX + 0.5) / columnWidth;
            if ((absoluteX - columnWidth * columnIdx) < mHorizontalGap) {
                return INDEX_NONE;
            }

            int rowHeight = mVerticalGap + mSlotHeight;
            float absoluteY = y;
            int rowIdx = (int) (absoluteY + 0.5) / rowHeight;
            if (((absoluteY - rowHeight * rowIdx) < mVerticalGap)
                || rowIdx >= mRowCount) {
                return INDEX_NONE;
            }
            int index = columnIdx * mRowCount + rowIdx;
            return index >= mSlotCount ? INDEX_NONE : index;
        }
    }

    private class MyGestureListener
            extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onFling(MotionEvent e1,
                MotionEvent e2, float velocityX, float velocityY) {
            int contentLength = mLayout.mContentLength;
            if (contentLength == 0) return false;
            velocityX = Utils.clamp(velocityX, -MAX_VELOCITY, MAX_VELOCITY);
            mScroller.fling(mScrollX, -(int) velocityX, 0, contentLength);
            invalidate();
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1,
                MotionEvent e2, float distanceX, float distanceY) {
            if (mLayout.mContentLength == 0) return false;
            setScrollPosition(mScrollX + (int) distanceX, false);
            invalidate();
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            int index = mLayout.getSlotIndexByPosition(e.getX(), e.getY());
            if (index != INDEX_NONE) mSlotTapListener.onSingleTapUp(index);
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            int index = mLayout.getSlotIndexByPosition(e.getX(), e.getY());
            if (index != INDEX_NONE) mSlotTapListener.onLongTap(index);
        }
   }

    public void setSlotCount(int slotCount) {
        mLayout.setSlotCount(slotCount);
    }

    public int getVisibleStart() {
        return mLayout.getVisibleStart();
    }

    public int getVisibleEnd() {
        return mLayout.getVisibleEnd();
    }
}